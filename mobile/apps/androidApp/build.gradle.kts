plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.supernutrition.android"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "app.supernutrition"
        minSdk = libs.versions.minSdk.get().toInt()
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
            minSdk = libs.versions.minSdk.get().toInt()
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
    implementation(project(":data:nutrition"))
    implementation(libs.sqldelight.android.driver)
    implementation(project(":feature:photo-analysis"))
    implementation(project(":inference:local-android"))
    implementation(project(":platform:photo-android"))
    // The current app uses the same local model runtime as the former PoC screen.
    implementation(project(":inference:local-android-poc"))
    androidTestImplementation(libs.androidx.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(project(":domain:photo-analysis"))
    androidTestImplementation(project(":platform:photo-android"))
    androidTestImplementation(project(":testing:scaffold-probe"))
}
