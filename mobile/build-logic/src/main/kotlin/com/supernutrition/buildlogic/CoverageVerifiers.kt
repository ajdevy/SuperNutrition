package com.supernutrition.buildlogic

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale
import org.w3c.dom.Element
import org.w3c.dom.Node

internal object NativeEquivalentEvidenceVerifier {
    fun allowed(
        coverageText: String,
        nativeGapsText: String,
        evidenceText: String,
        now: Instant = Instant.now(),
    ): Boolean {
        val coverage = PolicyToml.document(coverageText)
        val nativeGaps = PolicyToml.document(nativeGapsText)
        val evidence = PolicyToml.document(evidenceText)
        val denominatorBlocks = PolicyToml.blocks(coverage, "denominator")
        require(denominatorBlocks.isNotEmpty()) { "Coverage inventory has no denominator blocks" }
        val referencedIds = denominatorBlocks
            .filter { PolicyToml.string(it, "coverage_kind") == "native-equivalent" }
            .flatMap { PolicyToml.stringList(it, "evidence_ids") }
            .toSet()
        val gapBlocks = PolicyToml.blocks(nativeGaps, "gap")
        require(gapBlocks.mapNotNull { PolicyToml.string(it, "id") }.let { it.size == it.toSet().size }) {
            "Native-gap inventory contains duplicate IDs"
        }
        val optionalIds = gapBlocks
            .filter { PolicyToml.boolean(it, "optional") == true }
            .mapNotNull { PolicyToml.string(it, "id") }
            .toSet()
        val requiredIds = referencedIds - optionalIds
        if (requiredIds.isEmpty()) return true
        if (PolicyToml.string(nativeGaps, "lifecycle") != "active" ||
            PolicyToml.string(evidence, "lifecycle") != "active"
        ) return false

        val evidenceBlocks = PolicyToml.blocks(evidence, "evidence")
        require(evidenceBlocks.mapNotNull { PolicyToml.string(it, "id") }.let { it.size == it.toSet().size }) {
            "Evidence bindings contain duplicate IDs"
        }
        return requiredIds.all { id ->
            val gap = gapBlocks.firstOrNull { PolicyToml.string(it, "id") == id } ?: return@all false
            val evidence = evidenceBlocks.firstOrNull { PolicyToml.string(it, "id") == id } ?: return@all false
            if (PolicyToml.string(evidence, "status") != "success") return@all false
            if (PolicyToml.string(evidence, "commit_sha")?.isLowercaseHexDigest(40) != true ||
                PolicyToml.string(evidence, "artifact_digest")?.isLowercaseHexDigest(64) != true ||
                PolicyToml.string(evidence, "fixture_digest")?.isLowercaseHexDigest(64) != true
            ) return@all false
            if (PolicyToml.string(evidence, "device_os").isNullOrBlank() ||
                PolicyToml.string(evidence, "task_version").isNullOrBlank()
            ) return@all false
            val executedAt = runCatching { Instant.parse(PolicyToml.string(evidence, "executed_at")) }
                .getOrNull() ?: return@all false
            val maxAge = PolicyToml.integer(gap, "max_age_days") ?: return@all false
            executedAt.isAfter(now.minus(maxAge.toLong(), ChronoUnit.DAYS))
        }
    }

}

internal data class JvmCoverageSummary(
    val linePercent: Double,
    val branchPercent: Double,
    val moduleCount: Int,
)

internal data class JvmCoverageTarget(
    val projectPath: String,
    val lineMinimumPercent: Int,
    val branchMinimumPercent: Int,
)

internal object JvmCoverageVerifier {
    fun reportPaths(reportRoot: Path, projectPath: String): List<Path> {
        val projectRoot = reportRoot.resolve(projectPath.removePrefix(":").replace(':', '/'))
        return listOf(
            projectRoot.resolve("build/reports/kover/reportJvm.xml"),
            projectRoot.resolve("build/reports/kover/reportAndroid.xml"),
        )
    }

    fun reportPath(reportRoot: Path, projectPath: String): Path = reportPaths(reportRoot, projectPath).first()

    fun verify(
        reportRoot: Path,
        productionProjects: List<String>,
        targets: Map<String, JvmCoverageTarget> = productionProjects.associateWith {
            JvmCoverageTarget(it, lineMinimumPercent = 85, branchMinimumPercent = 85)
        },
    ): JvmCoverageSummary {
        require(productionProjects.isNotEmpty()) { "JVM host coverage has no production modules" }
        require(targets.keys == productionProjects.toSet()) {
            "JVM coverage targets must match the production modules exactly"
        }
        var lineMissed = 0L
        var lineCovered = 0L
        var branchMissed = 0L
        var branchCovered = 0L
        productionProjects.forEach { path ->
            // Branchless is allowed; missing lines is not. A production module can
            // contain executable lines but no branch instructions at all, and that
            // valid module must not become a product-wide empty-branch failure —
            // the other modules still carry the aggregate branch denominator.
            val (line, branch) = moduleCounters(reportRoot, path, allowEmptyBranch = true)
            val target = targets.getValue(path)
            val moduleLinePercent = percentage(line.second, line.first, "line")
            require(moduleLinePercent >= target.lineMinimumPercent) {
                "JVM module $path line coverage is ${formatCoverage(moduleLinePercent)}%; " +
                    "minimum is ${target.lineMinimumPercent}%"
            }
            lineMissed += line.first
            lineCovered += line.second
            if (branch.first + branch.second > 0) {
                val moduleBranchPercent = percentage(branch.second, branch.first, "branch")
                require(moduleBranchPercent >= target.branchMinimumPercent) {
                    "JVM module $path branch coverage is ${formatCoverage(moduleBranchPercent)}%; " +
                        "minimum is ${target.branchMinimumPercent}%"
                }
                branchMissed += branch.first
                branchCovered += branch.second
            }
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

    /**
     * Enforces a threshold stricter than the project floor on one module,
     * reusing the same report-reading path [verify] does so the two can never
     * read a module's coverage two different ways.
     */
    fun verifyModule(
        reportRoot: Path,
        projectPath: String,
        minLinePercent: Double,
        minBranchPercent: Double,
    ): JvmCoverageSummary {
        val (line, branch) = moduleCounters(reportRoot, projectPath)
        val linePercent = percentage(line.second, line.first, "line")
        val branchPercent = percentage(branch.second, branch.first, "branch")
        require(linePercent >= minLinePercent) {
            "$projectPath line coverage is ${formatCoverage(linePercent)}%; minimum is ${formatCoverage(minLinePercent)}%"
        }
        require(branchPercent >= minBranchPercent) {
            "$projectPath branch coverage is ${formatCoverage(branchPercent)}%; " +
                "minimum is ${formatCoverage(minBranchPercent)}%"
        }
        return JvmCoverageSummary(linePercent, branchPercent, 1)
    }

    private fun moduleCounters(
        reportRoot: Path,
        path: String,
        allowEmptyBranch: Boolean = false,
    ): Pair<Pair<Long, Long>, Pair<Long, Long>> {
        val report = reportPaths(reportRoot, path).firstOrNull(Files::isRegularFile)
        require(report != null) {
            "Missing JVM/Android Kover report for $path: ${reportPaths(reportRoot, path).joinToString()}"
        }
        val text = try {
            Files.readString(report)
        } catch (error: Exception) {
            throw IllegalArgumentException("Unable to read JVM Kover report for $path: $report", error)
        }
        return counter(text, "LINE", path) to counter(text, "BRANCH", path, allowEmpty = allowEmptyBranch)
    }

    fun counter(
        text: String,
        type: String,
        projectPath: String,
        allowEmpty: Boolean = false,
    ): Pair<Long, Long> {
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
        require(allowEmpty || missed + covered > 0) {
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
            val document = parseSecureXml(text)
            require(document.documentElement?.tagName == "report") { "root element is not <report>" }
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
