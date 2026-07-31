plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    id("com.supernutrition.kmp-apple-targets")
}

val enableIntelX64 = extra["supernutrition.enableIntelX64"] as Boolean
val isIntelMac = extra["supernutrition.isIntelMac"] as Boolean
val enableIosDeviceTarget = extra["supernutrition.enableIosDeviceTarget"] as Boolean

kotlin {
    jvm()
    android {
        namespace = "com.supernutrition.testing.composesupport"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
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
