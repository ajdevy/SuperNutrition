plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("com.supernutrition.kmp-apple-targets")
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:config"))
            implementation(project(":domain:inference"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
