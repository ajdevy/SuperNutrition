plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    id("com.supernutrition.kmp-apple-targets")
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
