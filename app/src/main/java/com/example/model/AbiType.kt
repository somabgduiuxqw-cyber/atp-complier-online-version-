package com.example.model

enum class AbiType(val dirName: String, val displayName: String, val elfMachine: Int) {
    ARM64_V8A("arm64-v8a", "ARM64 (v8a)", 183), // 0xB7 = 183 in decimal
    ARMEABI_V7A("armeabi-v7a", "ARM32 (v7a)", 40); // 0x28 = 40 in decimal

    companion object {
        fun fromDir(dir: String): AbiType? = entries.find { it.dirName.equals(dir, ignoreCase = true) }
    }
}

data class NativeLibraryInfo(
    val fileName: String,
    val abi: AbiType,
    val fileSizeBytes: Long,
    val isElfValid: Boolean,
    val detectedArchitecture: String,
    val filePath: String
)
