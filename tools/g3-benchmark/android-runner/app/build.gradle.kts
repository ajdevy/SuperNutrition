plugins {
    id("com.android.application")
}

android {
    namespace = "com.supernutrition.g3benchmark"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.supernutrition.g3benchmark"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "g3-spike"
    }
}

dependencies {
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.28.0")
}
