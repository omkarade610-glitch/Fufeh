package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.ThemePreference
import com.example.core.configureAppCheck
import com.example.services.VoiceAssistantController
import com.example.ui.auth.AuthScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.CreateScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.ProjectsScreen
import com.example.ui.theme.JarvisTheme
import com.example.viewmodel.JarvisViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1 (MANDATORY): Configure Firebase App Check with launch intent BEFORE UI or ViewModels.
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            JarvisRootNavigation()
        }
    }
}

private fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

private data class NavTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val JARVIS_TABS = listOf(
    NavTab("Home", Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
    NavTab("Chat", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome, "nav_chat"),
    NavTab("Create", Icons.Filled.Brush, Icons.Outlined.Brush, "nav_create"),
    NavTab("Projects", Icons.Filled.FolderSpecial, Icons.Outlined.FolderSpecial, "nav_projects"),
    NavTab("Profile", Icons.Filled.Person, Icons.Outlined.Person, "nav_profile")
)

@Composable
fun JarvisRootNavigation() {
    val auth = remember { Firebase.auth }
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        JarvisTheme(themePreference = ThemePreference.DARK) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                AuthScreen(onAuthSuccess = {})
            }
        }
    } else {
        key(user.uid) {
            JarvisCommandCenterApp(currentUserId = user.uid)
        }
    }
}

@Composable
private fun JarvisCommandCenterApp(currentUserId: String) {
    val context = LocalContext.current
    val viewModel: JarvisViewModel = viewModel(
        key = currentUserId,
        factory = JarvisViewModel.provideFactory(currentUserId)
    )
    val voiceController = remember(context) { VoiceAssistantController(context) }
    DisposableEffect(voiceController) {
        onDispose { voiceController.release() }
    }

    val localPrefs by viewModel.localPreferences.collectAsStateWithLifecycle()
    val voiceState by voiceController.state.collectAsStateWithLifecycle()
    val toastBanner by viewModel.toastBanner.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var createStudioIndex by rememberSaveable { mutableIntStateOf(0) }
    var projectsStudioIndex by rememberSaveable { mutableIntStateOf(0) }
    var pendingVoiceTrigger by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(toastBanner) {
        val msg = toastBanner
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearToast()
        }
    }

    // Predictive & ergonomic back navigation: return to Home from secondary tabs.
    BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0
    }

    JarvisTheme(themePreference = localPrefs.themePreference) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 600.dp

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    if (!useRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 4.dp
                        ) {
                            JARVIS_TABS.forEachIndexed { idx, tab ->
                                val selected = selectedTab == idx
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { selectedTab = idx },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label) },
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useRail) {
                        NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                            JARVIS_TABS.forEachIndexed { idx, tab ->
                                val selected = selectedTab == idx
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { selectedTab = idx },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label) },
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        when (selectedTab) {
                            0 -> HomeScreen(
                                viewModel = viewModel,
                                voiceState = voiceState,
                                onNavigateTab = { selectedTab = it },
                                onLaunchQuickPrompt = { prompt, enableSearch, startVoice ->
                                    if (enableSearch) viewModel.toggleWebSearchMode(true)
                                    if (prompt.isNotBlank()) {
                                        viewModel.sendChatMessage(prompt)
                                    }
                                    if (startVoice) {
                                        pendingVoiceTrigger = true
                                    }
                                    selectedTab = 1
                                },
                                onOpenCreateStudio = { studio ->
                                    createStudioIndex = studio
                                    selectedTab = 2
                                },
                                onOpenProjectsStudio = { studio ->
                                    projectsStudioIndex = studio
                                    selectedTab = 3
                                }
                            )
                            1 -> ChatScreen(
                                viewModel = viewModel,
                                voiceController = voiceController,
                                autoTriggerVoice = pendingVoiceTrigger,
                                onVoiceTriggerHandled = { pendingVoiceTrigger = false }
                            )
                            2 -> CreateScreen(
                                viewModel = viewModel,
                                selectedStudio = createStudioIndex,
                                onSelectStudio = { createStudioIndex = it }
                            )
                            3 -> ProjectsScreen(
                                viewModel = viewModel,
                                selectedStudio = projectsStudioIndex,
                                onSelectStudio = { projectsStudioIndex = it }
                            )
                            else -> ProfileScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}
