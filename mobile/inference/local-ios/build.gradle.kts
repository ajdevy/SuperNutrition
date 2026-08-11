plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("com.supernutrition.kmp-apple-targets")
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            api(project(":inference:local-shared"))
            implementation(project(":domain:inference"))
            implementation(project(":domain:photo-analysis"))
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
