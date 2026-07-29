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

        val repoRoot = repositoryRoot(mobileRoot)
        val verifierPaths = listOf(
            "scripts/verify-product-identity.sh",
            "scripts/verify-interaction-policy-contract.sh",
            "scripts/verify-mobile-api-contract.sh",
            "scripts/verify-inference-contract.sh",
            "scripts/verify-root-boundaries.sh",
            "scripts/verify-ci-policy.sh",
        ).map(repoRoot::resolve)
        project.tasks.register("verifyMobilePolicy") {
            group = "verification"
            description = "Validates the mobile scaffold policy without writing files."
            inputs.files(verifierPaths)
            inputs.files(
                mobileRoot.resolve("product-lifecycle.toml"),
                mobileRoot.resolve("config"),
                mobileRoot.resolve(".maestro"),
            )
            outputs.upToDateWhen { false }
            doLast {
                require(expectedRootName == "SuperNutrition-mobile") {
                    "Unexpected mobile root project name"
                }
                MobilePolicyValidator.validate(mobileRoot)
                ModuleBoundaryVerifier.validate(mobileRoot)
                verifierPaths.forEach { script ->
                    require(Files.isRegularFile(script)) { "Missing local policy verifier: $script" }
                    val result = ProcessBuilder("bash", script.toString())
                        .directory(repoRoot.toFile())
                        .inheritIO()
                        .start()
                        .waitFor()
                    require(result == 0) { "Local policy verifier failed: $script" }
                }
            }
        }
    }

    private fun repositoryRoot(mobileRoot: Path): Path = mobileRoot.parent
}
