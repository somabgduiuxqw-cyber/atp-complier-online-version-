package com.example.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AbiType
import com.example.model.CompilerTool
import com.example.model.NativeLibraryInfo
import com.example.ui.MainViewModel
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusOrange
import com.example.ui.theme.StatusRed
import java.io.File

@Composable
fun AbiManagerScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val selectedProject by viewModel.selectedProject.collectAsState()
    val nativeLibs by viewModel.projectNativeLibs.collectAsState()

    var abiToDisable by remember { mutableStateOf<AbiType?>(null) }
    var showImportSoDialog by remember { mutableStateOf(false) }
    var targetAbiForImport by remember { mutableStateOf(AbiType.ARM64_V8A) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "ABI Architecture Management",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Configure 64-bit and 32-bit native library targets for ${selectedProject?.name ?: "active project"}. Native libraries are stored in app/src/main/jniLibs/<abi>/.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ARM64 Switch Card
        item {
            val project = selectedProject
            val isArm64Enabled = project?.selectedAbis?.contains(AbiType.ARM64_V8A) == true
            AbiToggleCard(
                abi = AbiType.ARM64_V8A,
                isEnabled = isArm64Enabled,
                onToggle = { enable ->
                    if (!enable) {
                        abiToDisable = AbiType.ARM64_V8A
                    } else if (project != null) {
                        viewModel.toggleAbi(project, AbiType.ARM64_V8A, true)
                    }
                },
                onImportSo = {
                    targetAbiForImport = AbiType.ARM64_V8A
                    showImportSoDialog = true
                }
            )
        }

        // ARM32 Switch Card
        item {
            val project = selectedProject
            val isArm32Enabled = project?.selectedAbis?.contains(AbiType.ARMEABI_V7A) == true
            AbiToggleCard(
                abi = AbiType.ARMEABI_V7A,
                isEnabled = isArm32Enabled,
                onToggle = { enable ->
                    if (!enable) {
                        abiToDisable = AbiType.ARMEABI_V7A
                    } else if (project != null) {
                        viewModel.toggleAbi(project, AbiType.ARMEABI_V7A, true)
                    }
                },
                onImportSo = {
                    targetAbiForImport = AbiType.ARMEABI_V7A
                    showImportSoDialog = true
                }
            )
        }

        // Detected Native Libraries (.so) & ELF inspection
        item {
            Text(
                text = "Detected Native Libraries in Project (${nativeLibs.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }

        if (nativeLibs.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No .so native libraries currently in jniLibs/",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Pure Kotlin/Java applications do not require .so files.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(nativeLibs) { lib ->
                NativeLibraryCard(lib)
            }
        }
    }

    // Confirmation dialog when disabling an ABI
    abiToDisable?.let { abi ->
        AlertDialog(
            onDismissRequest = { abiToDisable = null },
            title = { Text("Disable ${abi.displayName}?") },
            text = {
                Text(
                    "Disabling this ABI will remove ONLY the 'app/src/main/jniLibs/${abi.dirName}/' directory and its contents.\n\nThe parent jniLibs/ folder, other ABIs, and all unrelated project files will remain safe."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val proj = selectedProject
                        if (proj != null) {
                            viewModel.toggleAbi(proj, abi, false)
                        }
                        abiToDisable = null
                        Toast.makeText(context, "${abi.displayName} disabled", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Confirm Disable")
                }
            },
            dismissButton = {
                TextButton(onClick = { abiToDisable = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Import .so Dialog
    if (showImportSoDialog) {
        ImportSoDialog(
            targetAbi = targetAbiForImport,
            onDismiss = { showImportSoDialog = false },
            onImport = { fileName, contentBytes ->
                val proj = selectedProject
                if (proj != null) {
                    val temp = File(context.cacheDir, fileName).apply { writeBytes(contentBytes) }
                    viewModel.importSoFile(proj, targetAbiForImport, temp)
                    showImportSoDialog = false
                    Toast.makeText(context, "$fileName imported into ${targetAbiForImport.dirName}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
fun AbiToggleCard(
    abi: AbiType,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onImportSo: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = abi.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Directory: app/src/main/jniLibs/${abi.dirName}/",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(checkedThumbColor = PrimaryBlue)
                )
            }

            if (isEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onImportSo,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import .so to ${abi.dirName}", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun NativeLibraryCard(lib: NativeLibraryInfo) {
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
            Icon(
                imageVector = if (lib.isElfValid) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint = if (lib.isElfValid) StatusGreen else StatusRed
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = lib.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "ELF Inspection: ${lib.detectedArchitecture}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (lib.isElfValid) MaterialTheme.colorScheme.onSurfaceVariant else StatusRed
                )
                Text(
                    text = "Size: ${CompilerTool.formatBytes(lib.fileSizeBytes)} | ABI: ${lib.abi.dirName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ImportSoDialog(
    targetAbi: AbiType,
    onDismiss: () -> Unit,
    onImport: (fileName: String, bytes: ByteArray) -> Unit
) {
    var fileName by remember { mutableStateOf("libnative.so") }
    var mockData by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import .so to ${targetAbi.dirName}") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Specify native shared object name to generate or link into ${targetAbi.dirName}/",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("File Name (.so)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (fileName.isNotBlank()) {
                        // Generate valid minimal ELF header for the given target ABI
                        val elfBytes = generateValidElfBytes(targetAbi)
                        onImport(fileName.trim(), elfBytes)
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

fun generateValidElfBytes(abi: AbiType): ByteArray {
    val bytes = ByteArray(64)
    // 0x7F, 'E', 'L', 'F'
    bytes[0] = 0x7F.toByte()
    bytes[1] = 'E'.code.toByte()
    bytes[2] = 'L'.code.toByte()
    bytes[3] = 'F'.code.toByte()

    val is64Bit = abi == AbiType.ARM64_V8A
    bytes[4] = (if (is64Bit) 2 else 1).toByte() // EI_CLASS
    bytes[5] = 1.toByte() // EI_DATA (little endian)
    bytes[6] = 1.toByte() // EI_VERSION

    // e_type: ET_DYN (3)
    bytes[16] = 3.toByte()
    bytes[17] = 0.toByte()

    // e_machine: EM_AARCH64 (183 = 0xB7) or EM_ARM (40 = 0x28)
    if (is64Bit) {
        bytes[18] = 0xB7.toByte()
        bytes[19] = 0x00.toByte()
    } else {
        bytes[18] = 0x28.toByte()
        bytes[19] = 0x00.toByte()
    }
    return bytes
}
