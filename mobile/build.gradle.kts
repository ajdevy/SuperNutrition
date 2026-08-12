// Load plugin implementations once in the mobile root. Subprojects only
// apply these pinned plugins, avoiding repeated Kotlin/AGP classloaders.
plugins {
    id("com.supernutrition.mobile-policy")
    id("com.supernutrition.quality")
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.compose) apply false
}

// Every project in the product build owns a committed lock state. Refreshing
// locks is an explicit dependency-policy operation; ordinary quality tasks
// only resolve against the checked-in state.
allprojects {
    dependencyLocking {
        lockAllConfigurations()
    }
}
