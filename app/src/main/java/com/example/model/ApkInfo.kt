package com.example.model

enum class BuildResult {
    SUCCESS,
    FAILED,
    CANCELLED
}

data class BuildHistoryItem(
    val id: String,
    val projectId: String,
    val projectName: String,
    val buildType: BuildMode,
    val timestamp: Long,
    val result: BuildResult,
    val apkPath: String?,
    val apkSize: Long,
    val durationMs: Long,
    val abiSelection: String,
    val logFilePath: String,
    val errorSummary: String? = null
) {
    val durationFormatted: String
        get() {
            val seconds = durationMs / 1000
            val millis = durationMs % 1000
            return "${seconds}.${millis / 100}s"
        }

    val apkSizeFormatted: String
        get() = if (apkSize > 0) CompilerTool.formatBytes(apkSize) else "--"
}

data class ApkInfo(
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val packageName: String,
    val versionName: String,
    val versionCode: Int,
    val minSdk: Int,
    val targetSdk: Int,
    val isSigned: Boolean,
    val signingScheme: String,
    val signerSubject: String,
    val includedAbis: List<String>,
    val isValid: Boolean,
    val hasManifest: Boolean,
    val hasDex: Boolean,
    val verificationErrors: List<String> = emptyList()
) {
    val fileSizeFormatted: String
        get() = CompilerTool.formatBytes(fileSize)
}

data class StorageInfo(
    val deviceFreeBytes: Long,
    val deviceTotalBytes: Long,
    val compilerSizeBytes: Long,
    val projectsSizeBytes: Long,
    val buildOutputsSizeBytes: Long,
    val cacheSizeBytes: Long,
    val requiredFreeBytesForBuild: Long = 150L * 1024L * 1024L // 150 MB
) {
    val deviceFreeFormatted: String
        get() = CompilerTool.formatBytes(deviceFreeBytes)

    val deviceTotalFormatted: String
        get() = CompilerTool.formatBytes(deviceTotalBytes)

    val compilerSizeFormatted: String
        get() = CompilerTool.formatBytes(compilerSizeBytes)

    val projectsSizeFormatted: String
        get() = CompilerTool.formatBytes(projectsSizeBytes)

    val buildOutputsSizeFormatted: String
        get() = CompilerTool.formatBytes(buildOutputsSizeBytes)

    val cacheSizeFormatted: String
        get() = CompilerTool.formatBytes(cacheSizeBytes)

    val requiredFreeFormatted: String
        get() = CompilerTool.formatBytes(requiredFreeBytesForBuild)

    val isStorageLow: Boolean
        get() = deviceFreeBytes < requiredFreeBytesForBuild
}
