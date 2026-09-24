package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.editor.SyntaxHighlighter
import com.example.ui.MainViewModel
import com.example.ui.theme.PrimaryBlue
import kotlinx.coroutines.delay
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeEditorScreen(
    viewModel: MainViewModel,
    onNavigateToBuild: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val editorState = viewModel.editorState
    val tabs by editorState.tabs.collectAsState()
    val activeIndex by editorState.activeTabIndex.collectAsState()
    val fontSize by editorState.fontSize.collectAsState()
    val findReplaceState by editorState.findReplaceState.collectAsState()
    val selectedProject by viewModel.selectedProject.collectAsState()

    var showFileBrowserSheet by remember { mutableStateOf(false) }
    var showFontSizeSlider by remember { mutableStateOf(false) }

    val currentTab = editorState.currentTab
    LaunchedEffect(currentTab?.content) {
        if (currentTab != null && currentTab.isDirty) {
            delay(1500)
            editorState.saveActiveFile()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Toolbar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { showFileBrowserSheet = true },
                    modifier = Modifier.testTag("editor_file_browser_btn")
                ) {
                    Icon(Icons.Default.Folder, contentDescription = "Browse Project Files", tint = PrimaryBlue)
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = { editorState.undo() },
                    enabled = currentTab != null && currentTab.undoStack.isNotEmpty(),
                    modifier = Modifier.testTag("editor_undo_btn")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                }

                IconButton(
                    onClick = { editorState.redo() },
                    enabled = currentTab != null && currentTab.redoStack.isNotEmpty(),
                    modifier = Modifier.testTag("editor_redo_btn")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                }

                IconButton(
                    onClick = { editorState.toggleFindReplace(!findReplaceState.isOpen) },
                    modifier = Modifier.testTag("editor_find_replace_btn")
                ) {
                    Icon(
                        Icons.Default.FindReplace,
                        contentDescription = "Find & Replace",
                        tint = if (findReplaceState.isOpen) PrimaryBlue else MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(onClick = { showFontSizeSlider = !showFontSizeSlider }) {
                    Icon(Icons.Default.FormatSize, contentDescription = "Font Size")
                }

                Spacer(modifier = Modifier.weight(1f))

                IconButton(
                    onClick = {
                        val saved = editorState.saveActiveFile()
                        if (saved) {
                            Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.testTag("editor_save_btn")
                ) {
                    Icon(
                        Icons.Default.Save,
                        contentDescription = "Save File",
                        tint = if (currentTab?.isDirty == true) PrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = onNavigateToBuild,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("editor_quick_build_btn")
                ) {
                    Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Build", fontSize = 12.sp)
                }
            }
        }

        // Font Size Slider Panel
        if (showFontSizeSlider) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Font Size: ${fontSize.toInt()}sp", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.width(16.dp))
                    Slider(
                        value = fontSize,
                        onValueChange = { editorState.setFontSize(it) },
                        valueRange = 10f..26f,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Find & Replace Overlay Bar
        if (findReplaceState.isOpen) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = findReplaceState.searchQuery,
                            onValueChange = { editorState.updateSearchQuery(it) },
                            placeholder = { Text("Find...") },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("editor_search_field"),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (findReplaceState.searchQuery.isNotEmpty()) {
                                "${findReplaceState.matchCount} found"
                            } else "",
                            style = MaterialTheme.typography.labelSmall
                        )
                        IconButton(onClick = { editorState.toggleFindReplace(false) }) {
                            Icon(Icons.Default.Close, contentDescription = "Close Find")
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = findReplaceState.replaceQuery,
                            onValueChange = { editorState.updateReplaceQuery(it) },
                            placeholder = { Text("Replace with...") },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("editor_replace_field"),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = { editorState.replaceCurrentMatch() },
                            enabled = findReplaceState.matchCount > 0
                        ) {
                            Text("Replace")
                        }
                        TextButton(
                            onClick = { editorState.replaceAllMatches() },
                            enabled = findReplaceState.matchCount > 0
                        ) {
                            Text("All")
                        }
                    }
                }
            }
        }

        // Tabs Row
        if (tabs.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tab ->
                    val isActive = index == activeIndex
                    Surface(
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                            .clickable { editorState.selectTab(index) }
                            .testTag("editor_tab_$index"),
                        color = if (isActive) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${tab.fileName}${if (tab.isDirty) "*" else ""}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                color = if (isActive) PrimaryBlue else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close tab",
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable { editorState.closeTab(index) }
                            )
                        }
                    }
                }
            }
        }

        // Editor Area
        if (currentTab != null) {
            val lines = remember(currentTab.content) { currentTab.content.lines() }
            val lineCount = lines.size.coerceAtLeast(1)

            val editorScrollState = rememberScrollState()
            val horizontalCodeScroll = rememberScrollState()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(42.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .verticalScroll(editorScrollState)
                        .padding(top = 8.dp, bottom = 40.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    for (i in 1..lineCount) {
                        Text(
                            text = "$i",
                            fontFamily = FontFamily.Monospace,
                            fontSize = fontSize.sp,
                            lineHeight = (fontSize * 1.4f).sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(editorScrollState)
                        .horizontalScroll(horizontalCodeScroll)
                        .padding(8.dp)
                ) {
                    BasicTextField(
                        value = currentTab.content,
                        onValueChange = { editorState.updateContent(it) },
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = fontSize.sp,
                            lineHeight = (fontSize * 1.4f).sp,
                            color = MaterialTheme.colorScheme.onBackground
                        ),
                        cursorBrush = SolidColor(PrimaryBlue),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("code_editor_text_input")
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.InsertDriveFile,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No open files",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { showFileBrowserSheet = true },
                        modifier = Modifier.testTag("editor_browse_files_btn")
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Browse Project Files")
                    }
                }
            }
        }
    }

    if (showFileBrowserSheet) {
        val proj = selectedProject
        ModalBottomSheet(
            onDismissRequest = { showFileBrowserSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Project Files (${proj?.name ?: "No project selected"})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (proj != null) {
                    val rootDir = File(proj.rootDirPath)
                    val allFiles = remember(proj.id) {
                        mutableListOf<File>().apply {
                            fun collect(f: File) {
                                if (f.isDirectory) {
                                    f.listFiles()?.forEach { collect(it) }
                                } else {
                                    add(f)
                                }
                            }
                            collect(rootDir)
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(350.dp)
                    ) {
                        items(allFiles) { file ->
                            val relPath = file.relativeTo(rootDir).path
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        editorState.openFile(file)
                                        showFileBrowserSheet = false
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.InsertDriveFile,
                                    contentDescription = null,
                                    tint = PrimaryBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = relPath,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
