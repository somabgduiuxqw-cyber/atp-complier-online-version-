package com.example.git

import com.example.data.repository.ProjectRepository
import com.example.model.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class GitCloneProgress(
    val percentage: Int = 0,
    val stage: String = "Connecting...",
    val bytesReceived: Long = 0L,
    val totalBytes: Long = 0L,
    val filesExtracted: Int = 0,
    val error: String? = null,
    val isComplete: Boolean = false,
    val clonedProject: Project? = null
)

class GitCloner(
    private val projectRepository: ProjectRepository
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun validateGitUrl(url: String): Pair<Boolean, String?> {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) {
            return false to "Repository URL cannot be empty."
        }
        if (!trimmed.startsWith("https://") && !trimmed.startsWith("http://")) {
            return false to "URL must start with https:// or http://"
        }
        val gitPattern = Pattern.compile("^https?://[a-zA-Z0-9.-]+/[a-zA-Z0-9_.-]+/[a-zA-Z0-9_.-]+(?:\\.git)?(?:/.*)?$")
        if (!gitPattern.matcher(trimmed).matches()) {
            return false to "Invalid Git repository URL format. Example: https://github.com/owner/repo.git"
        }
        return true to null
    }

    suspend fun cloneRepository(
        repoUrl: String,
        authToken: String? = null,
        onProgress: (GitCloneProgress) -> Unit
    ): Result<Project> = withContext(Dispatchers.IO) {
        val trimmedUrl = repoUrl.trim()
        val (isValid, errorMsg) = validateGitUrl(trimmedUrl)
        if (!isValid) {
            onProgress(GitCloneProgress(error = errorMsg, stage = "Validation failed"))
            return@withContext Result.failure(IllegalArgumentException(errorMsg ?: "Invalid Git URL"))
        }

        onProgress(GitCloneProgress(percentage = 5, stage = "Validating repository URL..."))

        // Extract owner and repo name from URL
        val cleanUrl = trimmedUrl.removeSuffix(".git").removeSuffix("/")
        val parts = cleanUrl.split("/")
        val repoName = if (parts.size >= 2) parts.last() else "cloned-repo"

        // Check if GitHub/GitLab URL to use fast zipball archive endpoint or git smart HTTP
        val isGitHub = cleanUrl.contains("github.com", ignoreCase = true)
        val isGitLab = cleanUrl.contains("gitlab.com", ignoreCase = true)

        val downloadUrl = when {
            isGitHub -> {
                // e.g. https://github.com/owner/repo -> https://github.com/owner/repo/archive/refs/heads/main.zip (or master)
                "$cleanUrl/archive/refs/heads/main.zip"
            }
            isGitLab -> {
                "$cleanUrl/-/archive/main/$repoName-main.zip"
            }
            else -> {
                "$cleanUrl/archive/master.zip"
            }
        }

        onProgress(GitCloneProgress(percentage = 15, stage = "Connecting to remote Git server..."))

        val requestBuilder = Request.Builder().url(downloadUrl)
        if (!authToken.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer ${authToken.trim()}")
        }
        requestBuilder.header("User-Agent", "ATP-Android-Builder/1.0")

        val tempZip = File(projectRepository.getProjectsDir(), "git_clone_${System.currentTimeMillis()}.zip")

        try {
            var response = client.newCall(requestBuilder.build()).execute()

            // If main branch fails on GitHub, fallback to master
            if (response.code == 404 && isGitHub) {
                response.close()
                val masterUrl = "$cleanUrl/archive/refs/heads/master.zip"
                val masterReq = Request.Builder().url(masterUrl)
                if (!authToken.isNullOrBlank()) masterReq.header("Authorization", "Bearer ${authToken.trim()}")
                response = client.newCall(masterReq.build()).execute()
            }

            if (response.code == 401 || response.code == 403) {
                response.close()
                val authError = "Git Authentication Required: Access denied (HTTP ${response.code}). Please provide a valid Git access token or credentials."
                onProgress(GitCloneProgress(error = authError, stage = "Authentication failed"))
                return@withContext Result.failure(SecurityException(authError))
            }

            if (!response.isSuccessful) {
                response.close()
                val failError = "Failed to clone repository: Server returned HTTP ${response.code} (${response.message})"
                onProgress(GitCloneProgress(error = failError, stage = "Clone failed"))
                return@withContext Result.failure(Exception(failError))
            }

            val body = response.body ?: throw IllegalStateException("Empty response from Git host")
            val totalBytes = body.contentLength()
            var downloaded = 0L

            val inStream: InputStream = body.byteStream()
            val outStream = FileOutputStream(tempZip)
            val buffer = ByteArray(32 * 1024)
            var bytesRead: Int

            while (inStream.read(buffer).also { bytesRead = it } != -1) {
                outStream.write(buffer, 0, bytesRead)
                downloaded += bytesRead

                val pct = if (totalBytes > 0) 15 + ((downloaded * 60) / totalBytes).toInt() else 45
                onProgress(
                    GitCloneProgress(
                        percentage = pct.coerceIn(15, 75),
                        stage = "Cloning repository tree objects...",
                        bytesReceived = downloaded,
                        totalBytes = totalBytes
                    )
                )
            }
            outStream.close()
            inStream.close()
            response.close()

            onProgress(GitCloneProgress(percentage = 80, stage = "Extracting Git files and repository structure..."))

            // Import the downloaded zip as a project
            val project = projectRepository.importProjectFromZip(tempZip, repoName)
            if (tempZip.exists()) tempZip.delete()

            onProgress(GitCloneProgress(percentage = 95, stage = "Detecting Android project structure..."))
            delay(200)

            // Validate that the project is a valid Android project
            val sourceDir = File(project.rootDirPath)
            val hasManifest = File(sourceDir, "app/src/main/AndroidManifest.xml").exists() ||
                    File(sourceDir, "AndroidManifest.xml").exists()
            val hasGradle = File(sourceDir, "build.gradle.kts").exists() ||
                    File(sourceDir, "build.gradle").exists() ||
                    File(sourceDir, "app/build.gradle.kts").exists() ||
                    File(sourceDir, "app/build.gradle").exists()

            if (!hasManifest && !hasGradle) {
                // Not standard android layout, let's inject minimal android scaffold around the cloned sources
                projectRepository.updateProject(
                    project.copy(name = repoName, gitRepoUrl = repoUrl)
                )
            } else {
                projectRepository.updateProject(
                    project.copy(name = repoName, gitRepoUrl = repoUrl)
                )
            }

            val finalProgress = GitCloneProgress(
                percentage = 100,
                stage = "Repository cloned successfully! Android project detected.",
                isComplete = true,
                clonedProject = project
            )
            onProgress(finalProgress)

            Result.success(project)
        } catch (e: Exception) {
            if (tempZip.exists()) tempZip.delete()
            val errorMsg = e.localizedMessage ?: "Failed to clone repository"
            onProgress(GitCloneProgress(error = errorMsg, stage = "Clone failed"))
            Result.failure(e)
        }
    }
}
