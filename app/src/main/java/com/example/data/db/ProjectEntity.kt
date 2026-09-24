package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.AbiType
import com.example.model.BuildMode
import com.example.model.Project

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Int,
    val minSdk: Int,
    val targetSdk: Int,
    val mainLanguage: String,
    val rootDirPath: String,
    val isFavorite: Boolean,
    val isPinned: Boolean,
    val lastOpenedAt: Long,
    val createdAt: Long,
    val gitRepoUrl: String?,
    val selectedAbis: String, // Comma-separated: "arm64-v8a,armeabi-v7a"
    val lastBuildType: String, // DEBUG or RELEASE
    val lastApkPath: String?
) {
    fun toDomain(): Project {
        val abis = selectedAbis.split(",")
            .mapNotNull { AbiType.fromDir(it.trim()) }
            .toSet()

        return Project(
            id = id,
            name = name,
            packageName = packageName,
            versionName = versionName,
            versionCode = versionCode,
            minSdk = minSdk,
            targetSdk = targetSdk,
            mainLanguage = mainLanguage,
            rootDirPath = rootDirPath,
            isFavorite = isFavorite,
            isPinned = isPinned,
            lastOpenedAt = lastOpenedAt,
            createdAt = createdAt,
            gitRepoUrl = gitRepoUrl,
            selectedAbis = if (abis.isEmpty()) setOf(AbiType.ARM64_V8A, AbiType.ARMEABI_V7A) else abis,
            lastBuildType = try { BuildMode.valueOf(lastBuildType) } catch (e: Exception) { BuildMode.DEBUG },
            lastApkPath = lastApkPath
        )
    }

    companion object {
        fun fromDomain(project: Project): ProjectEntity {
            return ProjectEntity(
                id = project.id,
                name = project.name,
                packageName = project.packageName,
                versionName = project.versionName,
                versionCode = project.versionCode,
                minSdk = project.minSdk,
                targetSdk = project.targetSdk,
                mainLanguage = project.mainLanguage,
                rootDirPath = project.rootDirPath,
                isFavorite = project.isFavorite,
                isPinned = project.isPinned,
                lastOpenedAt = project.lastOpenedAt,
                createdAt = project.createdAt,
                gitRepoUrl = project.gitRepoUrl,
                selectedAbis = project.selectedAbis.joinToString(",") { it.dirName },
                lastBuildType = project.lastBuildType.name,
                lastApkPath = project.lastApkPath
            )
        }
    }
}
