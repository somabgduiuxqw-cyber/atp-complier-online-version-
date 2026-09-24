package com.example.compiler

import com.example.model.AbiType
import com.example.model.NativeLibraryInfo
import java.io.File
import java.io.RandomAccessFile

object ElfInspector {
    fun inspectSoFile(file: File, fallbackAbi: AbiType? = null): NativeLibraryInfo {
        if (!file.exists() || file.length() < 20) {
            return NativeLibraryInfo(
                fileName = file.name,
                abi = fallbackAbi ?: AbiType.ARM64_V8A,
                fileSizeBytes = file.length(),
                isElfValid = false,
                detectedArchitecture = "Invalid / Corrupted (File too small)",
                filePath = file.absolutePath
            )
        }

        try {
            RandomAccessFile(file, "r").use { raf ->
                val ident = ByteArray(16)
                raf.readFully(ident)

                // ELF Magic: 0x7F, 'E', 'L', 'F'
                val isElf = ident[0] == 0x7F.toByte() &&
                        ident[1] == 'E'.code.toByte() &&
                        ident[2] == 'L'.code.toByte() &&
                        ident[3] == 'F'.code.toByte()

                if (!isElf) {
                    return NativeLibraryInfo(
                        fileName = file.name,
                        abi = fallbackAbi ?: AbiType.ARM64_V8A,
                        fileSizeBytes = file.length(),
                        isElfValid = false,
                        detectedArchitecture = "Not an ELF binary",
                        filePath = file.absolutePath
                    )
                }

                val is64Bit = ident[4] == 2.toByte()
                val isLittleEndian = ident[5] == 1.toByte()

                // Read e_machine at offset 18 (2 bytes)
                raf.seek(18)
                val byte1 = raf.read()
                val byte2 = raf.read()
                val machine = if (isLittleEndian) {
                    (byte2 shl 8) or byte1
                } else {
                    (byte1 shl 8) or byte2
                }

                val (abi, archDesc) = when (machine) {
                    183 -> AbiType.ARM64_V8A to "ARM64 (AArch64 / arm64-v8a)" // 0xB7
                    40 -> AbiType.ARMEABI_V7A to "ARM 32-bit (ARMv7 / armeabi-v7a)" // 0x28
                    62 -> AbiType.ARM64_V8A to "x86_64 (Mismatched for ARM target)"
                    3 -> AbiType.ARMEABI_V7A to "x86 (Mismatched for ARM target)"
                    else -> (fallbackAbi ?: AbiType.ARM64_V8A) to "Unknown Machine Architecture (0x${Integer.toHexString(machine)})"
                }

                return NativeLibraryInfo(
                    fileName = file.name,
                    abi = abi,
                    fileSizeBytes = file.length(),
                    isElfValid = true,
                    detectedArchitecture = archDesc,
                    filePath = file.absolutePath
                )
            }
        } catch (e: Exception) {
            return NativeLibraryInfo(
                fileName = file.name,
                abi = fallbackAbi ?: AbiType.ARM64_V8A,
                fileSizeBytes = file.length(),
                isElfValid = false,
                detectedArchitecture = "Read error: ${e.localizedMessage}",
                filePath = file.absolutePath
            )
        }
    }
}
