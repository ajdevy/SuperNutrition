package com.supernutrition.buildlogic

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ModuleBoundaryVerifierTest {
    @Test
    fun `declared production edge is accepted`() {
        val root = fixture("feature")
        try {
            Files.writeString(
                root.resolve("feature/photo/build.gradle.kts"),
                "implementation(project(\":domain:photo\"))",
            )
            ModuleBoundaryVerifier.validate(root)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `undeclared edge fails with source and target`() {
        val root = fixture("feature")
        try {
            Files.writeString(
                root.resolve("feature/photo/build.gradle.kts"),
                "implementation(project(\":platform:photo\"))",
            )
            val failure = assertFailsWith<IllegalArgumentException> {
                ModuleBoundaryVerifier.validate(root)
            }
            check(failure.message.orEmpty().contains("feature -> platform"))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `multiline production edge is accepted`() {
        val root = fixture("feature")
        try {
            Files.writeString(
                root.resolve("feature/photo/build.gradle.kts"),
                """
                    implementation(
                        project(":domain:photo")
                    )
                """.trimIndent(),
            )
            ModuleBoundaryVerifier.validate(root)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `platform wrapped project edge keeps its outer configuration`() {
        val root = fixture("feature")
        try {
            Files.writeString(
                root.resolve("feature/photo/build.gradle.kts"),
                "implementation(platform(project(\":domain:photo\")))",
            )
            ModuleBoundaryVerifier.validate(root)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `platform wrapped undeclared edge still fails`() {
        val root = fixture("feature")
        try {
            Files.writeString(
                root.resolve("feature/photo/build.gradle.kts"),
                "implementation(platform(project(\":platform:photo\")))",
            )
            assertFailsWith<IllegalArgumentException> { ModuleBoundaryVerifier.validate(root) }
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `forbidden import rules remain active through typed policy access`() {
        val root = fixture("feature")
        try {
            val inventory = root.resolve("config/architecture/module-boundaries.toml")
            Files.writeString(
                inventory,
                "forbidden_imports = [\"android.* in domain/**\"]\n" + Files.readString(inventory),
            )
            val source = root.resolve("domain/photo/src/commonMain/kotlin/Bad.kt")
            Files.createDirectories(source.parent)
            Files.writeString(source, "package bad\nimport android.foo.Bar\n")
            assertFailsWith<IllegalArgumentException> { ModuleBoundaryVerifier.validate(root) }
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `empty module inventory fails closed`() {
        val root = fixture("feature")
        try {
            Files.writeString(
                root.resolve("config/architecture/module-boundaries.toml"),
                "[[allowed_edge]]\nfrom = \"feature\"\nto = \"domain\"\nscope = \"production\"",
            )
            assertFailsWith<IllegalArgumentException> { ModuleBoundaryVerifier.validate(root) }
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private fun fixture(source: String): java.nio.file.Path {
        val root = Files.createTempDirectory("module-boundaries")
        Files.createDirectories(root.resolve("config/architecture"))
        Files.createDirectories(root.resolve("feature/photo"))
        Files.createDirectories(root.resolve("domain/photo"))
        Files.createDirectories(root.resolve("platform/photo"))
        Files.writeString(
            root.resolve("config/architecture/module-boundaries.toml"),
            """
                [[module]]
                id = "feature"
                path = "feature"

                [[module]]
                id = "domain"
                path = "domain"

                [[module]]
                id = "platform"
                path = "platform"

                [[allowed_edge]]
                from = "feature"
                to = "domain"
                scope = "production"
            """.trimIndent(),
        )
        Files.createFile(root.resolve("${source}/photo/placeholder.txt"))
        return root
    }
}
