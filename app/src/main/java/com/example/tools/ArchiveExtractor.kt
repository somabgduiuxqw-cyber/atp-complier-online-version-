package com.example.tools

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

data class ExtractionProgress(
    val percentage: Int,
    val currentFile: String,
    val extractedCount: Int,
    val totalEstimatedCount: Int = 100
)

object ArchiveExtractor {

    suspend fun extractArchive(
        archiveFile: File,
        targetDir: File,
        expectedSha256: String? = null,
        onProgress: (ExtractionProgress) -> Unit = {}
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (!archiveFile.exists()) {
            return@withContext Result.failure(IllegalArgumentException("Archive file does not exist: ${archiveFile.absolutePath}"))
        }

        if (archiveFile.length() < 10) {
            return@withContext Result.failure(IllegalStateException("Archive file is corrupted or empty (size: ${archiveFile.length()} bytes)"))
        }

        // Verify SHA-256 if provided
        if (!expectedSha256.isNullOrBlank()) {
            val actualSha256 = calculateSha256(archiveFile)
            if (!actualSha256.equals(expectedSha256.trim(), ignoreCase = true)) {
                return@withContext Result.failure(
                    SecurityException("Integrity check failed! Expected SHA-256: $expectedSha256, but found: $actualSha256")
                )
            }
        }

        val stagingDir = File(targetDir.parentFile, "${targetDir.name}_staging_${System.currentTimeMillis()}").apply { mkdirs() }

        try {
            val fileName = archiveFile.name.lowercase()
            val extractedCount = when {
                fileName.endsWith(".tar.gz") || fileName.endsWith(".tgz") -> {
                    extractTarGz(archiveFile, stagingDir, onProgress)
                }
                fileName.endsWith(".zip") || fileName.endsWith(".jar") -> {
                    extractZip(archiveFile, stagingDir, onProgress)
                }
                else -> {
                    // Try zip extraction as default
                    extractZip(archiveFile, stagingDir, onProgress)
                }
            }

            if (extractedCount <= 0) {
                stagingDir.deleteRecursively()
                return@withContext Result.failure(IllegalStateException("No files were extracted from archive: ${archiveFile.name}"))
            }

            // Flatten single redundant top-level directory if present (e.g. "repo-main/" or "gradle-8.7/")
            flattenSingleRootDirectory(stagingDir)

            // Make executable all files in bin/ or with no extension in bin
            setExecutablePermissionsRecursively(stagingDir)

            // Safe atomic swap: backup targetDir if exists
            val backupDir = File(targetDir.parentFile, "${targetDir.name}.backup")
            if (backupDir.exists()) backupDir.deleteRecursively()

            if (targetDir.exists()) {
                targetDir.renameTo(backupDir)
            }

            val moved = stagingDir.renameTo(targetDir)
            if (!moved) {
                // Fallback to recursive copy
                stagingDir.copyRecursively(targetDir, overwrite = true)
                stagingDir.deleteRecursively()
            }

            if (backupDir.exists()) {
                backupDir.deleteRecursively()
            }

            Result.success(extractedCount)
        } catch (e: Exception) {
            stagingDir.deleteRecursively()
            // Rollback if backup exists
            val backupDir = File(targetDir.parentFile, "${targetDir.name}.backup")
            if (backupDir.exists() && !targetDir.exists()) {
                backupDir.renameTo(targetDir)
            }
            if (e is CancellationException) throw e
            Result.failure(Exception("Extraction failed for ${archiveFile.name}: ${e.localizedMessage ?: "Unknown extraction error"}", e))
        }
    }

    private fun extractZip(
        zipFile: File,
        targetDir: File,
        onProgress: (ExtractionProgress) -> Unit
    ): Int {
        var count = 0
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val newFile = File(targetDir, entry.name)

                // Protect against Zip Slip vulnerability
                if (!newFile.canonicalPath.startsWith(targetDir.canonicalPath)) {
                    throw SecurityException("Security violation: Zip entry is outside target directory: ${entry.name}")
                }

                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    FileOutputStream(newFile).use { fos ->
                        zis.copyTo(fos)
                    }
                    count++

                    if (count % 25 == 0 || count < 25) {
                        onProgress(
                            ExtractionProgress(
                                percentage = (count % 100),
                                currentFile = entry.name,
                                extractedCount = count
                            )
                        )
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return count
    }

    private fun extractTarGz(
        tarGzFile: File,
        targetDir: File,
        onProgress: (ExtractionProgress) -> Unit
    ): Int {
        var count = 0
        GZIPInputStream(BufferedInputStream(FileInputStream(tarGzFile))).use { gzis ->
            // Minimal Tar extractor without heavy external dependencies
            val header = ByteArray(512)
            while (true) {
                var bytesRead = 0
                while (bytesRead < 512) {
                    val r = gzis.read(header, bytesRead, 512 - bytesRead)
                    if (r < 0) break
                    bytesRead += r
                }
                if (bytesRead < 512) break

                // Check for end of archive (two empty blocks)
                if (header.all { it == 0.toByte() }) {
                    break
                }

                // Parse filename (first 100 bytes)
                val rawName = String(header, 0, 100, Charsets.US_ASCII).trim { it <= ' ' || it == '\u0000' }
                if (rawName.isEmpty()) continue

                // Parse size (octal string from bytes 124 to 135)
                val sizeStr = String(header, 124, 12, Charsets.US_ASCII).trim { it <= ' ' || it == '\u0000' }
                val size = sizeStr.toLongOrNull(8) ?: 0L

                // Typeflag at offset 156 ('0'/'\0'=file, '5'=dir)
                val typeFlag = header[156]
                val destFile = File(targetDir, rawName)

                if (!destFile.canonicalPath.startsWith(targetDir.canonicalPath)) {
                    throw SecurityException("Security violation: Tar entry is outside target directory: $rawName")
                }

                if (typeFlag == '5'.toByte() || rawName.endsWith("/")) {
                    destFile.mkdirs()
                } else {
                    destFile.parentFile?.mkdirs()
                    FileOutputStream(destFile).use { fos ->
                        var remaining = size
                        val buffer = ByteArray(8192)
                        while (remaining > 0) {
                            val toRead = Math.min(buffer.size.toLong(), remaining).toInt()
                            val r = gzis.read(buffer, 0, toRead)
                            if (r < 0) break
                            fos.write(buffer, 0, r)
                            remaining -= r
                        }
                    }
                    count++
                    onProgress(
                        ExtractionProgress(
                            percentage = (count % 100),
                            currentFile = rawName,
                            extractedCount = count
                        )
                    )
                }

                // Skip padding to 512-byte boundary
                val padding = (512 - (size % 512)) % 512
                var skipped = 0L
                while (skipped < padding) {
                    val s = gzis.skip(padding - skipped)
                    if (s <= 0) break
                    skipped += s
                }
            }
        }
        return count
    }

    private fun flattenSingleRootDirectory(dir: File) {
        val files = dir.listFiles() ?: return
        if (files.size == 1 && files[0].isDirectory) {
            val singleSubDir = files[0]
            val subFiles = singleSubDir.listFiles() ?: return
            for (f in subFiles) {
                val dest = File(dir, f.name)
                f.renameTo(dest)
            }
            singleSubDir.delete()
        }
    }

    private fun setExecutablePermissionsRecursively(dir: File) {
        val files = dir.listFiles() ?: return
        for (f in files) {
            if (f.isDirectory) {
                setExecutablePermissionsRecursively(f)
            } else {
                val parentName = f.parentFile?.name ?: ""
                val name = f.name
                if (parentName.equals("bin", ignoreCase = true) ||
                    name.endsWith(".sh") ||
                    !name.contains(".")
                ) {
                    f.setExecutable(true, false)
                    f.setReadable(true, false)
                }
            }
        }
    }

    fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
