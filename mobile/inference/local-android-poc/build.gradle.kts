plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.supernutrition.inference.localandroidpoc"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }
}

dependencies {
    implementation(project(":domain:inference"))
    implementation(project(":domain:photo-analysis"))
    implementation(project(":inference:local-android"))
    implementation(files("libs/food-identification-runtime-0.1.0.aar"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.onnxruntime.android)
    testImplementation("junit:junit:4.13.2")
}
