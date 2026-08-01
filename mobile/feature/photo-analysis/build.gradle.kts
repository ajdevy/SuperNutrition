val platformLockFile = when {
    System.getProperty("os.name").contains("Linux", ignoreCase = true) -> "gradle-linux.lockfile"
    else -> "gradle.lockfile"
}

dependencyLocking {
    lockFile = file(platformLockFile)
}

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
    id("com.supernutrition.kmp-apple-targets")
}

composeCompiler {
    stabilityConfigurationFiles.add(
        rootProject.layout.projectDirectory.file("config/compose-stability.conf"),
    )
}

val enableIosDeviceTarget = extra["supernutrition.enableIosDeviceTarget"] as Boolean
val enableIntelX64 = extra["supernutrition.enableIntelX64"] as Boolean

kotlin {
    jvm()
    android {
        namespace = "com.supernutrition.feature.photoanalysis"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
        androidResources {
            enable = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":feature:photo-analysis-presenter"))
            implementation(project(":feature:photo-analysis-ui"))
            implementation(project(":domain:photo-analysis"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":core:ui"))
            implementation(project(":feature:photo-analysis-ui"))
            implementation(project(":testing:compose-support"))
            implementation(project(":feature:photo-analysis-presenter"))
        }
        jvmMain.dependencies {
            implementation(compose.runtime)
        }
        if (enableIosDeviceTarget) {
            iosMain.dependencies {
                implementation(libs.compose.ui)
            }
        }
        if (enableIntelX64) {
            iosX64Main.dependencies {
                implementation(libs.compose.ui)
            }
        }
        androidMain.dependencies {
            implementation(project(":core:config"))
            implementation(project(":core:ui"))
            implementation(project(":domain:photo-analysis"))
            implementation(libs.androidx.activity)
            implementation(libs.androidx.activity.compose)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(project(":platform:photo-android"))
        }
    }
}
