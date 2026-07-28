plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    val enableIntelX64 =
        System.getProperty("os.name") == "Mac OS X" &&
            System.getProperty("os.arch") == "x86_64" &&
            providers.gradleProperty("supernutrition.enableIntelIosX64").orNull == "true"
    val isIntelMac = System.getProperty("os.arch") == "x86_64"
    val enableIosDeviceTarget =
        !isIntelMac ||
            providers.gradleProperty("supernutrition.enableIosDeviceTarget").orNull == "true"

    jvm()
    android {
        namespace = "com.supernutrition.testing.scaffoldprobe"
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
        commonMain.dependencies {
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":testing:compose-support"))
            implementation(libs.koin.core)
            implementation(libs.decompose)
            implementation(libs.ktor.client.core)
            implementation(libs.ktorfit)
            implementation(libs.sqldelight.runtime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
