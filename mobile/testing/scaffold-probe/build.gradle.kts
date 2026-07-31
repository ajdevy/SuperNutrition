plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    id("com.supernutrition.kmp-apple-targets")
}

val enableIntelX64 = extra["supernutrition.enableIntelX64"] as Boolean
val isIntelMac = extra["supernutrition.isIntelMac"] as Boolean
val enableIosDeviceTarget = extra["supernutrition.enableIosDeviceTarget"] as Boolean

kotlin {
    jvm()
    android {
        namespace = "com.supernutrition.testing.scaffoldprobe"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
    }
    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":testing:compose-support"))
            implementation(libs.koin.core)
            implementation(libs.decompose)
            implementation(libs.ktor.client.core)
            implementation(libs.ktorfit)
            implementation(libs.sqldelight.runtime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
