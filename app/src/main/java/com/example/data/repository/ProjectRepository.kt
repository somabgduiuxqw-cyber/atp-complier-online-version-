package com.example.data.repository

import android.content.Context
import com.example.data.db.ProjectDao
import com.example.data.db.ProjectEntity
import com.example.model.AbiType
import com.example.model.BuildMode
import com.example.model.Project
import com.example.model.ProjectTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ProjectRepository(
    private val context: Context,
    private val projectDao: ProjectDao
) {
    val projects: Flow<List<Project>> = projectDao.getAllProjects()
        .map { list -> list.map { it.toDomain() } }

    val favoriteProjects: Flow<List<Project>> = projectDao.getFavoriteProjects()
        .map { list -> list.map { it.toDomain() } }

    fun getProjectsDir(): File {
        val dir = File(context.filesDir, "projects")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getProjectDir(projectId: String): File {
        return File(getProjectsDir(), projectId)
    }

    suspend fun getProjectById(id: String): Project? = withContext(Dispatchers.IO) {
        projectDao.getProjectById(id)?.toDomain()
    }

    suspend fun createProject(
        name: String,
        packageName: String,
        template: ProjectTemplate,
        minSdk: Int = 24,
        targetSdk: Int = 35,
        versionName: String = "1.0",
        versionCode: Int = 1
    ): Project = withContext(Dispatchers.IO) {
        val projectId = UUID.randomUUID().toString()
        val projectBaseDir = getProjectDir(projectId)

        val sourceDir = File(projectBaseDir, "source")
        val workspaceDir = File(projectBaseDir, "workspace")
        val buildDir = File(projectBaseDir, "build")
        val logsDir = File(projectBaseDir, "logs")
        val outputDir = File(projectBaseDir, "output")

        sourceDir.mkdirs()
        workspaceDir.mkdirs()
        buildDir.mkdirs()
        logsDir.mkdirs()
        outputDir.mkdirs()

        // Setup ABI jniLibs folders
        val jniLibsDir = File(sourceDir, "app/src/main/jniLibs")
        File(jniLibsDir, "arm64-v8a").mkdirs()
        File(jniLibsDir, "armeabi-v7a").mkdirs()

        val mainLanguage = when (template) {
            ProjectTemplate.BASIC_ACTIVITY_JAVA -> "Java"
            else -> "Kotlin"
        }

        populateTemplateFiles(sourceDir, name, packageName, template, minSdk, targetSdk, versionName, versionCode)

        val project = Project(
            id = projectId,
            name = name,
            packageName = packageName,
            versionName = versionName,
            versionCode = versionCode,
            minSdk = minSdk,
            targetSdk = targetSdk,
            mainLanguage = mainLanguage,
            rootDirPath = sourceDir.absolutePath,
            isFavorite = false,
            isPinned = false,
            lastOpenedAt = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis(),
            selectedAbis = setOf(AbiType.ARM64_V8A, AbiType.ARMEABI_V7A),
            lastBuildType = BuildMode.DEBUG
        )

        projectDao.insertProject(ProjectEntity.fromDomain(project))
        project
    }

    suspend fun updateProject(project: Project) = withContext(Dispatchers.IO) {
        projectDao.updateProject(ProjectEntity.fromDomain(project))
    }

    suspend fun renameProject(id: String, newName: String) = withContext(Dispatchers.IO) {
        projectDao.renameProject(id, newName)
    }

    suspend fun toggleFavorite(id: String, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        projectDao.toggleFavorite(id, isFavorite)
    }

    suspend fun togglePin(id: String, isPinned: Boolean) = withContext(Dispatchers.IO) {
        projectDao.togglePin(id, isPinned)
    }

    suspend fun updateLastOpened(id: String) = withContext(Dispatchers.IO) {
        projectDao.updateLastOpened(id, System.currentTimeMillis())
    }

    suspend fun deleteProject(id: String): Boolean = withContext(Dispatchers.IO) {
        val dir = getProjectDir(id)
        if (dir.exists()) {
            dir.deleteRecursively()
        }
        projectDao.deleteProjectById(id)
        true
    }

    suspend fun duplicateProject(originalId: String, newName: String): Project? = withContext(Dispatchers.IO) {
        val original = projectDao.getProjectById(originalId)?.toDomain() ?: return@withContext null
        val newId = UUID.randomUUID().toString()
        val newProjectDir = getProjectDir(newId)

        val originalDir = getProjectDir(originalId)
        val originalSource = File(originalDir, "source")
        val newSource = File(newProjectDir, "source")

        if (originalSource.exists()) {
            copyDirectory(originalSource, newSource)
        }

        File(newProjectDir, "workspace").mkdirs()
        File(newProjectDir, "build").mkdirs()
        File(newProjectDir, "logs").mkdirs()
        File(newProjectDir, "output").mkdirs()

        val duplicated = original.copy(
            id = newId,
            name = newName,
            rootDirPath = newSource.absolutePath,
            isPinned = false,
            createdAt = System.currentTimeMillis(),
            lastOpenedAt = System.currentTimeMillis(),
            lastApkPath = null
        )

        projectDao.insertProject(ProjectEntity.fromDomain(duplicated))
        duplicated
    }

    suspend fun importProjectFromZip(zipFile: File, projectName: String): Project = withContext(Dispatchers.IO) {
        val projectId = UUID.randomUUID().toString()
        val projectBaseDir = getProjectDir(projectId)
        val sourceDir = File(projectBaseDir, "source")
        sourceDir.mkdirs()
        File(projectBaseDir, "workspace").mkdirs()
        File(projectBaseDir, "build").mkdirs()
        File(projectBaseDir, "logs").mkdirs()
        File(projectBaseDir, "output").mkdirs()

        unzip(zipFile, sourceDir)

        // Detect project info from AndroidManifest.xml or build.gradle
        val detected = detectProjectMetadata(sourceDir, projectName)

        val project = Project(
            id = projectId,
            name = detected.name,
            packageName = detected.packageName,
            versionName = detected.versionName,
            versionCode = detected.versionCode,
            minSdk = detected.minSdk,
            targetSdk = detected.targetSdk,
            mainLanguage = detected.mainLanguage,
            rootDirPath = sourceDir.absolutePath,
            selectedAbis = setOf(AbiType.ARM64_V8A, AbiType.ARMEABI_V7A),
            lastBuildType = BuildMode.DEBUG
        )

        projectDao.insertProject(ProjectEntity.fromDomain(project))
        project
    }

    suspend fun importSingleSourceFile(
        sourceFileName: String,
        sourceContent: String,
        projectName: String
    ): Project = withContext(Dispatchers.IO) {
        val isXml = sourceFileName.endsWith(".xml", ignoreCase = true)
        val isGradle = sourceFileName.endsWith(".gradle") || sourceFileName.endsWith(".gradle.kts")

        if (isXml || isGradle) {
            // Create default project and place XML/Gradle file into appropriate directory
            val project = createProject(
                name = projectName,
                packageName = "com.atp.imported",
                template = ProjectTemplate.EMPTY_ACTIVITY_KOTLIN
            )
            val targetFile = if (isXml) {
                if (sourceFileName.equals("AndroidManifest.xml", ignoreCase = true)) {
                    File(project.rootDirPath, "app/src/main/AndroidManifest.xml")
                } else if (sourceFileName.contains("layout") || sourceFileName.startsWith("activity_") || sourceFileName.startsWith("fragment_")) {
                    File(project.rootDirPath, "app/src/main/res/layout/$sourceFileName")
                } else {
                    File(project.rootDirPath, "app/src/main/res/values/$sourceFileName")
                }
            } else {
                File(project.rootDirPath, "app/$sourceFileName")
            }
            targetFile.parentFile?.mkdirs()
            targetFile.writeText(sourceContent)
            return@withContext project
        }

        // Extract package name from file content
        val packageRegex = Regex("""package\s+([a-zA-Z0-9_.]+)""")
        val match = packageRegex.find(sourceContent)
        val detectedPackage = match?.groupValues?.get(1) ?: "com.atp.imported"
        val isKotlin = sourceFileName.endsWith(".kt", ignoreCase = true)
        val className = sourceFileName.substringBeforeLast('.')

        val template = if (isKotlin) ProjectTemplate.EMPTY_ACTIVITY_KOTLIN else ProjectTemplate.BASIC_ACTIVITY_JAVA
        val project = createProject(
            name = projectName,
            packageName = detectedPackage,
            template = template
        )

        // Write the custom source file into the package structure
        val packageDir = detectedPackage.replace('.', '/')
        val targetFile = File(project.rootDirPath, "app/src/main/java/$packageDir/$sourceFileName")
        targetFile.parentFile?.mkdirs()
        targetFile.writeText(sourceContent)

        // If the imported file has a custom activity class name, update AndroidManifest.xml
        if (className != "MainActivity" && (sourceContent.contains("Activity") || sourceContent.contains("AppCompatActivity"))) {
            val manifestFile = File(project.rootDirPath, "app/src/main/AndroidManifest.xml")
            if (manifestFile.exists()) {
                val manifestText = manifestFile.readText()
                val updatedManifest = manifestText.replace(".MainActivity", ".$className")
                manifestFile.writeText(updatedManifest)
            }
        }

        project
    }

    suspend fun exportProjectZip(project: Project, destinationZipFile: File): Boolean = withContext(Dispatchers.IO) {
        val sourceDir = File(project.rootDirPath)
        if (!sourceDir.exists()) return@withContext false
        zipDirectory(sourceDir, destinationZipFile)
        true
    }

    private fun populateTemplateFiles(
        sourceDir: File,
        appName: String,
        packageName: String,
        template: ProjectTemplate,
        minSdk: Int,
        targetSdk: Int,
        versionName: String,
        versionCode: Int
    ) {
        val packagePath = packageName.replace('.', '/')
        val appDir = File(sourceDir, "app")
        val mainDir = File(appDir, "src/main")
        val javaDir = File(mainDir, "java/$packagePath")
        val resDir = File(mainDir, "res")
        val valuesDir = File(resDir, "values")
        val layoutDir = File(resDir, "layout")

        javaDir.mkdirs()
        valuesDir.mkdirs()
        layoutDir.mkdirs()

        // settings.gradle.kts
        File(sourceDir, "settings.gradle.kts").writeText(
            """
            rootProject.name = "$appName"
            include(":app")
            """.trimIndent()
        )

        // root build.gradle.kts
        File(sourceDir, "build.gradle.kts").writeText(
            """
            // ATP Android Builder Project
            plugins {
                id("com.android.application") version "8.7.0" apply false
                id("org.jetbrains.kotlin.android") version "2.0.0" apply false
            }
            """.trimIndent()
        )

        // app/build.gradle.kts
        val isKotlin = template != ProjectTemplate.BASIC_ACTIVITY_JAVA
        File(appDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("com.android.application")
                ${if (isKotlin) "id(\"org.jetbrains.kotlin.android\")" else ""}
            }

            android {
                namespace = "$packageName"
                compileSdk = $targetSdk

                defaultConfig {
                    applicationId = "$packageName"
                    minSdk = $minSdk
                    targetSdk = $targetSdk
                    versionCode = $versionCode
                    versionName = "$versionName"

                    ndk {
                        abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a"))
                    }
                }

                buildTypes {
                    release {
                        isMinifyEnabled = false
                    }
                    debug {
                        isDebuggable = true
                    }
                }
            }

            dependencies {
                implementation("androidx.core:core-ktx:1.13.1")
                implementation("androidx.appcompat:appcompat:1.7.0")
                implementation("com.google.android.material:material:1.12.0")
            }
            """.trimIndent()
        )

        // AndroidManifest.xml
        File(mainDir, "AndroidManifest.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="$packageName">

                <application
                    android:allowBackup="true"
                    android:icon="@mipmap/ic_launcher"
                    android:label="@string/app_name"
                    android:roundIcon="@mipmap/ic_launcher"
                    android:supportsRtl="true"
                    android:theme="@style/Theme.ATPApp">
                    <activity
                        android:name=".MainActivity"
                        android:exported="true">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                </application>

            </manifest>
            """.trimIndent()
        )

        // res/values/strings.xml
        File(valuesDir, "strings.xml").writeText(
            """
            <resources>
                <string name="app_name">$appName</string>
                <string name="welcome_message">Welcome to $appName!</string>
                <string name="build_note">Built directly on device with ATP Android Builder Online</string>
            </resources>
            """.trimIndent()
        )

        // res/values/colors.xml
        File(valuesDir, "colors.xml").writeText(
            """
            <resources>
                <color name="primary_blue">#1976D2</color>
                <color name="deep_blue">#0D47A1</color>
                <color name="silver">#C0C0C0</color>
                <color name="light_silver">#ECEFF1</color>
                <color name="dark_silver">#37474F</color>
            </resources>
            """.trimIndent()
        )

        // res/values/themes.xml
        File(valuesDir, "themes.xml").writeText(
            """
            <resources>
                <style name="Theme.ATPApp" parent="android:Theme.Material.Light.NoActionBar">
                    <item name="android:colorPrimary">@color/primary_blue</item>
                    <item name="android:colorPrimaryDark">@color/deep_blue</item>
                </style>
            </resources>
            """.trimIndent()
        )

        // res/layout/activity_main.xml
        File(layoutDir, "activity_main.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:gravity="center"
                android:orientation="vertical"
                android:padding="24dp"
                android:background="#ECEFF1">

                <TextView
                    android:id="@+id/tv_title"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/welcome_message"
                    android:textSize="22sp"
                    android:textStyle="bold"
                    android:textColor="#0D47A1" />

                <TextView
                    android:id="@+id/tv_subtitle"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="12dp"
                    android:text="@string/build_note"
                    android:textSize="14sp"
                    android:textColor="#37474F" />

                <Button
                    android:id="@+id/btn_action"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="24dp"
                    android:text="Run Action"
                    android:backgroundTint="#1976D2"
                    android:textColor="#FFFFFF" />

            </LinearLayout>
            """.trimIndent()
        )

        // Source file
        if (template == ProjectTemplate.BASIC_ACTIVITY_JAVA) {
            File(javaDir, "MainActivity.java").writeText(
                """
                package $packageName;

                import android.app.Activity;
                import android.os.Bundle;
                import android.widget.Button;
                import android.widget.TextView;
                import android.widget.Toast;

                public class MainActivity extends Activity {
                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        super.onCreate(savedInstanceState);
                        setContentView(R.layout.activity_main);

                        Button btn = findViewById(R.id.btn_action);
                        if (btn != null) {
                            btn.setOnClickListener(v -> {
                                Toast.makeText(this, "Hello from Java on ATP Builder!", Toast.LENGTH_SHORT).show();
                            });
                        }
                    }
                }
                """.trimIndent()
            )
        } else {
            File(javaDir, "MainActivity.kt").writeText(
                """
                package $packageName

                import android.app.Activity
                import android.os.Bundle
                import android.widget.Button
                import android.widget.Toast

                class MainActivity : Activity() {
                    override fun onCreate(savedInstanceState: Bundle?) {
                        super.onCreate(savedInstanceState)
                        setContentView(R.layout.activity_main)

                        val button = findViewById<Button>(R.id.btn_action)
                        button?.setOnClickListener {
                            Toast.makeText(this, "Compiled directly on device by ATP Builder!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                """.trimIndent()
            )
        }
    }

    private fun detectProjectMetadata(sourceDir: File, defaultName: String): Project {
        var name = defaultName
        var packageName = "com.atp.imported"
        var versionName = "1.0"
        var versionCode = 1
        var minSdk = 24
        var targetSdk = 35
        var mainLanguage = "Kotlin"

        // Search for AndroidManifest.xml
        val manifestFile = findFile(sourceDir, "AndroidManifest.xml")
        if (manifestFile != null && manifestFile.exists()) {
            val content = manifestFile.readText()
            val pkgMatch = Regex("""package\s*=\s*"([^"]+)"""").find(content)
            if (pkgMatch != null) {
                packageName = pkgMatch.groupValues[1]
            }
        }

        // Search for build.gradle or build.gradle.kts
        val buildGradle = findFile(sourceDir, "build.gradle.kts") ?: findFile(sourceDir, "build.gradle")
        if (buildGradle != null && buildGradle.exists()) {
            val content = buildGradle.readText()
            val appMatch = Regex("""(?:applicationId|namespace)\s*(?:=)?\s*"([^"]+)"""").find(content)
            if (appMatch != null) {
                packageName = appMatch.groupValues[1]
            }
            val minSdkMatch = Regex("""minSdk\s*(?:=)?\s*(\d+)""").find(content)
            if (minSdkMatch != null) {
                minSdk = minSdkMatch.groupValues[1].toIntOrNull() ?: 24
            }
            val targetSdkMatch = Regex("""targetSdk\s*(?:=)?\s*(\d+)""").find(content)
            if (targetSdkMatch != null) {
                targetSdk = targetSdkMatch.groupValues[1].toIntOrNull() ?: 35
            }
            val vNameMatch = Regex("""versionName\s*(?:=)?\s*"([^"]+)"""").find(content)
            if (vNameMatch != null) {
                versionName = vNameMatch.groupValues[1]
            }
            val vCodeMatch = Regex("""versionCode\s*(?:=)?\s*(\d+)""").find(content)
            if (vCodeMatch != null) {
                versionCode = vCodeMatch.groupValues[1].toIntOrNull() ?: 1
            }
        }

        // Detect language
        val hasKt = hasFilesWithExtension(sourceDir, "kt")
        val hasJava = hasFilesWithExtension(sourceDir, "java")
        mainLanguage = when {
            hasKt -> "Kotlin"
            hasJava -> "Java"
            else -> "Kotlin"
        }

        return Project(
            id = "",
            name = name,
            packageName = packageName,
            versionName = versionName,
            versionCode = versionCode,
            minSdk = minSdk,
            targetSdk = targetSdk,
            mainLanguage = mainLanguage,
            rootDirPath = sourceDir.absolutePath
        )
    }

    private fun findFile(dir: File, fileName: String): File? {
        val files = dir.listFiles() ?: return null
        for (f in files) {
            if (f.name == fileName) return f
            if (f.isDirectory) {
                val found = findFile(f, fileName)
                if (found != null) return found
            }
        }
        return null
    }

    private fun hasFilesWithExtension(dir: File, ext: String): Boolean {
        val files = dir.listFiles() ?: return false
        for (f in files) {
            if (f.isFile && f.name.endsWith(".$ext", ignoreCase = true)) return true
            if (f.isDirectory && hasFilesWithExtension(f, ext)) return true
        }
        return false
    }

    private fun copyDirectory(source: File, destination: File) {
        if (source.isDirectory) {
            if (!destination.exists()) destination.mkdirs()
            source.list()?.forEach { child ->
                copyDirectory(File(source, child), File(destination, child))
            }
        } else {
            source.copyTo(destination, overwrite = true)
        }
    }

    private fun unzip(zipFile: File, targetDir: File) {
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val newFile = File(targetDir, entry.name)
                // Protect against Zip Slip vulnerability
                if (!newFile.canonicalPath.startsWith(targetDir.canonicalPath)) {
                    throw SecurityException("Zip entry is outside the target dir: ${entry.name}")
                }
                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    FileOutputStream(newFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun zipDirectory(dir: File, zipFile: File) {
        zipFile.parentFile?.mkdirs()
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
            zipFileEntry(dir, dir, zos)
        }
    }

    private fun zipFileEntry(root: File, source: File, zos: ZipOutputStream) {
        val files = source.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                zipFileEntry(root, file, zos)
            } else {
                val relativePath = root.toURI().relativize(file.toURI()).path
                val entry = ZipEntry(relativePath)
                zos.putNextEntry(entry)
                FileInputStream(file).use { fis ->
                    fis.copyTo(zos)
                }
                zos.closeEntry()
            }
        }
    }
}
