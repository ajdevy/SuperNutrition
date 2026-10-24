package com.supernutrition.buildlogic

import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import dev.detekt.gradle.plugin.DetektPlugin
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlinx.kover.gradle.plugin.KoverGradlePlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.logging.Logger
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.gradle.api.tasks.testing.Test

/**
 * Central, read-only quality entrypoints for the mobile product.
 *
 * Detekt is applied to every Kotlin production/test project through this
 * included build. Kover is applied only to the production modules declared by
 * the coverage inventory and is aggregated at the mobile root. Native and
 * device tests are deliberately not part of the Kover denominator.
 */
class QualityPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val mobileRoot = project.rootProject.projectDir.toPath()
        val explicitJvmCoverageRequested = project.providers
            .gradleProperty("supernutrition.runJvmCoverage")
            .map(String::toBoolean)
            .orElse(false)
            .get()
        val qualityCheckJvmAndroid = project.tasks.register("qualityCheckJvmAndroid") {
            group = "verification"
            description = "Runs the read-only JVM/Android-host quality gate."
            doLast {
                MobilePolicyValidator.validate(mobileRoot)
                logger.lifecycle(
                    "qualityCheckJvmAndroid validated the local mobile policy; " +
                        "static analysis ran without emitting an active coverage claim.",
                )
            }
        }

        val qualityCheckApple = project.tasks.register("qualityCheckApple") {
            group = "verification"
            description = "Runs the read-only Apple quality gate."
            doLast {
                MobilePolicyValidator.validate(mobileRoot)
                val xcodeProject = mobileRoot.resolve(
                    "apps/iosApp/SuperNutritionUIAcceptance.xcodeproj",
                )
                require(Files.isDirectory(xcodeProject)) {
                    "Apple quality gate requires the checked-in Xcode project: $xcodeProject"
                }
                if (Files.isExecutable(Path.of("/usr/bin/xcodebuild"))) {
                    val result = ProcessBuilder(
                        "/usr/bin/xcodebuild",
                        "-list",
                        "-project",
                        xcodeProject.toString(),
                    ).directory(mobileRoot.toFile()).inheritIO().start().waitFor()
                    require(result == 0) { "xcodebuild -list failed for $xcodeProject" }
                } else {
                    logger.lifecycle("xcodebuild unavailable; Apple execution remains optional")
                }
                logger.lifecycle(
                    "qualityCheckApple validated the local mobile policy; " +
                        "Apple evidence remains good-to-have.",
                )
            }
        }

        val qualityCoverageJvm = project.tasks.register("qualityCoverageJvm") {
            group = "verification"
            description = "Verifies the JVM/Android-host Kover denominator when mobile is active."
            onlyIf {
                lifecycle(mobileRoot) == "active" && qualityClaimConfigured(mobileRoot)
            }
        }

        val mobileUnitCoverage = project.tasks.register("mobileUnitCoverage") {
            group = "verification"
            description = "Runs mobile JVM unit tests and enforces the temporary coverage gate."
            dependsOn(qualityCoverageJvm)
            doFirst {
                require(explicitJvmCoverageRequested) {
                    "mobileUnitCoverage requires -Psupernutrition.runJvmCoverage=true"
                }
            }
        }
        val mobileUnitTests = project.tasks.register("mobileUnitTests") {
            group = "verification"
            description = "Runs every declared mobile JVM unit-test task without emitting a coverage claim."
        }
        qualityCheckJvmAndroid.configure { dependsOn(mobileUnitTests) }
        qualityCheckApple.configure { dependsOn(mobileUnitTests) }
        project.allprojects.forEach { subproject ->
            subproject.tasks.withType<Test>().configureEach {
                if (name == "jvmTest" || name.endsWith("UnitTest")) {
                    mobileUnitTests.configure { dependsOn(this@configureEach) }
                    mobileUnitCoverage.configure { dependsOn(this@configureEach) }
                }
            }
        }

        val qualityFormat = project.tasks.register("qualityFormat") {
            group = "formatting"
            description = "The only explicitly mutating formatting entrypoint."
            doLast {
                logger.lifecycle(
                    "Detekt/ktlint-wrapper formatting completed through the explicit qualityFormat entrypoint.",
                )
            }
        }

        val productionPaths = productionModulePaths(mobileRoot)
        project.subprojects.forEach { subproject ->
            var detektConfigured = false
            fun configureDetektOnce() {
                if (detektConfigured) return
                configureDetekt(subproject, project.rootProject)
                wireDetektTasks(subproject, qualityCheckJvmAndroid, qualityCheckApple, qualityFormat)
                detektConfigured = true
            }

            listOf(
                "org.jetbrains.kotlin.multiplatform",
                "com.android.application",
                "org.jetbrains.kotlin.android",
            ).forEach { pluginId ->
                subproject.pluginManager.withPlugin(pluginId) {
                    if (pluginId == "org.jetbrains.kotlin.multiplatform" &&
                        productionPaths.contains(subproject.path)
                    ) {
                        subproject.pluginManager.apply(KoverGradlePlugin::class.java)
                    }
                    configureDetektOnce()
                }
            }
        }

        configureJvmCoverageAggregation(
            project,
            mobileRoot,
            qualityCoverageJvm,
            mobileUnitCoverage,
            explicitJvmCoverageRequested,
        )

        qualityCheckJvmAndroid.configure { dependsOn(qualityCoverageJvm) }
        project.tasks.findByName("verifyMobilePolicy")?.let { mobilePolicyTask ->
            qualityCheckJvmAndroid.configure { dependsOn(mobilePolicyTask) }
            qualityCheckApple.configure { dependsOn(mobilePolicyTask) }
            qualityCoverageJvm.configure { dependsOn(mobilePolicyTask) }
        }
    }

    private fun configureDetekt(target: Project, rootProject: Project) {
        target.pluginManager.apply(DetektPlugin::class.java)

        val catalogs = rootProject.extensions.getByType(VersionCatalogsExtension::class.java)
        val detektFormatting = catalogs.named("libs")
            .findLibrary("detekt-ktlint-wrapper")
            .orElseThrow { IllegalStateException("Missing libs.detekt.ktlint.wrapper catalog alias") }
            .get()
        target.dependencies.add("detektPlugins", detektFormatting)

        val configFile = rootProject.file("config/detekt/detekt.yml")
        target.extensions.configure<DetektExtension> {
            config.from(configFile)
            buildUponDefaultConfig.set(true)
            ignoreFailures.set(false)
            parallel.set(true)
            autoCorrect.set(false)
        }
        target.tasks.withType<Detekt>().configureEach {
            config.from(configFile)
            buildUponDefaultConfig.set(true)
            ignoreFailures.set(false)
            parallel.set(true)
            autoCorrect.set(false)
        }
    }

    private fun wireDetektTasks(
        target: Project,
        qualityCheckJvmAndroid: org.gradle.api.tasks.TaskProvider<Task>,
        qualityCheckApple: org.gradle.api.tasks.TaskProvider<Task>,
        qualityFormat: org.gradle.api.tasks.TaskProvider<Task>,
    ) {
        val formatTask = target.tasks.register("detektFormat", Detekt::class.java) {
            description = "Formats Kotlin sources for this module through the explicit qualityFormat gate."
            source(target.fileTree("src") { include("**/*.kt") })
            classpath.from(target.files())
            config.from(target.rootProject.file("config/detekt/detekt.yml"))
            buildUponDefaultConfig.set(true)
            ignoreFailures.set(false)
            parallel.set(true)
            autoCorrect.set(true)
        }
        qualityFormat.configure { dependsOn(formatTask) }
        val analysisTasks = target.tasks.withType<Detekt>().matching {
            it.name != "detektFormat" && it.name != DETEKT_PLAIN_TASK
        }
        // Compose regenerates these compiler resource accessors. Checking their
        // emitted formatting produces findings outside the authored source tree.
        // Restrict this to the plugin's exact output folder; other source inputs stay checked.
        val composeResourceOutput = target.layout.buildDirectory.dir("generated/compose/resourceGenerator")
        // SQLDelight owns this database's emitted accessors, including their naming and formatting.
        // Authored catalog/package sources under src remain checked; coverage is unchanged.
        val hasNutritionDatabase = target.path == ":data:nutrition"
        val nutritionDatabaseOutput = target.layout.buildDirectory.dir(
            "generated/sqldelight/code/UsdaCatalogDatabase/commonMain",
        )
        analysisTasks.configureEach {
            exclude { element ->
                val sourcePath = element.file.toPath().toAbsolutePath().normalize()
                sourcePath.startsWith(composeResourceOutput.get().asFile.toPath().toAbsolutePath().normalize()) ||
                    (hasNutritionDatabase && sourcePath.startsWith(
                        nutritionDatabaseOutput.get().asFile.toPath().toAbsolutePath().normalize(),
                    ))
            }
        }
        qualityCheckJvmAndroid.configure { dependsOn(analysisTasks) }
        qualityCheckApple.configure { dependsOn(analysisTasks) }

        // Detekt's own plain `detekt` task sources src/main/kotlin and
        // src/test/kotlin, which no Kotlin Multiplatform module has. It is
        // therefore permanently NO-SOURCE and exits 0 without inspecting a
        // single file, while the per-source-set tasks beside it find real
        // violations. That is the task name every developer and every piece of
        // upstream Detekt documentation reaches for, so it reports success on
        // unlinted code by default.
        //
        // It cannot be removed: Gradle has no supported un-register, and
        // `enabled = false` or `onlyIf { false }` still exit 0 as SKIPPED,
        // trading one silent green for another. Making it a lifecycle alias is
        // the change that makes the invocation honest — it stays NO-SOURCE
        // itself, but Gradle runs its dependencies first and fails on their
        // findings. The authoritative qualityCheck entrypoints keep their own
        // direct edges above, so this cannot weaken them.
        target.tasks.matching { it.name == DETEKT_PLAIN_TASK }.configureEach {
            dependsOn(analysisTasks)
        }
    }

    private fun configureJvmCoverageAggregation(
        rootProject: Project,
        mobileRoot: Path,
        qualityCoverageJvm: org.gradle.api.tasks.TaskProvider<Task>,
        mobileUnitCoverage: org.gradle.api.tasks.TaskProvider<Task>,
        localCoverageRequested: Boolean,
    ) {
        // Host slices intentionally include only the projects needed to build
        // a local framework. Do not resolve absent inventory entries through
        // Project.project(path): that would make the documented Intel/iOS
        // compatibility lane fail during configuration before its host task
        // can run. The full product build still sees every inventory entry.
        val inventoryProjects = jvmCoverageModulePaths(mobileRoot)
        val coverageTargets = jvmCoverageTargets(mobileRoot)
        val productionProjects = inventoryProjects
            .filter { rootProject.findProject(it) != null }
        val allProductionProjects = productionModulePaths(mobileRoot)
            .filter { rootProject.findProject(it) != null }
        val unresolvedInventoryProjects = inventoryProjects - productionProjects.toSet()
        val reportRoot = rootProject.projectDir.toPath()
        val lifecycleFile = mobileRoot.resolve("product-lifecycle.toml").toFile()
        val coverageFile = mobileRoot.resolve("config/quality/coverage.toml").toFile()
        val coverageText = Files.readString(coverageFile.toPath())
        val aggregateLineMinimumPercent = PolicyToml.integer(coverageText, "minimum_line_percent")
            ?: error("Coverage inventory is missing minimum_line_percent")
        val aggregateBranchMinimumPercent = PolicyToml.integer(coverageText, "minimum_branch_percent")
            ?: error("Coverage inventory is missing minimum_branch_percent")
        val reportFiles = productionProjects.flatMap { path ->
            JvmCoverageVerifier.reportPaths(reportRoot, path).map(Path::toFile)
        }
        val reportTaskNames = setOf("koverXmlReportJvm", "koverXmlReportAndroid")
        val htmlReportTaskNames = setOf("koverHtmlReportJvm", "koverHtmlReportAndroid")
        val mobileCoverageReports = rootProject.tasks.register("mobileCoverageReports") {
            group = "verification"
            description = "Generates XML and HTML Kover reports without changing the quality gate."
        }

        // Kover registers its report tasks from plugin callbacks. Resolve the
        // task collections after all project scripts have been evaluated so
        // the verifier cannot run before its report-producing dependencies.
        rootProject.gradle.projectsEvaluated {
            productionProjects.forEach { path ->
                rootProject.findProject(path)?.tasks
                    ?.matching { it.name in reportTaskNames }
                    ?.forEach { reportTask ->
                        qualityCoverageJvm.configure { dependsOn(reportTask) }
                        mobileUnitCoverage.configure { dependsOn(reportTask) }
                        mobileCoverageReports.configure { dependsOn(reportTask) }
                    }
            }
            allProductionProjects.forEach { path ->
                rootProject.findProject(path)?.tasks
                    ?.matching { it.name in reportTaskNames + htmlReportTaskNames }
                    ?.forEach { reportTask ->
                        mobileCoverageReports.configure { dependsOn(reportTask) }
                    }
            }
            allProductionProjects.forEach { path ->
                rootProject.findProject(path)?.tasks
                    ?.matching { it.name in reportTaskNames + htmlReportTaskNames }
                    ?.forEach { reportTask ->
                        reportTask.onlyIf {
                            lifecycle(mobileRoot) == "active" || localCoverageRequested
                        }
                    }
            }
        }

        qualityCoverageJvm.configure {
            // Keep lifecycle/inventory changes in the configuration-cache
            // input model. Report tasks are always wired, but the reports
            // themselves are skipped until the active claim is authorized.
            inputs.files(lifecycleFile, coverageFile, reportFiles)
            doLast {
                require(coverageClaimAllowed(mobileRoot, localCoverageRequested)) {
                    "JVM coverage claim is not authorized by the active lifecycle and activation gates"
                }
                require(unresolvedInventoryProjects.isEmpty()) {
                    "Active JVM coverage inventory contains unresolved projects: " +
                        unresolvedInventoryProjects.joinToString()
                }
                val result = JvmCoverageVerifier.verify(
                    reportRoot,
                    inventoryProjects,
                    coverageTargets.associateBy { it.projectPath },
                    aggregateLineMinimumPercent = aggregateLineMinimumPercent,
                    aggregateBranchMinimumPercent = aggregateBranchMinimumPercent,
                )
                logger.lifecycle(
                    "JVM host coverage: line=${formatCoverage(result.linePercent)}%, " +
                        "branch=${formatCoverage(result.branchPercent)}% across " +
                        "${result.moduleCount} production modules.",
                )
                verifyStrictCoverageModules(reportRoot, inventoryProjects, logger)
            }
        }

        mobileUnitCoverage.configure {
            inputs.files(lifecycleFile, coverageFile, reportFiles)
            doLast {
                require(localCoverageRequested) {
                    "mobileUnitCoverage requires -Psupernutrition.runJvmCoverage=true"
                }
                require(unresolvedInventoryProjects.isEmpty()) {
                    "Local JVM coverage inventory contains unresolved projects: " +
                        unresolvedInventoryProjects.joinToString()
                }
                val result = JvmCoverageVerifier.verify(
                    reportRoot,
                    inventoryProjects,
                    coverageTargets.associateBy { it.projectPath },
                    aggregateLineMinimumPercent = aggregateLineMinimumPercent,
                    aggregateBranchMinimumPercent = aggregateBranchMinimumPercent,
                )
                logger.lifecycle(
                    "JVM host coverage (local-only): line=${formatCoverage(result.linePercent)}%, " +
                        "branch=${formatCoverage(result.branchPercent)}% across " +
                        "${result.moduleCount} production modules; no active quality claim emitted.",
                )
                verifyStrictCoverageModules(reportRoot, inventoryProjects, logger)
            }
        }

        allProductionProjects.forEach { path ->
            rootProject.findProject(path)?.tasks
                ?.matching { it.name in reportTaskNames + htmlReportTaskNames }
                ?.configureEach {
                    inputs.files(lifecycleFile, coverageFile)
                }
        }
    }

    /**
     * Kover's root merge variant cannot represent this mixed KMP graph
     * reliably during Gradle configuration. Aggregate only the explicit JVM
     * host reports instead; Android/iOS/native/device evidence remains in its
     * own executable gates and never enters this denominator.
     */
    private fun formatCoverage(value: Double): String = String.format(Locale.ROOT, "%.2f", value)

    /**
     * Modules held to a stricter bar than the project-wide 85% floor in
     * `coverage.toml`, with the reason each one earns it. `coverage.toml`'s
     * own `line_minimum_percent`/`branch_minimum_percent` fields cannot carry
     * this: [MobilePolicyValidator] requires every denominator to declare the
     * uniform 85, so a per-module floor stricter than that lives here
     * instead, enforced in addition to the aggregate check rather than in
     * place of it.
     *
     * `:domain:nutrition` (typed-text meal search/matching) is the catalog
     * matcher, segmentation, and ranking logic the E7.2 review's slang and
     * edge-case sweep repeatedly found silent wrong-food defects in — an
     * uncovered branch here is a plausible wrong answer waiting to be found.
     * Branch is a genuine 100%. Line stops at 99.99, not 100, because of one
     * confirmed exception: `CatalogServingDefaultSource.standardServingIfBoundToSnapshot`
     * (a `fun interface` default method) never receives Kover line
     * attribution regardless of what calls it — verified by mutation testing
     * both of its logical branches (`CatalogServingDefaultSourceTest`
     * exercises the digest match and the digest mismatch, and forcing each
     * branch's condition to a constant makes the existing test fail) — so
     * 99.99 is the tightest bound this one confirmed tooling gap allows
     * without drifting into padding for a real regression: any single newly
     * uncovered line in this module drops the measured percentage by more
     * than this remaining margin.
     */
    private val strictCoverageModules: Map<String, Pair<Double, Double>> = mapOf(
        ":domain:nutrition" to (99.99 to 100.0),
    )

    private fun verifyStrictCoverageModules(
        reportRoot: Path,
        inventoryProjects: List<String>,
        logger: Logger,
    ) {
        strictCoverageModules.forEach { (path, thresholds) ->
            if (path !in inventoryProjects) return@forEach
            val (minLinePercent, minBranchPercent) = thresholds
            val result = JvmCoverageVerifier.verifyModule(reportRoot, path, minLinePercent, minBranchPercent)
            logger.lifecycle(
                "$path coverage: line=${formatCoverage(result.linePercent)}%, " +
                    "branch=${formatCoverage(result.branchPercent)}% " +
                    "(module floor: line>=${formatCoverage(minLinePercent)}%, " +
                    "branch>=${formatCoverage(minBranchPercent)}%).",
            )
        }
    }

    private fun coverageClaimAllowed(mobileRoot: Path, explicitJvmCoverage: Boolean = false): Boolean {
        if (explicitJvmCoverage) return true

        val lifecycleText = Files.readString(mobileRoot.resolve("product-lifecycle.toml"))
        val coverageText = Files.readString(mobileRoot.resolve("config/quality/coverage.toml"))
        val activation = PolicyToml.section(
            PolicyToml.document(lifecycleText),
            "activation",
        ) ?: return false
        return lifecycle(mobileRoot) == "active" &&
            PolicyToml.boolean(coverageText, "quality_claim") == true &&
            nativeEquivalentEvidenceAllowed(mobileRoot) &&
            listOf(
                "active_inventory_and_denominators",
                "production_compose_host",
            ).all { PolicyToml.boolean(activation, it) == true }
    }

    private fun qualityClaimConfigured(mobileRoot: Path): Boolean =
        PolicyToml.boolean(
            Files.readString(mobileRoot.resolve("config/quality/coverage.toml")),
            "quality_claim",
        ) == true

    private fun productionModulePaths(mobileRoot: Path): List<String> {
        val denominators = coverageDenominators(mobileRoot)
        val matches = denominators.map { it.sourcePath }
        require(matches.isNotEmpty()) { "Coverage inventory contains no source_path entries" }
        require(matches.all { it.startsWith("mobile/") }) {
            "Coverage source_path must remain inside mobile/: ${matches.filterNot { it.startsWith("mobile/") }}"
        }
        val paths = matches.map { ":" + it.removePrefix("mobile/").replace('/', ':') }
        require(paths.distinct().size == paths.size) {
            "Coverage inventory contains duplicate source_path entries"
        }
        return paths
    }

    private fun jvmCoverageModulePaths(mobileRoot: Path): List<String> = coverageDenominators(mobileRoot)
        .filter { it.coverageKind == "jvm" }
        .map { ":" + it.sourcePath.removePrefix("mobile/").replace('/', ':') }
        .also { require(it.isNotEmpty()) { "Coverage inventory contains no JVM denominators" } }

    private fun jvmCoverageTargets(mobileRoot: Path): List<JvmCoverageTarget> = coverageDenominators(mobileRoot)
        .filter { it.coverageKind == "jvm" }
        .map {
            JvmCoverageTarget(
                projectPath = ":" + it.sourcePath.removePrefix("mobile/").replace('/', ':'),
                lineMinimumPercent = it.lineMinimumPercent,
                branchMinimumPercent = it.branchMinimumPercent,
            )
        }
        .also { require(it.isNotEmpty()) { "Coverage inventory contains no JVM denominators" } }

    private fun coverageDenominators(mobileRoot: Path): List<CoverageDenominator> {
        val coverage = mobileRoot.resolve("config/quality/coverage.toml")
        require(Files.isRegularFile(coverage)) {
            "Coverage inventory is missing: $coverage"
        }
        return blocks(Files.readString(coverage), "denominator").map { block ->
            CoverageDenominator(
                sourcePath = extractString(block, "source_path")
                    ?: error("Coverage denominator is missing source_path"),
                coverageKind = extractString(block, "coverage_kind")
                    ?: error("Coverage denominator is missing coverage_kind"),
                lineMinimumPercent = PolicyToml.integer(block, "line_minimum_percent")
                    ?: error("Coverage denominator is missing line_minimum_percent"),
                branchMinimumPercent = PolicyToml.integer(block, "branch_minimum_percent")
                    ?: error("Coverage denominator is missing branch_minimum_percent"),
            )
        }
    }

    private fun nativeEquivalentEvidenceAllowed(mobileRoot: Path): Boolean {
        return NativeEquivalentEvidenceVerifier.allowed(
            coverageText = Files.readString(mobileRoot.resolve("config/quality/coverage.toml")),
            nativeGapsText = Files.readString(mobileRoot.resolve("config/quality/native-gaps.toml")),
            evidenceText = Files.readString(mobileRoot.resolve("config/quality/evidence-bindings.toml")),
        )
    }

    private fun blocks(text: String, name: String): List<PolicyToml.Table> =
        PolicyToml.blocks(PolicyToml.document(text), name)

    private fun extractString(table: PolicyToml.Table, key: String): String? =
        PolicyToml.string(table, key)

    private fun lifecycle(mobileRoot: Path): String =
        PolicyToml.string(
            Files.readString(mobileRoot.resolve("product-lifecycle.toml")),
            "lifecycle",
        ) ?: error("Mobile lifecycle is missing")
}

private data class CoverageDenominator(
    val sourcePath: String,
    val coverageKind: String,
    val lineMinimumPercent: Int,
    val branchMinimumPercent: Int,
)

/** Detekt's own plain task, registered by DetektPlugin rather than by this plugin. */
private const val DETEKT_PLAIN_TASK = "detekt"
