plugins {
    alias(libs.plugins.android.library)
}

val attachLocalFoodModel = providers.gradleProperty("supernutrition.attachLocalFoodModel")
    .map(String::toBoolean)
    .getOrElse(false)

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
    if (attachLocalFoodModel) {
        implementation("com.supernutrition.inference:food-identification-runtime:0.1.1")
    }
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.onnxruntime.android)
    testImplementation("junit:junit:4.13.2")
}
