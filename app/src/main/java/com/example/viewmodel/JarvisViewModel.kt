package com.example.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.ai.GeminiService
import com.example.core.LocalAppPreferences
import com.example.core.ThemePreference
import com.example.core.UserPreferencesStore
import com.example.data.AdminLog
import com.example.data.ArtworkItem
import com.example.data.ChatMessage
import com.example.data.Conversation
import com.example.data.DrawingTutorStep
import com.example.data.JarvisLanguage
import com.example.data.JarvisRepository
import com.example.data.KnowledgeItem
import com.example.data.MangaPanel
import com.example.data.ProductivityTask
import com.example.data.SearchRecord
import com.example.data.StoryboardScene
import com.example.data.StudioProject
import com.example.data.SystemConfig
import com.example.data.UiState
import com.example.data.UserProfile
import com.example.data.WebSource
import com.example.services.GitHubBuildRun
import com.example.services.GitHubBuildService
import com.example.services.MediaExportHelper
import com.example.services.ParsedDocument
import com.example.services.WebSearchService
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

private const val TAG = "JarvisViewModel"

sealed interface ImageResultState {
    data object Idle : ImageResultState
    data class Loading(val statusLabel: String) : ImageResultState
    data class Success(
        val bitmap: Bitmap,
        val prompt: String,
        val title: String,
        val savedPath: String,
        val referenceBitmap: Bitmap? = null
    ) : ImageResultState
    data class Error(val message: String) : ImageResultState
}

sealed interface GenerationState<out T> {
    data object Idle : GenerationState<Nothing>
    data object Loading : GenerationState<Nothing>
    data class Success<T>(val data: T) : GenerationState<T>
    data class Error(val message: String) : GenerationState<Nothing>
}

data class LocalPrivateMessage(
    val id: String,
    val role: String,
    val text: String,
    val sources: List<WebSource> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class JarvisViewModel(
    private val repository: JarvisRepository,
    private val geminiService: GeminiService,
    private val webSearchService: WebSearchService,
    private val githubBuildService: GitHubBuildService,
    private val preferencesStore: UserPreferencesStore,
    val currentUserId: String
) : ViewModel() {

    private fun <T> Flow<T>.toUiStateFlow(errorLabel: String): StateFlow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                Log.w(TAG, errorLabel, root)
                emit(UiState.Error(root.message ?: errorLabel))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    // --- LOCAL PREFERENCES ---
    val localPreferences: StateFlow<LocalAppPreferences> = preferencesStore.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), LocalAppPreferences())

    // --- CLOUD FIRESTORE STREAMS ---
    val userProfile: StateFlow<UiState<UserProfile?>> = repository.observeUserProfile(currentUserId)
        .toUiStateFlow("Failed to load profile")

    val conversations: StateFlow<UiState<List<Conversation>>> = repository.observeConversations(currentUserId)
        .toUiStateFlow("Failed to load conversations")

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    val activeMessages: StateFlow<UiState<List<ChatMessage>>> = _activeConversationId
        .flatMapLatest { convId ->
            if (convId.isNullOrBlank()) flowOf(emptyList())
            else repository.observeMessages(currentUserId, convId)
        }
        .toUiStateFlow("Failed to load chat messages")

    val knowledgeItems: StateFlow<UiState<List<KnowledgeItem>>> = repository.observeKnowledge(currentUserId)
        .toUiStateFlow("Failed to load knowledge center")

    val artworks: StateFlow<UiState<List<ArtworkItem>>> = repository.observeArtworks(currentUserId)
        .toUiStateFlow("Failed to load anime gallery")

    val projects: StateFlow<UiState<List<StudioProject>>> = repository.observeProjects(currentUserId)
        .toUiStateFlow("Failed to load projects")

    val tasks: StateFlow<UiState<List<ProductivityTask>>> = repository.observeTasks(currentUserId)
        .toUiStateFlow("Failed to load tasks")

    val searchHistory: StateFlow<UiState<List<SearchRecord>>> = repository.observeSearchHistory(currentUserId)
        .toUiStateFlow("Failed to load search history")

    val systemConfig: StateFlow<UiState<SystemConfig>> = repository.observeSystemConfig(currentUserId)
        .toUiStateFlow("Failed to load system config")

    // --- CHAT & LIVE WEB SEARCH STATE ---
    private val _streamingReply = MutableStateFlow("")
    val streamingReply: StateFlow<String> = _streamingReply.asStateFlow()

    private val _chatBusy = MutableStateFlow(false)
    val chatBusy: StateFlow<Boolean> = _chatBusy.asStateFlow()

    private val _chatError = MutableStateFlow<String?>(null)
    val chatError: StateFlow<String?> = _chatError.asStateFlow()

    private val _privateMode = MutableStateFlow(false)
    val privateMode: StateFlow<Boolean> = _privateMode.asStateFlow()

    private val _privateMessages = MutableStateFlow<List<LocalPrivateMessage>>(emptyList())
    val privateMessages: StateFlow<List<LocalPrivateMessage>> = _privateMessages.asStateFlow()

    private val _webSearchMode = MutableStateFlow(false)
    val webSearchMode: StateFlow<Boolean> = _webSearchMode.asStateFlow()

    private val _latestWebSources = MutableStateFlow<List<WebSource>>(emptyList())
    val latestWebSources: StateFlow<List<WebSource>> = _latestWebSources.asStateFlow()

    private val _selectedModelId = MutableStateFlow("gemini-3.8-flash")
    val selectedModelId: StateFlow<String> = _selectedModelId.asStateFlow()

    private val _selectedLanguage = MutableStateFlow(JarvisLanguage.ENGLISH)
    val selectedLanguage: StateFlow<JarvisLanguage> = _selectedLanguage.asStateFlow()

    private val _toastBanner = MutableStateFlow<String?>(null)
    val toastBanner: StateFlow<String?> = _toastBanner.asStateFlow()

    // --- CREATIVE STUDIO STATES ---
    private val _animeSketchState = MutableStateFlow<ImageResultState>(ImageResultState.Idle)
    val animeSketchState: StateFlow<ImageResultState> = _animeSketchState.asStateFlow()

    private val _photoToAnimeState = MutableStateFlow<ImageResultState>(ImageResultState.Idle)
    val photoToAnimeState: StateFlow<ImageResultState> = _photoToAnimeState.asStateFlow()

    private val _generalImageState = MutableStateFlow<ImageResultState>(ImageResultState.Idle)
    val generalImageState: StateFlow<ImageResultState> = _generalImageState.asStateFlow()

    private val _drawingTutorState = MutableStateFlow<GenerationState<List<DrawingTutorStep>>>(GenerationState.Idle)
    val drawingTutorState: StateFlow<GenerationState<List<DrawingTutorStep>>> = _drawingTutorState.asStateFlow()

    private val _characterSheetState = MutableStateFlow<GenerationState<String>>(GenerationState.Idle)
    val characterSheetState: StateFlow<GenerationState<String>> = _characterSheetState.asStateFlow()

    private val _animeSeriesState = MutableStateFlow<GenerationState<String>>(GenerationState.Idle)
    val animeSeriesState: StateFlow<GenerationState<String>> = _animeSeriesState.asStateFlow()

    private val _mangaScriptState = MutableStateFlow<GenerationState<List<MangaPanel>>>(GenerationState.Idle)
    val mangaScriptState: StateFlow<GenerationState<List<MangaPanel>>> = _mangaScriptState.asStateFlow()

    private val _mangaArtState = MutableStateFlow<ImageResultState>(ImageResultState.Idle)
    val mangaArtState: StateFlow<ImageResultState> = _mangaArtState.asStateFlow()

    private val _storyboardState = MutableStateFlow<GenerationState<List<StoryboardScene>>>(GenerationState.Idle)
    val storyboardState: StateFlow<GenerationState<List<StoryboardScene>>> = _storyboardState.asStateFlow()

    // --- PROJECTS, CODING, DOCUMENTS, APK BUILD ---
    private val _codingOutputState = MutableStateFlow<GenerationState<String>>(GenerationState.Idle)
    val codingOutputState: StateFlow<GenerationState<String>> = _codingOutputState.asStateFlow()

    private val _webPreviewHtml = MutableStateFlow<String>("")
    val webPreviewHtml: StateFlow<String> = _webPreviewHtml.asStateFlow()

    private val _loadedDocument = MutableStateFlow<ParsedDocument?>(null)
    val loadedDocument: StateFlow<ParsedDocument?> = _loadedDocument.asStateFlow()

    private val _documentAnalysisState = MutableStateFlow<GenerationState<String>>(GenerationState.Idle)
    val documentAnalysisState: StateFlow<GenerationState<String>> = _documentAnalysisState.asStateFlow()

    private val _apkWorkflowYaml = MutableStateFlow("")
    val apkWorkflowYaml: StateFlow<String> = _apkWorkflowYaml.asStateFlow()

    private val _githubRunsState = MutableStateFlow<GenerationState<List<GitHubBuildRun>>>(GenerationState.Idle)
    val githubRunsState: StateFlow<GenerationState<List<GitHubBuildRun>>> = _githubRunsState.asStateFlow()

    private val _githubDispatchMessage = MutableStateFlow<String?>(null)
    val githubDispatchMessage: StateFlow<String?> = _githubDispatchMessage.asStateFlow()

    // --- OWNER ADMIN DASHBOARD ---
    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private val _adminUsersState = MutableStateFlow<GenerationState<List<UserProfile>>>(GenerationState.Idle)
    val adminUsersState: StateFlow<GenerationState<List<UserProfile>>> = _adminUsersState.asStateFlow()

    private val _adminLogsState = MutableStateFlow<GenerationState<List<AdminLog>>>(GenerationState.Idle)
    val adminLogsState: StateFlow<GenerationState<List<AdminLog>>> = _adminLogsState.asStateFlow()

    init {
        viewModelScope.launch {
            val profileResult = repository.ensureUserProfile()
            profileResult.getOrNull()?.let { profile ->
                if (profile.preferredModel.isNotBlank()) _selectedModelId.value = profile.preferredModel
                if (profile.preferredLanguage.isNotBlank()) {
                    _selectedLanguage.value = JarvisLanguage.fromCode(profile.preferredLanguage)
                }
            }
            val adminVerified = repository.checkIsAdmin()
            _isAdmin.value = adminVerified
            if (adminVerified) {
                refreshAdminDashboard()
            }
        }
    }

    fun clearToast() {
        _toastBanner.value = null
    }

    fun showToast(msg: String) {
        _toastBanner.value = msg
    }

    // --- SETTINGS ---

    fun setTheme(theme: ThemePreference) {
        viewModelScope.launch { preferencesStore.setTheme(theme) }
    }

    fun setVoiceSettings(rate: Float, pitch: Float, autoSpeak: Boolean, voiceName: String) {
        viewModelScope.launch { preferencesStore.setVoiceSettings(rate, pitch, autoSpeak, voiceName) }
    }

    fun selectModel(modelId: String) {
        _selectedModelId.value = modelId
        viewModelScope.launch { repository.updateUserPreferences(model = modelId) }
    }

    fun selectLanguage(lang: JarvisLanguage) {
        _selectedLanguage.value = lang
        viewModelScope.launch { repository.updateUserPreferences(language = lang.code) }
    }

    fun togglePrivateMode(enabled: Boolean) {
        _privateMode.value = enabled
        if (enabled) {
            showToast("Private mode active: messages stay in memory only.")
        }
    }

    fun toggleWebSearchMode(enabled: Boolean) {
        _webSearchMode.value = enabled
    }

    fun startNewConversation() {
        _activeConversationId.value = null
        _privateMessages.value = emptyList()
        _latestWebSources.value = emptyList()
        _chatError.value = null
    }

    fun selectConversation(conversationId: String) {
        _privateMode.value = false
        _activeConversationId.value = conversationId
        _chatError.value = null
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            if (_activeConversationId.value == conversationId) _activeConversationId.value = null
            repository.deleteConversation(conversationId)
        }
    }

    // --- CHAT + LIVE WEB SEARCH + VOICE REPLY ---

    fun sendChatMessage(
        prompt: String,
        attachedImage: Bitmap? = null,
        onCompleteReply: ((String) -> Unit)? = null
    ) {
        val cleaned = prompt.trim()
        if (cleaned.isBlank() || _chatBusy.value) return

        viewModelScope.launch {
            _chatBusy.value = true
            _chatError.value = null
            _streamingReply.value = ""
            _latestWebSources.value = emptyList()

            try {
                val modelId = _selectedModelId.value
                val language = _selectedLanguage.value
                val isPrivate = _privateMode.value

                // 1. Live Web Search if enabled
                var webSources: List<WebSource> = emptyList()
                if (_webSearchMode.value) {
                    val searchRes = webSearchService.searchLiveWeb(cleaned)
                    webSources = searchRes.getOrElse { err ->
                        Log.w(TAG, "Web search warning: ${err.message}")
                        emptyList()
                    }
                    _latestWebSources.value = webSources
                }

                // 2. Active pinned memory context
                val pinnedMemory = (knowledgeItems.value as? UiState.Success)?.data
                    ?.filter { it.pinned && it.itemType == "memory" }
                    ?.take(8)
                    ?.joinToString("\n") { "- ${it.title}: ${it.content.take(300)}" }
                    .orEmpty()

                // 3. History & persistence
                var targetConvId = _activeConversationId.value
                val historyPairs: List<Pair<String, String>> = if (isPrivate) {
                    _privateMessages.value.map { it.role to it.text }
                } else {
                    (activeMessages.value as? UiState.Success)?.data?.map { it.role to it.text }.orEmpty()
                }

                if (isPrivate) {
                    _privateMessages.value = _privateMessages.value + LocalPrivateMessage(
                        id = "u_${System.currentTimeMillis()}",
                        role = "user",
                        text = cleaned
                    )
                } else {
                    if (targetConvId.isNullOrBlank()) {
                        val created = repository.createConversation(
                            title = cleaned.take(60),
                            modelUsed = modelId,
                            language = language.code
                        )
                        targetConvId = created.getOrThrow()
                        _activeConversationId.value = targetConvId
                    }
                    repository.appendMessage(
                        conversationId = targetConvId,
                        role = "user",
                        text = cleaned,
                        modelUsed = modelId,
                        language = language.code
                    )
                }

                // 4. Stream Gemini response via Firebase AI Logic
                val fullReply = StringBuilder()
                withTimeout(60_000L) {
                    geminiService.streamChatReply(
                        modelId = modelId,
                        language = language,
                        memoryContext = pinnedMemory,
                        history = historyPairs,
                        userPrompt = cleaned,
                        attachedImage = attachedImage,
                        webSources = webSources
                    ).collect { chunk ->
                        fullReply.append(chunk)
                        _streamingReply.value = fullReply.toString()
                    }
                }

                val finalReply = fullReply.toString().trim()
                if (finalReply.isBlank()) {
                    throw IllegalStateException("Gemini returned an empty reply.")
                }

                if (isPrivate) {
                    _privateMessages.value = _privateMessages.value + LocalPrivateMessage(
                        id = "a_${System.currentTimeMillis()}",
                        role = "assistant",
                        text = finalReply,
                        sources = webSources
                    )
                } else if (!targetConvId.isNullOrBlank()) {
                    repository.appendMessage(
                        conversationId = targetConvId,
                        role = "assistant",
                        text = finalReply,
                        sources = webSources,
                        modelUsed = modelId,
                        language = language.code
                    )
                    repository.updateUserPreferences(incrementPrompts = true)
                    if (_webSearchMode.value) {
                        repository.saveSearchRecord(
                            query = cleaned,
                            summary = finalReply.take(450),
                            sourceCount = webSources.size
                        )
                    }
                }

                _streamingReply.value = ""
                onCompleteReply?.invoke(finalReply)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Chat timed out", e)
                _chatError.value = "Response timed out after 60 seconds. Please try again."
                repository.logAdminEvent("ai_timeout", "Chat generation timed out", "warning")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Chat error", e)
                _chatError.value = e.localizedMessage ?: "Failed to generate Gemini reply."
                repository.logAdminEvent("ai_chat_error", e.localizedMessage ?: "Unknown error", "error")
            } finally {
                _chatBusy.value = false
                _streamingReply.value = ""
            }
        }
    }

    // --- ANIME SKETCH ARTIST & TOKYO REVENGERS STUDIO ---

    fun generateAnimeSketch(
        context: Context,
        universe: String,
        characterPreset: String,
        drawingStyle: String,
        viewAngle: String,
        expression: String,
        pencilGrade: String,
        crossHatching: Boolean,
        paperTexture: Boolean,
        customDetails: String
    ) {
        viewModelScope.launch {
            _animeSketchState.value = ImageResultState.Loading("Synthesizing $pencilGrade graphite anime sketch (30–60s)...")
            try {
                val engineeredPrompt = buildString {
                    append("Professional Japanese manga and anime hand-drawn artwork. ")
                    append("Original fan-art study inspired by $universe aesthetic (not official artwork). ")
                    append("Subject: $characterPreset. ")
                    append("Framing: $viewAngle. Expression: $expression. Style: $drawingStyle. ")
                    append("Medium: $pencilGrade graphite pencil strokes, fine anime hair detailing, expressive anime eye detailing, dramatic facial shadows. ")
                    if (crossHatching) append("Intricate mangaka cross-hatching and deep dark graphite shading with smooth tonal blending. ")
                    if (paperTexture) append("Drawn on realistic off-white heavyweight sketchbook paper texture with subtle graphite grain. ")
                    if (customDetails.isNotBlank()) append("Additional artist notes: $customDetails. ")
                    append("High contrast, crisp masterwork line art, no watermarks or text.")
                }

                val bitmap = geminiService.generateAnimeOrArtImage(
                    prompt = engineeredPrompt,
                    aspectRatioLabel = "3:4"
                )
                val title = "$characterPreset ($pencilGrade Sketch)"
                val savedPath = MediaExportHelper.saveBitmapToInternalArtworks(context, bitmap, "anime_sketch")
                repository.saveArtwork(
                    title = title,
                    prompt = engineeredPrompt,
                    universe = universe,
                    style = drawingStyle,
                    pencilGrade = pencilGrade,
                    collectionName = universe,
                    localFilePath = savedPath
                )
                _animeSketchState.value = ImageResultState.Success(
                    bitmap = bitmap,
                    prompt = engineeredPrompt,
                    title = title,
                    savedPath = savedPath
                )
                showToast("Saved sketch to Anime Art Gallery.")
            } catch (e: TimeoutCancellationException) {
                _animeSketchState.value = ImageResultState.Error("Image generation timed out after 60 seconds. Tap Generate to retry.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Anime sketch generation failed", e)
                _animeSketchState.value = ImageResultState.Error(
                    e.localizedMessage ?: "Failed to generate anime sketch. Ensure Firebase Blaze plan & App Check are active."
                )
            }
        }
    }

    fun convertPhotoToAnimeSketch(
        context: Context,
        sourceBitmap: Bitmap,
        targetMode: String,
        intensity: Float,
        backgroundStyle: String
    ) {
        viewModelScope.launch {
            _photoToAnimeState.value = ImageResultState.Loading("Transforming photo into $targetMode (30–60s)...")
            try {
                val intensityDesc = when {
                    intensity >= 0.75f -> "bold dark 6B-8B high-contrast shading and crisp ink contours"
                    intensity >= 0.4f -> "balanced 4B graphite cross-hatching and fine linework"
                    else -> "delicate light HB-2B sketchbook pencil lines"
                }
                val editPrompt = """
                    Transform this reference photo into a professional Japanese $targetMode illustration.
                    Preserve the person's pose, hairstyle silhouette, and key facial geometry while rendering in authentic Japanese anime/manga style.
                    Sketch intensity: $intensityDesc.
                    Background treatment: $backgroundStyle.
                    Clean master-grade result, no text or watermarks.
                """.trimIndent()

                val resultBitmap = geminiService.generateAnimeOrArtImage(
                    prompt = editPrompt,
                    aspectRatioLabel = "1:1",
                    referenceImage = sourceBitmap
                )
                val savedPath = MediaExportHelper.saveBitmapToInternalArtworks(context, resultBitmap, "photo_anime")
                repository.saveArtwork(
                    title = "Photo to $targetMode",
                    prompt = editPrompt,
                    universe = "Photo Conversion",
                    style = targetMode,
                    pencilGrade = "4B",
                    collectionName = "Photo to Anime",
                    localFilePath = savedPath
                )
                _photoToAnimeState.value = ImageResultState.Success(
                    bitmap = resultBitmap,
                    prompt = editPrompt,
                    title = "Photo to $targetMode",
                    savedPath = savedPath,
                    referenceBitmap = sourceBitmap
                )
                showToast("Converted photo saved to Gallery.")
            } catch (e: TimeoutCancellationException) {
                _photoToAnimeState.value = ImageResultState.Error("Photo conversion timed out after 60 seconds.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Photo-to-anime failed", e)
                _photoToAnimeState.value = ImageResultState.Error(e.localizedMessage ?: "Photo conversion failed.")
            }
        }
    }

    fun generateDrawingTutor(subject: String, universe: String, pencilGrade: String) {
        viewModelScope.launch {
            _drawingTutorState.value = GenerationState.Loading
            try {
                val steps = geminiService.generateAnimeDrawingTutor(subject, universe, pencilGrade)
                _drawingTutorState.value = GenerationState.Success(steps)
            } catch (e: TimeoutCancellationException) {
                _drawingTutorState.value = GenerationState.Error("Tutor generation timed out.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Tutor failed", e)
                _drawingTutorState.value = GenerationState.Error(e.localizedMessage ?: "Failed to generate drawing guide.")
            }
        }
    }

    fun generateCharacterProfile(archetype: String, universe: String, hairStyle: String, outfit: String, expression: String) {
        viewModelScope.launch {
            _characterSheetState.value = GenerationState.Loading
            try {
                val prompt = """
                    Create a detailed original Japanese anime Character Design Sheet & Consistency DNA profile:
                    Archetype: $archetype | Inspired Genre/Universe: $universe
                    Hairstyle: $hairStyle | Outfit: $outfit | Signature Expression: $expression
                    Include:
                    1. Full Name (Japanese + Romaji) & Alias
                    2. Personality & Combat/Special Abilities
                    3. Visual Consistency Prompt DNA (exact repeatable visual description to keep this character consistent across scenes)
                    4. Backstory & Rivalries
                    5. Signature Catchphrase
                """.trimIndent()
                val sheet = geminiService.generateText(prompt)
                _characterSheetState.value = GenerationState.Success(sheet)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _characterSheetState.value = GenerationState.Error(e.localizedMessage ?: "Failed to generate character sheet.")
            }
        }
    }

    // --- ANIME MOVIE & SERIES CREATOR ---

    fun generateAnimeSeries(title: String, genre: String, premise: String, seasons: Int) {
        viewModelScope.launch {
            _animeSeriesState.value = GenerationState.Loading
            try {
                val bible = geminiService.generateAnimeSeriesBible(title, genre, premise, seasons)
                _animeSeriesState.value = GenerationState.Success(bible)
                repository.saveProject(
                    title = title.ifBlank { "Untitled Anime Series" },
                    projectType = "anime_series",
                    categoryTag = genre,
                    summary = premise.take(300),
                    contentText = bible
                )
                showToast("Anime Series Bible saved to Projects.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _animeSeriesState.value = GenerationState.Error(e.localizedMessage ?: "Failed to create anime series.")
            }
        }
    }

    // --- MANGA CREATOR ---

    fun generateMangaPage(context: Context, title: String, genre: String, premise: String, renderArtwork: Boolean) {
        viewModelScope.launch {
            _mangaScriptState.value = GenerationState.Loading
            try {
                val panels = geminiService.generateMangaScript(title, genre, premise)
                _mangaScriptState.value = GenerationState.Success(panels)
                val formattedScript = panels.joinToString("\n\n") { p ->
                    "PANEL ${p.panelNumber} [${p.shotType}]\nVisual: ${p.visualDescription}\nSpeech Bubble: ${p.dialogue}\nSFX: ${p.soundEffect}"
                }
                repository.saveProject(
                    title = "${title.ifBlank { "Manga" }} — Chapter Page",
                    projectType = "manga",
                    categoryTag = genre,
                    summary = premise.take(300),
                    contentText = formattedScript
                )
                if (renderArtwork) {
                    _mangaArtState.value = ImageResultState.Loading("Inking black-and-white manga page (30–60s)...")
                    val pagePrompt = "Authentic Japanese black-and-white shonen manga comic page layout with 4 dynamic ink panels, dramatic speedlines, screentone shading, clean white speech bubbles, high-contrast G-pen ink linework. Story: $title ($genre) - ${panels.take(3).joinToString(" | ") { it.visualDescription }}"
                    val bmp = geminiService.generateAnimeOrArtImage(pagePrompt, "3:4")
                    val path = MediaExportHelper.saveBitmapToInternalArtworks(context, bmp, "manga_page")
                    repository.saveArtwork(
                        title = "$title Manga Page",
                        prompt = pagePrompt,
                        universe = genre,
                        style = "Black-and-White Manga",
                        pencilGrade = "G-Pen Ink",
                        collectionName = "Manga Pages",
                        localFilePath = path
                    )
                    _mangaArtState.value = ImageResultState.Success(bmp, pagePrompt, "$title Manga Page", path)
                }
                showToast("Manga script saved to Projects.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _mangaScriptState.value = GenerationState.Error(e.localizedMessage ?: "Manga generation failed.")
                if (renderArtwork) {
                    _mangaArtState.value = ImageResultState.Error(e.localizedMessage ?: "Manga page render failed.")
                }
            }
        }
    }

    // --- GENERAL AI IMAGE STUDIO ---

    fun generateGeneralImage(
        context: Context,
        prompt: String,
        category: String,
        aspectRatio: String,
        referenceBitmap: Bitmap? = null
    ) {
        viewModelScope.launch {
            _generalImageState.value = ImageResultState.Loading("Generating $category image ($aspectRatio, 30–60s)...")
            try {
                val fullPrompt = "Category: $category. High-craft visual artwork: $prompt"
                val bmp = geminiService.generateAnimeOrArtImage(fullPrompt, aspectRatio, referenceBitmap)
                val path = MediaExportHelper.saveBitmapToInternalArtworks(context, bmp, "jarvis_art")
                repository.saveArtwork(
                    title = prompt.take(48).ifBlank { "$category Art" },
                    prompt = fullPrompt,
                    universe = category,
                    style = category,
                    pencilGrade = aspectRatio,
                    collectionName = category,
                    localFilePath = path
                )
                _generalImageState.value = ImageResultState.Success(bmp, fullPrompt, prompt.take(48), path, referenceBitmap)
                showToast("Image saved to Gallery.")
            } catch (e: TimeoutCancellationException) {
                _generalImageState.value = ImageResultState.Error("Image generation timed out after 60 seconds.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _generalImageState.value = ImageResultState.Error(e.localizedMessage ?: "Image generation failed.")
            }
        }
    }

    // --- VIDEO STORYBOARD STUDIO ---

    fun generateVideoStoryboard(concept: String, style: String) {
        viewModelScope.launch {
            _storyboardState.value = GenerationState.Loading
            try {
                val scenes = geminiService.generateVideoStoryboard(concept, style)
                _storyboardState.value = GenerationState.Success(scenes)
                val srt = buildSrtFromStoryboard(scenes)
                repository.saveProject(
                    title = "Storyboard: ${concept.take(48)}",
                    projectType = "storyboard",
                    categoryTag = style,
                    summary = "${scenes.size} scenes • ${scenes.sumOf { it.durationSec }}s total",
                    contentText = srt
                )
                showToast("Storyboard & SRT subtitles saved to Projects.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _storyboardState.value = GenerationState.Error(e.localizedMessage ?: "Storyboard generation failed.")
            }
        }
    }

    fun buildSrtFromStoryboard(scenes: List<StoryboardScene>): String {
        var cursorSec = 0
        return scenes.mapIndexed { idx, scene ->
            val start = formatSrtTime(cursorSec)
            cursorSec += scene.durationSec.coerceAtLeast(2)
            val end = formatSrtTime(cursorSec)
            "${idx + 1}\n$start --> $end\n${scene.subtitle}\n"
        }.joinToString("\n")
    }

    private fun formatSrtTime(totalSec: Int): String {
        val mins = totalSec / 60
        val secs = totalSec % 60
        return String.format("00:%02d:%02d,000", mins, secs)
    }

    // --- CODING STUDIO & WEBSITE/APP BUILDER ---

    fun runCodingStudio(
        language: String,
        mode: String,
        promptOrCode: String,
        saveToProjects: Boolean = true
    ) {
        viewModelScope.launch {
            _codingOutputState.value = GenerationState.Loading
            try {
                val systemPrompt = when (mode) {
                    "Explain" -> "Explain this $language code clearly step by step, highlighting edge cases and complexity:\n\n$promptOrCode"
                    "Debug" -> "Debug and fix this $language code. Explain the root cause and provide the corrected production-ready code:\n\n$promptOrCode"
                    "Web App Builder" -> """
                        Build a complete, self-contained single-file responsive HTML5 + CSS3 + Vanilla JavaScript web app for:
                        "$promptOrCode"
                        Include modern dark futuristic styling, interactive JavaScript logic, and clean semantic HTML.
                        Output ONLY the raw complete HTML document starting with <!DOCTYPE html> and ending with </html> (no markdown fences).
                    """.trimIndent()
                    else -> """
                        Act as a principal $language engineer. Write clean, complete, production-ready $language code for:
                        "$promptOrCode"
                        Include brief architecture notes and usage instructions. Never claim code was executed.
                    """.trimIndent()
                }

                val result = geminiService.generateText(systemPrompt, _selectedModelId.value)
                _codingOutputState.value = GenerationState.Success(result)

                if (mode == "Web App Builder" || language == "HTML") {
                    val cleanedHtml = result
                        .removePrefix("```html")
                        .removePrefix("```")
                        .removeSuffix("```")
                        .trim()
                    if (cleanedHtml.contains("<html", ignoreCase = true) || cleanedHtml.contains("<body", ignoreCase = true)) {
                        _webPreviewHtml.value = cleanedHtml
                    }
                }

                if (saveToProjects) {
                    repository.saveProject(
                        title = "${if (mode == "Web App Builder") "Web App" else "$language $mode"}: ${promptOrCode.take(40)}",
                        projectType = if (mode == "Web App Builder") "web_app" else "code",
                        categoryTag = language,
                        summary = "$mode generated with ${_selectedModelId.value}",
                        contentText = result
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _codingOutputState.value = GenerationState.Error(e.localizedMessage ?: "Coding studio request failed.")
            }
        }
    }

    // --- DOCUMENT ASSISTANT ---

    fun setLoadedDocument(doc: ParsedDocument) {
        _loadedDocument.value = doc
        _documentAnalysisState.value = GenerationState.Idle
    }

    fun analyzeLoadedDocument(action: String, customQuestion: String = "") {
        val doc = _loadedDocument.value ?: return
        viewModelScope.launch {
            _documentAnalysisState.value = GenerationState.Loading
            try {
                val prompt = when (action) {
                    "Summarize" -> "Provide a clear executive summary and key bullet points for document '${doc.fileName}':\n\n${doc.extractedText}"
                    "Translate" -> "Translate the key content of '${doc.fileName}' into ${_selectedLanguage.value.displayName}:\n\n${doc.extractedText}"
                    "Extract" -> "Extract all structured facts, names, dates, numbers, and action items from '${doc.fileName}':\n\n${doc.extractedText}"
                    else -> "Answer this question about document '${doc.fileName}': $customQuestion\n\nDocument content:\n${doc.extractedText}"
                }
                val reply = geminiService.generateText(
                    prompt = prompt,
                    modelId = _selectedModelId.value,
                    attachedImages = doc.pdfPageBitmaps
                )
                _documentAnalysisState.value = GenerationState.Success(reply)
                repository.saveKnowledgeItem(
                    title = "${action}: ${doc.fileName}",
                    content = reply,
                    folder = "Documents",
                    itemType = "document",
                    pinned = false
                )
                showToast("Document analysis saved to Knowledge Center.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _documentAnalysisState.value = GenerationState.Error(e.localizedMessage ?: "Document analysis failed.")
            }
        }
    }

    // --- ANDROID APK BUILD MANAGER (GITHUB ACTIONS) ---

    fun generateApkWorkflowConfig(appName: String, packageName: String, versionName: String, buildType: String) {
        val yaml = githubBuildService.generateAndroidBuildWorkflowYaml(appName, packageName, versionName, buildType)
        _apkWorkflowYaml.value = yaml
        viewModelScope.launch {
            repository.saveProject(
                title = "APK Build: $appName ($buildType)",
                projectType = "apk_config",
                categoryTag = packageName,
                summary = "Version $versionName • GitHub Actions Gradle workflow",
                contentText = yaml
            )
            showToast("Saved APK build workflow to Projects.")
        }
    }

    fun saveGitHubConnection(ownerRepo: String, workflowFile: String, token: String) {
        viewModelScope.launch {
            preferencesStore.setGitHubConfig(ownerRepo, workflowFile, token)
            showToast("GitHub Actions connection updated locally.")
        }
    }

    fun triggerRemoteApkBuild() {
        val prefs = localPreferences.value
        viewModelScope.launch {
            _githubDispatchMessage.value = "Dispatching GitHub Actions workflow..."
            val res = githubBuildService.triggerWorkflowDispatch(
                ownerRepo = prefs.githubOwnerRepo,
                workflowFile = prefs.githubWorkflowFile,
                token = prefs.githubToken
            )
            _githubDispatchMessage.value = res.fold(
                onSuccess = { it },
                onFailure = { it.localizedMessage ?: "Build trigger failed." }
            )
            if (res.isSuccess) fetchRemoteBuildRuns()
        }
    }

    fun fetchRemoteBuildRuns() {
        val prefs = localPreferences.value
        viewModelScope.launch {
            _githubRunsState.value = GenerationState.Loading
            val res = githubBuildService.fetchWorkflowRuns(prefs.githubOwnerRepo, prefs.githubToken)
            _githubRunsState.value = res.fold(
                onSuccess = { GenerationState.Success(it) },
                onFailure = { GenerationState.Error(it.localizedMessage ?: "Could not load workflow runs.") }
            )
        }
    }

    // --- KNOWLEDGE / GALLERY / TASKS / PROJECTS CRUD ---

    fun addKnowledgeItem(title: String, content: String, folder: String, itemType: String, pinned: Boolean) {
        viewModelScope.launch {
            val res = repository.saveKnowledgeItem(title, content, folder, itemType, pinned)
            showToast(if (res.isSuccess) "Saved to Knowledge Center." else "Save failed: ${res.exceptionOrNull()?.message}")
        }
    }

    fun toggleKnowledgePinned(item: KnowledgeItem) {
        viewModelScope.launch {
            repository.updateKnowledgeItem(item.id, item.title, item.content, item.folder, !item.pinned)
        }
    }

    fun deleteKnowledgeItem(itemId: String) {
        viewModelScope.launch { repository.deleteKnowledgeItem(itemId) }
    }

    fun toggleArtworkFavorite(item: ArtworkItem) {
        viewModelScope.launch {
            repository.updateArtwork(item.id, item.title, item.collectionName, !item.favorite)
        }
    }

    fun renameArtwork(item: ArtworkItem, newTitle: String, newCollection: String) {
        viewModelScope.launch {
            repository.updateArtwork(item.id, newTitle, newCollection, item.favorite)
            showToast("Artwork updated.")
        }
    }

    fun deleteArtwork(itemId: String) {
        viewModelScope.launch { repository.deleteArtwork(itemId) }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch { repository.deleteProject(projectId) }
    }

    fun addTask(title: String, notes: String, priority: String, dueLabel: String) {
        viewModelScope.launch {
            val res = repository.addTask(title, notes, priority, dueLabel)
            if (res.isSuccess) showToast("Task added.")
        }
    }

    fun toggleTask(task: ProductivityTask) {
        viewModelScope.launch { repository.toggleTaskCompleted(task.id, !task.completed) }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch { repository.deleteTask(taskId) }
    }

    // --- OWNER ADMIN DASHBOARD ---

    fun refreshAdminDashboard() {
        viewModelScope.launch {
            _adminUsersState.value = GenerationState.Loading
            _adminLogsState.value = GenerationState.Loading
            val usersRes = repository.fetchAdminUsers()
            _adminUsersState.value = usersRes.fold(
                onSuccess = { GenerationState.Success(it) },
                onFailure = { GenerationState.Error(it.localizedMessage ?: "Admin access denied.") }
            )
            val logsRes = repository.fetchAdminLogs()
            _adminLogsState.value = logsRes.fold(
                onSuccess = { GenerationState.Success(it) },
                onFailure = { GenerationState.Error(it.localizedMessage ?: "Could not load audit logs.") }
            )
        }
    }

    fun updateSystemConfig(
        maintenanceMode: Boolean,
        animeStudioEnabled: Boolean,
        webSearchEnabled: Boolean,
        codingStudioEnabled: Boolean
    ) {
        viewModelScope.launch {
            val res = repository.updateSystemConfig(
                maintenanceMode,
                animeStudioEnabled,
                webSearchEnabled,
                codingStudioEnabled
            )
            if (res.isSuccess) {
                showToast("System configuration updated.")
                refreshAdminDashboard()
            } else {
                showToast("Update failed: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    fun deleteMyAccountData(onDone: () -> Unit) {
        viewModelScope.launch {
            val res = repository.deleteCurrentUserAccountData()
            if (res.isSuccess) {
                onDone()
            } else {
                showToast("Delete failed: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    companion object {
        fun provideFactory(currentUserId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) { "APPLICATION_KEY missing" }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                JarvisViewModel(
                    repository = JarvisRepository(db),
                    geminiService = GeminiService(),
                    webSearchService = WebSearchService(),
                    githubBuildService = GitHubBuildService(),
                    preferencesStore = UserPreferencesStore(app),
                    currentUserId = currentUserId
                )
            }
        }
    }
}
