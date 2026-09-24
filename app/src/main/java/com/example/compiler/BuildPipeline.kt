package com.example.compiler

import com.example.model.BuildMode
import com.example.model.BuildResult
import com.example.model.BuildStage
import com.example.model.StageState
import com.example.model.StageStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BuildPipeline {
    private val _stages = MutableStateFlow(createInitialStages())
    val stages: StateFlow<List<StageState>> = _stages.asStateFlow()

    private val _currentStage = MutableStateFlow<BuildStage?>(null)
    val currentStage: StateFlow<BuildStage?> = _currentStage.asStateFlow()

    private val _isBuilding = MutableStateFlow(false)
    val isBuilding: StateFlow<Boolean> = _isBuilding.asStateFlow()

    private val _buildLogs = MutableStateFlow<List<String>>(emptyList())
    val buildLogs: StateFlow<List<String>> = _buildLogs.asStateFlow()

    private val _finalResult = MutableStateFlow<BuildResult?>(null)
    val finalResult: StateFlow<BuildResult?> = _finalResult.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var logWriter: FileWriter? = null
    var activeLogFile: File? = null
        private set

    fun reset() {
        _stages.value = createInitialStages()
        _currentStage.value = null
        _isBuilding.value = false
        _buildLogs.value = emptyList()
        _finalResult.value = null
        _errorMessage.value = null
        closeLogWriter()
    }

    fun startBuild(logsDir: File, projectId: String, mode: BuildMode): File {
        reset()
        _isBuilding.value = true
        logsDir.mkdirs()
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val logFile = File(logsDir, "build-$timestamp.log")
        activeLogFile = logFile
        logWriter = FileWriter(logFile, true)

        appendLog("==================================================")
        appendLog("ATP Android Builder Online - Build Execution")
        appendLog("Project: $projectId | Mode: ${mode.name} | Started: $timestamp")
        appendLog("==================================================")
        return logFile
    }

    fun setStageRunning(stage: BuildStage, task: String = "", details: String = "") {
        _currentStage.value = stage
        updateStage(stage) {
            it.copy(
                status = StageStatus.RUNNING,
                currentTask = task,
                details = details,
                startedAtMs = System.currentTimeMillis()
            )
        }
        appendLog("[STAGE ${stage.stageNumber}/10: ${stage.title}] RUNNING - $task $details")
    }

    fun setStageTask(stage: BuildStage, task: String) {
        updateStage(stage) { it.copy(currentTask = task) }
        appendLog("  > $task")
    }

    fun setStageCompleted(stage: BuildStage, details: String = "") {
        val now = System.currentTimeMillis()
        updateStage(stage) {
            val duration = if (it.startedAtMs > 0) now - it.startedAtMs else 0L
            it.copy(
                status = StageStatus.COMPLETED,
                currentTask = "",
                details = details,
                durationMs = duration
            )
        }
        appendLog("[STAGE ${stage.stageNumber}/10: ${stage.title}] COMPLETED ✓")
    }

    fun setStageFailed(stage: BuildStage, error: String) {
        _currentStage.value = null
        _isBuilding.value = false
        _finalResult.value = BuildResult.FAILED
        _errorMessage.value = error
        updateStage(stage) {
            it.copy(
                status = StageStatus.FAILED,
                errorMessage = error
            )
        }
        appendLog("[STAGE ${stage.stageNumber}/10: ${stage.title}] FAILED ✕")
        appendLog("ERROR: $error")
        appendLog("==================================================")
        appendLog("BUILD FAILED")
        appendLog("==================================================")
        closeLogWriter()
    }

    fun finishSuccess() {
        _currentStage.value = null
        _isBuilding.value = false
        _finalResult.value = BuildResult.SUCCESS
        appendLog("==================================================")
        appendLog("BUILD SUCCESSFUL")
        appendLog("==================================================")
        closeLogWriter()
    }

    fun finishCancelled() {
        val curr = _currentStage.value
        if (curr != null) {
            updateStage(curr) { it.copy(status = StageStatus.FAILED, errorMessage = "Build cancelled by user.") }
        }
        _currentStage.value = null
        _isBuilding.value = false
        _finalResult.value = BuildResult.CANCELLED
        _errorMessage.value = "Build was stopped by user request."
        appendLog("[CANCELLED] Build was cancelled by the user.")
        appendLog("==================================================")
        appendLog("BUILD CANCELLED")
        appendLog("==================================================")
        closeLogWriter()
    }

    fun appendLog(line: String) {
        val currentList = _buildLogs.value.toMutableList()
        currentList.add(line)
        _buildLogs.value = currentList
        try {
            logWriter?.write(line + "\n")
            logWriter?.flush()
        } catch (e: Exception) {
            // Ignore stream logging failure
        }
    }

    private fun updateStage(stage: BuildStage, transform: (StageState) -> StageState) {
        _stages.value = _stages.value.map {
            if (it.stage == stage) transform(it) else it
        }
    }

    private fun closeLogWriter() {
        try {
            logWriter?.flush()
            logWriter?.close()
        } catch (e: Exception) {
            // ignore
        } finally {
            logWriter = null
        }
    }

    companion object {
        fun createInitialStages(): List<StageState> {
            return BuildStage.entries.map {
                StageState(stage = it, status = StageStatus.NOT_STARTED)
            }
        }
    }
}
