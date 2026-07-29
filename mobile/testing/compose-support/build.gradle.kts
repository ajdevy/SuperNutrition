plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    val enableIntelX64 =
        providers.systemProperty("os.name").orNull == "Mac OS X" &&
            providers.systemProperty("os.arch").orNull == "x86_64" &&
            providers.gradleProperty("supernutrition.enableIntelIosX64").orNull == "true"
    val isIntelMac = providers.systemProperty("os.arch").orNull == "x86_64"
    val enableIosDeviceTarget =
        !isIntelMac ||
            providers.gradleProperty("supernutrition.enableIosDeviceTarget").orNull == "true"

    jvm()
    android {
        namespace = "com.supernutrition.testing.composesupport"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
    }
    if (enableIosDeviceTarget) {
        iosArm64()
    }
    if (!isIntelMac) {
        iosSimulatorArm64()
    }
    if (enableIntelX64) {
        // Explicit local compatibility only. Canonical Apple evidence remains ARM64.
        iosX64()
    }

    sourceSets {
        // Compose UI-test 1.11.1 has no iosX64 publication. Keep the pinned
        // artifact on canonical/host-supported source sets so the Intel-only
        // compatibility lane can still compile this dependency-free substrate.
        jvmMain.dependencies {
            implementation(libs.compose.ui.test)
        }
        androidMain.dependencies {
            implementation(libs.compose.ui.test)
        }
        if (enableIosDeviceTarget) {
            iosArm64Main.dependencies {
                implementation(libs.compose.ui.test)
            }
        }
        if (!isIntelMac) {
            iosSimulatorArm64Main.dependencies {
                implementation(libs.compose.ui.test)
            }
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
