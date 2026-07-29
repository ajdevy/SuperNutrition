package com.supernutrition.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/** Central target policy for every KMP module on Apple and Intel hosts. */
class KmpAppleTargetsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val isIntelMac = project.providers.systemProperty("os.name").orNull == "Mac OS X" &&
            project.providers.systemProperty("os.arch").orNull == "x86_64"
        val enableIosDeviceTarget = !isIntelMac ||
            project.providers.gradleProperty("supernutrition.enableIosDeviceTarget").orNull == "true"
        val enableIntelX64 = isIntelMac &&
            project.providers.gradleProperty("supernutrition.enableTestingAppleTargets").orNull == "true" &&
            project.providers.gradleProperty("supernutrition.enableIntelIosX64").orNull == "true"

        project.extensions.extraProperties["supernutrition.enableIosDeviceTarget"] = enableIosDeviceTarget
        project.extensions.extraProperties["supernutrition.enableIntelX64"] = enableIntelX64
        project.extensions.extraProperties["supernutrition.isIntelMac"] = isIntelMac

        project.extensions.configure<KotlinMultiplatformExtension> {
            if (enableIosDeviceTarget) {
                iosArm64()
                if (!isIntelMac) {
                    iosSimulatorArm64()
                }
            }
            if (enableIntelX64) {
                iosX64()
            }
        }
    }
}
