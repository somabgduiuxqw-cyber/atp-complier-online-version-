package com.example.tools

import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.example.model.StorageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class StorageManager(
    private val context: Context,
    private val toolManager: ToolManager
) {
    suspend fun getStorageInfo(): StorageInfo = withContext(Dispatchers.IO) {
        val stat = StatFs(context.filesDir.absolutePath)
        val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
        val totalBytes = stat.blockCountLong * stat.blockSizeLong

        val compilerSizeBytes = toolManager.getInstalledCompilerTotalSizeBytes()

        val projectsDir = File(context.filesDir, "projects")
        val projectsSizeBytes = calculateDirectorySize(projectsDir)

        // Calculate build output and cache sizes across projects
        var buildOutputsSize = 0L
        var cacheSize = 0L

        if (projectsDir.exists()) {
            projectsDir.listFiles()?.forEach { projDir ->
                if (projDir.isDirectory) {
                    val outDir = File(projDir, "output")
                    buildOutputsSize += calculateDirectorySize(outDir)

                    val buildDir = File(projDir, "build")
                    val workspaceDir = File(projDir, "workspace")
                    cacheSize += calculateDirectorySize(buildDir) + calculateDirectorySize(workspaceDir)
                }
            }
        }

        // Add app cache directory
        cacheSize += calculateDirectorySize(context.cacheDir)

        StorageInfo(
            deviceFreeBytes = availableBytes,
            deviceTotalBytes = totalBytes,
            compilerSizeBytes = compilerSizeBytes,
            projectsSizeBytes = projectsSizeBytes,
            buildOutputsSizeBytes = buildOutputsSize,
            cacheSizeBytes = cacheSize
        )
    }

    suspend fun cleanBuildOutputs(): Long = withContext(Dispatchers.IO) {
        var reclaimed = 0L
        val projectsDir = File(context.filesDir, "projects")
        if (projectsDir.exists()) {
            projectsDir.listFiles()?.forEach { projDir ->
                if (projDir.isDirectory) {
                    val outDir = File(projDir, "output")
                    if (outDir.exists()) {
                        val sz = calculateDirectorySize(outDir)
                        outDir.deleteRecursively()
                        outDir.mkdirs()
                        reclaimed += sz
                    }
                }
            }
        }
        reclaimed
    }

    suspend fun cleanTemporaryBuildFiles(): Long = withContext(Dispatchers.IO) {
        var reclaimed = 0L
        val projectsDir = File(context.filesDir, "projects")
        if (projectsDir.exists()) {
            projectsDir.listFiles()?.forEach { projDir ->
                if (projDir.isDirectory) {
                    val buildDir = File(projDir, "build")
                    if (buildDir.exists()) {
                        val sz = calculateDirectorySize(buildDir)
                        buildDir.deleteRecursively()
                        buildDir.mkdirs()
                        reclaimed += sz
                    }
                    val workspaceDir = File(projDir, "workspace")
                    if (workspaceDir.exists()) {
                        val sz = calculateDirectorySize(workspaceDir)
                        workspaceDir.deleteRecursively()
                        workspaceDir.mkdirs()
                        reclaimed += sz
                    }
                }
            }
        }
        reclaimed
    }

    suspend fun cleanProjectCaches(): Long = withContext(Dispatchers.IO) {
        var reclaimed = 0L
        val cache = context.cacheDir
        if (cache.exists()) {
            val sz = calculateDirectorySize(cache)
            cache.deleteRecursively()
            cache.mkdirs()
            reclaimed += sz
        }
        reclaimed
    }

    private fun calculateDirectorySize(dir: File): Long {
        if (!dir.exists()) return 0L
        var size = 0L
        val files = dir.listFiles() ?: return 0L
        for (f in files) {
            size += if (f.isDirectory) calculateDirectorySize(f) else f.length()
        }
        return size
    }
}
