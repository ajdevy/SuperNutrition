plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.supernutrition.android"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.supernutrition.android"
        // The shared local ONNX model runtime requires API 24.
        minSdk = 24
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.0.0-scaffold"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("production") {
            dimension = "distribution"
        }
        create("uiAcceptance") {
            dimension = "distribution"
            applicationIdSuffix = ".uiAcceptance"
        }
        create("poc") {
            dimension = "distribution"
            applicationIdSuffix = ".poc"
            minSdk = 24
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
        }
    }

}

dependencies {
    implementation(libs.koin.core)
    implementation(project(":core:ui"))
    implementation(project(":feature:photo-analysis"))
    implementation(project(":inference:local-android"))
    implementation(project(":platform:photo-android"))
    // The current app uses the same local model runtime as the former PoC screen.
    implementation(project(":inference:local-android-poc"))
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(project(":domain:photo-analysis"))
    androidTestImplementation(project(":platform:photo-android"))
    androidTestImplementation(project(":testing:scaffold-probe"))
}
