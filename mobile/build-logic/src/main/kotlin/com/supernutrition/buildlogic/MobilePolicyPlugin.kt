package com.supernutrition.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import java.nio.file.Files
import java.nio.file.Path

/**
 * Minimal policy convention for the scaffold root.
 *
 * Product modules receive their concrete convention plugins in their owning
 * module slices. This plugin only establishes the root policy tasks and
 * fail-closed metadata required before product source exists.
 */
class MobilePolicyPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val expectedRootName = project.rootProject.name
        val mobileRoot = project.rootProject.projectDir.toPath()

        project.group = "com.supernutrition.mobile"
        project.version = "0.0.0-scaffold"

        val lifecycle = mobileRoot.resolve("product-lifecycle.toml")
            .takeIf(Files::isRegularFile)
            ?.let { path ->
                Regex("(?m)^lifecycle\\s*=\\s*\\\"([^\\\"]+)\\\"\\s*$")
                    .find(Files.readString(path))
                    ?.groupValues
                    ?.get(1)
            }
            ?: "scaffold-pending"
        project.extensions.extraProperties["supernutrition.lifecycle"] = lifecycle
        project.extensions.extraProperties["supernutrition.packageNamespace"] =
            "com.supernutrition"

        project.tasks.register("verifyMobilePolicy") {
            group = "verification"
            description = "Validates the mobile scaffold policy without writing files."
            doLast {
                require(expectedRootName == "SuperNutrition-mobile") {
                    "Unexpected mobile root project name"
                }
                MobilePolicyValidator.validate(mobileRoot)
            }
        }
    }
}
