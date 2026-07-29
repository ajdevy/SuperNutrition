package com.supernutrition.buildlogic

import java.nio.file.Files
import java.nio.file.Path

/** Enforces the dependency graph declared by config/architecture/module-boundaries.toml. */
internal object ModuleBoundaryVerifier {
    private val dependencyPattern = Regex(
        "(?m)^\\s*([A-Za-z][A-Za-z0-9_]*)\\s*\\([^\\n]*project\\(\\\"(:[^\\\"]+)\\\"\\)",
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

        val violations = Files.walk(mobileRoot).use { paths ->
            paths.filter { it.fileName.toString() == "build.gradle.kts" }
                .filter { !it.toString().contains("/build/") }
                .flatMap { source -> dependencies(source, mobileRoot, modules, edges).stream() }
                .toList()
        }
        val forbiddenImportViolations = Files.walk(mobileRoot).use { paths ->
            paths.filter { it.fileName.toString().endsWith(".kt") }
                .filter { !it.toString().contains("/build/") }
                .flatMap { source -> forbiddenImports(source, mobileRoot, text).stream() }
                .toList()
        }
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
                sourceSet?.contains("test", ignoreCase = true) == true
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
        val rules = PolicyToml.stringList(inventoryText, "forbidden_imports").mapNotNull { rule ->
            val match = Regex("^(.+?)\\s+in\\s+(.+)$").matchEntire(rule) ?: return@mapNotNull null
            val importPattern = match.groupValues[1].removeSuffix("*")
            val scope = match.groupValues[2]
                .removePrefix("mobile/")
                .removeSuffix("/**")
                .trimEnd('/')
            importPattern to scope
        }
        if (rules.isEmpty() || !rules.any { (_, scope) ->
                relativeSource == scope || relativeSource.startsWith("$scope/")
            }) return emptyList()

        return Files.readAllLines(sourceFile).mapIndexedNotNull { index, line ->
            val imported = Regex("^\\s*import\\s+([^\\s]+)").find(line)?.groupValues?.get(1)
                ?: return@mapIndexedNotNull null
            rules.firstOrNull { (prefix, scope) ->
                (relativeSource == scope || relativeSource.startsWith("$scope/")) &&
                    imported.startsWith(prefix)
            }?.let { (prefix, _) ->
                "${mobileRoot.relativize(sourceFile)}:${index + 1}: import $imported matches forbidden $prefix*"
            }
        }
    }

    private data class Module(val id: String, val path: String)

    private data class Edge(val from: List<String>, val to: List<String>, val scope: String)
}
