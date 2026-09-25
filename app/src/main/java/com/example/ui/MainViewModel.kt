package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.compiler.ApkVerifier
import com.example.compiler.ElfInspector
import com.example.compiler.KeystoreHelper
import com.example.compiler.KeystoreVerificationResult
import com.example.compiler.LocalCompilerEngine
import com.example.compiler.RamSelection
import com.example.compiler.RamStatus
import com.example.data.db.BuilderDatabase
import com.example.data.preferences.PreferenceManager
import com.example.data.repository.BuildHistoryRepository
import com.example.data.repository.ProjectRepository
import com.example.editor.CodeEditorState
import com.example.git.GitCloneProgress
import com.example.git.GitCloner
import com.example.model.AbiType
import com.example.model.ApkInfo
import com.example.model.BuildConfiguration
import com.example.model.BuildHistoryItem
import com.example.model.BuildMode
import com.example.model.CompilerTool
import com.example.model.NativeLibraryInfo
import com.example.model.Project
import com.example.model.ProjectTemplate
import com.example.model.StorageInfo
import com.example.model.ToolDownloadProgress
import com.example.tools.DownloadManager
import com.example.tools.StorageManager
import com.example.tools.ToolManager
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = BuilderDatabase.getDatabase(application)
    val projectRepository = ProjectRepository(application, db.projectDao())
    val buildHistoryRepository = BuildHistoryRepository(db.buildHistoryDao())
    val downloadManager = DownloadManager()
    val toolManager = ToolManager(application, db.toolDao(), downloadManager)
    val keystoreHelper = KeystoreHelper(application)
    val apkVerifier = ApkVerifier(application)
    val compilerEngine = LocalCompilerEngine(
        context = application,
        projectRepository = projectRepository,
        toolManager = toolManager,
        buildHistoryRepository = buildHistoryRepository,
        keystoreHelper = keystoreHelper,
        apkVerifier = apkVerifier
    )
    val storageManager = StorageManager(application, toolManager)
    val gitCloner = GitCloner(projectRepository)
    val preferenceManager = PreferenceManager(application)
    val editorState = CodeEditorState()

    // Preferences & Theme
    val themeMode: StateFlow<ThemeMode> = preferenceManager.themeMode

    // Projects
    val projects: StateFlow<List<Project>> = projectRepository.projects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedProject = MutableStateFlow<Project?>(null)
    val selectedProject: StateFlow<Project?> = _selectedProject.asStateFlow()

    private val _projectSearchQuery = MutableStateFlow("")
    val projectSearchQuery: StateFlow<String> = _projectSearchQuery.asStateFlow()

    // Tools
    val tools: StateFlow<List<CompilerTool>> = toolManager.tools
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeToolDownloads = MutableStateFlow<Map<String, ToolDownloadProgress>>(emptyMap())
    val activeToolDownloads: StateFlow<Map<String, ToolDownloadProgress>> = _activeToolDownloads.asStateFlow()

    // Build
    private val _buildConfig = MutableStateFlow(BuildConfiguration(projectId = ""))
    val buildConfig: StateFlow<BuildConfiguration> = _buildConfig.asStateFlow()

    private val _lastApkInfo = MutableStateFlow<ApkInfo?>(null)
    val lastApkInfo: StateFlow<ApkInfo?> = _lastApkInfo.asStateFlow()

    private var buildJob: Job? = null

    // RAM Manager State
    private val _selectedRam = MutableStateFlow(RamSelection.AUTO)
    val selectedRam: StateFlow<RamSelection> = _selectedRam.asStateFlow()

    private val _ramStatus = MutableStateFlow(compilerEngine.ramManager.detectCurrentRamStatus(RamSelection.AUTO))
    val ramStatus: StateFlow<RamStatus> = _ramStatus.asStateFlow()

    // Debug Keystore State
    private val _keystoreVerification = MutableStateFlow(keystoreHelper.verifyDebugKeystore())
    val keystoreVerification: StateFlow<KeystoreVerificationResult> = _keystoreVerification.asStateFlow()

    // Storage
    private val _storageInfo = MutableStateFlow<StorageInfo?>(null)
    val storageInfo: StateFlow<StorageInfo?> = _storageInfo.asStateFlow()

    // Build History
    val buildHistory: StateFlow<List<BuildHistoryItem>> = buildHistoryRepository.allHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Git Clone UI State
    private val _gitProgress = MutableStateFlow<GitCloneProgress?>(null)
    val gitProgress: StateFlow<GitCloneProgress?> = _gitProgress.asStateFlow()

    // Native Libraries in project
    private val _projectNativeLibs = MutableStateFlow<List<NativeLibraryInfo>>(emptyList())
    val projectNativeLibs: StateFlow<List<NativeLibraryInfo>> = _projectNativeLibs.asStateFlow()

    init {
        viewModelScope.launch {
            toolManager.initializeDefaultTools()
            refreshStorageInfo()
            refreshRamStatus()
            verifyDebugKeystore()

            projectRepository.projects.collect { list ->
                if (list.isNotEmpty() && _selectedProject.value == null) {
                    val activeId = preferenceManager.getActiveProjectId()
                    val match = list.find { it.id == activeId } ?: list.first()
                    selectProject(match)
                } else if (list.isEmpty()) {
                    createDefaultSampleProject()
                }
            }
        }
    }

    private suspend fun createDefaultSampleProject() {
        val proj = projectRepository.createProject(
            name = "ATP Demo App",
            packageName = "com.atp.demo",
            template = ProjectTemplate.EMPTY_ACTIVITY_KOTLIN
        )
        selectProject(proj)
    }

    fun selectProject(project: Project) {
        _selectedProject.value = project
        preferenceManager.setActiveProjectId(project.id)
        viewModelScope.launch {
            projectRepository.updateLastOpened(project.id)
            _buildConfig.value = BuildConfiguration(
                projectId = project.id,
                buildMode = project.lastBuildType,
                applicationId = project.packageName,
                versionName = project.versionName,
                versionCode = project.versionCode,
                minSdk = project.minSdk,
                targetSdk = project.targetSdk,
                targetAbis = project.selectedAbis
            )
            refreshNativeLibraries(project)

            val sourceDir = File(project.rootDirPath)
            val mainFile = findMainSourceFile(sourceDir)
            if (mainFile != null) {
                editorState.openFile(mainFile)
            }
        }
    }

    fun setProjectSearchQuery(query: String) {
        _projectSearchQuery.value = query
    }

    fun createProject(
        name: String,
        packageName: String,
        template: ProjectTemplate,
        minSdk: Int = 24,
        targetSdk: Int = 35,
        versionName: String = "1.0",
        versionCode: Int = 1
    ) {
        viewModelScope.launch {
            val newProject = projectRepository.createProject(
                name = name,
                packageName = packageName,
                template = template,
                minSdk = minSdk,
                targetSdk = targetSdk,
                versionName = versionName,
                versionCode = versionCode
            )
            selectProject(newProject)
        }
    }

    fun duplicateProject(project: Project, newName: String) {
        viewModelScope.launch {
            val duplicated = projectRepository.duplicateProject(project.id, newName)
            if (duplicated != null) {
                selectProject(duplicated)
            }
        }
    }

    fun renameProject(project: Project, newName: String) {
        viewModelScope.launch {
            projectRepository.renameProject(project.id, newName)
            if (_selectedProject.value?.id == project.id) {
                _selectedProject.value = _selectedProject.value?.copy(name = newName)
            }
        }
    }

    fun toggleFavorite(project: Project) {
        viewModelScope.launch {
            projectRepository.toggleFavorite(project.id, !project.isFavorite)
        }
    }

    fun togglePin(project: Project) {
        viewModelScope.launch {
            projectRepository.togglePin(project.id, !project.isPinned)
        }
    }

    fun deleteProject(project: Project) {
        viewModelScope.launch {
            projectRepository.deleteProject(project.id)
            if (_selectedProject.value?.id == project.id) {
                _selectedProject.value = null
            }
        }
    }

    fun importProjectZip(zipFile: File, name: String) {
        viewModelScope.launch {
            val imported = projectRepository.importProjectFromZip(zipFile, name)
            selectProject(imported)
        }
    }

    fun importSingleSourceFile(fileName: String, content: String, projectName: String) {
        viewModelScope.launch {
            val imported = projectRepository.importSingleSourceFile(fileName, content, projectName)
            selectProject(imported)
        }
    }

    fun exportProjectZip(project: Project, targetFile: File): Boolean {
        var success = false
        viewModelScope.launch {
            success = projectRepository.exportProjectZip(project, targetFile)
        }
        return success
    }

    // Git Clone
    fun cloneGitRepository(url: String, token: String? = null, onComplete: (Project?) -> Unit) {
        viewModelScope.launch {
            _gitProgress.value = GitCloneProgress(stage = "Starting Git clone...")
            val result = gitCloner.cloneRepository(url, token) { progress ->
                _gitProgress.value = progress
            }
            if (result.isSuccess) {
                val proj = result.getOrNull()
                if (proj != null) selectProject(proj)
                onComplete(proj)
            } else {
                onComplete(null)
            }
        }
    }

    fun clearGitProgress() {
        _gitProgress.value = null
    }

    // Tools Manager
    fun installOrUpdateTool(toolId: String) {
        viewModelScope.launch {
            toolManager.installOrUpdateTool(toolId) { progress ->
                val map = _activeToolDownloads.value.toMutableMap()
                map[toolId] = progress
                _activeToolDownloads.value = map
            }
            refreshStorageInfo()
        }
    }

    fun repairTool(toolId: String) {
        installOrUpdateTool(toolId)
    }

    fun pauseToolDownload(toolId: String) {
        downloadManager.pauseDownload(toolId)
    }

    fun resumeToolDownload(toolId: String) {
        downloadManager.resumeDownload(toolId)
    }

    // RAM Manager Actions
    fun selectRam(selection: RamSelection) {
        _selectedRam.value = selection
        _ramStatus.value = compilerEngine.ramManager.detectCurrentRamStatus(selection)
    }

    fun refreshRamStatus() {
        _ramStatus.value = compilerEngine.ramManager.detectCurrentRamStatus(_selectedRam.value)
    }

    // Debug Keystore Actions
    fun verifyDebugKeystore() {
        viewModelScope.launch(Dispatchers.IO) {
            _keystoreVerification.value = keystoreHelper.verifyDebugKeystore()
        }
    }

    fun repairDebugKeystore() {
        viewModelScope.launch(Dispatchers.IO) {
            _keystoreVerification.value = _keystoreVerification.value.copy(
                status = com.example.compiler.DebugKeystoreStatus.REPAIRING,
                details = "Repairing debug.keystore..."
            )
            val result = keystoreHelper.repairDebugKeystore()
            if (result.isSuccess) {
                _keystoreVerification.value = _keystoreVerification.value.copy(
                    status = com.example.compiler.DebugKeystoreStatus.REPAIRED,
                    details = "Debug keystore repaired and verified ✓"
                )
            } else {
                _keystoreVerification.value = _keystoreVerification.value.copy(
                    status = com.example.compiler.DebugKeystoreStatus.INVALID_OR_CORRUPTED,
                    details = "Repair failed: ${result.exceptionOrNull()?.localizedMessage}"
                )
            }
        }
    }

    // Build Settings
    fun updateBuildConfig(transform: (BuildConfiguration) -> BuildConfiguration) {
        _buildConfig.value = transform(_buildConfig.value)
    }

    // Build Execution
    fun startBuild(ignoreAndBuild: Boolean = false) {
        val proj = _selectedProject.value ?: return
        if (compilerEngine.pipeline.isBuilding.value) return

        buildJob = viewModelScope.launch {
            val result = compilerEngine.executeBuild(
                project = proj,
                config = _buildConfig.value,
                ramSelection = _selectedRam.value,
                ignoreAndBuild = ignoreAndBuild
            )
            if (result.isSuccess) {
                _lastApkInfo.value = result.getOrNull()
            }
            refreshStorageInfo()
        }
    }

    fun cancelBuild() {
        compilerEngine.cancelBuild()
        buildJob?.cancel()
    }

    // ABI Management
    fun refreshNativeLibraries(project: Project) {
        viewModelScope.launch(Dispatchers.IO) {
            val sourceDir = File(project.rootDirPath)
            val jniLibsDir = File(sourceDir, "app/src/main/jniLibs")
            val list = mutableListOf<NativeLibraryInfo>()

            for (abi in AbiType.entries) {
                val dir = File(jniLibsDir, abi.dirName)
                if (dir.exists()) {
                    val files = dir.listFiles { _, name -> name.endsWith(".so", ignoreCase = true) }
                    files?.forEach { file ->
                        list.add(ElfInspector.inspectSoFile(file, abi))
                    }
                }
            }
            _projectNativeLibs.value = list
        }
    }

    fun toggleAbi(project: Project, abi: AbiType, enable: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val sourceDir = File(project.rootDirPath)
            val jniLibsDir = File(sourceDir, "app/src/main/jniLibs")
            val abiDir = File(jniLibsDir, abi.dirName)

            if (!enable) {
                if (abiDir.exists()) {
                    abiDir.deleteRecursively()
                }
            } else {
                abiDir.mkdirs()
            }

            val currentAbis = project.selectedAbis.toMutableSet()
            if (enable) currentAbis.add(abi) else currentAbis.remove(abi)
            val updated = project.copy(selectedAbis = currentAbis)

            projectRepository.updateProject(updated)
            _selectedProject.value = updated
            _buildConfig.value = _buildConfig.value.copy(targetAbis = currentAbis)
            refreshNativeLibraries(updated)
        }
    }

    fun importSoFile(project: Project, abi: AbiType, soFile: File) {
        viewModelScope.launch(Dispatchers.IO) {
            val sourceDir = File(project.rootDirPath)
            val abiDir = File(sourceDir, "app/src/main/jniLibs/${abi.dirName}").apply { mkdirs() }
            val targetFile = File(abiDir, soFile.name)
            soFile.copyTo(targetFile, overwrite = true)
            refreshNativeLibraries(project)
        }
    }

    // Storage Management
    fun refreshStorageInfo() {
        viewModelScope.launch {
            _storageInfo.value = storageManager.getStorageInfo()
        }
    }

    fun cleanBuildOutputs() {
        viewModelScope.launch {
            val bytes = storageManager.cleanBuildOutputs()
            Toast.makeText(getApplication(), "Cleaned ${CompilerTool.formatBytes(bytes)} of build outputs", Toast.LENGTH_SHORT).show()
            refreshStorageInfo()
        }
    }

    fun cleanTempFiles() {
        viewModelScope.launch {
            val bytes = storageManager.cleanTemporaryBuildFiles()
            Toast.makeText(getApplication(), "Cleaned ${CompilerTool.formatBytes(bytes)} of temp build files", Toast.LENGTH_SHORT).show()
            refreshStorageInfo()
        }
    }

    fun cleanCaches() {
        viewModelScope.launch {
            val bytes = storageManager.cleanProjectCaches()
            Toast.makeText(getApplication(), "Cleaned ${CompilerTool.formatBytes(bytes)} of project caches", Toast.LENGTH_SHORT).show()
            refreshStorageInfo()
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        preferenceManager.setThemeMode(mode)
    }

    // APK Tools Actions
    fun shareApk(context: Context, apkFile: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share APK"))
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot share APK: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun installApk(context: Context, apkFile: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot prompt install: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun findMainSourceFile(dir: File): File? {
        val files = dir.listFiles() ?: return null
        for (f in files) {
            if (f.name.equals("MainActivity.kt", ignoreCase = true) || f.name.equals("MainActivity.java", ignoreCase = true)) {
                return f
            }
            if (f.isDirectory) {
                val found = findMainSourceFile(f)
                if (found != null) return found
            }
        }
        return null
    }
}
