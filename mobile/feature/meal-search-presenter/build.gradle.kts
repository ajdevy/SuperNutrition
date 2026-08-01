plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("com.supernutrition.kmp-apple-targets")
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":domain:nutrition"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
