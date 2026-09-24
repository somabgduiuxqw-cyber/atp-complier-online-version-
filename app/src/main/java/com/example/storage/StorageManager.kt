package com.example.storage

import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.example.model.StorageInfo
import com.example.tools.ToolManager
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

        val compilerBytes = toolManager.getInstalledCompilerTotalSizeBytes()
        val projectsDir = File(context.filesDir, "projects")
        val projectBytes = calculateDirSize(projectsDir)
        val buildOutputsBytes = calculateBuildOutputsSize(projectsDir)
        val cacheDir = context.cacheDir
        val cacheBytes = calculateDirSize(cacheDir)

        StorageInfo(
            deviceFreeBytes = availableBytes,
            deviceTotalBytes = totalBytes,
            compilerSizeBytes = compilerBytes,
            projectsSizeBytes = projectBytes,
            buildOutputsSizeBytes = buildOutputsBytes,
            cacheSizeBytes = cacheBytes,
            requiredFreeBytesForBuild = 150L * 1024L * 1024L
        )
    }

    suspend fun cleanBuildIntermediates(): Long = withContext(Dispatchers.IO) {
        var reclaimed = 0L
        val projectsDir = File(context.filesDir, "projects")
        if (projectsDir.exists()) {
            val projects = projectsDir.listFiles() ?: emptyArray()
            for (p in projects) {
                val buildDir = File(p, "build")
                if (buildDir.exists()) {
                    reclaimed += calculateDirSize(buildDir)
                    buildDir.deleteRecursively()
                    buildDir.mkdirs()
                }
            }
        }
        reclaimed
    }

    suspend fun cleanBuildOutputs(): Long = withContext(Dispatchers.IO) {
        var reclaimed = 0L
        val projectsDir = File(context.filesDir, "projects")
        if (projectsDir.exists()) {
            val projects = projectsDir.listFiles() ?: emptyArray()
            for (p in projects) {
                val outputDir = File(p, "output")
                if (outputDir.exists()) {
                    reclaimed += calculateDirSize(outputDir)
                    outputDir.deleteRecursively()
                    outputDir.mkdirs()
                }
            }
        }
        reclaimed
    }

    suspend fun cleanAppCache(): Long = withContext(Dispatchers.IO) {
        val cacheDir = context.cacheDir
        var reclaimed = calculateDirSize(cacheDir)
        cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        reclaimed
    }

    private fun calculateBuildOutputsSize(projectsDir: File): Long {
        if (!projectsDir.exists()) return 0L
        var total = 0L
        val projects = projectsDir.listFiles() ?: return 0L
        for (p in projects) {
            val outputDir = File(p, "output")
            total += calculateDirSize(outputDir)
        }
        return total
    }

    private fun calculateDirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        var size = 0L
        val files = dir.listFiles() ?: return 0L
        for (f in files) {
            size += if (f.isDirectory) calculateDirSize(f) else f.length()
        }
        return size
    }
}
