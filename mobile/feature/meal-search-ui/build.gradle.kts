plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
    id("com.supernutrition.kmp-apple-targets")
}

dependencyLocking {
    // Skiko publishes one native runtime module per host OS/architecture, so the
    // host-selected JVM test dependency below cannot share one cross-platform
    // lock entry. Versions stay pinned by the version catalog and the artifacts
    // stay covered by dependency verification. Same reasoning as
    // feature/photo-analysis-ui.
    ignoredDependencies.add("org.jetbrains.skiko:skiko-awt-runtime-*")
}

composeCompiler {
    stabilityConfigurationFiles.add(
        rootProject.layout.projectDirectory.file("config/compose-stability.conf"),
    )
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            api(project(":feature:meal-search-presenter"))
            implementation(project(":core:ui"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.compose.ui.test)
        }
        jvmTest.dependencies {
            // Compose UI Test v2 renders through Skiko on the JVM host. Test-only,
            // and selected for the executing host.
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
