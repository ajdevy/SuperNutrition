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
        System.getProperty("os.name") == "Mac OS X" &&
            System.getProperty("os.arch") == "x86_64" &&
            providers.gradleProperty("supernutrition.enableIntelIosX64").orNull == "true"

    if (enableIosDeviceTarget) {
        iosArm64 {
            compilations.getByName("main") {
                cinterops {
                    create("photoPermission") {
                        defFile(project.file("src/nativeInterop/cinterop/photo-permission.def"))
                        compilerOpts("-I${project.file("src/nativeInterop/cinterop").absolutePath}")
                    }
                }
            }
        }
        if (!isIntelMac) {
            iosSimulatorArm64 {
                compilations.getByName("main") {
                    cinterops {
                        create("photoPermission") {
                            defFile(project.file("src/nativeInterop/cinterop/photo-permission.def"))
                            compilerOpts("-I${project.file("src/nativeInterop/cinterop").absolutePath}")
                        }
                    }
                }
            }
        }
    }
    if (enableIntelX64) {
        iosX64 {
            compilations.getByName("main") {
                cinterops {
                    create("photoFixture") {
                        defFile(project.file("src/nativeInterop/cinterop/photo-fixture.def"))
                        compilerOpts("-I${project.file("src/nativeInterop/cinterop").absolutePath}")
                    }
                    create("photoPermission") {
                        defFile(project.file("src/nativeInterop/cinterop/photo-permission.def"))
                        compilerOpts("-I${project.file("src/nativeInterop/cinterop").absolutePath}")
                    }
                }
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:ui"))
            implementation(project(":domain:photo-analysis"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
