import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
    repositories {
        gradlePluginPortal()
        google {
            content {
                // These pinned SQLDelight families are published on Maven Central.
                excludeGroupByRegex("app\\.cash\\..*")
                excludeGroup("co.touchlab")
                excludeGroup("org.xerial")
                excludeGroup("org.slf4j")
            }
        }
        mavenCentral()
    }

    includeBuild("build-logic")
}

val attachLocalFoodModel = providers.gradleProperty("supernutrition.attachLocalFoodModel")
    .map(String::toBoolean)
    .getOrElse(false)

if (attachLocalFoodModel) {
    val privateFoodModelSource = providers.gradleProperty("supernutrition.foodModelSource").orNull
        ?: error(
            "Set -Psupernutrition.foodModelSource to the local " +
                "supernutrition-food-identification-android checkout when attaching the private model.",
        )
    val privateFoodModelDirectory = file(privateFoodModelSource)
    require(privateFoodModelDirectory.resolve("settings.gradle.kts").isFile) {
        "The private food-identification source checkout was not found at $privateFoodModelDirectory"
    }
    includeBuild(privateFoodModelDirectory) {
        dependencySubstitution {
            substitute(module("com.supernutrition.inference:food-identification-runtime"))
                .using(project(":"))
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                excludeGroupByRegex("app\\.cash\\..*")
                excludeGroup("co.touchlab")
                excludeGroup("org.xerial")
                excludeGroup("org.slf4j")
            }
        }
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
    ":inference:local-android-poc",
    ":inference:local-ios",
    ":inference:local-shared",
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
            ":domain:nutrition",
            ":data:nutrition",
            ":domain:photo-analysis",
            ":feature:photo-analysis",
            ":feature:photo-analysis-presenter",
            ":feature:photo-analysis-ui",
            ":platform:photo-android",
            ":platform:photo-ios",
            ":testing:compose-support",
        )
        "androidApp" -> path in setOf(
            ":apps:androidApp",
            ":inference:local-android-poc",
        )
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
