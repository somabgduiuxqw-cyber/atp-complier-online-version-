package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BuildHistoryItem
import com.example.model.BuildResult
import com.example.model.CompilerTool
import com.example.ui.MainViewModel
import com.example.ui.theme.DarkSilver
import com.example.ui.theme.LightSilver
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusOrange
import com.example.ui.theme.StatusRed
import com.example.ui.theme.ThemeMode
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StorageSettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    val storageInfo by viewModel.storageInfo.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val buildHistory by viewModel.buildHistory.collectAsState()

    var selectedHistoryLog by remember { mutableStateOf<BuildHistoryItem?>(null) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp)
    ) {
        if (storageInfo != null && storageInfo!!.deviceFreeBytes < 200L * 1024L * 1024L) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = StatusOrange.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = StatusOrange)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Low Free Space Warning: Device free storage is under 200 MB. Substantial builds may require additional workspace space.",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusOrange,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Storage Management Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("storage_management_card"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Storage, contentDescription = null, tint = PrimaryBlue)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Device Storage & Space Allocation",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (storageInfo != null) {
                        val s = storageInfo!!
                        DetailRow("Available Device Storage", CompilerTool.formatBytes(s.deviceFreeBytes))
                        DetailRow("Total Device Capacity", CompilerTool.formatBytes(s.deviceTotalBytes))
                        DetailRow("Installed Compiler Tools", CompilerTool.formatBytes(s.compilerSizeBytes))
                        DetailRow("Projects Source Code", CompilerTool.formatBytes(s.projectsSizeBytes))
                        DetailRow("Build Outputs (APKs)", CompilerTool.formatBytes(s.buildOutputsSizeBytes))
                        DetailRow("Temporary Workspace & Caches", CompilerTool.formatBytes(s.cacheSizeBytes))
                    } else {
                        Text("Calculating storage sizes...", style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp), tint = StatusGreen)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Compiler tools remain protected and will never be deleted automatically.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text("Safe Storage Cleanup:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.cleanBuildOutputs() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Clean APKs", fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = { viewModel.cleanTempFiles() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Clean Temp", fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = { viewModel.cleanCaches() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Clean Cache", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Appearance Theme Settings Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Palette, contentDescription = null, tint = PrimaryBlue)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Appearance & Theme",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = themeMode == ThemeMode.SYSTEM,
                            onClick = { viewModel.setThemeMode(ThemeMode.SYSTEM) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                        ) {
                            Text("System", fontSize = 12.sp)
                        }
                        SegmentedButton(
                            selected = themeMode == ThemeMode.LIGHT,
                            onClick = { viewModel.setThemeMode(ThemeMode.LIGHT) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                        ) {
                            Text("Light", fontSize = 12.sp)
                        }
                        SegmentedButton(
                            selected = themeMode == ThemeMode.DARK,
                            onClick = { viewModel.setThemeMode(ThemeMode.DARK) },
                            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                        ) {
                            Text("Dark", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Build History Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.History, contentDescription = null, tint = PrimaryBlue)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Build History (${buildHistory.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (buildHistory.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No builds recorded yet.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        } else {
            items(buildHistory) { item ->
                BuildHistoryRow(
                    item = item,
                    dateFormat = dateFormat,
                    onViewLog = { selectedHistoryLog = item }
                )
            }
        }
    }

    selectedHistoryLog?.let { historyItem ->
        val logContent = remember(historyItem.id) {
            val file = File(historyItem.logFilePath)
            if (file.exists()) file.readText() else "Log file not found: ${historyItem.logFilePath}"
        }

        AlertDialog(
            onDismissRequest = { selectedHistoryLog = null },
            title = { Text("${historyItem.projectName} (${historyItem.buildType.name})") },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSilver)
                        .padding(8.dp)
                ) {
                    LazyColumn {
                        item {
                            Text(
                                text = logContent,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = LightSilver
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clip = ClipData.newPlainText("Build Log", logContent)
                        clipboardManager.setPrimaryClip(clip)
                        Toast.makeText(context, "Log copied", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Copy Log")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedHistoryLog = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun BuildHistoryRow(
    item: BuildHistoryItem,
    dateFormat: SimpleDateFormat,
    onViewLog: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (badgeBg, badgeText, label) = when (item.result) {
                BuildResult.SUCCESS -> Triple(StatusGreen.copy(alpha = 0.15f), StatusGreen, "SUCCESS")
                BuildResult.FAILED -> Triple(StatusRed.copy(alpha = 0.15f), StatusRed, "FAILED")
                BuildResult.CANCELLED -> Triple(StatusOrange.copy(alpha = 0.15f), StatusOrange, "CANCELLED")
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = badgeBg
            ) {
                Text(
                    text = label,
                    color = badgeText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${item.projectName} • ${item.buildType.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${dateFormat.format(Date(item.timestamp))} • Duration: ${item.durationMs / 1000}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (item.apkSize > 0) {
                    Text(
                        text = "APK: ${CompilerTool.formatBytes(item.apkSize)} • ABIs: ${item.abiSelection}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedButton(
                onClick = onViewLog,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("Log", fontSize = 11.sp)
            }
        }
    }
}
