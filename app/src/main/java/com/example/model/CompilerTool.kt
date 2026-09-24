package com.example.model

enum class ToolStatus {
    NOT_INSTALLED,
    DOWNLOADING,
    EXTRACTING,
    VERIFYING,
    READY,
    UPDATE_AVAILABLE,
    CORRUPTED,
    ERROR
}

data class CompilerTool(
    val id: String,
    val name: String,
    val description: String,
    val installedVersion: String? = null,
    val latestVersion: String,
    val downloadSizeBytes: Long,
    val installedSizeBytes: Long = 0L,
    val status: ToolStatus = ToolStatus.NOT_INSTALLED,
    val isRequired: Boolean = true,
    val downloadUrl: String,
    val sha256Checksum: String,
    val localDirectoryPath: String,
    val primaryBinaryPath: String? = null,
    val compatibilityNotes: String = "",
    val errorMessage: String? = null
) {
    val downloadSizeFormatted: String
        get() = formatBytes(downloadSizeBytes)

    val installedSizeFormatted: String
        get() = if (installedSizeBytes > 0) formatBytes(installedSizeBytes) else "--"

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB")
            var size = bytes.toDouble()
            var unitIndex = 0
            while (size >= 1024.0 && unitIndex < units.size - 1) {
                size /= 1024.0
                unitIndex++
            }
            return String.format("%.1f %s", size, units[unitIndex])
        }
    }
}

data class ToolDownloadProgress(
    val toolId: String,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val percentage: Int = 0,
    val speedBytesPerSec: Long = 0L,
    val remainingBytes: Long = 0L,
    val etaSeconds: Long = 0L,
    val currentStep: String = "",
    val isPaused: Boolean = false,
    val error: String? = null
) {
    val speedFormatted: String
        get() = "${CompilerTool.formatBytes(speedBytesPerSec)}/s"

    val remainingFormatted: String
        get() = CompilerTool.formatBytes(remainingBytes)

    val etaFormatted: String
        get() {
            if (etaSeconds <= 0) return "--"
            val mins = etaSeconds / 60
            val secs = etaSeconds % 60
            return if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
        }
}
