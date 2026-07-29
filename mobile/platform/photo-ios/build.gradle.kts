plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    val isIntelMac =
        providers.systemProperty("os.name").orNull == "Mac OS X" &&
            providers.systemProperty("os.arch").orNull == "x86_64"
    val enableIosDeviceTarget =
        !isIntelMac ||
            providers.gradleProperty("supernutrition.enableIosDeviceTarget").orNull == "true"
    val enableIntelX64 =
        providers.systemProperty("os.name").orNull == "Mac OS X" &&
            providers.systemProperty("os.arch").orNull == "x86_64" &&
            providers.gradleProperty("supernutrition.enableIntelIosX64").orNull == "true"

    // Keep policy-only and non-Apple hosts configurable. This host-test target
    // is absent whenever an Apple target is enabled, so it is not part of the
    // Apple artifact or evidence denominator.
    if (!enableIosDeviceTarget && !enableIntelX64) {
        jvm()
    }

    if (enableIosDeviceTarget) {
        iosArm64 {
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
                    create("commonCrypto") {
                        defFile(project.file("src/nativeInterop/cinterop/common-crypto.def"))
                        compilerOpts("-I${project.file("src/nativeInterop/cinterop").absolutePath}", "-framework", "CommonCrypto")
                    }
                }
            }
        }
        if (!isIntelMac) {
            iosSimulatorArm64 {
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
                        create("commonCrypto") {
                            defFile(project.file("src/nativeInterop/cinterop/common-crypto.def"))
                            compilerOpts("-I${project.file("src/nativeInterop/cinterop").absolutePath}", "-framework", "CommonCrypto")
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
                    create("commonCrypto") {
                        defFile(project.file("src/nativeInterop/cinterop/common-crypto.def"))
                        compilerOpts("-I${project.file("src/nativeInterop/cinterop").absolutePath}", "-framework", "CommonCrypto")
                    }
                }
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:config"))
            implementation(project(":core:ui"))
            implementation(project(":domain:photo-analysis"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
