package com.example.abi

import com.example.model.AbiType
import com.example.model.NativeLibraryInfo
import java.io.File
import java.io.RandomAccessFile

object AbiInspector {
    /**
     * Inspects an ELF binary file header to verify its actual target architecture.
     * ELF Header:
     * - Bytes 0..3: 0x7F, 'E', 'L', 'F' (Magic number)
     * - Byte 4: EI_CLASS (1 = 32-bit, 2 = 64-bit)
     * - Byte 5: EI_DATA (1 = Little-endian, 2 = Big-endian)
     * - Bytes 18..19: e_machine (Architecture)
     *   - 0x28 (40): ARM (32-bit armeabi-v7a)
     *   - 0xB7 (183): AArch64 (64-bit arm64-v8a)
     *   - 0x3E (62): x86_64
     *   - 0x03 (3): x86
     */
    fun inspectSoFile(file: File, expectedAbi: AbiType): NativeLibraryInfo {
        if (!file.exists() || file.length() < 20) {
            return NativeLibraryInfo(
                fileName = file.name,
                abi = expectedAbi,
                fileSizeBytes = file.length(),
                isElfValid = false,
                detectedArchitecture = "Invalid / Corrupted (< 20 bytes)",
                filePath = file.absolutePath
            )
        }

        try {
            RandomAccessFile(file, "r").use { raf ->
                val magic = ByteArray(4)
                raf.readFully(magic)
                val isElf = magic[0] == 0x7F.toByte() &&
                        magic[1] == 'E'.code.toByte() &&
                        magic[2] == 'L'.code.toByte() &&
                        magic[3] == 'F'.code.toByte()

                if (!isElf) {
                    return NativeLibraryInfo(
                        fileName = file.name,
                        abi = expectedAbi,
                        fileSizeBytes = file.length(),
                        isElfValid = false,
                        detectedArchitecture = "Non-ELF Binary",
                        filePath = file.absolutePath
                    )
                }

                val eiClass = raf.readByte().toInt() and 0xFF // 1 = 32-bit, 2 = 64-bit
                val eiData = raf.readByte().toInt() and 0xFF  // 1 = Little endian, 2 = Big endian

                // e_machine is at offset 18
                raf.seek(18)
                val b1 = raf.readByte().toInt() and 0xFF
                val b2 = raf.readByte().toInt() and 0xFF
                val eMachine = if (eiData == 1) {
                    (b2 shl 8) or b1
                } else {
                    (b1 shl 8) or b2
                }

                val (archName, detectedAbi) = when (eMachine) {
                    183 -> "AArch64 (ARM 64-bit)" to AbiType.ARM64_V8A
                    40 -> "ARM (32-bit Thumb/v7a)" to AbiType.ARMEABI_V7A
                    62 -> "x86_64 (Intel 64-bit)" to null
                    3 -> "x86 (Intel 32-bit)" to null
                    else -> "Unknown machine ($eMachine)" to null
                }

                val matchesExpected = detectedAbi == expectedAbi

                return NativeLibraryInfo(
                    fileName = file.name,
                    abi = expectedAbi,
                    fileSizeBytes = file.length(),
                    isElfValid = matchesExpected,
                    detectedArchitecture = if (matchesExpected) archName else "$archName (Mismatch for ${expectedAbi.dirName})",
                    filePath = file.absolutePath
                )
            }
        } catch (e: Exception) {
            return NativeLibraryInfo(
                fileName = file.name,
                abi = expectedAbi,
                fileSizeBytes = file.length(),
                isElfValid = false,
                detectedArchitecture = "Error inspecting ELF: ${e.message}",
                filePath = file.absolutePath
            )
        }
    }

    fun listLibrariesForProject(projectRootDir: File): Map<AbiType, List<NativeLibraryInfo>> {
        val jniLibsDir = File(projectRootDir, "app/src/main/jniLibs")
        val result = mutableMapOf<AbiType, MutableList<NativeLibraryInfo>>()
        AbiType.entries.forEach { result[it] = mutableListOf() }

        if (!jniLibsDir.exists()) return result

        for (abi in AbiType.entries) {
            val abiDir = File(jniLibsDir, abi.dirName)
            if (abiDir.exists() && abiDir.isDirectory) {
                val files = abiDir.listFiles { _, name -> name.endsWith(".so", ignoreCase = true) }
                if (files != null) {
                    for (soFile in files) {
                        result[abi]?.add(inspectSoFile(soFile, abi))
                    }
                }
            }
        }

        return result
    }

    fun removeAbiDirectory(projectRootDir: File, abi: AbiType): Boolean {
        val jniLibsDir = File(projectRootDir, "app/src/main/jniLibs")
        val abiDir = File(jniLibsDir, abi.dirName)
        if (abiDir.exists()) {
            return abiDir.deleteRecursively()
        }
        return true
    }

    fun ensureAbiDirectory(projectRootDir: File, abi: AbiType): File {
        val jniLibsDir = File(projectRootDir, "app/src/main/jniLibs")
        val abiDir = File(jniLibsDir, abi.dirName)
        if (!abiDir.exists()) {
            abiDir.mkdirs()
        }
        return abiDir
    }
}
