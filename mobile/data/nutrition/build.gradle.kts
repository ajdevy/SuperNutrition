plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.sqldelight)
    id("com.supernutrition.kmp-apple-targets")
}

kotlin {
    jvm()
    sourceSets {
        commonMain.dependencies {
            api(project(":domain:nutrition"))
            api(libs.sqldelight.runtime)
        }
        commonTest.dependencies { implementation(kotlin("test")) }
        jvmTest.dependencies { implementation(libs.sqldelight.sqlite.driver) }
    }
}

sqldelight {
    databases.create("UsdaCatalogDatabase") {
        packageName.set("com.supernutrition.data.nutrition.db")
        generateAsync.set(false)
    }
}
