plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

val hostIsIntelMac =
    System.getProperty("os.name") == "Mac OS X" &&
        System.getProperty("os.arch") == "x86_64"

kotlin {
    jvm()
    val isIntelMac =
        System.getProperty("os.name") == "Mac OS X" &&
            System.getProperty("os.arch") == "x86_64"
    val enableFrameworkExport = providers.gradleProperty("supernutrition.enableSharedKitFramework").orNull == "true"
    val enableIosDeviceTarget =
        System.getProperty("os.arch") != "x86_64" ||
            providers.gradleProperty("supernutrition.enableIosDeviceTarget").orNull == "true"
    val enableAppleTargets = enableIosDeviceTarget || !isIntelMac
    if (enableAppleTargets) {
        if (enableIosDeviceTarget) {
            iosArm64 {
                if (enableFrameworkExport) {
                    binaries.framework {
                        baseName = "SharedKit"
                        isStatic = true
                        export(project(":platform:photo-ios"))
                    }
                }
            }
        }
        if (!isIntelMac) {
            iosSimulatorArm64 {
                if (enableFrameworkExport) {
                    binaries.framework {
                        baseName = "SharedKit"
                        isStatic = true
                        export(project(":platform:photo-ios"))
                    }
                }
            }
        }
    }
    val enableIntelX64 =
        isIntelMac &&
            providers.gradleProperty("supernutrition.enableIntelIosX64").orNull == "true"
    if (enableIntelX64) {
        // Explicit local compatibility only. No ARM64 target is configured on this Intel lane.
        iosX64 {
            if (enableFrameworkExport) {
                binaries.framework {
                    baseName = "SharedKit"
                    isStatic = true
                    export(project(":platform:photo-ios"))
                }
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.koin.core)
            implementation(project(":core:ui"))
            implementation(project(":feature:photo-analysis-presenter"))
            if (!hostIsIntelMac || enableIosDeviceTarget) {
                implementation(project(":feature:photo-analysis-ui"))
            }
        }
        if (enableAppleTargets) {
            if (enableIosDeviceTarget) {
                iosArm64Main.dependencies {
                    api(project(":platform:photo-ios"))
                }
            }
            if (!isIntelMac) {
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
