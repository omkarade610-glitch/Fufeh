package com.example.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.UiState
import com.example.services.DocumentParser
import com.example.services.MediaExportHelper
import com.example.services.PhoneAssistantHelper
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorBannerCard
import com.example.ui.components.GlassPanel
import com.example.ui.components.SectionHeader
import com.example.ui.components.SetupRequiredBanner
import com.example.ui.components.StatusPillBadge
import com.example.ui.components.StudioSelectorStrip
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.CobaltBlue
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSignal
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NeonViolet
import com.example.viewmodel.GenerationState
import com.example.viewmodel.JarvisViewModel
import kotlinx.coroutines.launch

private val CODING_LANGUAGES = listOf(
    "HTML", "CSS", "JavaScript", "TypeScript", "Python", "Java", "Kotlin",
    "C", "C++", "C#", "PHP", "SQL", "Dart", "Swift", "Go", "Rust"
)

private val CODING_MODES = listOf("Generate", "Web App Builder", "Explain", "Debug")

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ProjectsScreen(
    viewModel: JarvisViewModel,
    selectedStudio: Int,
    onSelectStudio: (Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val projectsState by viewModel.projects.collectAsStateWithLifecycle()
    val knowledgeState by viewModel.knowledgeItems.collectAsStateWithLifecycle()
    val tasksState by viewModel.tasks.collectAsStateWithLifecycle()
    val codingState by viewModel.codingOutputState.collectAsStateWithLifecycle()
    val webPreviewHtml by viewModel.webPreviewHtml.collectAsStateWithLifecycle()
    val loadedDoc by viewModel.loadedDocument.collectAsStateWithLifecycle()
    val docAnalysisState by viewModel.documentAnalysisState.collectAsStateWithLifecycle()
    val apkYaml by viewModel.apkWorkflowYaml.collectAsStateWithLifecycle()
    val githubRunsState by viewModel.githubRunsState.collectAsStateWithLifecycle()
    val githubDispatchMsg by viewModel.githubDispatchMessage.collectAsStateWithLifecycle()
    val localPrefs by viewModel.localPreferences.collectAsStateWithLifecycle()

    // Coding studio inputs
    var selectedLang by remember { mutableStateOf("Kotlin") }
    var selectedCodeMode by remember { mutableStateOf("Generate") }
    var codeInput by remember { mutableStateOf("Create a clean Jetpack Compose animated neon countdown timer component") }
    var showLivePreview by remember { mutableStateOf(false) }

    // APK builder inputs
    var apkAppName by remember { mutableStateOf("My Jarvis App") }
    var apkPackage by remember { mutableStateOf("com.example.myjarvisapp") }
    var apkVersion by remember { mutableStateOf("1.0.0") }
    var apkBuildType by remember { mutableStateOf("Debug APK") }
    var ghRepoInput by remember(localPrefs.githubOwnerRepo) { mutableStateOf(localPrefs.githubOwnerRepo) }
    var ghWorkflowInput by remember(localPrefs.githubWorkflowFile) { mutableStateOf(localPrefs.githubWorkflowFile) }
    var ghTokenInput by remember(localPrefs.githubToken) { mutableStateOf(localPrefs.githubToken) }

    // Document & Knowledge inputs
    var docQuestion by remember { mutableStateOf("") }
    var memTitle by remember { mutableStateOf("") }
    var memContent by remember { mutableStateOf("") }
    var memFolder by remember { mutableStateOf("Memory") }
    var knowledgeSearch by remember { mutableStateOf("") }

    // Productivity inputs
    var taskTitle by remember { mutableStateOf("") }
    var taskNotes by remember { mutableStateOf("") }
    var taskDue by remember { mutableStateOf("Today") }
    var taskPriority by remember { mutableStateOf("medium") }

    val documentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val res = DocumentParser.parseDocument(context, uri)
                res.onSuccess { doc ->
                    viewModel.setLoadedDocument(doc)
                    viewModel.showToast("Loaded ${doc.fileName}")
                }.onFailure { err ->
                    viewModel.showToast("Could not read file: ${err.localizedMessage}")
                }
            }
        }
    }

    val studioTabs = listOf(
        "Coding & Web" to Icons.Filled.Code,
        "APK Builder" to Icons.Filled.Android,
        "Docs & Memory" to Icons.Filled.Description,
        "Tasks & Plans" to Icons.Filled.TaskAlt
    )

    Column(modifier = Modifier.fillMaxSize()) {
        StudioSelectorStrip(
            items = studioTabs,
            selectedIndex = selectedStudio,
            onSelect = onSelectStudio,
            testTagPrefix = "projects_studio_tab"
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (selectedStudio) {
                0 -> {
                    // CODING STUDIO & WEBSITE/APP BUILDER
                    item {
                        GlassPanel(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Coding & Website Builder", style = MaterialTheme.typography.titleMedium)
                                StatusPillBadge(label = "16 LANGUAGES", color = CobaltBlue)
                            }
                            Text(
                                "Generate, explain, or debug code across 16 languages. Choose 'Web App Builder' for interactive live HTML/CSS/JS WebView preview.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(CODING_MODES) { m ->
                                    FilterChip(
                                        selected = selectedCodeMode == m,
                                        onClick = {
                                            selectedCodeMode = m
                                            if (m == "Web App Builder") selectedLang = "HTML"
                                        },
                                        label = { Text(m) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(CODING_LANGUAGES) { lang ->
                                    FilterChip(selected = selectedLang == lang, onClick = { selectedLang = lang }, label = { Text(lang) })
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = codeInput,
                                onValueChange = { codeInput = it },
                                label = { Text(if (selectedCodeMode in listOf("Explain", "Debug")) "Paste $selectedLang Code" else "Describe App, Website, or Code") },
                                minLines = 3,
                                modifier = Modifier.fillMaxWidth().testTag("coding_prompt_input")
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { viewModel.runCodingStudio(selectedLang, selectedCodeMode, codeInput) },
                                enabled = codingState !is GenerationState.Loading && codeInput.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("run_coding_studio_btn")
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Run $selectedCodeMode ($selectedLang)", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    item {
                        when (val cs = codingState) {
                            GenerationState.Idle -> {}
                            GenerationState.Loading -> LoadingCard("Engineering $selectedLang solution with Gemini...")
                            is GenerationState.Error -> ErrorBannerCard(cs.message)
                            is GenerationState.Success -> {
                                GlassPanel(modifier = Modifier.fillMaxWidth(), accentColor = ElectricCyan) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Generated Output (Static — Not Executed)", style = MaterialTheme.typography.titleSmall)
                                        Row {
                                            if (webPreviewHtml.isNotBlank()) {
                                                OutlinedButton(onClick = { showLivePreview = !showLivePreview }) {
                                                    Icon(Icons.Filled.Web, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(if (showLivePreview) "Code" else "Live Preview")
                                                }
                                            }
                                            IconButton(onClick = { MediaExportHelper.shareTextDocument(context, "$selectedLang Code", cs.data) }) {
                                                Icon(Icons.Filled.Share, contentDescription = "Export code")
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    if (showLivePreview && webPreviewHtml.isNotBlank()) {
                                        AndroidView(
                                            factory = { ctx ->
                                                WebView(ctx).apply {
                                                    settings.javaScriptEnabled = true
                                                    settings.domStorageEnabled = true
                                                }
                                            },
                                            update = { webView ->
                                                webView.loadDataWithBaseURL(null, webPreviewHtml, "text/html", "UTF-8", null)
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(340.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                        )
                                    } else {
                                        Text(
                                            text = cs.data,
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.72f))
                                                .padding(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Saved Studio Projects
                    item {
                        SectionHeader("Saved Cloud Projects", "Anime bibles, manga scripts, websites & code")
                    }
                    val savedProjects = (projectsState as? UiState.Success)?.data.orEmpty()
                    if (savedProjects.isEmpty()) {
                        item {
                            EmptyStateCard(Icons.Filled.FolderOpen, "No saved projects yet", "Generated code, anime series, and manga scripts automatically sync here.")
                        }
                    } else {
                        items(savedProjects, key = { it.id }) { proj ->
                            GlassPanel(modifier = Modifier.fillMaxWidth(), accentColor = NeonViolet) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(proj.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${proj.projectType.uppercase()} • ${proj.categoryTag}", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                                    }
                                    Row {
                                        IconButton(onClick = { MediaExportHelper.shareTextDocument(context, proj.title, proj.contentText) }) {
                                            Icon(Icons.Filled.Share, contentDescription = "Export project")
                                        }
                                        IconButton(onClick = { viewModel.deleteProject(proj.id) }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete project", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // REAL ANDROID APK BUILD MANAGER (GITHUB ACTIONS)
                    item {
                        val isConnected = localPrefs.githubOwnerRepo.contains("/") && localPrefs.githubToken.isNotBlank()
                        if (!isConnected) {
                            SetupRequiredBanner(
                                serviceName = "Remote Android APK/AAB Build Service (GitHub Actions)",
                                reason = "On-device apps cannot natively compile signed Gradle APKs locally. Generate your real GitHub Actions android-build.yml workflow below, or connect your GitHub repository & PAT to dispatch real cloud builds and inspect verified artifacts."
                            )
                        } else {
                            GlassPanel(accentColor = EmeraldSignal, modifier = Modifier.fillMaxWidth()) {
                                StatusPillBadge(label = "GITHUB ACTIONS CONNECTED • ${localPrefs.githubOwnerRepo}", color = EmeraldSignal)
                            }
                        }
                    }

                    item {
                        GlassPanel(modifier = Modifier.fillMaxWidth()) {
                            Text("Android APK / AAB Build Configuration", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(value = apkAppName, onValueChange = { apkAppName = it }, label = { Text("App Name") }, modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(value = apkPackage, onValueChange = { apkPackage = it }, label = { Text("Application Package Name") }, modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(value = apkVersion, onValueChange = { apkVersion = it }, label = { Text("Version Name") }, modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Debug APK", "Release AAB").forEach { bt ->
                                    FilterChip(selected = apkBuildType == bt, onClick = { apkBuildType = bt }, label = { Text(bt) })
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { viewModel.generateApkWorkflowConfig(apkAppName, apkPackage, apkVersion, apkBuildType) },
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("generate_apk_yaml_btn")
                            ) {
                                Text("Generate GitHub Actions Workflow YAML")
                            }
                            if (apkYaml.isNotBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = apkYaml,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f))
                                        .padding(10.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { MediaExportHelper.shareTextDocument(context, "android-build.yml", apkYaml) },
                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                ) {
                                    Icon(Icons.Filled.Share, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Export android-build.yml")
                                }
                            }
                        }
                    }

                    item {
                        GlassPanel(modifier = Modifier.fillMaxWidth(), accentColor = CobaltBlue) {
                            Text("Connect GitHub Actions Runner", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Credentials are stored strictly on-device in private DataStore and used only to call api.github.com.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(value = ghRepoInput, onValueChange = { ghRepoInput = it }, label = { Text("Repository (owner/repo)") }, modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(value = ghWorkflowInput, onValueChange = { ghWorkflowInput = it }, label = { Text("Workflow Filename") }, modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = ghTokenInput,
                                onValueChange = { ghTokenInput = it },
                                label = { Text("GitHub PAT (actions:write)") },
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { viewModel.saveGitHubConnection(ghRepoInput, ghWorkflowInput, ghTokenInput) },
                                    modifier = Modifier.weight(1f).height(48.dp)
                                ) {
                                    Text("Save Config")
                                }
                                Button(
                                    onClick = { viewModel.triggerRemoteApkBuild() },
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("dispatch_apk_build_btn")
                                ) {
                                    Text("Trigger Build")
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = { viewModel.fetchRemoteBuildRuns() },
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Fetch Real GitHub Build History")
                            }

                            if (!githubDispatchMsg.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(githubDispatchMsg!!, style = MaterialTheme.typography.bodySmall, color = ElectricCyan)
                            }
                        }
                    }

                    item {
                        when (val rs = githubRunsState) {
                            GenerationState.Idle -> {}
                            GenerationState.Loading -> LoadingCard("Querying GitHub Actions build runs...")
                            is GenerationState.Error -> ErrorBannerCard(rs.message)
                            is GenerationState.Success -> {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    rs.data.forEach { run ->
                                        GlassPanel(
                                            modifier = Modifier.fillMaxWidth(),
                                            onClick = { PhoneAssistantHelper.openUrl(context, run.htmlUrl) }
                                        ) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(run.name, style = MaterialTheme.typography.titleSmall)
                                                    Text("${run.status} • ${run.conclusion} • ${run.createdAt.take(10)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open build run", tint = ElectricCyan)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // DOCUMENT ASSISTANT & AI MEMORY KNOWLEDGE CENTER
                    item {
                        GlassPanel(modifier = Modifier.fillMaxWidth(), accentColor = AmberAlert) {
                            Text("Document Assistant (PDF, DOCX, XLSX, PPTX, TXT, CSV)", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Uses Android Storage Access Framework (zero broad storage permissions). PDFs render into Gemini Vision pages; Office XML & text files are extracted natively.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    documentPicker.launch(
                                        arrayOf(
                                            "application/pdf",
                                            "text/*",
                                            "application/json",
                                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("upload_document_btn")
                            ) {
                                Icon(Icons.Filled.UploadFile, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (loadedDoc == null) "Upload Document" else "Loaded: ${loadedDoc!!.fileName}")
                            }

                            if (loadedDoc != null) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    OutlinedButton(onClick = { viewModel.analyzeLoadedDocument("Summarize") }, modifier = Modifier.weight(1f).height(48.dp)) { Text("Summarize") }
                                    OutlinedButton(onClick = { viewModel.analyzeLoadedDocument("Translate") }, modifier = Modifier.weight(1f).height(48.dp)) { Text("Translate") }
                                    OutlinedButton(onClick = { viewModel.analyzeLoadedDocument("Extract") }, modifier = Modifier.weight(1f).height(48.dp)) { Text("Extract") }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = docQuestion,
                                        onValueChange = { docQuestion = it },
                                        placeholder = { Text("Ask a question about this document...") },
                                        modifier = Modifier.weight(1f)
                                    )
                                    Button(
                                        onClick = {
                                            if (docQuestion.isNotBlank()) viewModel.analyzeLoadedDocument("Q&A", docQuestion)
                                        },
                                        modifier = Modifier.height(52.dp)
                                    ) { Text("Ask") }
                                }
                            }
                        }
                    }

                    item {
                        when (val da = docAnalysisState) {
                            GenerationState.Idle -> {}
                            GenerationState.Loading -> LoadingCard("Analyzing document with Gemini AI...")
                            is GenerationState.Error -> ErrorBannerCard(da.message)
                            is GenerationState.Success -> {
                                GlassPanel(modifier = Modifier.fillMaxWidth(), accentColor = EmeraldSignal) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("Document Analysis Result", style = MaterialTheme.typography.titleSmall)
                                        IconButton(onClick = { MediaExportHelper.shareTextDocument(context, loadedDoc?.fileName ?: "Analysis", da.data) }) {
                                            Icon(Icons.Filled.Share, contentDescription = "Export analysis")
                                        }
                                    }
                                    Text(da.data, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }

                    // User-Approved AI Memory Vault
                    item {
                        GlassPanel(modifier = Modifier.fillMaxWidth()) {
                            Text("User-Approved AI Memory & Knowledge Vault", style = MaterialTheme.typography.titleMedium)
                            Text("Pinned memories are automatically injected as context into Jarvis AI chat.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(value = memTitle, onValueChange = { memTitle = it }, label = { Text("Memory / Note Title") }, modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(value = memContent, onValueChange = { memContent = it }, label = { Text("Information to remember") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                listOf("Memory", "Documents", "Prompts", "Anime Lore").forEach { folder ->
                                    FilterChip(selected = memFolder == folder, onClick = { memFolder = folder }, label = { Text(folder) })
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    if (memTitle.isNotBlank() && memContent.isNotBlank()) {
                                        viewModel.addKnowledgeItem(memTitle, memContent, memFolder, if (memFolder == "Prompts") "prompt" else "memory", true)
                                        memTitle = ""
                                        memContent = ""
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_memory_btn")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save to Knowledge Vault")
                            }
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = knowledgeSearch,
                            onValueChange = { knowledgeSearch = it },
                            label = { Text("Search Saved Knowledge & Memories") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    val filteredMem = (knowledgeState as? UiState.Success)?.data.orEmpty().filter {
                        knowledgeSearch.isBlank() || it.title.contains(knowledgeSearch, true) || it.content.contains(knowledgeSearch, true) || it.folder.contains(knowledgeSearch, true)
                    }
                    if (filteredMem.isEmpty()) {
                        item { EmptyStateCard(Icons.Filled.Description, "No saved knowledge", "Save important facts, document summaries, or reusable prompts here.") }
                    } else {
                        items(filteredMem, key = { it.id }) { item ->
                            GlassPanel(modifier = Modifier.fillMaxWidth(), accentColor = if (item.pinned) ElectricCyan else NeonViolet) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.title, style = MaterialTheme.typography.titleSmall)
                                        Text("${item.folder} • ${item.itemType.uppercase()}", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                                    }
                                    Row {
                                        IconButton(onClick = { viewModel.toggleKnowledgePinned(item) }) {
                                            Icon(Icons.Filled.PushPin, contentDescription = "Toggle AI memory pin", tint = if (item.pinned) EmeraldSignal else MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        IconButton(onClick = { viewModel.deleteKnowledgeItem(item.id) }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete memory", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(item.content, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }

                else -> {
                    // PERSONAL PRODUCTIVITY TOOLS
                    item {
                        GlassPanel(modifier = Modifier.fillMaxWidth()) {
                            Text("Personal Tasks, Reminders & Calendar", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(value = taskTitle, onValueChange = { taskTitle = it }, label = { Text("Task Title") }, modifier = Modifier.fillMaxWidth().testTag("task_title_input"))
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(value = taskNotes, onValueChange = { taskNotes = it }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                listOf("low", "medium", "high").forEach { p ->
                                    FilterChip(selected = taskPriority == p, onClick = { taskPriority = p }, label = { Text(p.uppercase()) })
                                }
                                OutlinedTextField(value = taskDue, onValueChange = { taskDue = it }, label = { Text("Due") }, singleLine = true, modifier = Modifier.weight(1f))
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    if (taskTitle.isNotBlank()) {
                                        viewModel.addTask(taskTitle, taskNotes, taskPriority, taskDue)
                                        taskTitle = ""
                                        taskNotes = ""
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("add_task_btn")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Cloud Task")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { PhoneAssistantHelper.openCalendarEventCreator(context, taskTitle.ifBlank { "Jarvis AI Plan" }, taskNotes) },
                                    modifier = Modifier.weight(1f).height(48.dp)
                                ) {
                                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Calendar")
                                }
                                OutlinedButton(
                                    onClick = { PhoneAssistantHelper.openAlarmCreator(context, taskTitle.ifBlank { "Jarvis Reminder" }) },
                                    modifier = Modifier.weight(1f).height(48.dp)
                                ) {
                                    Icon(Icons.Filled.Alarm, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Set Alarm")
                                }
                            }
                        }
                    }

                    val taskList = (tasksState as? UiState.Success)?.data.orEmpty()
                    if (taskList.isEmpty()) {
                        item { EmptyStateCard(Icons.Filled.TaskAlt, "All clear!", "Add tasks or schedule reminders synced to your Jarvis cloud account.") }
                    } else {
                        items(taskList, key = { it.id }) { task ->
                            GlassPanel(
                                modifier = Modifier.fillMaxWidth(),
                                accentColor = if (task.completed) EmeraldSignal else ElectricCyan
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { viewModel.toggleTask(task) }) {
                                        Icon(
                                            if (task.completed) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                                            contentDescription = "Toggle task completed",
                                            tint = if (task.completed) EmeraldSignal else ElectricCyan
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = task.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None
                                        )
                                        Text(
                                            text = "${task.priority.uppercase()} • ${task.dueLabel}" + if (task.notes.isNotBlank()) " • ${task.notes}" else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { viewModel.deleteTask(task.id) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete task", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
