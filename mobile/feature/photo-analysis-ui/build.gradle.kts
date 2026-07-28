plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

composeCompiler {
    stabilityConfigurationFiles.add(
        layout.projectDirectory.file("../../config/compose-stability.conf"),
    )
}

kotlin {
    jvm()

    val isIntelMac =
        System.getProperty("os.name") == "Mac OS X" &&
            System.getProperty("os.arch") == "x86_64"
    val enableIosDeviceTarget =
        !isIntelMac ||
            providers.gradleProperty("supernutrition.enableIosDeviceTarget").orNull == "true"

    if (enableIosDeviceTarget) {
        iosArm64()
        if (!isIntelMac) {
            iosSimulatorArm64()
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":feature:photo-analysis-presenter"))
            implementation(project(":core:config"))
            implementation(project(":core:ui"))
            implementation(project(":domain:photo-analysis"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            // Compose Multiplatform 1.11.1 exposes the experimental v2 test
            // entry point from the common UI-test artifact. Keep the screen
            // contract authored once in commonTest; platform hosts execute
            // the same semantics on their supported targets.
            implementation(libs.compose.ui.test)
        }
        jvmTest.dependencies {
            // Compose UI Test v2 uses Skiko for the JVM host. The native
            // runtime is test-only and selected for the executing CI host.
            val skikoRuntime = when {
                System.getProperty("os.name") == "Mac OS X" &&
                    System.getProperty("os.arch") == "x86_64" ->
                    libs.skiko.awt.runtime.macos.x64
                System.getProperty("os.name") == "Mac OS X" ->
                    libs.skiko.awt.runtime.macos.arm64
                else -> libs.skiko.awt.runtime.linux.x64
            }
            implementation(skikoRuntime)
        }
    }
}
