package com.supernutrition.buildlogic

import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

class MobilePolicyValidatorTest {
    @Test
    fun `validator accepts the checked in scaffold policy`() {
        MobilePolicyValidator.validate(
            PathLocator.repoMobileRoot(),
        )
    }

    @Test
    fun `validator accepts local policy without optional GitHub hosting gates`() {
        val root = fixtureRoot("supernutrition-policy-local-hosting-test")
        copyPolicyFixtures(root)
        val lifecycle = Files.readString(root.resolve("product-lifecycle.toml"))

        assertFalse(lifecycle.contains("all_six_jobs_success"))
        assertFalse(lifecycle.contains("trusted_ruleset_audit"))
        MobilePolicyValidator.validate(root)
    }

    @Test
    fun `validator accepts active local policy with optional Apple evidence`() {
        val root = fixtureRoot("supernutrition-policy-optional-apple-test")
        copyPolicyFixtures(root)
        Files.walk(root).use { paths ->
            paths.filter { Files.isRegularFile(it) }.forEach { path ->
                val text = Files.readString(path)
                Files.writeString(
                    path,
                    text.replace("lifecycle = \"scaffold-pending\"", "lifecycle = \"active\"")
                        .replace("source_digest = \"pending\"", "source_digest = \"local-only\"")
                        .replace("transition = \"blocked\"", "transition = \"active\"")
                        .replace("active_inventory_and_denominators = false", "active_inventory_and_denominators = true"),
                )
            }
        }
        val manifestPaths = listOf(
            "config/architecture/module-boundaries.toml",
            "config/quality/module-matrix.toml",
            "config/quality/ui-evidence-profile.toml",
            "config/quality/positive-scenarios.toml",
            "config/quality/journeys.toml",
            "config/quality/platform-contracts.toml",
            "config/quality/native-gaps.toml",
            "config/quality/coverage.toml",
            "config/quality/suppression-registry.toml",
            "config/quality/interaction-policy.toml",
            "config/product-identity.toml",
            "config/compose-stability.conf",
            "product-lifecycle.toml",
        ).sorted()
        val digest = MessageDigest.getInstance("SHA-256").apply {
            manifestPaths.forEach { relative ->
                update(relative.toByteArray())
                update(0)
                update(Files.readAllBytes(root.resolve(relative)))
            }
        }.digest().joinToString("") { "%02x".format(it) }
        val evidence = root.resolve("config/quality/evidence-bindings.toml")
        Files.writeString(
            evidence,
            Files.readString(evidence).replace(Regex("manifest_digest = \"[^\"]+\""), "manifest_digest = \"$digest\""),
        )

        MobilePolicyValidator.validate(root)
    }

    @Test
    fun `validator rejects wildcard stability entries`() {
        val root = fixtureRoot("supernutrition-policy-test")
        copyPolicyFixtures(root)
        val stability = root.resolve("config/compose-stability.conf")
        Files.writeString(stability, "com.example.* = stable\n")

        assertFailsWith<IllegalArgumentException> {
            MobilePolicyValidator.validate(root)
        }
    }

    @Test
    fun `validator rejects stale manifest digest`() {
        val root = fixtureRoot("supernutrition-policy-digest-test")
        copyPolicyFixtures(root)
        val manifest = root.resolve("config/quality/module-matrix.toml")
        Files.writeString(manifest, Files.readString(manifest) + "\n# changed\n")

        assertFailsWith<IllegalArgumentException> {
            MobilePolicyValidator.validate(root)
        }
    }

    @Test
    fun `validator rejects duplicate units and orphaned platform gaps`() {
        val duplicateRoot = fixtureRoot("supernutrition-policy-duplicate-test")
        copyPolicyFixtures(duplicateRoot)
        val matrix = duplicateRoot.resolve("config/quality/module-matrix.toml")
        Files.writeString(
            matrix,
            Files.readString(matrix) +
                "\n[[unit]]\nid = \"mobile-build-policy\"\npath = \"mobile/build-logic\"\n",
        )
        assertFailsWith<IllegalArgumentException> {
            MobilePolicyValidator.validate(duplicateRoot)
        }

        val orphanRoot = fixtureRoot("supernutrition-policy-orphan-test")
        copyPolicyFixtures(orphanRoot)
        val contracts = orphanRoot.resolve("config/quality/platform-contracts.toml")
        Files.writeString(
            contracts,
            Files.readString(contracts).replace(
                "native_gap_id = \"photo-ios-camera-picker\"",
                "native_gap_id = \"missing-gap\"",
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            MobilePolicyValidator.validate(orphanRoot)
        }
    }

    @Test
    fun `validator rejects evidence from a different profile`() {
        val root = fixtureRoot("supernutrition-policy-profile-test")
        copyPolicyFixtures(root)
        val evidence = root.resolve("config/quality/evidence-bindings.toml")
        Files.writeString(
            evidence,
            Files.readString(evidence).replace(
                "profile = \"photo-analysis\"",
                "profile = \"bootstrap\"",
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            MobilePolicyValidator.validate(root)
        }
    }

    @Test
    fun `validator rejects a production unit without a denominator`() {
        val root = fixtureRoot("supernutrition-policy-coverage-test")
        copyPolicyFixtures(root)
        val coverage = root.resolve("config/quality/coverage.toml")
        Files.writeString(
            coverage,
            Files.readString(coverage).replace(
                "unit_id = \"feature-photo-analysis\"",
                "unit_id = \"missing-production-unit\"",
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            MobilePolicyValidator.validate(root)
        }
    }

    @Test
    fun `validator rejects a journey without locale evidence`() {
        val root = fixtureRoot("supernutrition-policy-locale-test")
        copyPolicyFixtures(root)
        val journeys = root.resolve("config/quality/journeys.toml")
        Files.writeString(
            journeys,
            Files.readString(journeys).replace("locale_ids = [\"en-US\"]", "locale_ids = []"),
        )

        assertFailsWith<IllegalArgumentException> {
            MobilePolicyValidator.validate(root)
        }
    }

    @Test
    fun `validator rejects a reduced forbidden-default inventory`() {
        val root = fixtureRoot("supernutrition-policy-forbidden-defaults-test")
        copyPolicyFixtures(root)
        val interaction = root.resolve("config/quality/interaction-policy.toml")
        Files.writeString(
            interaction,
            Files.readString(interaction).replace(
                "  \"allergenConclusion\",\n",
                "",
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            MobilePolicyValidator.validate(root)
        }
    }

    @Test
    fun `validator rejects a noncanonical Android label hidden in another string`() {
        val root = fixtureRoot("supernutrition-policy-android-identity-test")
        copyPolicyFixtures(root)
        val strings = root.resolve("apps/androidApp/src/main/res/values/strings.xml")
        Files.writeString(
            strings,
            Files.readString(strings)
                .replace(
                    "<string name=\"app_name\">SuperNutrition</string>",
                    "<string name=\"app_name\">AI nutritionist</string>",
                )
                .replace(
                    "</resources>",
                    "<string name=\"unrelated\">SuperNutrition</string></resources>",
                ),
        )

        assertFailsWith<IllegalArgumentException> {
            MobilePolicyValidator.validate(root)
        }
    }

    @Test
    fun `validator rejects drift in every visible identity field`() {
        val root = fixtureRoot("supernutrition-policy-identity-manifest-test")
        copyPolicyFixtures(root)
        val identity = root.resolve("config/product-identity.toml")
        Files.writeString(
            identity,
            Files.readString(identity).replace(
                "in_app_name = \"SuperNutrition\"",
                "in_app_name = \"SuperNutrition AI nutritionist\"",
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            MobilePolicyValidator.validate(root)
        }
    }

    @Test
    fun `validator rejects a noncanonical iOS display name hidden in another key`() {
        val root = fixtureRoot("supernutrition-policy-ios-identity-test")
        copyPolicyFixtures(root)
        val info = root.resolve("apps/iosApp/Info.plist")
        Files.writeString(
            info,
            Files.readString(info)
                .replace(
                    "<key>CFBundleDisplayName</key>\n\t<string>SuperNutrition</string>",
                    "<key>CFBundleDisplayName</key>\n\t<string>AI nutritionist</string>",
                )
                .replace(
                    "</dict>",
                    "\t<key>CFBundleName</key>\n\t<string>SuperNutrition</string>\n</dict>",
                ),
        )

        assertFailsWith<IllegalArgumentException> {
            MobilePolicyValidator.validate(root)
        }
    }

    @Test
    fun `validator rejects an oversized product Gradle heap`() {
        val root = fixtureRoot("supernutrition-policy-memory-test")
        copyPolicyFixtures(root)
        val properties = root.resolve("gradle.properties")
        Files.writeString(
            properties,
            Files.readString(properties).replace("-Xmx4g", "-Xmx32g"),
        )

        assertFailsWith<IllegalArgumentException> {
            MobilePolicyValidator.validate(root)
        }
    }

    @Test
    fun `validator rejects unbounded product worker fanout`() {
        val root = fixtureRoot("supernutrition-policy-workers-test")
        copyPolicyFixtures(root)
        val properties = root.resolve("gradle.properties")
        Files.writeString(
            properties,
            Files.readString(properties).replace("org.gradle.workers.max=4", "org.gradle.workers.max=12"),
        )

        assertFailsWith<IllegalArgumentException> {
            MobilePolicyValidator.validate(root)
        }
    }

    @Test
    fun `validator rejects silent Kotlin compiler fallback`() {
        val root = fixtureRoot("supernutrition-policy-kotlin-fallback-test")
        copyPolicyFixtures(root)
        val properties = root.resolve("gradle.properties")
        Files.writeString(
            properties,
            Files.readString(properties).replace(
                "kotlin.daemon.useFallbackStrategy=false",
                "kotlin.daemon.useFallbackStrategy=true",
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            MobilePolicyValidator.validate(root)
        }
    }

    @Test
    fun `validator rejects legacy Detekt coordinates`() {
        val root = fixtureRoot("supernutrition-policy-quality-tools-test")
        copyPolicyFixtures(root)
        val catalog = root.resolve("gradle/libs.versions.toml")
        Files.writeString(
            catalog,
            Files.readString(catalog).replace(
                "dev.detekt:detekt-gradle-plugin",
                "io.gitlab.arturbosch.detekt:detekt-gradle-plugin",
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            MobilePolicyValidator.validate(root)
        }
    }

    @Test
    fun `validator rejects a missing project dependency lock`() {
        val root = fixtureRoot("supernutrition-policy-lock-test")
        copyPolicyFixtures(root)
        Files.delete(root.resolve("domain/photo-analysis/gradle.lockfile"))

        assertFailsWith<IllegalArgumentException> {
            MobilePolicyValidator.validate(root)
        }
    }

    @Test
    fun `validator ignores generated build trees while checking project locks`() {
        val root = fixtureRoot("supernutrition-policy-generated-tree-test")
        copyPolicyFixtures(root)
        Files.createDirectories(root.resolve("build/transient/kotlin"))
        Files.writeString(root.resolve("build/build.gradle.kts"), "// generated output")

        MobilePolicyValidator.validate(root)
    }

    private fun copyPolicyFixtures(root: java.nio.file.Path) {
        val sourceRoot = PathLocator.repoMobileRoot()
        Files.createDirectories(root.resolve("feature/photo-analysis"))
        listOf(
            "config/architecture/module-boundaries.toml",
            "config/quality/module-matrix.toml",
            "config/quality/ui-evidence-profile.toml",
            "config/quality/positive-scenarios.toml",
            "config/quality/journeys.toml",
            "config/quality/platform-contracts.toml",
            "config/quality/native-gaps.toml",
            "config/quality/coverage.toml",
            "config/quality/suppression-registry.toml",
            "config/quality/interaction-policy.toml",
            "config/quality/evidence-bindings.toml",
            "config/product-identity.toml",
            "config/compose-stability.conf",
            "config/detekt/detekt.yml",
            "product-lifecycle.toml",
            "gradle/libs.versions.toml",
        ).forEach { relative ->
            val target = root.resolve(relative)
            Files.createDirectories(target.parent)
            Files.copy(sourceRoot.resolve(relative), target)
        }
        Files.createDirectories(root.resolve("build-logic"))
        Files.copy(
            sourceRoot.resolve("build-logic/gradle.properties"),
            root.resolve("build-logic/gradle.properties"),
        )
        Files.copy(
            sourceRoot.resolve("gradle.properties"),
            root.resolve("gradle.properties"),
        )
        Files.copy(
            sourceRoot.resolve("settings-gradle.lockfile"),
            root.resolve("settings-gradle.lockfile"),
        )
        listOf(
            "apps/androidApp/src/main/res/values/strings.xml",
            "apps/iosApp/Info.plist",
            ".maestro/photo-analysis/photo-analysis-capture-recovery.yaml",
        ).forEach { relative ->
            val target = root.resolve(relative)
            Files.createDirectories(target.parent)
            Files.copy(sourceRoot.resolve(relative), target)
        }
        listOf(
            "build-logic",
            "config",
            ".maestro/bootstrap",
            ".maestro/photo-analysis",
            "core/config",
            "core/ui",
            "domain/photo-analysis",
            "domain/nutrition",
            "platform/photo-android",
            "platform/photo-ios",
            "feature/photo-analysis-ui",
            "feature/photo-analysis-presenter",
            "feature/photo-analysis",
        ).forEach { relative ->
            Files.createDirectories(root.resolve(relative))
        }
        listOf(
            "apps/androidApp",
            "apps/sharedKit",
            "build-logic",
            "core/config",
            "core/ui",
            "domain/nutrition",
            "domain/photo-analysis",
            "feature/photo-analysis",
            "feature/photo-analysis-presenter",
            "feature/photo-analysis-ui",
            "platform/photo-android",
            "platform/photo-ios",
            "testing/compose-support",
            "testing/scaffold-probe",
        ).forEach { relative ->
            val target = root.resolve(relative).resolve("gradle.lockfile")
            Files.createDirectories(target.parent)
            Files.copy(sourceRoot.resolve(relative).resolve("gradle.lockfile"), target)
            Files.writeString(target.parent.resolve("build.gradle.kts"), "// fixture Gradle project\n")
        }
    }

    private fun fixtureRoot(prefix: String): java.nio.file.Path {
        val parent = Files.createTempDirectory(prefix)
        return parent.resolve("mobile").also(Files::createDirectories)
    }
}

private object PathLocator {
    fun repoMobileRoot(): java.nio.file.Path =
        java.nio.file.Path.of(System.getProperty("user.dir")).resolve("..").normalize()
}
