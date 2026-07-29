plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("com.supernutrition.kmp-apple-targets")
}

val isIntelMac = extra["supernutrition.isIntelMac"] as Boolean
val enableIosDeviceTarget = extra["supernutrition.enableIosDeviceTarget"] as Boolean
val enableIntelX64 = extra["supernutrition.enableIntelX64"] as Boolean

kotlin {
    // Keep policy-only and non-Apple hosts configurable. This host-test target
    // is absent whenever an Apple target is enabled, so it is not part of the
    // Apple artifact or evidence denominator.
    if (!enableIosDeviceTarget && !enableIntelX64) {
        jvm()
    }

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
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
                    compilerOpts(
                        "-I${project.file("src/nativeInterop/cinterop").absolutePath}",
                        "-framework",
                        "CommonCrypto",
                    )
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
