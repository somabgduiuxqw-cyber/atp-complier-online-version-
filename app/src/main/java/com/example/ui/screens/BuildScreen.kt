package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.compiler.DebugKeystoreStatus
import com.example.compiler.MemoryPressureLevel
import com.example.compiler.RamSelection
import com.example.model.BuildMode
import com.example.model.BuildResult
import com.example.model.StageState
import com.example.model.StageStatus
import com.example.ui.MainViewModel
import com.example.ui.components.StageStatusIcon
import com.example.ui.theme.DarkSilver
import com.example.ui.theme.LightSilver
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusOrange
import com.example.ui.theme.StatusRed

@Composable
fun BuildScreen(
    viewModel: MainViewModel,
    onNavigateToApkTools: () -> Unit,
    onNavigateToAbi: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

    val selectedProject by viewModel.selectedProject.collectAsState()
    val buildConfig by viewModel.buildConfig.collectAsState()
    val stages by viewModel.compilerEngine.pipeline.stages.collectAsState()
    val isBuilding by viewModel.compilerEngine.pipeline.isBuilding.collectAsState()
    val buildLogs by viewModel.compilerEngine.pipeline.buildLogs.collectAsState()
    val finalResult by viewModel.compilerEngine.pipeline.finalResult.collectAsState()
    val errorMessage by viewModel.compilerEngine.pipeline.errorMessage.collectAsState()

    val selectedRam by viewModel.selectedRam.collectAsState()
    val ramStatus by viewModel.ramStatus.collectAsState()
    val keystoreVerification by viewModel.keystoreVerification.collectAsState()

    var showConfigDialog by remember { mutableStateOf(false) }
    var showFullLogDialog by remember { mutableStateOf(false) }

    val logListState = rememberLazyListState()
    LaunchedEffect(buildLogs.size) {
        if (buildLogs.isNotEmpty()) {
            logListState.animateScrollToItem(buildLogs.size - 1)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp)
    ) {
        // Project & Build Configuration Summary Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("build_config_card"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedProject?.name ?: "No project selected",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = buildConfig.applicationId,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        OutlinedButton(
                            onClick = { showConfigDialog = true },
                            enabled = !isBuilding,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Configure", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = buildConfig.buildMode == BuildMode.DEBUG,
                            onClick = {
                                viewModel.updateBuildConfig { it.copy(buildMode = BuildMode.DEBUG) }
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            enabled = !isBuilding
                        ) {
                            Text("Debug APK")
                        }
                        SegmentedButton(
                            selected = buildConfig.buildMode == BuildMode.RELEASE,
                            onClick = {
                                viewModel.updateBuildConfig { it.copy(buildMode = BuildMode.RELEASE) }
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            enabled = !isBuilding
                        ) {
                            Text("Release APK")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

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
                                text = "v${buildConfig.versionName} (${buildConfig.versionCode})",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "ABIs: ${buildConfig.targetAbis.joinToString { it.dirName }}",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        TextButton(
                            onClick = onNavigateToAbi,
                            enabled = !isBuilding
                        ) {
                            Text("ABI Manager", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Debug Keystore Manager Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("debug_keystore_card"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = if (keystoreVerification.status == DebugKeystoreStatus.READY || keystoreVerification.status == DebugKeystoreStatus.REPAIRED) StatusGreen else StatusOrange,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Debug Keystore",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.weight(1f))

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = when (keystoreVerification.status) {
                                DebugKeystoreStatus.READY, DebugKeystoreStatus.REPAIRED -> StatusGreen.copy(alpha = 0.15f)
                                DebugKeystoreStatus.REPAIRING -> PrimaryBlue.copy(alpha = 0.15f)
                                else -> StatusRed.copy(alpha = 0.15f)
                            }
                        ) {
                            Text(
                                text = "Status: ${keystoreVerification.status.label}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = when (keystoreVerification.status) {
                                    DebugKeystoreStatus.READY, DebugKeystoreStatus.REPAIRED -> StatusGreen
                                    DebugKeystoreStatus.REPAIRING -> PrimaryBlue
                                    else -> StatusRed
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = keystoreVerification.details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.verifyDebugKeystore() },
                            enabled = !isBuilding,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Verify", fontSize = 12.sp)
                        }

                        Button(
                            onClick = { viewModel.repairDebugKeystore() },
                            enabled = !isBuilding,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                        ) {
                            Text("Repair", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // RAM Manager Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ram_manager_card"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = null,
                            tint = if (ramStatus.memoryPressure == MemoryPressureLevel.HIGH_PRESSURE) StatusRed else PrimaryBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "RAM Manager",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.weight(1f))

                        IconButton(
                            onClick = { viewModel.refreshRamStatus() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh RAM", modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Total RAM: ${ramStatus.totalRamFormatted}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Available: ${ramStatus.availableRamFormatted}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (ramStatus.memoryPressure == MemoryPressureLevel.HIGH_PRESSURE) StatusRed else StatusGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Status: ${ramStatus.statusText}",
                        style = MaterialTheme.typography.bodySmall,
                        color = when (ramStatus.memoryPressure) {
                            MemoryPressureLevel.HIGH_PRESSURE -> StatusRed
                            MemoryPressureLevel.MODERATE -> StatusOrange
                            MemoryPressureLevel.NORMAL -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Requested: ${ramStatus.requestedFormatted}  |  Using: ${ramStatus.allocatedFormatted}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "RAM Target Limit:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    // Horizontal scrollable RAM limits
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RamSelection.entries.forEach { sel ->
                            FilterChip(
                                selected = selectedRam == sel,
                                onClick = { viewModel.selectRam(sel) },
                                label = { Text(sel.displayName, fontSize = 11.sp) },
                                enabled = !isBuilding
                            )
                        }
                    }
                }
            }
        }

        // Build Action Buttons
        item {
            if (isBuilding) {
                Button(
                    onClick = { viewModel.cancelBuild() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("cancel_build_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Cancel, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("CANCEL BUILD", fontWeight = FontWeight.Bold)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.startBuild(ignoreAndBuild = true) },
                        modifier = Modifier
                            .weight(0.42f)
                            .height(48.dp)
                            .testTag("ignore_and_build_button"),
                        shape = RoundedCornerShape(10.dp),
                        enabled = selectedProject != null
                    ) {
                        Text("Ignore & Build", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = { viewModel.startBuild(ignoreAndBuild = false) },
                        modifier = Modifier
                            .weight(0.58f)
                            .height(48.dp)
                            .testTag("start_build_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        enabled = selectedProject != null
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "BUILD ${if (buildConfig.buildMode == BuildMode.RELEASE) "RELEASE" else "DEBUG"}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Result Card: SUCCESS
        if (finalResult == BuildResult.SUCCESS) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("build_success_card"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = StatusGreen.copy(alpha = 0.12f)),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(StatusGreen),
                        width = 1.5.dp
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusGreen)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "BUILD SUCCESSFUL",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = StatusGreen
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Genuine Android APK assembled, signed with RSA-2048, and verified successfully.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onNavigateToApkTools,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("view_apk_details_button"),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = StatusGreen)
                        ) {
                            Text("Inspect & Export APK", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else if (finalResult == BuildResult.FAILED) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("build_failed_card"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = StatusRed.copy(alpha = 0.12f)),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(StatusRed),
                        width = 1.5.dp
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = StatusRed)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "BUILD FAILED",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = StatusRed
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = errorMessage ?: "Unknown build error",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val clip = ClipData.newPlainText("Build Error", errorMessage ?: "")
                                    clipboardManager.setPrimaryClip(clip)
                                    Toast.makeText(context, "Error copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("Copy Error", fontSize = 11.sp)
                            }

                            OutlinedButton(
                                onClick = { showFullLogDialog = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("Open Log", fontSize = 11.sp)
                            }

                            Button(
                                onClick = { viewModel.startBuild() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                            ) {
                                Text("Retry", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // 10 Pipeline Stages
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pipeline_stages_card"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Build Pipeline (10 Stages)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    stages.forEach { stageState ->
                        StageRowItem(stageState = stageState)
                        if (stageState.stage.stageNumber < 10) {
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }
            }
        }

        // Console Output
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("live_logs_card"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Build Output Console",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.weight(1f))

                        IconButton(
                            onClick = {
                                val fullLog = buildLogs.joinToString("\n")
                                val clip = ClipData.newPlainText("Build Log", fullLog)
                                clipboardManager.setPrimaryClip(clip)
                                Toast.makeText(context, "Full build log copied", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy log", modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSilver)
                            .padding(8.dp)
                    ) {
                        if (buildLogs.isEmpty()) {
                            Text(
                                text = "Build console idle. Press BUILD to start compilation.",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = LightSilver.copy(alpha = 0.6f)
                            )
                        } else {
                            LazyColumn(
                                state = logListState,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(buildLogs) { line ->
                                    val textColor = when {
                                        line.contains("FAILED", ignoreCase = true) || line.contains("ERROR", ignoreCase = true) -> StatusRed
                                        line.contains("SUCCESS", ignoreCase = true) || line.contains("✓") -> StatusGreen
                                        line.startsWith("[STAGE") -> Color(0xFF64B5F6)
                                        else -> LightSilver
                                    }
                                    Text(
                                        text = line,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        lineHeight = 14.sp,
                                        color = textColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showConfigDialog) {
        var appId by remember { mutableStateOf(buildConfig.applicationId) }
        var vName by remember { mutableStateOf(buildConfig.versionName) }
        var vCodeStr by remember { mutableStateOf(buildConfig.versionCode.toString()) }
        var minSdkStr by remember { mutableStateOf(buildConfig.minSdk.toString()) }
        var targetSdkStr by remember { mutableStateOf(buildConfig.targetSdk.toString()) }

        AlertDialog(
            onDismissRequest = { showConfigDialog = false },
            title = { Text("Configure Build Settings") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = appId,
                        onValueChange = { appId = it },
                        label = { Text("Application ID / Package") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = vName,
                            onValueChange = { vName = it },
                            label = { Text("Version Name") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = vCodeStr,
                            onValueChange = { vCodeStr = it },
                            label = { Text("Version Code") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = minSdkStr,
                            onValueChange = { minSdkStr = it },
                            label = { Text("Min SDK") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = targetSdkStr,
                            onValueChange = { targetSdkStr = it },
                            label = { Text("Target SDK") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val vc = vCodeStr.toIntOrNull() ?: 1
                        val minS = minSdkStr.toIntOrNull() ?: 24
                        val tarS = targetSdkStr.toIntOrNull() ?: 35
                        viewModel.updateBuildConfig {
                            it.copy(
                                applicationId = appId.trim(),
                                versionName = vName.trim(),
                                versionCode = vc,
                                minSdk = minS,
                                targetSdk = tarS
                            )
                        }
                        showConfigDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfigDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showFullLogDialog) {
        val fullLog = remember(buildLogs) { buildLogs.joinToString("\n") }
        AlertDialog(
            onDismissRequest = { showFullLogDialog = false },
            title = { Text("Full Build Log") },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSilver)
                        .padding(8.dp)
                ) {
                    LazyColumn {
                        item {
                            Text(
                                text = fullLog.ifEmpty { "Log is empty" },
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = LightSilver
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clip = ClipData.newPlainText("Full Build Log", fullLog)
                        clipboardManager.setPrimaryClip(clip)
                        Toast.makeText(context, "Log copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Copy Full Log")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFullLogDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun StageRowItem(stageState: StageState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (stageState.status == StageStatus.RUNNING) {
                    PrimaryBlue.copy(alpha = 0.08f)
                } else Color.Transparent
            )
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StageStatusIcon(status = stageState.status)

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${stageState.stage.stageNumber}. ${stageState.stage.title}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (stageState.status == StageStatus.RUNNING) FontWeight.Bold else FontWeight.Normal,
                color = when (stageState.status) {
                    StageStatus.COMPLETED -> StatusGreen
                    StageStatus.RUNNING -> PrimaryBlue
                    StageStatus.FAILED -> StatusRed
                    StageStatus.NOT_STARTED -> MaterialTheme.colorScheme.onSurface
                }
            )

            if (stageState.currentTask.isNotEmpty()) {
                Text(
                    text = stageState.currentTask,
                    style = MaterialTheme.typography.labelSmall,
                    color = PrimaryBlue,
                    fontFamily = FontFamily.Monospace
                )
            } else if (stageState.details.isNotEmpty()) {
                Text(
                    text = stageState.details,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (stageState.durationMs > 0) {
            Text(
                text = "${stageState.durationMs}ms",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
