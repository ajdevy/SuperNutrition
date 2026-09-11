plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
    id("com.supernutrition.kmp-apple-targets")
}

dependencyLocking {
    // Skiko publishes one native runtime module per host OS/architecture. The
    // JVM test dependency below selects the current host, so these artifacts
    // cannot share one cross-platform lock entry. Their versions remain pinned
    // by the version catalog and their artifacts remain covered by Gradle
    // dependency verification.
    ignoredDependencies.add("org.jetbrains.skiko:skiko-awt-runtime-*")
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
        namespace = "com.supernutrition.feature.photoanalysis.ui"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":feature:photo-analysis-presenter"))
            implementation(project(":core:config"))
            implementation(project(":core:ui"))
            implementation(project(":domain:nutrition"))
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
                providers.systemProperty("os.name").orNull == "Mac OS X" &&
                    providers.systemProperty("os.arch").orNull == "x86_64" ->
                    libs.skiko.awt.runtime.macos.x64
                providers.systemProperty("os.name").orNull == "Mac OS X" ->
                    libs.skiko.awt.runtime.macos.arm64
                else -> libs.skiko.awt.runtime.linux.x64
            }
            implementation(skikoRuntime)
        }
    }
}
