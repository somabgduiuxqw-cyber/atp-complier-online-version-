package com.example.model

enum class StageStatus(val symbol: String) {
    NOT_STARTED("○"),
    RUNNING("●"),
    COMPLETED("✓"),
    FAILED("✕")
}

enum class BuildStage(val stageNumber: Int, val title: String) {
    PREPARING_PROJECT(1, "Preparing project"),
    VALIDATING_PROJECT(2, "Validating project"),
    VALIDATING_COMPILER(3, "Validating compiler"),
    CONFIGURING_BUILD(4, "Configuring build"),
    COMPILING_KOTLIN_JAVA(5, "Compiling Kotlin/Java"),
    COMPILING_RESOURCES(6, "Compiling Android resources"),
    GENERATING_DEX(7, "Generating DEX"),
    PACKAGING_APK(8, "Packaging APK"),
    SIGNING_APK(9, "Signing APK"),
    VERIFYING_APK(10, "Verifying APK");

    companion object {
        fun byNumber(num: Int): BuildStage? = entries.find { it.stageNumber == num }
    }
}

data class StageState(
    val stage: BuildStage,
    val status: StageStatus = StageStatus.NOT_STARTED,
    val currentTask: String = "",
    val details: String = "",
    val startedAtMs: Long = 0L,
    val durationMs: Long = 0L,
    val errorMessage: String? = null
)

enum class BuildMode {
    DEBUG,
    RELEASE
}

data class BuildConfiguration(
    val projectId: String,
    val buildMode: BuildMode = BuildMode.DEBUG,
    val applicationId: String = "com.example.app",
    val versionName: String = "1.0",
    val versionCode: Int = 1,
    val minSdk: Int = 24,
    val targetSdk: Int = 35,
    val targetAbis: Set<AbiType> = setOf(AbiType.ARM64_V8A, AbiType.ARMEABI_V7A),
    val optimizeR8: Boolean = false
)
