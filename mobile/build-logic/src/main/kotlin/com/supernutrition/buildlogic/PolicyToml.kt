package com.supernutrition.buildlogic

/**
 * Small, deliberately scoped parser helpers for the repository's policy TOML.
 *
 * The policy manifests are intentionally data-only and already have a narrow
 * grammar. Keeping the shared extraction rules here prevents validators from
 * silently accepting different spellings of the same policy field.
 */
internal object PolicyToml {
    data class ForbiddenImportRule(
        val importPrefix: String,
        val sourceScope: String? = null,
        val configurationScope: String? = null,
    )

    val requiredManifests = listOf(
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
        "product-lifecycle.toml",
    )

    fun blocks(text: String, name: String): List<String> =
        Regex("(?ms)^\\[\\[$name]]\\s*\\n(.*?)(?=^\\[\\[|\\z)")
            .findAll(text)
            .map { it.groupValues[1] }
            .toList()

    fun section(text: String, name: String): String =
        Regex("(?ms)^\\[${Regex.escape(name)}]\\s*\\n(.*?)(?=^\\[|\\z)")
            .find(text)
            ?.groupValues
            ?.get(1)
            ?: ""

    fun string(text: String, key: String): String? =
        Regex("(?m)^${Regex.escape(key)}\\s*=\\s*\\\"([^\\\"]*)\\\"\\s*$")
            .find(text)
            ?.groupValues
            ?.get(1)

    fun integer(text: String, key: String): Int? =
        Regex("(?m)^${Regex.escape(key)}\\s*=\\s*(\\d+)\\s*$")
            .find(text)
            ?.groupValues
            ?.get(1)
            ?.toInt()

    fun boolean(text: String, key: String): Boolean? =
        Regex("(?m)^${Regex.escape(key)}\\s*=\\s*(true|false)\\s*$")
            .find(text)
            ?.groupValues
            ?.get(1)
            ?.toBoolean()

    fun stringList(text: String, key: String): List<String> =
        Regex("(?m)^${Regex.escape(key)}\\s*=\\s*\\[([^]]*)]")
            .find(text)
            ?.groupValues
            ?.get(1)
            ?.let { values ->
                Regex("\\\"([^\\\"]+)\\\"").findAll(values).map { it.groupValues[1] }.toList()
            }
            ?: emptyList()

    fun forbiddenImportRules(text: String): List<ForbiddenImportRule> =
        stringList(text, "forbidden_imports").map { rawRule ->
            val parts = rawRule.split(" in ", limit = 2)
            require(parts.size == 2) { "Invalid forbidden import rule: $rawRule" }
            val importPrefix = parts[0].trim().removeSuffix("*")
            val scope = parts[1].trim()
            require(importPrefix.isNotEmpty()) { "Forbidden import prefix must not be empty" }
            when {
                scope == "distributable configurations" -> ForbiddenImportRule(
                    importPrefix = importPrefix,
                    configurationScope = "distributable",
                )

                scope.endsWith("/**") -> ForbiddenImportRule(
                    importPrefix = importPrefix,
                    sourceScope = scope.removeSuffix("/**").trimEnd('/'),
                )

                else -> error("Unsupported forbidden import scope: $scope")
            }
        }
}
