package com.supernutrition.buildlogic

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/** Enforces the dependency graph declared by config/architecture/module-boundaries.toml. */
internal object ModuleBoundaryVerifier {
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
                            "build", ".gradle", ".kotlin", ".git", "node_modules", "out", "DerivedData",
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
        val violations = mutableListOf<String>()
        val sourceSetScopes = mutableListOf<SourceSetScope>()
        var braceDepth = 0
        var multilineDependencyConfiguration: String? = null
        Files.readAllLines(buildFile).forEach { rawLine ->
            val line = stripComment(rawLine)
            parseProjectDependency(line, multilineDependencyConfiguration)?.let { dependency ->
            val configuration = dependency.configuration
            val targetPath = dependency.targetPath
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
                ?: run {
                    violations += "${source.id} -> $targetPath ($configuration): target is not in module inventory"
                    return@let
                }
            val sourceSet = sourceSetScopes.lastOrNull()?.name
            val scope = if (
                configuration.contains("test", ignoreCase = true) ||
                sourceSet?.endsWith("Test") == true
            ) "test-only" else "production"
            val allowed = edges.any {
                it.scope == scope && source.id in it.from && target.id in it.to
            }
            if (!allowed) violations += "${source.id} -> ${target.id} ($scope, $configuration)"
            }

            parseSourceSet(line)?.let { name ->
                sourceSetScopes += SourceSetScope(braceDepth + 1, name)
            }
            multilineDependencyConfiguration = if (line.contains("project(")) {
                null
            } else {
                line.trim()
                    .takeIf { it.endsWith('(') }
                    ?.dropLast(1)
                    ?.trim()
                    ?.substringAfterLast('.')
                    ?.takeIf { it.isNotEmpty() && it.all { char -> char.isLetterOrDigit() || char == '_' } }
            }
            braceDepth += braceDelta(line)
            sourceSetScopes.removeAll { it.depth > braceDepth }
        }
        return violations
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
            val trimmed = line.trim()
            if (!trimmed.startsWith("import ")) return@mapIndexedNotNull null
            val imported = trimmed.removePrefix("import ").trim().substringBefore(" //").trim()
            applicableRules.firstOrNull { rule -> imported.startsWith(rule.importPrefix) }?.let { rule ->
                "${mobileRoot.relativize(sourceFile)}:${index + 1}: import $imported matches forbidden ${rule.importPrefix}*"
            }
        }
    }

    private data class ProjectDependency(val configuration: String, val targetPath: String)

    private data class SourceSetScope(val depth: Int, val name: String)

    private fun parseProjectDependency(line: String, fallbackConfiguration: String?): ProjectDependency? {
        val projectMarker = line.indexOf("project(")
        if (projectMarker < 0) return null
        val prefix = line.substring(0, projectMarker).trim()
        val configuration = prefix
            .substringBefore('(')
            .trim()
            .substringAfterLast(' ')
            .substringAfterLast('.')
            .ifEmpty { fallbackConfiguration.orEmpty() }
        if (configuration.isEmpty()) return null
        val valueStart = projectMarker + "project(".length
        val quote = line.getOrNull(valueStart) ?: return null
        if (quote != '"' && quote != '\'') return null
        val valueEnd = line.indexOf(quote, valueStart + 1)
        if (valueEnd <= valueStart + 1) return null
        val targetPath = line.substring(valueStart + 1, valueEnd)
        return targetPath.takeIf { it.startsWith(":") }?.let { ProjectDependency(configuration, it) }
    }

    private fun parseSourceSet(line: String): String? {
        val marker = line.indexOf(".dependencies")
        if (marker <= 0 || !line.substring(marker).contains('{')) return null
        val name = line.substring(0, marker).trim().substringAfterLast(' ').substringAfterLast('.')
        return name.takeIf { it.isNotEmpty() && it.all { char -> char.isLetterOrDigit() || char == '_' } }
    }

    private fun braceDelta(line: String): Int = line.count { it == '{' } - line.count { it == '}' }

    private fun stripComment(line: String): String {
        var quoted = false
        var escaped = false
        line.forEachIndexed { index, char ->
            if (escaped) escaped = false
            else if (char == '\\' && quoted) escaped = true
            else if (char == '"') quoted = !quoted
            else if (!quoted && char == '/' && line.getOrNull(index + 1) == '/') return line.substring(0, index)
        }
        return line
    }

    private data class Module(val id: String, val path: String)

    private data class Edge(val from: List<String>, val to: List<String>, val scope: String)
}
