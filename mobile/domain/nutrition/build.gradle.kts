plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm()

    val isIntelMac =
        System.getProperty("os.name") == "Mac OS X" &&
            System.getProperty("os.arch") == "x86_64"
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

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:config"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
