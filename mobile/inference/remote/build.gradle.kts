plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    jvm()

    android {
        namespace = "com.supernutrition.inference.remote"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
    }

    // HttpGatewayTransport is pure java.net/java.io — no Android API. Sharing it
    // across jvm+android means the class that ships in the APK is the very class
    // the JVM integration test exercises against the running gateway, instead of
    // a JVM lookalike that could drift from it.
    applyDefaultHierarchyTemplate()

    sourceSets {
        val jvmAndroidMain by creating { dependsOn(commonMain.get()) }
        jvmMain.get().dependsOn(jvmAndroidMain)
        androidMain.get().dependsOn(jvmAndroidMain)

        commonMain.dependencies {
            implementation(project(":domain:inference"))
            implementation(libs.kotlinx.serialization.json)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
