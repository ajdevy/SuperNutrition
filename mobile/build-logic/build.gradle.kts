plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

dependencyLocking {
    lockAllConfigurations()
}

group = "com.supernutrition.buildlogic"
version = "0.0.0-scaffold"

gradlePlugin {
    plugins {
        register("mobilePolicy") {
            id = "com.supernutrition.mobile-policy"
            implementationClass = "com.supernutrition.buildlogic.MobilePolicyPlugin"
        }
        register("quality") {
            id = "com.supernutrition.quality"
            implementationClass = "com.supernutrition.buildlogic.QualityPlugin"
        }
    }
}

dependencies {
    implementation(libs.detekt.gradle)
    implementation(libs.detekt.ktlint.wrapper)
    implementation(libs.kover.gradle)
    testImplementation(kotlin("test"))
    testImplementation(gradleTestKit())
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
