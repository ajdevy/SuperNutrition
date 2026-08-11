import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }

    includeBuild("build-logic")
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }

}

rootProject.name = "SuperNutrition-mobile"

// The build-policy slice owns the complete module registry. A later, separately
// owned slice creates each directory and its declarative build file; absent
// directories are intentionally not materialized by this policy-only change.
val scaffoldModules = listOf(
    ":apps:androidApp",
    ":apps:sharedKit",
    ":core:config",
    ":core:model",
    ":core:navigation",
    ":core:network",
    ":core:platform-api",
    ":core:ui",
    ":domain:inference",
    ":domain:nutrition",
    ":domain:photo-analysis",
    ":data:nutrition",
    ":inference:local-android",
    ":inference:local-ios",
    ":inference:remote",
    ":platform:photo-android",
    ":platform:photo-ios",
    ":feature:photo-analysis",
    ":feature:photo-analysis-presenter",
    ":feature:photo-analysis-ui",
    ":feature:meal-search-presenter",
    ":feature:meal-search-ui",
    ":testing:compose-support",
    ":testing:inference",
    ":testing:scaffold-probe",
    ":evaluation:harness",
)

scaffoldModules.forEach { path ->
    val hostSlice = providers.gradleProperty("supernutrition.hostSlice").orNull
    val isHostSliceProject = when (hostSlice) {
        "sharedKit" -> path in setOf(
            ":apps:sharedKit",
            ":core:config",
            ":core:ui",
            ":domain:inference",
            ":domain:photo-analysis",
            ":feature:photo-analysis",
            ":feature:photo-analysis-presenter",
            ":feature:photo-analysis-ui",
            ":platform:photo-android",
            ":platform:photo-ios",
            // :feature:photo-analysis declares this in androidMain, and a
            // project dependency resolves at configuration time whatever source
            // set it sits in — so leaving it out did not trim the slice, it made
            // the slice unconfigurable. The documented Apple command failed with
            // "Project with path ':inference:remote' could not be found" before
            // reaching a single Kotlin file, which is how the missing import in
            // SharedKitPhotoAnalysisSession went unnoticed: nothing in CI or
            // locally ever compiled that source set.
            ":inference:remote",
            ":testing:compose-support",
        )
        "androidApp" -> path == ":apps:androidApp"
        "photoIos" -> path in setOf(
            ":core:config",
            ":core:ui",
            ":domain:inference",
            ":domain:photo-analysis",
            ":platform:photo-ios",
        )
        "photoUi" -> path in setOf(
            ":core:config",
            ":core:ui",
            ":domain:inference",
            ":domain:photo-analysis",
            ":feature:photo-analysis-presenter",
            ":feature:photo-analysis-ui",
        )
        null -> true
        else -> error("Unsupported supernutrition.hostSlice: $hostSlice")
    }
    if (!isHostSliceProject) return@forEach
    val directory = file(path.removePrefix(":").replace(':', '/'))
    if (directory.isDirectory) {
        include(path)
    }
}
