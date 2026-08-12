plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    id("com.supernutrition.kmp-apple-targets")
}

kotlin {
    jvm()

    android {
        namespace = "com.supernutrition.inference.localshared"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":domain:inference"))
            api(project(":domain:photo-analysis"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.core)
        }
        getByName("commonTest").kotlin.srcDir("../local-android/src/commonTest/kotlin")
    }
}
