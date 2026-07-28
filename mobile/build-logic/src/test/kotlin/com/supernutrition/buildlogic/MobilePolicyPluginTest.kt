package com.supernutrition.buildlogic

import java.nio.file.Files
import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertNotNull

class MobilePolicyPluginTest {
    @Test
    fun `mobile policy registers verification task`() {
        val project = ProjectBuilder.builder().build()

        MobilePolicyPlugin().apply(project)

        assertNotNull(project.tasks.findByName("verifyMobilePolicy"))
    }

    @Test
    fun `quality plugin registers read-only task names`() {
        val fixture = Files.createTempDirectory("quality-plugin-fixture")
        Files.createDirectories(fixture.resolve("config/quality"))
        Files.writeString(
            fixture.resolve("config/quality/coverage.toml"),
            """
                [[denominator]]
                source_path = "mobile/core/config"
                coverage_kind = "jvm"
            """.trimIndent(),
        )
        val project = ProjectBuilder.builder()
            .withProjectDir(fixture.toFile())
            .build()

        try {
            QualityPlugin().apply(project)

            assertNotNull(project.tasks.findByName("qualityCheckJvmAndroid"))
            assertNotNull(project.tasks.findByName("qualityCheckApple"))
            assertNotNull(project.tasks.findByName("qualityCoverageJvm"))
            assertNotNull(project.tasks.findByName("mobileUnitCoverage"))
            assertNotNull(project.tasks.findByName("qualityFormat"))
        } finally {
            fixture.toFile().deleteRecursively()
        }
    }
}
