[
  {
    "location": "mobile/build-logic/src/main/kotlin/com/supernutrition/buildlogic/PolicyToml.kt:100-104",
    "trigger_condition": "forbidden_imports appears after a table header",
    "guard_snippet": "require(document.root.containsKey(\"forbidden_imports\")) { \"forbidden_imports must be root\" }",
    "potential_consequence": "Forbidden domain imports are silently skipped"
  },
  {
    "location": "mobile/build-logic/src/main/kotlin/com/supernutrition/buildlogic/PolicyToml.kt:61-92",
    "trigger_condition": "A required scalar or list key exists only inside a table",
    "guard_snippet": "findValue(document, key)",
    "potential_consequence": "Typed and legacy accessors return different policy values"
  },
  {
    "location": "mobile/build-logic/src/main/kotlin/com/supernutrition/buildlogic/PolicyToml.kt:42-45,344-353",
    "trigger_condition": "A table string contains an escaped newline, carriage return, or tab",
    "guard_snippet": "consumeTableDirectlyOrEscapeControlCharacters(renderValue(value))",
    "potential_consequence": "Remaining round-trip callers reject valid policy text"
  },
  {
    "location": "mobile/build-logic/src/main/kotlin/com/supernutrition/buildlogic/CoverageVerifiers.kt:21-31",
    "trigger_condition": "Coverage contains no denominator blocks or a renamed denominator section",
    "guard_snippet": "require(PolicyToml.blocks(coverage, \"denominator\").isNotEmpty())",
    "potential_consequence": "Native evidence gate authorizes an unbound coverage claim"
  },
  {
    "location": "mobile/build-logic/src/main/kotlin/com/supernutrition/buildlogic/ModuleBoundaryVerifier.kt:15-32",
    "trigger_condition": "Module inventory contains edges but no module entries",
    "guard_snippet": "require(modules.isNotEmpty()) { \"Module inventory is empty\" }",
    "potential_consequence": "Boundary scan passes without inspecting module files"
  },
  {
    "location": "mobile/build-logic/src/main/kotlin/com/supernutrition/buildlogic/PolicyToml.kt:167-175",
    "trigger_condition": "A root key is assigned twice",
    "guard_snippet": "require(!target.containsKey(key)) { \"Duplicate policy key: $key\" }",
    "potential_consequence": "Last assignment silently overrides fail-closed policy"
  },
  {
    "location": "mobile/build-logic/src/main/kotlin/com/supernutrition/buildlogic/PolicyToml.kt:167-175",
    "trigger_condition": "A key is assigned twice within one table",
    "guard_snippet": "require(!current.values.containsKey(key)) { \"Duplicate table key: $key\" }",
    "potential_consequence": "Conflicting table values are hidden by assignment order"
  },
  {
    "location": "mobile/build-logic/src/main/kotlin/com/supernutrition/buildlogic/PolicyToml.kt:55-56,188-192",
    "trigger_condition": "Two regular tables share one name",
    "guard_snippet": "require(document.tables.none { !it.array && it.name == name })",
    "potential_consequence": "section returns the first table and ignores later policy"
  },
  {
    "location": "mobile/build-logic/src/main/kotlin/com/supernutrition/buildlogic/CoverageVerifiers.kt:38-40",
    "trigger_condition": "Two evidence blocks share an ID and the first record succeeds",
    "guard_snippet": "require(evidenceIds.size == evidenceIds.toSet().size)",
    "potential_consequence": "Invalid duplicate evidence remains invisible to the gate"
  },
  {
    "location": "mobile/build-logic/src/main/kotlin/com/supernutrition/buildlogic/PolicyToml.kt:48-49,129-137",
    "trigger_condition": "A caller mutates cached document data during parallel validation",
    "guard_snippet": "return ImmutableDocument(root.toMap(), tables.map(Table::immutable))",
    "potential_consequence": "Concurrent validation observes races or partial policy state"
  }
]
