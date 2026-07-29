package com.supernutrition.buildlogic

import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import dev.detekt.gradle.plugin.DetektPlugin
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.kover.gradle.plugin.KoverGradlePlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.gradle.api.tasks.testing.Test
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource

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
        val analysisTasks = target.tasks.withType<Detekt>().matching { it.name != "detektFormat" }
        qualityCheckJvmAndroid.configure { dependsOn(analysisTasks) }
        qualityCheckApple.configure { dependsOn(analysisTasks) }
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
        val productionProjects = inventoryProjects
            .filter { rootProject.findProject(it) != null }
        val unresolvedInventoryProjects = inventoryProjects - productionProjects.toSet()
        val reportRoot = rootProject.projectDir.toPath()
        val lifecycleFile = mobileRoot.resolve("product-lifecycle.toml").toFile()
        val coverageFile = mobileRoot.resolve("config/quality/coverage.toml").toFile()
        val reportFiles = productionProjects.flatMap { path ->
            JvmCoverageVerifier.reportPaths(reportRoot, path).map(Path::toFile)
        }
        val reportTaskNames = setOf("koverXmlReportJvm", "koverXmlReportAndroid")
        val reportTasks = productionProjects.flatMap { path ->
            rootProject.findProject(path)?.tasks
                ?.matching { it.name in reportTaskNames }
                ?.toList()
                .orEmpty()
        }

        qualityCoverageJvm.configure {
            // Keep lifecycle/inventory changes in the configuration-cache
            // input model. Report tasks are always wired, but the reports
            // themselves are skipped until the active claim is authorized.
            dependsOn(*reportTasks.toTypedArray())
            inputs.files(lifecycleFile, coverageFile, reportFiles)
            doLast {
                require(coverageClaimAllowed(mobileRoot, localCoverageRequested)) {
                    "JVM coverage claim is not authorized by the active lifecycle and activation gates"
                }
                require(unresolvedInventoryProjects.isEmpty()) {
                    "Active JVM coverage inventory contains unresolved projects: " +
                        unresolvedInventoryProjects.joinToString()
                }
                val result = JvmCoverageVerifier.verify(reportRoot, inventoryProjects)
                logger.lifecycle(
                    "JVM host coverage: line=${formatCoverage(result.linePercent)}%, " +
                        "branch=${formatCoverage(result.branchPercent)}% across " +
                        "${result.moduleCount} production modules.",
                )
            }
        }

        mobileUnitCoverage.configure {
            dependsOn(*reportTasks.toTypedArray())
            inputs.files(lifecycleFile, coverageFile, reportFiles)
            doLast {
                require(localCoverageRequested) {
                    "mobileUnitCoverage requires -Psupernutrition.runJvmCoverage=true"
                }
                require(unresolvedInventoryProjects.isEmpty()) {
                    "Local JVM coverage inventory contains unresolved projects: " +
                        unresolvedInventoryProjects.joinToString()
                }
                val result = JvmCoverageVerifier.verify(reportRoot, inventoryProjects)
                logger.lifecycle(
                    "JVM host coverage (local-only): line=${formatCoverage(result.linePercent)}%, " +
                        "branch=${formatCoverage(result.branchPercent)}% across " +
                        "${result.moduleCount} production modules; no active quality claim emitted.",
                )
            }
        }

        productionProjects.forEach { path ->
            rootProject.findProject(path)?.tasks
                ?.matching { it.name in reportTaskNames }
                ?.configureEach {
                    onlyIf {
                        lifecycle(mobileRoot) == "active" || localCoverageRequested
                    }
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

    private fun coverageClaimAllowed(mobileRoot: Path, explicitJvmCoverage: Boolean = false): Boolean {
        if (explicitJvmCoverage) return true

        val lifecycleText = Files.readString(mobileRoot.resolve("product-lifecycle.toml"))
        val coverageText = Files.readString(mobileRoot.resolve("config/quality/coverage.toml"))
        return lifecycle(mobileRoot) == "active" &&
            coverageText.contains("quality_claim = true") &&
            nativeEquivalentEvidenceAllowed(mobileRoot) &&
            listOf(
                "active_inventory_and_denominators = true",
                "production_compose_host = true",
            ).all(lifecycleText::contains)
    }

    private fun qualityClaimConfigured(mobileRoot: Path): Boolean =
        Files.readString(mobileRoot.resolve("config/quality/coverage.toml"))
            .contains("quality_claim = true")

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

    private fun blocks(text: String, name: String): List<String> =
        PolicyToml.blocks(text, name)

    private fun extractString(text: String, key: String): String? =
        PolicyToml.string(text, key)

    private fun lifecycle(mobileRoot: Path): String =
        Regex("(?m)^lifecycle\\s*=\\s*\\\"([^\\\"]+)\\\"$")
            .find(Files.readString(mobileRoot.resolve("product-lifecycle.toml")))
            ?.groupValues
            ?.get(1)
            ?: error("Mobile lifecycle is missing")
}

private data class CoverageDenominator(
    val sourcePath: String,
    val coverageKind: String,
)

internal object NativeEquivalentEvidenceVerifier {
    fun allowed(
        coverageText: String,
        nativeGapsText: String,
        evidenceText: String,
        now: Instant = Instant.now(),
    ): Boolean {
        val referencedIds = blocks(coverageText, "denominator")
            .filter { extractString(it, "coverage_kind") == "native-equivalent" }
            .flatMap { extractStringList(it, "evidence_ids") }
            .toSet()
        val gapBlocks = blocks(nativeGapsText, "gap")
        val optionalIds = gapBlocks
            .filter { extractBoolean(it, "optional") == true }
            .mapNotNull { extractString(it, "id") }
            .toSet()
        val requiredIds = referencedIds - optionalIds
        if (requiredIds.isEmpty()) return true
        if (extractString(nativeGapsText, "lifecycle") != "active" ||
            extractString(evidenceText, "lifecycle") != "active"
        ) return false

        val evidenceBlocks = blocks(evidenceText, "evidence")
        return requiredIds.all { id ->
            val gap = gapBlocks.firstOrNull { extractString(it, "id") == id } ?: return@all false
            val evidence = evidenceBlocks.firstOrNull { extractString(it, "id") == id } ?: return@all false
            if (extractString(evidence, "status") != "success") return@all false
            if (!Regex("[0-9a-f]{40}").matches(extractString(evidence, "commit_sha").orEmpty())) return@all false
            if (!Regex("[0-9a-f]{64}").matches(extractString(evidence, "artifact_digest").orEmpty())) return@all false
            if (!Regex("[0-9a-f]{64}").matches(extractString(evidence, "fixture_digest").orEmpty())) return@all false
            if (extractString(evidence, "device_os").isNullOrBlank() ||
                extractString(evidence, "task_version").isNullOrBlank()
            ) return@all false
            val executedAt = runCatching { Instant.parse(extractString(evidence, "executed_at")) }
                .getOrNull() ?: return@all false
            val maxAge = extractInt(gap, "max_age_days") ?: return@all false
            executedAt.isAfter(now.minus(maxAge.toLong(), ChronoUnit.DAYS))
        }
    }

    private fun blocks(text: String, name: String): List<String> =
        PolicyToml.blocks(text, name)

    private fun extractString(text: String, key: String): String? =
        PolicyToml.string(text, key)

    private fun extractInt(text: String, key: String): Int? =
        PolicyToml.integer(text, key)

    private fun extractBoolean(text: String, key: String): Boolean? =
        PolicyToml.boolean(text, key)

    private fun extractStringList(text: String, key: String): List<String> =
        PolicyToml.stringList(text, key)
}

internal data class JvmCoverageSummary(
    val linePercent: Double,
    val branchPercent: Double,
    val moduleCount: Int,
)

internal object JvmCoverageVerifier {
    fun reportPaths(reportRoot: Path, projectPath: String): List<Path> {
        val projectRoot = reportRoot.resolve(projectPath.removePrefix(":").replace(':', '/'))
        return listOf(
            projectRoot.resolve("build/reports/kover/reportJvm.xml"),
            projectRoot.resolve("build/reports/kover/reportAndroid.xml"),
        )
    }

    fun reportPath(reportRoot: Path, projectPath: String): Path =
        reportPaths(reportRoot, projectPath).first()

    fun verify(reportRoot: Path, productionProjects: List<String>): JvmCoverageSummary {
        require(productionProjects.isNotEmpty()) {
            "JVM host coverage has no production modules"
        }

        var lineMissed = 0L
        var lineCovered = 0L
        var branchMissed = 0L
        var branchCovered = 0L

        productionProjects.forEach { path ->
            val report = reportPaths(reportRoot, path).firstOrNull(Files::isRegularFile)
            require(report != null) {
                "Missing JVM/Android Kover report for $path: ${reportPaths(reportRoot, path).joinToString()}"
            }
            val text = try {
                Files.readString(report)
            } catch (error: Exception) {
                throw IllegalArgumentException("Unable to read JVM Kover report for $path: $report", error)
            }
            val line = counter(text, "LINE", path)
            val branch = counter(text, "BRANCH", path)
            lineMissed += line.first
            lineCovered += line.second
            branchMissed += branch.first
            branchCovered += branch.second
        }

        val linePercent = percentage(lineCovered, lineMissed, "line")
        val branchPercent = percentage(branchCovered, branchMissed, "branch")
        require(linePercent >= 85.0) {
            "JVM host line coverage is ${formatCoverage(linePercent)}%; minimum is 85%"
        }
        require(branchPercent >= 85.0) {
            "JVM host branch coverage is ${formatCoverage(branchPercent)}%; minimum is 85%"
        }
        return JvmCoverageSummary(linePercent, branchPercent, productionProjects.size)
    }

    fun counter(text: String, type: String, projectPath: String): Pair<Long, Long> {
        val report = parseReport(text, projectPath)
        val rootCounters = (0 until report.childNodes.length)
            .map(report.childNodes::item)
            .filter { node ->
                node.nodeType == Node.ELEMENT_NODE &&
                    (node as Element).tagName == "counter" &&
                    node.getAttribute("type") == type
            }
        require(rootCounters.size == 1) {
            "JVM Kover report for $projectPath must have exactly one report-level $type counter"
        }
        val match = rootCounters.single() as Element

        val missed = counterValue(match, "missed", type, projectPath)
        val covered = counterValue(match, "covered", type, projectPath)
        require(missed + covered > 0) {
            "JVM Kover report for $projectPath has an empty $type denominator"
        }
        return missed to covered
    }

    fun percentage(covered: Long, missed: Long, kind: String): Double {
        val total = covered + missed
        require(total > 0) { "JVM host $kind coverage has an empty denominator" }
        return covered.toDouble() * 100.0 / total.toDouble()
    }

    private fun parseReport(text: String, projectPath: String): Element {
        try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                isXIncludeAware = false
                isExpandEntityReferences = false
            }
            val document = factory.newDocumentBuilder().parse(InputSource(StringReader(text)))
            require(document.documentElement?.tagName == "report") {
                "root element is not <report>"
            }
            return document.documentElement
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("Malformed JVM Kover report for $projectPath: ${error.message}", error)
        } catch (error: Exception) {
            throw IllegalArgumentException("Malformed JVM Kover report for $projectPath", error)
        }
    }

    private fun counterValue(element: Element, name: String, type: String, projectPath: String): Long =
        element.getAttribute(name).toLongOrNull()?.also { value ->
            require(value >= 0) {
                "JVM Kover report for $projectPath has a negative $type $name counter"
            }
        } ?: throw IllegalArgumentException(
            "JVM Kover report for $projectPath has an invalid $type $name counter",
        )

    private fun formatCoverage(value: Double): String = String.format(Locale.ROOT, "%.2f", value)
}
