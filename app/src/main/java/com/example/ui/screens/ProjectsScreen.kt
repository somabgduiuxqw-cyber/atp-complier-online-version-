package com.example.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CompilerTool
import com.example.model.Project
import com.example.model.ProjectTemplate
import com.example.ui.MainViewModel
import com.example.ui.theme.DarkSilver
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusOrange
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProjectsScreen(
    viewModel: MainViewModel,
    onNavigateToEditor: () -> Unit,
    onNavigateToBuild: () -> Unit,
    onNavigateToAbi: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val projects by viewModel.projects.collectAsState()
    val selectedProject by viewModel.selectedProject.collectAsState()
    val searchQuery by viewModel.projectSearchQuery.collectAsState()
    val gitProgress by viewModel.gitProgress.collectAsState()

    var selectedFilterTab by remember { mutableIntStateOf(0) } // 0: All, 1: Favorites, 2: Pinned
    var showNewProjectDialog by remember { mutableStateOf(false) }
    var showGitCloneDialog by remember { mutableStateOf(false) }
    var showImportSingleFileDialog by remember { mutableStateOf(false) }
    var projectToDelete by remember { mutableStateOf<Project?>(null) }
    var projectToRename by remember { mutableStateOf<Project?>(null) }
    var projectToDuplicate by remember { mutableStateOf<Project?>(null) }

    val filteredProjects = projects.filter { proj ->
        val matchesQuery = proj.name.contains(searchQuery, ignoreCase = true) ||
                proj.packageName.contains(searchQuery, ignoreCase = true)
        val matchesTab = when (selectedFilterTab) {
            1 -> proj.isFavorite
            2 -> proj.isPinned
            else -> true
        }
        matchesQuery && matchesTab
    }.sortedWith(compareByDescending<Project> { it.isPinned }.thenByDescending { it.lastOpenedAt })

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Search Bar & Filter Chips
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setProjectSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("project_search_field"),
                placeholder = { Text("Search Android projects...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setProjectSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Filter Chips & Quick Import Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = selectedFilterTab == 0,
                    onClick = { selectedFilterTab = 0 },
                    label = { Text("All (${projects.size})") },
                    modifier = Modifier.testTag("filter_all")
                )
                FilterChip(
                    selected = selectedFilterTab == 1,
                    onClick = { selectedFilterTab = 1 },
                    label = { Text("Favorites") },
                    leadingIcon = { Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("filter_favorites")
                )
                FilterChip(
                    selected = selectedFilterTab == 2,
                    onClick = { selectedFilterTab = 2 },
                    label = { Text("Pinned") },
                    leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("filter_pinned")
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Quick Actions Bar: Git Clone, Import .kt/.java
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showGitCloneDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("git_clone_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Clone Git", fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = { showImportSingleFileDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("import_file_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import File", fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (filteredProjects.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No matching projects found" else "No Android projects yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { showNewProjectDialog = true },
                            modifier = Modifier.testTag("empty_state_create_project")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Create Project")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filteredProjects, key = { it.id }) { project ->
                        ProjectCard(
                            project = project,
                            isSelected = selectedProject?.id == project.id,
                            onSelect = { viewModel.selectProject(project) },
                            onOpenEditor = {
                                viewModel.selectProject(project)
                                onNavigateToEditor()
                            },
                            onOpenBuild = {
                                viewModel.selectProject(project)
                                onNavigateToBuild()
                            },
                            onToggleFavorite = { viewModel.toggleFavorite(project) },
                            onTogglePin = { viewModel.togglePin(project) },
                            onRename = { projectToRename = project },
                            onDuplicate = { projectToDuplicate = project },
                            onDelete = { projectToDelete = project },
                            onOpenAbiManager = {
                                viewModel.selectProject(project)
                                onNavigateToAbi()
                            },
                            onExportZip = {
                                val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                                val targetZip = File(exportDir, "${project.name.replace(" ", "_")}-src.zip")
                                val success = viewModel.exportProjectZip(project, targetZip)
                                if (success) {
                                    Toast.makeText(context, "Project exported to ${targetZip.name}", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Failed to export project ZIP", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        }

        // Floating Action Button for New Project
        FloatingActionButton(
            onClick = { showNewProjectDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("fab_create_project"),
            containerColor = PrimaryBlue,
            contentColor = androidx.compose.ui.graphics.Color.White
        ) {
            Icon(Icons.Default.Add, contentDescription = "Create Project")
        }
    }

    // New Project Dialog
    if (showNewProjectDialog) {
        NewProjectDialog(
            onDismiss = { showNewProjectDialog = false },
            onCreate = { name, pkg, tpl, minSdk, targetSdk ->
                viewModel.createProject(name, pkg, tpl, minSdk, targetSdk)
                showNewProjectDialog = false
            }
        )
    }

    // Git Clone Dialog
    if (showGitCloneDialog) {
        GitCloneDialog(
            progress = gitProgress,
            onDismiss = {
                viewModel.clearGitProgress()
                showGitCloneDialog = false
            },
            onClone = { url, token ->
                viewModel.cloneGitRepository(url, token) { proj ->
                    if (proj != null) {
                        Toast.makeText(context, "Cloned: ${proj.name}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // Import Single Source File Dialog
    if (showImportSingleFileDialog) {
        ImportSingleFileDialog(
            onDismiss = { showImportSingleFileDialog = false },
            onImport = { fileName, content, projName ->
                viewModel.importSingleSourceFile(fileName, content, projName)
                showImportSingleFileDialog = false
            }
        )
    }

    // Confirmation Dialog for Delete
    projectToDelete?.let { proj ->
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            title = { Text("Delete Project") },
            text = {
                Text("Are you sure you want to permanently delete \"${proj.name}\"?\nAll project source files and build outputs in files/projects/${proj.id}/ will be removed.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteProject(proj)
                        projectToDelete = null
                        Toast.makeText(context, "Project deleted", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Rename Dialog
    projectToRename?.let { proj ->
        var newName by remember { mutableStateOf(proj.name) }
        AlertDialog(
            onDismissRequest = { projectToRename = null },
            title = { Text("Rename Project") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Project Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            viewModel.renameProject(proj, newName.trim())
                            projectToRename = null
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Duplicate Dialog
    projectToDuplicate?.let { proj ->
        var dupName by remember { mutableStateOf("${proj.name} Copy") }
        AlertDialog(
            onDismissRequest = { projectToDuplicate = null },
            title = { Text("Duplicate Project") },
            text = {
                OutlinedTextField(
                    value = dupName,
                    onValueChange = { dupName = it },
                    label = { Text("New Project Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (dupName.isNotBlank()) {
                            viewModel.duplicateProject(proj, dupName.trim())
                            projectToDuplicate = null
                        }
                    }
                ) {
                    Text("Duplicate")
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToDuplicate = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ProjectCard(
    project: Project,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onOpenEditor: () -> Unit,
    onOpenBuild: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePin: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onOpenAbiManager: () -> Unit,
    onExportZip: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy HH:mm", Locale.US) }
    val formattedDate = remember(project.lastOpenedAt) { dateFormat.format(Date(project.lastOpenedAt)) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .testTag("project_card_${project.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) {
            CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(PrimaryBlue),
                width = 2.dp
            )
        } else {
            CardDefaults.outlinedCardBorder()
        }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = project.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        if (isSelected) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = PrimaryBlue
                            ) {
                                Text(
                                    text = "ACTIVE",
                                    color = androidx.compose.ui.graphics.Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = project.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onTogglePin) {
                    Icon(
                        imageVector = if (project.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = "Pin project",
                        tint = if (project.isPinned) PrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (project.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (project.isFavorite) StatusOrange else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More actions")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Duplicate") },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onDuplicate()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Export ZIP") },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onExportZip()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("ABI Manager") },
                            leadingIcon = { Icon(Icons.Default.Build, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onOpenAbiManager()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Info row: Version, SDK, ABIs, Size
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "v${project.versionName} (${project.versionCode})",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "API ${project.minSdk}-${project.targetSdk}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = project.mainLanguage,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Actions row: [ Code Editor ] [ Build APK ]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenEditor,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Editor", fontSize = 13.sp)
                }

                Button(
                    onClick = onOpenBuild,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Build APK", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun NewProjectDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, pkg: String, template: ProjectTemplate, minSdk: Int, targetSdk: Int) -> Unit
) {
    var name by remember { mutableStateOf("My Application") }
    var pkg by remember { mutableStateOf("com.example.myapp") }
    var selectedTemplate by remember { mutableStateOf(ProjectTemplate.EMPTY_ACTIVITY_KOTLIN) }
    var minSdk by remember { mutableIntStateOf(24) }
    var targetSdk by remember { mutableIntStateOf(35) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Android Project") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        val clean = it.lowercase().replace(" ", "")
                        pkg = "com.example.$clean"
                    },
                    label = { Text("Application Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = pkg,
                    onValueChange = { pkg = it },
                    label = { Text("Package Name / Application ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Template:", style = MaterialTheme.typography.labelLarge)
                ProjectTemplate.entries.forEach { tpl ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedTemplate = tpl }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedTemplate == tpl,
                            onClick = { selectedTemplate = tpl }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(tpl.title, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && pkg.isNotBlank()) {
                        onCreate(name.trim(), pkg.trim(), selectedTemplate, minSdk, targetSdk)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun GitCloneDialog(
    progress: com.example.git.GitCloneProgress?,
    onDismiss: () -> Unit,
    onClone: (url: String, token: String?) -> Unit
) {
    var repoUrl by remember { mutableStateOf("https://github.com/android/architecture-samples.git") }
    var token by remember { mutableStateOf("") }
    var isCloning by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isCloning) onDismiss() },
        title = { Text("Clone Git Repository") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Clone a complete Android project directly into the app workspace.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = repoUrl,
                    onValueChange = { repoUrl = it },
                    label = { Text("Git Repository URL") },
                    placeholder = { Text("https://github.com/owner/repo.git") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isCloning
                )

                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Personal Access Token (Optional)") },
                    placeholder = { Text("Required for private repositories") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isCloning
                )

                if (progress != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = progress.stage,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (progress.error != null) MaterialTheme.colorScheme.error else PrimaryBlue
                    )
                    LinearProgressIndicator(
                        progress = { (progress.percentage / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = PrimaryBlue
                    )
                    if (progress.error != null) {
                        Text(
                            text = progress.error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (repoUrl.isNotBlank()) {
                        isCloning = true
                        onClone(repoUrl.trim(), token.ifBlank { null })
                    }
                },
                enabled = !isCloning && repoUrl.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text(if (isCloning) "Cloning..." else "Clone")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isCloning
            ) {
                Text("Close")
            }
        }
    )
}

@Composable
fun ImportSingleFileDialog(
    onDismiss: () -> Unit,
    onImport: (fileName: String, content: String, projName: String) -> Unit
) {
    var fileName by remember { mutableStateOf("MainActivity.kt") }
    var projectName by remember { mutableStateOf("Imported App") }
    var content by remember {
        mutableStateOf(
            """package com.example.imported

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this)
        tv.text = "Hello from ATP Android Builder Online!"
        setContentView(tv)
    }
}"""
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Source File") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it },
                    label = { Text("Target Project Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("Source File Name (.kt or .java)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Source Code") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (fileName.isNotBlank() && projectName.isNotBlank()) {
                        onImport(fileName.trim(), content, projectName.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
