package com.example.compiler

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import java.text.DecimalFormat

enum class RamSelection(val displayName: String, val targetBytes: Long) {
    RAM_500MB("500 MB", 500L * 1024L * 1024L),
    RAM_1GB("1 GB", 1024L * 1024L * 1024L),
    RAM_1_5GB("1.5 GB", 1536L * 1024L * 1024L),
    RAM_2GB("2 GB", 2048L * 1024L * 1024L),
    RAM_2_5GB("2.5 GB", 2560L * 1024L * 1024L),
    RAM_3GB("3 GB", 3072L * 1024L * 1024L),
    RAM_3_5GB("3.5 GB", 3584L * 1024L * 1024L),
    RAM_4GB("4 GB", 4096L * 1024L * 1024L),
    AUTO("Auto (Recommended)", 0L)
}

enum class MemoryPressureLevel {
    NORMAL,
    MODERATE,
    HIGH_PRESSURE
}

data class RamStatus(
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val systemUsedRamBytes: Long,
    val memoryPressure: MemoryPressureLevel,
    val selectedSelection: RamSelection,
    val requestedBytes: Long,
    val allocatedBytes: Long,
    val statusText: String,
    val workerCount: Int,
    val parallelCompilation: Boolean,
    val jvmArgs: String
) {
    val totalRamFormatted: String
        get() = formatBytes(totalRamBytes)

    val availableRamFormatted: String
        get() = formatBytes(availableRamBytes)

    val requestedFormatted: String
        get() = if (selectedSelection == RamSelection.AUTO) "Auto" else formatBytes(requestedBytes)

    val allocatedFormatted: String
        get() = formatBytes(allocatedBytes)

    companion object {
        fun formatBytes(bytes: Long): String {
            val df = DecimalFormat("#.##")
            val mb = bytes.toDouble() / (1024 * 1024)
            return if (mb >= 1024) {
                "${df.format(mb / 1024)} GB"
            } else {
                "${df.format(mb)} MB"
            }
        }
    }
}

class RamManager(private val context: Context) {

    fun detectCurrentRamStatus(
        selected: RamSelection = RamSelection.AUTO,
        ignoreAndBuild: Boolean = false
    ): RamStatus {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)

        val totalRam = if (memoryInfo.totalMem > 0) memoryInfo.totalMem else 4L * 1024L * 1024L * 1024L
        val availRam = if (memoryInfo.availMem > 0) memoryInfo.availMem else (totalRam * 0.45).toLong()
        val systemUsedRam = (totalRam - availRam).coerceAtLeast(0L)

        val pressure = when {
            memoryInfo.lowMemory || availRam < 500L * 1024L * 1024L -> MemoryPressureLevel.HIGH_PRESSURE
            availRam < 1200L * 1024L * 1024L -> MemoryPressureLevel.MODERATE
            else -> MemoryPressureLevel.NORMAL
        }

        if (ignoreAndBuild) {
            // Default configuration without RAM limit throttling
            val defaultAllocMb = 1024
            return RamStatus(
                totalRamBytes = totalRam,
                availableRamBytes = availRam,
                systemUsedRamBytes = systemUsedRam,
                memoryPressure = pressure,
                selectedSelection = selected,
                requestedBytes = availRam,
                allocatedBytes = defaultAllocMb * 1024L * 1024L,
                statusText = "✓ Default System Configuration",
                workerCount = 2,
                parallelCompilation = true,
                jvmArgs = "-Xmx${defaultAllocMb}m -XX:+UseG1GC"
            )
        }

        // Calculate safe allocation
        val osReserveBytes = 400L * 1024L * 1024L // Keep 400MB reserved for Android OS & system processes
        val maxSafeAvailBytes = (availRam - osReserveBytes).coerceAtLeast(256L * 1024L * 1024L)

        val (allocatedBytes, statusText) = if (selected == RamSelection.AUTO) {
            when (pressure) {
                MemoryPressureLevel.HIGH_PRESSURE -> {
                    val safe = (maxSafeAvailBytes * 0.4).toLong().coerceIn(256L * 1024 * 1024, 512L * 1024 * 1024)
                    safe to "⚠ High Memory Pressure (Reduced memory mode)"
                }
                MemoryPressureLevel.MODERATE -> {
                    val safe = (maxSafeAvailBytes * 0.6).toLong().coerceIn(512L * 1024 * 1024, 1200L * 1024 * 1024)
                    safe to "✓ Auto (Moderate RAM balance)"
                }
                MemoryPressureLevel.NORMAL -> {
                    val safe = (maxSafeAvailBytes * 0.75).toLong().coerceIn(1024L * 1024 * 1024, 2048L * 1024 * 1024)
                    safe to "✓ Auto (Optimal build speed)"
                }
            }
        } else {
            val requested = selected.targetBytes
            if (requested > maxSafeAvailBytes) {
                maxSafeAvailBytes to "⚠ Limited RAM (Requested exceeds safe available)"
            } else {
                requested to "✓ Ready (Safe within limits)"
            }
        }

        val allocatedMb = (allocatedBytes / (1024 * 1024)).coerceAtLeast(256)
        val workerCount = when (pressure) {
            MemoryPressureLevel.HIGH_PRESSURE -> 1
            MemoryPressureLevel.MODERATE -> 2
            MemoryPressureLevel.NORMAL -> Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
        }
        val parallelCompilation = pressure == MemoryPressureLevel.NORMAL

        val jvmArgs = "-Xmx${allocatedMb}m -Xms${(allocatedMb / 2).coerceAtLeast(128)}m -XX:+UseG1GC"

        return RamStatus(
            totalRamBytes = totalRam,
            availableRamBytes = availRam,
            systemUsedRamBytes = systemUsedRam,
            memoryPressure = pressure,
            selectedSelection = selected,
            requestedBytes = if (selected == RamSelection.AUTO) allocatedBytes else selected.targetBytes,
            allocatedBytes = allocatedBytes,
            statusText = statusText,
            workerCount = workerCount,
            parallelCompilation = parallelCompilation,
            jvmArgs = jvmArgs
        )
    }
}
