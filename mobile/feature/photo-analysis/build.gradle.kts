plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
}

composeCompiler {
    stabilityConfigurationFiles.add(
        rootProject.layout.projectDirectory.file("config/compose-stability.conf"),
    )
}

kotlin {
    jvm()
    val isIntelMac =
        providers.systemProperty("os.name").orNull == "Mac OS X" &&
            providers.systemProperty("os.arch").orNull == "x86_64"
    val enableIosDeviceTarget =
        !isIntelMac ||
            providers.gradleProperty("supernutrition.enableIosDeviceTarget").orNull == "true"
    val enableIntelX64 =
        isIntelMac &&
            providers.gradleProperty("supernutrition.enableTestingAppleTargets").orNull == "true" &&
            providers.gradleProperty("supernutrition.enableIntelIosX64").orNull == "true"
    if (enableIosDeviceTarget) {
        iosArm64()
        if (!isIntelMac) {
            iosSimulatorArm64()
        }
    }
    if (enableIntelX64) {
        iosX64()
    }
    android {
        namespace = "com.supernutrition.feature.photoanalysis"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
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
            implementation(project(":feature:photo-analysis-presenter"))
            implementation(project(":testing:compose-support"))
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
