package com.example.model

enum class ProjectTemplate(val title: String) {
    EMPTY_ACTIVITY_KOTLIN("Empty Activity (Kotlin)"),
    BASIC_ACTIVITY_JAVA("Basic Activity (Java)"),
    COMPOSE_STARTER("Compose Starter"),
    MINIMAL_SINGLE_FILE("Minimal Single File")
}

data class Project(
    val id: String,
    val name: String,
    val packageName: String,
    val versionName: String = "1.0",
    val versionCode: Int = 1,
    val minSdk: Int = 24,
    val targetSdk: Int = 35,
    val mainLanguage: String = "Kotlin",
    val rootDirPath: String,
    val isFavorite: Boolean = false,
    val isPinned: Boolean = false,
    val lastOpenedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val gitRepoUrl: String? = null,
    val selectedAbis: Set<AbiType> = setOf(AbiType.ARM64_V8A, AbiType.ARMEABI_V7A),
    val lastBuildType: BuildMode = BuildMode.DEBUG,
    val lastApkPath: String? = null
)
