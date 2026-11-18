package com.example.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.Conversation
import com.example.data.GeminiModelOption
import com.example.data.StudioProject
import com.example.data.UiState
import com.example.services.VoiceUiState
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.GlassPanel
import com.example.ui.components.JarvisAiOrb
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPillBadge
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.CobaltBlue
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSignal
import com.example.ui.theme.NeonViolet
import com.example.viewmodel.JarvisViewModel
import java.util.Calendar

@Composable
fun HomeScreen(
    viewModel: JarvisViewModel,
    voiceState: VoiceUiState,
    onNavigateTab: (Int) -> Unit,
    onLaunchQuickPrompt: (prompt: String, enableSearch: Boolean, startVoice: Boolean) -> Unit,
    onOpenCreateStudio: (studioIndex: Int) -> Unit,
    onOpenProjectsStudio: (studioIndex: Int) -> Unit
) {
    val profileState by viewModel.userProfile.collectAsStateWithLifecycle()
    val conversationsState by viewModel.conversations.collectAsStateWithLifecycle()
    val projectsState by viewModel.projects.collectAsStateWithLifecycle()
    val knowledgeState by viewModel.knowledgeItems.collectAsStateWithLifecycle()
    val tasksState by viewModel.tasks.collectAsStateWithLifecycle()
    val systemConfigState by viewModel.systemConfig.collectAsStateWithLifecycle()
    val selectedModelId by viewModel.selectedModelId.collectAsStateWithLifecycle()

    var quickPrompt by remember { mutableStateOf("") }

    val userName = (profileState as? UiState.Success)?.data?.displayName?.ifBlank { "Commander" } ?: "Commander"
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }
    val modelLabel = GeminiModelOption.fromId(selectedModelId).label.uppercase()
    val maintenanceOn = (systemConfigState as? UiState.Success)?.data?.maintenanceMode == true

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (maintenanceOn) {
            item {
                GlassPanel(accentColor = AmberAlert, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.Warning, contentDescription = "Maintenance", tint = AmberAlert)
                        Text(
                            text = "Owner Maintenance Mode is active. Some cloud studio tools may be temporarily paused.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AmberAlert
                        )
                    }
                }
            }
        }

        // Hero Command Banner + Animated AI Orb
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_jarvis_command_banner_1795024203311),
                    contentDescription = "Jarvis command center hero",
                    modifier = Modifier
                        .matchParentSize()
                        .alpha(0.32f),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.25f),
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                                )
                            )
                        )
                )

                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusPillBadge(label = "$modelLabel • ONLINE", color = EmeraldSignal)
                        Row {
                            IconButton(
                                onClick = { onOpenProjectsStudio(3) },
                                modifier = Modifier.size(48.dp).testTag("home_notifications_btn")
                            ) {
                                Icon(
                                    Icons.Filled.Notifications,
                                    contentDescription = "Tasks and reminders",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = { onNavigateTab(4) },
                                modifier = Modifier.size(48.dp).testTag("home_settings_btn")
                            ) {
                                Icon(
                                    Icons.Filled.Settings,
                                    contentDescription = "Settings and Profile",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        JarvisAiOrb(
                            size = 82.dp,
                            voiceStatus = voiceState.status,
                            amplitude = voiceState.rmsAmplitude
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "$greeting, $userName",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Jarvis AI core ready. Ask anything, sketch Tokyo Revengers anime, analyze docs, or ship code.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Direct AI Prompt + Voice Input Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = quickPrompt,
                            onValueChange = { quickPrompt = it },
                            placeholder = { Text("Command Jarvis AI...", style = MaterialTheme.typography.bodyMedium) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                                focusedContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.55f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("home_quick_prompt_input")
                        )

                        Surface(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .clickable { onLaunchQuickPrompt("", false, true) }
                                .testTag("home_mic_button"),
                            color = NeonViolet.copy(alpha = 0.22f),
                            shape = CircleShape
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Mic, contentDescription = "Start Voice Mode", tint = NeonViolet)
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .clickable {
                                    val text = quickPrompt.trim()
                                    if (text.isNotBlank()) {
                                        quickPrompt = ""
                                        onLaunchQuickPrompt(text, false, false)
                                    } else {
                                        onNavigateTab(1)
                                    }
                                }
                                .testTag("home_send_button"),
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send to Jarvis Chat",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Quick Actions Command Matrix
        item {
            SectionHeader(
                title = "Command Modules",
                subtitle = "Direct jump into Jarvis AI studios"
            )
            Spacer(modifier = Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    CommandTile(
                        title = "Anime Sketch",
                        subtitle = "Tokyo Revengers • HB–8B",
                        icon = Icons.Filled.Brush,
                        accent = ElectricCyan,
                        modifier = Modifier.weight(1f).testTag("quick_anime_sketch"),
                        onClick = { onOpenCreateStudio(0) }
                    )
                    CommandTile(
                        title = "Female Voice",
                        subtitle = "Hindi, English, JP, KR",
                        icon = Icons.Filled.RecordVoiceOver,
                        accent = NeonViolet,
                        modifier = Modifier.weight(1f).testTag("quick_voice_mode"),
                        onClick = { onLaunchQuickPrompt("", false, true) }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    CommandTile(
                        title = "Live Web Search",
                        subtitle = "Verified sources & links",
                        icon = Icons.Filled.Public,
                        accent = EmeraldSignal,
                        modifier = Modifier.weight(1f).testTag("quick_web_search"),
                        onClick = { onLaunchQuickPrompt("", true, false) }
                    )
                    CommandTile(
                        title = "Coding & Apps",
                        subtitle = "16 langs + HTML preview",
                        icon = Icons.Filled.Code,
                        accent = CobaltBlue,
                        modifier = Modifier.weight(1f).testTag("quick_coding_studio"),
                        onClick = { onOpenProjectsStudio(0) }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    CommandTile(
                        title = "Docs & Memory",
                        subtitle = "PDF, DOCX, XLSX, Vault",
                        icon = Icons.Filled.Description,
                        accent = AmberAlert,
                        modifier = Modifier.weight(1f).testTag("quick_docs_vault"),
                        onClick = { onOpenProjectsStudio(2) }
                    )
                    CommandTile(
                        title = "Phone Assistant",
                        subtitle = "Torch, Battery, Intents",
                        icon = Icons.Filled.PhoneAndroid,
                        accent = ElectricCyan,
                        modifier = Modifier.weight(1f).testTag("quick_phone_assistant"),
                        onClick = { onNavigateTab(4) }
                    )
                }
            }
        }

        // Live Workspace Metrics Strip
        item {
            val convCount = (conversationsState as? UiState.Success)?.data?.size ?: 0
            val projCount = (projectsState as? UiState.Success)?.data?.size ?: 0
            val memCount = (knowledgeState as? UiState.Success)?.data?.size ?: 0
            val openTasks = (tasksState as? UiState.Success)?.data?.count { !it.completed } ?: 0

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MetricChip("Chats", "$convCount", ElectricCyan, Modifier.weight(1f))
                MetricChip("Projects", "$projCount", CobaltBlue, Modifier.weight(1f))
                MetricChip("Memory", "$memCount", NeonViolet, Modifier.weight(1f))
                MetricChip("Tasks", "$openTasks", EmeraldSignal, Modifier.weight(1f))
            }
        }

        // Recent Conversations
        item {
            SectionHeader(
                title = "Recent Conversations",
                subtitle = "Synced in Cloud Firestore"
            )
        }
        val recentConvs: List<Conversation> = (conversationsState as? UiState.Success)?.data?.take(3).orEmpty()
        if (recentConvs.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Filled.Forum,
                    title = "No conversations yet",
                    subtitle = "Start a chat or tap the microphone to talk with Jarvis AI."
                )
            }
        } else {
            items(recentConvs, key = { it.id }) { conv ->
                GlassPanel(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        viewModel.selectConversation(conv.id)
                        onNavigateTab(1)
                    }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(20.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(conv.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                conv.lastMessagePreview.ifBlank { "${conv.messageCount} messages" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        StatusPillBadge(label = conv.language, color = CobaltBlue)
                    }
                }
            }
        }

        // Recent Projects
        item {
            SectionHeader(
                title = "Recent Studio Projects",
                subtitle = "Anime series, manga, code & builds"
            )
        }
        val recentProjects: List<StudioProject> = (projectsState as? UiState.Success)?.data?.take(3).orEmpty()
        if (recentProjects.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Filled.CheckCircle,
                    title = "Your creative workspace is ready",
                    subtitle = "Create an Anime Series, Manga Page, Website, or Code module in Create or Projects."
                )
            }
        } else {
            items(recentProjects, key = { it.id }) { project ->
                GlassPanel(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = NeonViolet,
                    onClick = { onNavigateTab(3) }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(project.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                project.summary.ifBlank { project.categoryTag },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        StatusPillBadge(label = project.projectType.uppercase(), color = NeonViolet)
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    GlassPanel(
        modifier = modifier,
        accentColor = accent,
        onClick = onClick,
        contentPadding = PaddingValues(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = title, tint = accent, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(text = title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MetricChip(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    GlassPanel(
        modifier = modifier,
        accentColor = color,
        contentPadding = PaddingValues(vertical = 10.dp, horizontal = 8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(text = value, style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Bold)
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
