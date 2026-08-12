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

    sourceSets {
        getByName("main") {
            java.directories.add("../../../../mobile/inference/local-android-poc/src/main/java")
        }
    }
}

dependencies {
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.28.0")
    testImplementation("junit:junit:4.13.2")
}
