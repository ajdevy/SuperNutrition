package com.supernutrition.buildlogic

import java.util.Collections
import java.util.WeakHashMap

/**
 * Small typed parser for the repository's data-only TOML policy manifests.
 *
 * The policy grammar intentionally stays narrow: root/table assignments,
 * strings, integers, booleans, and arrays of those values. Parsing the values
 * structurally keeps validators fail-closed without making build logic depend
 * on a runtime TOML library.
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
        "product-lifecycle.toml",
    )

    fun blocks(text: String, name: String): List<String> =
        parse(text).tables
            .filter { it.array && it.name == name }
            .map(::renderTable)

    fun section(text: String, name: String): String =
        parse(text).tables
            .firstOrNull { !it.array && it.name == name }
            ?.let(::renderTable)
            .orEmpty()

    fun string(text: String, key: String): String? =
        (findValue(parse(text), key) as? Value.Text)?.value

    fun integer(text: String, key: String): Int? =
        (findValue(parse(text), key) as? Value.Number)?.value

    fun boolean(text: String, key: String): Boolean? =
        (findValue(parse(text), key) as? Value.Flag)?.value

    fun stringList(text: String, key: String): List<String> =
        (findValue(parse(text), key) as? Value.ListValue)
            ?.values
            ?.map { (it as? Value.Text)?.value ?: error("Policy value '$key' must contain strings") }
            .orEmpty()

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

    // Validators issue many independent queries against one immutable manifest.
    // Cache the typed representation by source text instead of reparsing each accessor.
    private val documents = Collections.synchronizedMap(WeakHashMap<String, Document>())

    private data class Document(
        val root: LinkedHashMap<String, Value> = linkedMapOf(),
        val tables: MutableList<Table> = mutableListOf(),
    )

    private data class Table(
        val name: String,
        val array: Boolean,
        val values: LinkedHashMap<String, Value> = linkedMapOf(),
    )

    private sealed interface Value {
        data class Text(val value: String) : Value
        data class Number(val value: Int) : Value
        data class Flag(val value: Boolean) : Value
        data class ListValue(val values: List<Value>) : Value
    }

    private fun findValue(document: Document, key: String): Value? =
        document.root[key] ?: document.tables.asSequence()
            .mapNotNull { it.values[key] }
            .firstOrNull()

    private fun parse(text: String): Document = documents[text] ?: synchronized(documents) {
        documents[text] ?: parseDocument(text).also { documents[text] = it }
    }

    private fun parseDocument(text: String): Document {
        val document = Document()
        var current: Table? = null
        val pending = StringBuilder()
        var bracketDepth = 0
        var inString = false
        var escaped = false

        fun resetValueState() {
            pending.clear()
            bracketDepth = 0
            inString = false
            escaped = false
        }

        fun consumeAssignment() {
            val assignment = pending.toString().trim()
            val separator = findTopLevelEquals(assignment)
            require(separator >= 0) { "Policy assignment is missing '=': $assignment" }
            val key = assignment.substring(0, separator).trim()
            require(key.isNotEmpty()) { "Policy assignment key must not be empty" }
            val value = parseValue(assignment.substring(separator + 1).trim())
            (current?.values ?: document.root)[key] = value
            resetValueState()
        }

        text.lineSequence().forEach { rawLine ->
            val line = stripComment(rawLine).trim()
            if (line.isEmpty()) return@forEach

            if (pending.isEmpty() && line.startsWith("[[") && line.endsWith("]]")) {
                val name = line.substring(2, line.length - 2).trim()
                require(name.isNotEmpty()) { "Policy array-table name must not be empty" }
                current = Table(name = name, array = true).also(document.tables::add)
                return@forEach
            }
            if (pending.isEmpty() && line.startsWith("[") && line.endsWith("]")) {
                val name = line.substring(1, line.length - 1).trim()
                require(name.isNotEmpty()) { "Policy table name must not be empty" }
                current = Table(name = name, array = false).also(document.tables::add)
                return@forEach
            }

            if (pending.isNotEmpty()) pending.append(' ')
            pending.append(line)
            line.forEach { character ->
                if (escaped) {
                    escaped = false
                } else if (character == '\\' && inString) {
                    escaped = true
                } else if (character == '"') {
                    inString = !inString
                } else if (!inString) {
                    when (character) {
                        '[' -> bracketDepth++
                        ']' -> bracketDepth--
                    }
                    require(bracketDepth >= 0) { "Policy array closes before it opens" }
                }
            }
            require(!inString && !escaped) { "Policy strings must be closed on one line" }
            if (bracketDepth == 0 && !inString) consumeAssignment()
        }

        require(pending.isEmpty()) { "Policy contains an unterminated assignment: $pending" }
        return document
    }

    private fun parseValue(raw: String): Value {
        require(raw.isNotEmpty()) { "Policy value must not be empty" }
        return when {
            raw.startsWith('"') -> Value.Text(parseString(raw))
            raw == "true" -> Value.Flag(true)
            raw == "false" -> Value.Flag(false)
            raw.startsWith('[') && raw.endsWith(']') -> Value.ListValue(parseArray(raw))
            else -> Value.Number(raw.toIntOrNull() ?: error("Unsupported policy value: $raw"))
        }
    }

    private fun parseString(raw: String): String {
        require(raw.length >= 2 && raw.last() == '"') { "Policy string is not closed: $raw" }
        val result = StringBuilder()
        var escaped = false
        raw.substring(1, raw.length - 1).forEach { character ->
            if (escaped) {
                result.append(
                    when (character) {
                        '"' -> '"'
                        '\\' -> '\\'
                        'n' -> '\n'
                        'r' -> '\r'
                        't' -> '\t'
                        else -> error("Unsupported policy string escape: \\$character")
                    },
                )
                escaped = false
            } else if (character == '\\') {
                escaped = true
            } else {
                result.append(character)
            }
        }
        require(!escaped) { "Policy string ends with an escape" }
        return result.toString()
    }

    private fun parseArray(raw: String): List<Value> {
        val content = raw.substring(1, raw.length - 1).trim()
        if (content.isEmpty()) return emptyList()
        return splitArrayElements(content).map(::parseValue)
    }

    private fun splitArrayElements(content: String): List<String> {
        val values = mutableListOf<String>()
        val element = StringBuilder()
        var inString = false
        var escaped = false
        var nestedDepth = 0

        fun addElement() {
            val value = element.toString().trim()
            require(value.isNotEmpty()) { "Policy array contains an empty value" }
            values += value
            element.clear()
        }

        content.forEach { character ->
            if (escaped) {
                escaped = false
                element.append(character)
            } else if (character == '\\' && inString) {
                escaped = true
                element.append(character)
            } else if (character == '"') {
                inString = !inString
                element.append(character)
            } else if (!inString && character == '[') {
                nestedDepth++
                element.append(character)
            } else if (!inString && character == ']') {
                nestedDepth--
                require(nestedDepth >= 0) { "Nested policy array closes before it opens" }
                element.append(character)
            } else if (!inString && nestedDepth == 0 && character == ',') {
                addElement()
            } else {
                element.append(character)
            }
        }
        require(!inString && !escaped && nestedDepth == 0) { "Policy array is malformed" }
        if (element.toString().trim().isNotEmpty()) {
            addElement()
        } else {
            require(values.isNotEmpty()) { "Policy array contains an empty value" }
        }
        return values
    }

    private fun stripComment(line: String): String {
        var inString = false
        var escaped = false
        line.forEachIndexed { index, character ->
            if (escaped) {
                escaped = false
            } else if (character == '\\' && inString) {
                escaped = true
            } else if (character == '"') {
                inString = !inString
            } else if (!inString && character == '#') {
                return line.substring(0, index)
            }
        }
        return line
    }

    private fun findTopLevelEquals(value: String): Int {
        var inString = false
        var escaped = false
        value.forEachIndexed { index, character ->
            if (escaped) {
                escaped = false
            } else if (character == '\\' && inString) {
                escaped = true
            } else if (character == '"') {
                inString = !inString
            } else if (!inString && character == '=') {
                return index
            }
        }
        return -1
    }

    private fun renderTable(table: Table): String = table.values.entries.joinToString("\n") { (key, value) ->
        "$key = ${renderValue(value)}"
    }

    private fun renderValue(value: Value): String = when (value) {
        is Value.Text -> "\"${value.value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        is Value.Number -> value.value.toString()
        is Value.Flag -> value.value.toString()
        is Value.ListValue -> value.values.joinToString(prefix = "[", postfix = "]", separator = ", ", transform = ::renderValue)
    }
}
