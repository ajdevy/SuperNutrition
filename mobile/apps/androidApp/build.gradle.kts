plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.supernutrition.android"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.supernutrition.android"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.0.0-scaffold"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    implementation(project(":platform:photo-android"))
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(project(":domain:photo-analysis"))
    androidTestImplementation(project(":platform:photo-android"))
    androidTestImplementation(project(":testing:scaffold-probe"))
    // The consent path asserts EXIF/GPS are absent from the scrubbed bytes the
    // receipt digest covers, so the test source set needs to read EXIF itself.
    androidTestImplementation(libs.androidx.exifinterface)
}
