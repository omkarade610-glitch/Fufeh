package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.GeminiModelOption
import com.example.data.JarvisLanguage
import com.example.data.UiState
import com.example.data.WebSource
import com.example.services.MediaExportHelper
import com.example.services.PhoneAssistantHelper
import com.example.services.VoiceAssistantController
import com.example.services.VoiceStatus
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorBannerCard
import com.example.ui.components.GlassPanel
import com.example.ui.components.JarvisAiOrb
import com.example.ui.components.StatusPillBadge
import com.example.ui.components.VoiceWaveformBar
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSignal
import com.example.ui.theme.NeonViolet
import com.example.viewmodel.JarvisViewModel
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    viewModel: JarvisViewModel,
    voiceController: VoiceAssistantController,
    autoTriggerVoice: Boolean = false,
    onVoiceTriggerHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val conversationsState by viewModel.conversations.collectAsStateWithLifecycle()
    val messagesState by viewModel.activeMessages.collectAsStateWithLifecycle()
    val privateMode by viewModel.privateMode.collectAsStateWithLifecycle()
    val privateMessages by viewModel.privateMessages.collectAsStateWithLifecycle()
    val streamingReply by viewModel.streamingReply.collectAsStateWithLifecycle()
    val chatBusy by viewModel.chatBusy.collectAsStateWithLifecycle()
    val chatError by viewModel.chatError.collectAsStateWithLifecycle()
    val webSearchMode by viewModel.webSearchMode.collectAsStateWithLifecycle()
    val selectedModelId by viewModel.selectedModelId.collectAsStateWithLifecycle()
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val localPrefs by viewModel.localPreferences.collectAsStateWithLifecycle()
    val voiceState by voiceController.state.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    var attachedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showVoiceSheet by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    LaunchedEffect(selectedLanguage, localPrefs) {
        voiceController.configureVoice(
            languageTag = selectedLanguage.code,
            rate = localPrefs.voiceRate,
            pitch = localPrefs.voicePitch,
            voiceName = localPrefs.selectedVoiceName
        )
    }

    LaunchedEffect(chatBusy) {
        voiceController.setThinking(chatBusy)
    }

    val startVoiceCapture: () -> Unit = {
        voiceController.startListening(selectedLanguage.code) { spoken ->
            inputText = spoken
            viewModel.sendChatMessage(
                prompt = spoken,
                attachedImage = attachedBitmap,
                onCompleteReply = { reply ->
                    voiceController.speak(reply)
                }
            )
            attachedBitmap = null
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showVoiceSheet = true
            startVoiceCapture()
        } else {
            viewModel.showToast("Microphone permission denied. Voice input requires mic access only while listening.")
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                attachedBitmap = MediaExportHelper.loadBitmapFromUri(context, uri)
            }
        }
    }

    val triggerMicSafely: () -> Unit = {
        val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            showVoiceSheet = true
            startVoiceCapture()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(autoTriggerVoice) {
        if (autoTriggerVoice) {
            onVoiceTriggerHandled()
            triggerMicSafely()
        }
    }

    val displayMessages: List<Triple<String, String, List<WebSource>>> = if (privateMode) {
        privateMessages.map { Triple(it.role, it.text, it.sources) }
    } else {
        (messagesState as? UiState.Success)?.data?.map { msg ->
            val parsedSources = msg.sources.mapNotNull { encoded ->
                val parts = encoded.split("|", limit = 2)
                if (parts.size == 2) WebSource(parts[0], parts[1], "") else null
            }
            Triple(msg.role, msg.text, parsedSources)
        }.orEmpty()
    }

    LaunchedEffect(displayMessages.size, streamingReply.length) {
        if (displayMessages.isNotEmpty() || streamingReply.isNotBlank()) {
            listState.animateScrollToItem((displayMessages.size).coerceAtLeast(0))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Futuristic Top Control Strip
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    JarvisAiOrb(size = 34.dp, voiceStatus = voiceState.status, amplitude = voiceState.rmsAmplitude)
                    Column {
                        Text("Jarvis AI Brain", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${GeminiModelOption.fromId(selectedModelId).label} • ${selectedLanguage.displayName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Row {
                    if (voiceState.status == VoiceStatus.SPEAKING) {
                        IconButton(
                            onClick = { voiceController.stopSpeaking() },
                            modifier = Modifier.size(48.dp).testTag("stop_speaking_btn")
                        ) {
                            Icon(Icons.Filled.Stop, contentDescription = "Stop speaking", tint = AmberAlert)
                        }
                    }
                    IconButton(
                        onClick = { showHistoryDialog = true },
                        modifier = Modifier.size(48.dp).testTag("chat_history_btn")
                    ) {
                        Icon(Icons.Filled.History, contentDescription = "Conversation history")
                    }
                    IconButton(
                        onClick = { viewModel.startNewConversation() },
                        modifier = Modifier.size(48.dp).testTag("new_chat_btn")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "New conversation")
                    }
                }
            }

            // Mode & Language Filter Chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = webSearchMode,
                        onClick = { viewModel.toggleWebSearchMode(!webSearchMode) },
                        label = { Text("Live Web Search") },
                        leadingIcon = { Icon(Icons.Filled.Public, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("toggle_web_search_chip")
                    )
                }
                item {
                    FilterChip(
                        selected = privateMode,
                        onClick = { viewModel.togglePrivateMode(!privateMode) },
                        label = { Text("Private") },
                        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("toggle_private_chip")
                    )
                }
                item {
                    FilterChip(
                        selected = showVoiceSheet,
                        onClick = { showVoiceSheet = !showVoiceSheet },
                        label = { Text("Female Voice") },
                        leadingIcon = { Icon(Icons.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("toggle_voice_panel_chip")
                    )
                }
                items(JarvisLanguage.entries) { lang ->
                    FilterChip(
                        selected = selectedLanguage == lang,
                        onClick = { viewModel.selectLanguage(lang) },
                        label = { Text(lang.nativeLabel) }
                    )
                }
            }
        }

        // Optional Female Voice Assistant Live Panel
        if (showVoiceSheet) {
            GlassPanel(
                accentColor = NeonViolet,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentPadding = PaddingValues(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Female Voice Assistant (${selectedLanguage.displayName})", style = MaterialTheme.typography.titleSmall)
                        Text(
                            when (voiceState.status) {
                                VoiceStatus.LISTENING -> "Listening... ${voiceState.partialTranscript}"
                                VoiceStatus.THINKING -> "Thinking with Gemini..."
                                VoiceStatus.SPEAKING -> "Speaking (${voiceState.activeVoiceLabel})..."
                                VoiceStatus.IDLE -> voiceState.lastError ?: "Tap Mic to speak • Voice: ${voiceState.activeVoiceLabel}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                    IconButton(onClick = { showVoiceSheet = false }) {
                        Icon(Icons.Filled.Close, contentDescription = "Close voice panel")
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                VoiceWaveformBar(voiceStatus = voiceState.status, amplitude = voiceState.rmsAmplitude)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            if (voiceState.status == VoiceStatus.LISTENING) voiceController.stopListening()
                            else triggerMicSafely()
                        },
                        modifier = Modifier.weight(1f).height(48.dp).testTag("voice_listen_toggle_btn")
                    ) {
                        Icon(
                            if (voiceState.status == VoiceStatus.LISTENING) Icons.Filled.Stop else Icons.Filled.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (voiceState.status == VoiceStatus.LISTENING) "Stop Mic" else "Speak Now")
                    }
                    if (voiceState.status == VoiceStatus.SPEAKING) {
                        Button(
                            onClick = { voiceController.stopSpeaking() },
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Mute")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Speed ${"%.1f".format(localPrefs.voiceRate)}x", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = localPrefs.voiceRate,
                        onValueChange = { viewModel.setVoiceSettings(it, localPrefs.voicePitch, localPrefs.autoSpeakReplies, localPrefs.selectedVoiceName) },
                        valueRange = 0.6f..1.7f,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    Text("Auto-Speak", style = MaterialTheme.typography.labelSmall)
                    Switch(
                        checked = localPrefs.autoSpeakReplies,
                        onCheckedChange = { viewModel.setVoiceSettings(localPrefs.voiceRate, localPrefs.voicePitch, it, localPrefs.selectedVoiceName) }
                    )
                }
            }
        }

        // Messages List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (displayMessages.isEmpty() && streamingReply.isBlank() && !chatBusy) {
                item {
                    EmptyStateCard(
                        icon = Icons.Filled.Public,
                        title = if (privateMode) "Private Session Active" else "Jarvis AI Command Chat",
                        subtitle = "Ask questions in English, Hindi, Marathi, Japanese, or Korean. Toggle Live Web Search for real cited URLs, or attach an image for Gemini Vision."
                    )
                }
            }

            items(displayMessages) { (role, text, sources) ->
                val isUser = role == "user"
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                ) {
                    GlassPanel(
                        accentColor = if (isUser) ElectricCyan else NeonViolet,
                        modifier = Modifier.fillMaxWidth(0.92f),
                        contentPadding = PaddingValues(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isUser) "YOU" else "JARVIS AI",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isUser) ElectricCyan else NeonViolet
                            )
                            if (!isUser) {
                                Row {
                                    IconButton(
                                        onClick = { voiceController.speak(text) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Filled.VolumeUp, contentDescription = "Read aloud", modifier = Modifier.size(18.dp), tint = ElectricCyan)
                                    }
                                    IconButton(
                                        onClick = {
                                            viewModel.addKnowledgeItem(
                                                title = text.lineSequence().firstOrNull().orEmpty().take(60),
                                                content = text,
                                                folder = "Memory",
                                                itemType = "memory",
                                                pinned = true
                                            )
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Filled.BookmarkAdd, contentDescription = "Save to AI Memory", modifier = Modifier.size(18.dp), tint = EmeraldSignal)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = text, style = MaterialTheme.typography.bodyMedium)

                        if (sources.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "VERIFIED LIVE WEB SOURCES",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldSignal
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                sources.forEach { src ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { PhoneAssistantHelper.openUrl(context, src.url) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(src.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(src.url, style = MaterialTheme.typography.labelSmall, color = ElectricCyan, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open source link", tint = ElectricCyan, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (chatBusy) {
                item {
                    GlassPanel(accentColor = NeonViolet, modifier = Modifier.fillMaxWidth(0.92f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = ElectricCyan)
                            Text(
                                text = if (webSearchMode && streamingReply.isBlank()) "Searching live web & synthesizing..." else "Jarvis AI streaming...",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan
                            )
                        }
                        if (streamingReply.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = streamingReply, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            if (!chatError.isNullOrBlank()) {
                item {
                    ErrorBannerCard(message = chatError!!)
                }
            }
        }

        // Attached Image Preview
        if (attachedBitmap != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Image(
                    bitmap = attachedBitmap!!.asImageBitmap(),
                    contentDescription = "Attached image",
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
                Text("Image attached for Gemini Vision", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { attachedBitmap = null }) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove image")
                }
            }
        }

        // Bottom Input Composer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier.size(48.dp).testTag("chat_attach_photo_btn")
            ) {
                Icon(Icons.Filled.Image, contentDescription = "Attach photo via Photo Picker", tint = ElectricCyan)
            }

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = {
                    Text(
                        if (webSearchMode) "Search live web & ask Jarvis..." else "Ask Jarvis AI anything...",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                shape = RoundedCornerShape(18.dp),
                maxLines = 4,
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_message_input")
            )

            IconButton(
                onClick = triggerMicSafely,
                modifier = Modifier.size(48.dp).testTag("chat_mic_btn")
            ) {
                Icon(
                    Icons.Filled.Mic,
                    contentDescription = "Voice input",
                    tint = if (voiceState.status == VoiceStatus.LISTENING) EmeraldSignal else NeonViolet
                )
            }

            Surface(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(enabled = !chatBusy && inputText.isNotBlank()) {
                        val promptToSend = inputText
                        val img = attachedBitmap
                        inputText = ""
                        attachedBitmap = null
                        viewModel.sendChatMessage(
                            prompt = promptToSend,
                            attachedImage = img,
                            onCompleteReply = { reply ->
                                if (localPrefs.autoSpeakReplies) {
                                    voiceController.speak(reply)
                                }
                            }
                        )
                    }
                    .testTag("chat_send_btn"),
                color = if (!chatBusy && inputText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = CircleShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send message",
                        tint = if (!chatBusy && inputText.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showHistoryDialog) {
        val convList = (conversationsState as? UiState.Success)?.data.orEmpty()
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            title = { Text("Conversation History", fontWeight = FontWeight.Bold) },
            text = {
                if (convList.isEmpty()) {
                    Text("No saved conversations yet.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(convList, key = { it.id }) { conv ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                                    .clickable {
                                        viewModel.selectConversation(conv.id)
                                        showHistoryDialog = false
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(conv.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${conv.messageCount} msgs • ${conv.language}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { viewModel.deleteConversation(conv.id) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete conversation", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHistoryDialog = false }) { Text("Close") }
            }
        )
    }
}
