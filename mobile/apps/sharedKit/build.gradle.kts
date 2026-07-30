plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("com.supernutrition.kmp-apple-targets")
}

val hostIsIntelMac = extra["supernutrition.isIntelMac"] as Boolean
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
        if (enableAppleTargets) {
            val iosMain = maybeCreate("iosMain")
            val iosMainSourceSet = getByName("iosMain").also { it.dependsOn(getByName("commonMain")) }
            if (enableIosDeviceTarget) {
                named("iosArm64Main") { dependsOn(iosMainSourceSet) }
                if (!hostIsIntelMac) {
                    named("iosSimulatorArm64Main") { dependsOn(iosMainSourceSet) }
                }
            }
            if (enableIntelX64) {
                named("iosX64Main") { dependsOn(iosMainSourceSet) }
            }
            iosMain.dependencies {
                implementation(project(":platform:photo-ios"))
            }
        }
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
