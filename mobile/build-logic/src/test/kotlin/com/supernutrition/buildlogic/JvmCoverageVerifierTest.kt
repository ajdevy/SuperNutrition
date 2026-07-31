package com.supernutrition.buildlogic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import java.nio.file.Files
import java.nio.file.Path

class JvmCoverageVerifierTest {
    @Test
    fun `counter uses the report level counter rather than class counters`() {
        val report = """
            <report>
              <package name="example">
                <counter type="LINE" missed="1" covered="2"/>
              </package>
              <counter type="LINE" missed="4" covered="9"/>
            </report>
        """.trimIndent()

        assertEquals(4L to 9L, JvmCoverageVerifier.counter(report, "LINE", ":example"))
    }

    @Test
    fun `percentage fails closed for an empty denominator`() {
        assertFailsWith<IllegalArgumentException> {
            JvmCoverageVerifier.percentage(0, 0, "branch")
        }
    }

    @Test
    fun `counter requires a report level counter`() {
        val report = """
            <report>
              <package name="example">
                <counter type="LINE" missed="1" covered="99"/>
              </package>
            </report>
        """.trimIndent()

        val failure = assertFailsWith<IllegalArgumentException> {
            JvmCoverageVerifier.counter(report, "LINE", ":example")
        }

        assertTrue(failure.message.orEmpty().contains(":example"))
    }

    @Test
    fun `malformed report fails with its project path`() {
        val report = """
            <report>
              <counter type="LINE" missed="1" covered="99"/>
            </report
        """.trimIndent()

        val failure = assertFailsWith<IllegalArgumentException> {
            JvmCoverageVerifier.counter(report, "LINE", ":broken")
        }

        assertTrue(failure.message.orEmpty().contains(":broken"))
    }

    @Test
    fun `aggregate consumes exactly the listed report files`() {
        val root = Files.createTempDirectory("jvm-coverage-aggregate")
        try {
            writeReport(root, ":first", lineMissed = 1, lineCovered = 9, branchMissed = 1, branchCovered = 9)
            writeReport(root, ":second", lineMissed = 0, lineCovered = 10, branchMissed = 0, branchCovered = 10)

            val result = JvmCoverageVerifier.verify(root, listOf(":first", ":second"))

            assertEquals(95.0, result.linePercent)
            assertEquals(95.0, result.branchPercent)
            assertEquals(2, result.moduleCount)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `aggregate ignores a branchless module but keeps its line denominator`() {
        val root = Files.createTempDirectory("jvm-coverage-branchless")
        try {
            writeReport(root, ":branchless", lineMissed = 0, lineCovered = 10, branchMissed = 0, branchCovered = 0)
            writeReport(root, ":branchful", lineMissed = 1, lineCovered = 9, branchMissed = 1, branchCovered = 9)

            val result = JvmCoverageVerifier.verify(root, listOf(":branchless", ":branchful"))

            assertEquals(95.0, result.linePercent)
            assertEquals(90.0, result.branchPercent)
            assertEquals(2, result.moduleCount)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `missing report fails with its project path`() {
        val root = Files.createTempDirectory("jvm-coverage-missing")
        try {
            val failure = assertFailsWith<IllegalArgumentException> {
                JvmCoverageVerifier.verify(root, listOf(":missing"))
            }

            assertTrue(failure.message.orEmpty().contains(":missing"))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `empty report denominator fails with its project path`() {
        val root = Files.createTempDirectory("jvm-coverage-empty")
        try {
            writeReport(root, ":empty", lineMissed = 0, lineCovered = 0, branchMissed = 1, branchCovered = 1)

            val failure = assertFailsWith<IllegalArgumentException> {
                JvmCoverageVerifier.verify(root, listOf(":empty"))
            }

            assertTrue(failure.message.orEmpty().contains(":empty"))
            assertTrue(failure.message.orEmpty().contains("empty"))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private fun writeReport(
        root: Path,
        projectPath: String,
        lineMissed: Int,
        lineCovered: Int,
        branchMissed: Int,
        branchCovered: Int,
    ) {
        val report = root
            .resolve(projectPath.removePrefix(":").replace(':', '/'))
            .resolve("build/reports/kover/reportJvm.xml")
        Files.createDirectories(report.parent)
        Files.writeString(
            report,
            """
                <report>
                  <package name="example">
                    <counter type="LINE" missed="1" covered="1"/>
                  </package>
                  <counter type="LINE" missed="$lineMissed" covered="$lineCovered"/>
                  <counter type="BRANCH" missed="$branchMissed" covered="$branchCovered"/>
                </report>
            """.trimIndent(),
        )
    }
}
