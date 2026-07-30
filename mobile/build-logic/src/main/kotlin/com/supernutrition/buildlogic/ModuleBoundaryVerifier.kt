package com.supernutrition.buildlogic

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/** Enforces the dependency graph declared by config/architecture/module-boundaries.toml. */
internal object ModuleBoundaryVerifier {
    private val dependencyPattern = Regex(
        "([A-Za-z][A-Za-z0-9_]*)\\s*\\(\\s*project\\(\\s*\\\"(:[^\\\"]+)\\\"\\s*\\)\\s*\\)",
    )

    fun validate(mobileRoot: Path) {
        val inventory = mobileRoot.resolve("config/architecture/module-boundaries.toml")
        require(Files.isRegularFile(inventory)) { "Missing module-boundary inventory: $inventory" }
        val text = Files.readString(inventory)
        val modules = PolicyToml.blocks(text, "module").map { block ->
            Module(
                id = PolicyToml.string(block, "id") ?: error("Module inventory entry is missing id"),
                path = PolicyToml.string(block, "path") ?: error("Module inventory entry is missing path"),
            )
        }
        val edges = PolicyToml.blocks(text, "allowed_edge").map { block ->
            Edge(
                from = PolicyToml.string(block, "from")?.split('|').orEmpty(),
                to = PolicyToml.string(block, "to")?.split('|').orEmpty(),
                scope = PolicyToml.string(block, "scope") ?: error("Allowed edge is missing scope"),
            )
        }
        require(modules.map { it.id }.distinct().size == modules.size) {
            "Module-boundary inventory contains duplicate module IDs"
        }
        require(edges.isNotEmpty()) { "Module-boundary inventory contains no allowed edges" }

        val moduleFiles = modules.flatMap { module ->
            val moduleRoot = mobileRoot.resolve(module.path.removePrefix("mobile/"))
            if (!Files.isDirectory(moduleRoot)) {
                emptyList()
            } else {
                val files = mutableListOf<Path>()
                Files.walkFileTree(moduleRoot, object : SimpleFileVisitor<Path>() {
                    override fun preVisitDirectory(
                        directory: Path,
                        attributes: BasicFileAttributes,
                    ): FileVisitResult = if (
                        directory != moduleRoot && directory.fileName.toString() in setOf(
                            "build", ".gradle", ".git", "node_modules",
                        )
                    ) {
                        FileVisitResult.SKIP_SUBTREE
                    } else {
                        FileVisitResult.CONTINUE
                    }

                    override fun visitFile(
                        file: Path,
                        attributes: BasicFileAttributes,
                    ): FileVisitResult {
                        if (attributes.isRegularFile && (
                                file.fileName.toString() == "build.gradle.kts" ||
                                    file.fileName.toString().endsWith(".kt")
                            )
                        ) files.add(file)
                        return FileVisitResult.CONTINUE
                    }
                })
                files
            }
        }.distinct()
        val violations = moduleFiles
            .filter { it.fileName.toString() == "build.gradle.kts" }
            .flatMap { source -> dependencies(source, mobileRoot, modules, edges) }
        val forbiddenImportViolations = moduleFiles
            .filter { it.fileName.toString().endsWith(".kt") }
            .flatMap { source -> forbiddenImports(source, mobileRoot, text) }
        val allViolations = violations + forbiddenImportViolations
        require(allViolations.isEmpty()) {
            "Module-boundary violations:\n${allViolations.joinToString("\n") { "- $it" }}"
        }
    }

    private fun dependencies(
        buildFile: Path,
        mobileRoot: Path,
        modules: List<Module>,
        edges: List<Edge>,
    ): List<String> {
        val sourcePath = mobileRoot.relativize(buildFile.parent).toString().replace('\\', '/')
        val source = modules
            .filter {
                val moduleRelativePath = it.path.removePrefix("mobile/")
                sourcePath == moduleRelativePath || sourcePath.startsWith("$moduleRelativePath/")
            }
            .maxByOrNull { it.path.length }
            ?: return emptyList()
        val script = Files.readString(buildFile)
        return dependencyPattern.findAll(script).mapNotNull { match ->
            val configuration = match.groupValues[1]
            val targetPath = match.groupValues[2]
            val targetRelativePath = targetPath
                .removePrefix(":")
                .replace(':', '/')
            val target = modules
                .filter { module ->
                    val moduleRelativePath = module.path.removePrefix("mobile/")
                    targetRelativePath == moduleRelativePath ||
                        targetRelativePath.startsWith("$moduleRelativePath/")
                }
                .maxByOrNull { it.path.length }
                ?: return@mapNotNull "${source.id} -> $targetPath ($configuration): target is not in module inventory"
            val sourceSet = Regex("(?m)([A-Za-z][A-Za-z0-9_]*)\\.dependencies\\s*\\{")
                .findAll(script.substring(0, match.range.first))
                .lastOrNull()
                ?.groupValues
                ?.get(1)
            val scope = if (
                configuration.contains("test", ignoreCase = true) ||
                sourceSet?.endsWith("Test") == true
            ) "test-only" else "production"
            val allowed = edges.any {
                it.scope == scope && source.id in it.from && target.id in it.to
            }
            if (allowed) null else "${source.id} -> ${target.id} ($scope, $configuration)"
        }.toList()
    }

    private fun forbiddenImports(
        sourceFile: Path,
        mobileRoot: Path,
        inventoryText: String,
    ): List<String> {
        val relativeSource = mobileRoot.relativize(sourceFile).toString().replace('\\', '/')
        val rules = PolicyToml.forbiddenImportRules(inventoryText)
        val applicableRules = rules.filter { rule ->
            when {
                rule.sourceScope != null -> {
                    val scope = rule.sourceScope.removePrefix("mobile/")
                    relativeSource == scope || relativeSource.startsWith("$scope/")
                }

                rule.configurationScope == "distributable" ->
                    relativeSource.split('/').let { segments ->
                        val srcIndex = segments.indexOf("src")
                        srcIndex >= 0 && segments.getOrNull(srcIndex + 1)?.endsWith("Test") != true
                    }

                else -> false
            }
        }
        if (applicableRules.isEmpty()) return emptyList()

        return Files.readAllLines(sourceFile).mapIndexedNotNull { index, line ->
            val imported = Regex("^\\s*import\\s+([^\\s]+)").find(line)?.groupValues?.get(1)
                ?: return@mapIndexedNotNull null
            applicableRules.firstOrNull { rule -> imported.startsWith(rule.importPrefix) }?.let { rule ->
                "${mobileRoot.relativize(sourceFile)}:${index + 1}: import $imported matches forbidden ${rule.importPrefix}*"
            }
        }
    }

    private data class Module(val id: String, val path: String)

    private data class Edge(val from: List<String>, val to: List<String>, val scope: String)
}
