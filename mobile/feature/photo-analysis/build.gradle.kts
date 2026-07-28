plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
}

composeCompiler {
    stabilityConfigurationFiles.add(
        layout.projectDirectory.file("../../config/compose-stability.conf"),
    )
}

kotlin {
    jvm()
    android {
        namespace = "com.supernutrition.feature.photoanalysis"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":feature:photo-analysis-presenter"))
            implementation(project(":feature:photo-analysis-ui"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":core:ui"))
            implementation(project(":feature:photo-analysis-ui"))
            implementation(project(":feature:photo-analysis-presenter"))
            implementation(project(":testing:compose-support"))
        }
        jvmMain.dependencies {
            implementation(compose.runtime)
        }
        androidMain.dependencies {
            implementation(project(":core:config"))
            implementation(project(":domain:photo-analysis"))
            implementation(project(":feature:photo-analysis-ui"))
            implementation(libs.androidx.activity)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(project(":platform:photo-android"))
        }
    }
}
