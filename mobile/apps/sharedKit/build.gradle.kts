plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("com.supernutrition.kmp-apple-targets")
}

val hostIsIntelMac =
    providers.systemProperty("os.name").orNull == "Mac OS X" &&
        providers.systemProperty("os.arch").orNull == "x86_64"
val enableIosDeviceTarget = extra["supernutrition.enableIosDeviceTarget"] as Boolean
val enableIntelX64 = extra["supernutrition.enableIntelX64"] as Boolean
val enableAppleTargets = enableIosDeviceTarget || !hostIsIntelMac

kotlin {
    jvm()
    val enableFrameworkExport = providers.gradleProperty("supernutrition.enableSharedKitFramework").orNull == "true"
    if (enableFrameworkExport) {
        targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
            binaries.framework {
                baseName = "SharedKit"
                isStatic = true
                export(project(":platform:photo-ios"))
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.koin.core)
            implementation(project(":core:ui"))
            api(project(":feature:photo-analysis-presenter"))
            api(project(":feature:photo-analysis"))
        }
        if (enableAppleTargets) {
            if (enableIosDeviceTarget) {
                iosArm64Main.dependencies {
                    api(project(":platform:photo-ios"))
                }
            }
            if (!hostIsIntelMac) {
                iosSimulatorArm64Main.dependencies {
                    api(project(":platform:photo-ios"))
                }
            }
        }
        if (enableIntelX64) {
            iosX64Main.dependencies {
                api(project(":platform:photo-ios"))
            }
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
