package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.ProjectPill
import com.example.ui.screens.AbiManagerScreen
import com.example.ui.screens.ApkToolsScreen
import com.example.ui.screens.BuildScreen
import com.example.ui.screens.CodeEditorScreen
import com.example.ui.screens.ProjectsScreen
import com.example.ui.screens.StorageSettingsScreen
import com.example.ui.screens.ToolsScreen
import com.example.ui.theme.PrimaryBlue

enum class NavDestination(val route: String, val title: String, val icon: ImageVector) {
    PROJECTS("projects", "Projects", Icons.Default.Folder),
    EDITOR("editor", "Editor", Icons.Default.Code),
    BUILD("build", "Build", Icons.Default.PlayArrow),
    TOOLS("tools", "Tools", Icons.Default.Construction),
    STORAGE("storage", "Storage", Icons.Default.Storage),
    APK_TOOLS("apk_tools", "APK Tools", Icons.Default.Verified),
    ABI_MANAGER("abi_manager", "ABIs", Icons.Default.Memory)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    var currentDestination by remember { mutableStateOf(NavDestination.PROJECTS) }
    var previousDestination by remember { mutableStateOf<NavDestination?>(null) }

    val selectedProject by viewModel.selectedProject.collectAsState()
    val isBuilding by viewModel.compilerEngine.pipeline.isBuilding.collectAsState()
    val lastApkInfo by viewModel.lastApkInfo.collectAsState()

    fun navigateTo(dest: NavDestination) {
        if (dest != currentDestination) {
            previousDestination = currentDestination
            currentDestination = dest
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentDestination == NavDestination.APK_TOOLS || currentDestination == NavDestination.ABI_MANAGER) {
                            IconButton(onClick = {
                                currentDestination = previousDestination ?: NavDestination.BUILD
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ATP Android Builder",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = PrimaryBlue
                            )
                            Text(
                                text = when (currentDestination) {
                                    NavDestination.PROJECTS -> "Projects Workspace"
                                    NavDestination.EDITOR -> "Code Editor"
                                    NavDestination.BUILD -> "Build System (Local Compiler)"
                                    NavDestination.TOOLS -> "Online Tool Manager"
                                    NavDestination.STORAGE -> "Storage & Settings"
                                    NavDestination.APK_TOOLS -> "APK Inspection & Verification"
                                    NavDestination.ABI_MANAGER -> "ABI Architecture Manager"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (selectedProject != null) {
                            ProjectPill(
                                projectName = selectedProject!!.name,
                                onClick = { navigateTo(NavDestination.PROJECTS) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                listOf(
                    NavDestination.PROJECTS,
                    NavDestination.EDITOR,
                    NavDestination.BUILD,
                    NavDestination.TOOLS,
                    NavDestination.STORAGE
                ).forEach { item ->
                    val isSelected = currentDestination == item
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { navigateTo(item) },
                        icon = {
                            if (item == NavDestination.BUILD && isBuilding) {
                                BadgedBox(badge = { Badge { Text("●") } }) {
                                    Icon(item.icon, contentDescription = item.title)
                                }
                            } else {
                                Icon(item.icon, contentDescription = item.title)
                            }
                        },
                        label = { Text(item.title, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PrimaryBlue,
                            selectedTextColor = PrimaryBlue,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.testTag("nav_${item.route}")
                    )
                }
            }
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = currentDestination,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier.padding(innerPadding),
            label = "screen_transition"
        ) { dest ->
            when (dest) {
                NavDestination.PROJECTS -> ProjectsScreen(
                    viewModel = viewModel,
                    onNavigateToEditor = { navigateTo(NavDestination.EDITOR) },
                    onNavigateToBuild = { navigateTo(NavDestination.BUILD) },
                    onNavigateToAbi = { navigateTo(NavDestination.ABI_MANAGER) }
                )

                NavDestination.EDITOR -> CodeEditorScreen(
                    viewModel = viewModel,
                    onNavigateToBuild = { navigateTo(NavDestination.BUILD) }
                )

                NavDestination.BUILD -> BuildScreen(
                    viewModel = viewModel,
                    onNavigateToApkTools = { navigateTo(NavDestination.APK_TOOLS) },
                    onNavigateToAbi = { navigateTo(NavDestination.ABI_MANAGER) }
                )

                NavDestination.TOOLS -> ToolsScreen(
                    viewModel = viewModel
                )

                NavDestination.STORAGE -> StorageSettingsScreen(
                    viewModel = viewModel
                )

                NavDestination.APK_TOOLS -> ApkToolsScreen(
                    viewModel = viewModel
                )

                NavDestination.ABI_MANAGER -> AbiManagerScreen(
                    viewModel = viewModel
                )
            }
        }
    }
}
