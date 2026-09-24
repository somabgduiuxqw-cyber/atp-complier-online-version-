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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CompilerTool
import com.example.ui.MainViewModel
import com.example.ui.theme.DarkSilver
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import java.io.File

@Composable
fun ApkToolsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    val lastApkInfo by viewModel.lastApkInfo.collectAsState()
    val selectedProject by viewModel.selectedProject.collectAsState()

    // Determine target APK: either from last build or from project's lastApkPath
    val apkFile = remember(lastApkInfo, selectedProject) {
        if (lastApkInfo != null) {
            File(lastApkInfo!!.filePath)
        } else if (selectedProject?.lastApkPath != null) {
            File(selectedProject!!.lastApkPath!!)
        } else null
    }

    val info = remember(apkFile) {
        if (apkFile != null && apkFile.exists()) {
            viewModel.apkVerifier.verifyApk(apkFile)
        } else lastApkInfo
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp)
    ) {
        if (info == null || apkFile == null || !apkFile.exists()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Android,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No Built APK Available",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Run a build in the Build tab to generate a signed Android APK.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            // Header Card: File name & verification status
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("apk_info_header_card"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (info.isValid) StatusGreen.copy(alpha = 0.1f) else StatusRed.copy(alpha = 0.1f)
                    ),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(if (info.isValid) StatusGreen else StatusRed)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (info.isValid) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (info.isValid) StatusGreen else StatusRed
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = info.fileName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (info.isValid) "✓ Verified Android APK Package (Ready for distribution)" else "✕ Verification issues detected",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (info.isValid) StatusGreen else StatusRed,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Primary Actions: [ Share APK ] [ Install APK ]
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.shareApk(context, apkFile) },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("share_apk_btn"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share APK")
                    }

                    Button(
                        onClick = { viewModel.installApk(context, apkFile) },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("install_apk_btn"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = StatusGreen)
                    ) {
                        Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Install APK")
                    }
                }
            }

            // Secondary Actions: [ Copy Path ] [ Copy Info ]
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clip = ClipData.newPlainText("APK Path", apkFile.absolutePath)
                            clipboardManager.setPrimaryClip(clip)
                            Toast.makeText(context, "APK path copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy Path", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            val parent = apkFile.parentFile?.absolutePath ?: ""
                            val clip = ClipData.newPlainText("Output Folder", parent)
                            clipboardManager.setPrimaryClip(clip)
                            Toast.makeText(context, "Output folder copied: $parent", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Output Folder", fontSize = 12.sp)
                    }
                }
            }

            // APK Metadata Details Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "APK Package Specifications",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )

                        DetailRow("Package Name", info.packageName)
                        DetailRow("Version", "${info.versionName} (Code ${info.versionCode})")
                        DetailRow("File Size", CompilerTool.formatBytes(info.fileSize))
                        DetailRow("SDK Support", "Min API ${info.minSdk} | Target API ${info.targetSdk}")
                        DetailRow("Included ABIs", info.includedAbis.joinToString().ifEmpty { "None (Pure Java/Kotlin)" })
                        DetailRow("Signing Scheme", info.signingScheme)
                        DetailRow("Signer", info.signerSubject)
                        DetailRow("Classes DEX", if (info.hasDex) "✓ Verified (classes.dex)" else "✕ Missing")
                        DetailRow("Manifest", if (info.hasManifest) "✓ Binary AndroidManifest.xml" else "✕ Missing")
                    }
                }
            }

            // Verification Check Details
            if (info.verificationErrors.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = StatusRed.copy(alpha = 0.1f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Verification Issues:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = StatusRed
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            info.verificationErrors.forEach { err ->
                                Text(
                                    text = "• $err",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = StatusRed
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}
