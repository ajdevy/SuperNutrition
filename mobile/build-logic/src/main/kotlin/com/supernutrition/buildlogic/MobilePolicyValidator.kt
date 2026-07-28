package com.supernutrition.buildlogic

import java.nio.file.Files
import java.nio.file.FileVisitResult
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Read-only validator for the fail-closed mobile scaffold policy.
 *
 * This deliberately validates the bootstrap schema without attempting to
 * activate the product lifecycle. The active-state validator belongs to the
 * later product and evidence slices.
 */
object MobilePolicyValidator {
    private val requiredManifests = listOf(
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

    fun validate(mobileRoot: Path) {
        require(Files.isDirectory(mobileRoot)) { "Missing mobile root: $mobileRoot" }

        val files = requiredManifests.map { relative ->
            val path = mobileRoot.resolve(relative)
            require(Files.isRegularFile(path)) { "Missing policy manifest: $relative" }
            relative to Files.readString(path)
        }

        files.filter { it.first.endsWith(".toml") }.forEach { (relative, text) ->
            validateCommonManifest(relative, text)
        }

        val moduleMatrix = files.single { it.first == "config/quality/module-matrix.toml" }.second
        require(moduleMatrix.contains("[[unit]]")) {
            "The scaffold product inventory must contain policy units"
        }
        require(moduleMatrix.contains("undeclared_executable_policy = \"fail\"")) {
            "The module inventory must fail on undeclared executables"
        }

        val profile = files.single { it.first == "config/quality/ui-evidence-profile.toml" }.second
        val scenarios = files.single { it.first == "config/quality/positive-scenarios.toml" }.second
        val journeys = files.single { it.first == "config/quality/journeys.toml" }.second
        when {
            profile.contains("profile = \"bootstrap\"") -> {
                require(scenarios.contains("profile = \"bootstrap\"")) {
                    "Bootstrap scenarios must use the bootstrap profile"
                }
                require(journeys.contains("profile = \"bootstrap\"")) {
                    "Bootstrap journeys must use the bootstrap profile"
                }
                require(!profile.contains("profile = \"photo-analysis\"")) {
                    "Bootstrap profile cannot activate product journeys"
                }
            }
            profile.contains("profile = \"photo-analysis\"") -> {
                require(scenarios.contains("profile = \"photo-analysis\"")) {
                    "Photo-analysis scenarios must bind the transitioned profile"
                }
                require(journeys.contains("profile = \"photo-analysis\"")) {
                    "Photo-analysis journeys must bind the transitioned profile"
                }
                require((extractInt(scenarios, "scenario_count") ?: 0) > 0) {
                    "Photo-analysis transition requires a non-empty scenario catalog"
                }
                require((extractInt(journeys, "journey_count") ?: 0) > 0) {
                    "Photo-analysis transition requires a non-empty journey catalog"
                }
                require(Files.exists(mobileRoot.resolve("feature/photo-analysis"))) {
                    "Photo-analysis profile requires the feature source"
                }
                require(profile.contains("ios_simulator_task = \"iosSimulatorArm64Test\"")) {
                    "Photo-analysis profile must keep iosSimulatorArm64Test as the canonical Apple task"
                }
                require(profile.contains("local_compatibility_task = \"iosX64Test\"")) {
                    "Photo-analysis profile must declare the Intel x64 compatibility task separately"
                }
                require(profile.contains("local_compatibility_only = true")) {
                    "Intel x64 evidence must never be treated as canonical Apple evidence"
                }
            }
            else -> error("Unknown mobile UI evidence profile")
        }

        val coverage = files.single { it.first == "config/quality/coverage.toml" }.second
        require(coverage.contains("quality_claim = false")) {
            "Scaffold coverage must not make a product quality claim"
        }
        require(coverage.contains("empty_or_zero_denominator = \"fail\"")) {
            "Zero coverage denominators must fail closed"
        }

        val lifecycle = files.single { it.first == "product-lifecycle.toml" }.second
        require(lifecycle.contains("transition = \"blocked\"")) {
            "The scaffold lifecycle must remain blocked until activation evidence is complete"
        }
        require(lifecycle.contains("production_compose_host = true")) {
            "The lifecycle record must prove that the production Compose host exists"
        }
        listOf(
            "all_six_jobs_success = false",
            "trusted_ruleset_audit = false",
            "canonical_ios_simulator_arm64 = false",
            "exact_xcode_toolchain = false",
            "active_inventory_and_denominators = false",
        ).forEach { gate ->
            require(lifecycle.contains(gate)) {
                "Activation gate must remain fail-closed: $gate"
            }
        }

        validateGradleMemoryPolicy(mobileRoot)
        validateQualityToolCatalog(mobileRoot)
        validateDependencyLockPolicy(mobileRoot)

        val stability = files.single { it.first == "config/compose-stability.conf" }.second
        require(!Regex("(^|[.=])\\s*\\*", RegexOption.MULTILINE).containsMatchIn(stability)) {
            "Wildcard Compose stability entries are forbidden"
        }

        val identity = files.single { it.first == "config/product-identity.toml" }.second
        require(identity.contains("canonical_name = \"SuperNutrition\"")) {
            "Installed product identity must be SuperNutrition"
        }
        require(identity.contains("store_listing_title = \"SuperNutrition AI nutritionist\"")) {
            "Store listing title must be SuperNutrition AI nutritionist"
        }
        require(identity.contains("acceptance_android_application_id = \"com.supernutrition.android.uiAcceptance\"")) {
            "Android acceptance identity must remain unique"
        }
        require(identity.contains("acceptance_ios_bundle_identifier = \"com.supernutrition.ios.uiacceptance\"")) {
            "iOS acceptance identity must remain unique"
        }
        val androidStrings = mobileRoot.resolve("apps/androidApp/src/main/res/values/strings.xml")
        require(Files.isRegularFile(androidStrings) && Files.readString(androidStrings).contains(">SuperNutrition<")) {
            "Android visible identity must match the canonical manifest"
        }
        val iosInfo = mobileRoot.resolve("apps/iosApp/Info.plist")
        require(Files.isRegularFile(iosInfo) && Files.readString(iosInfo).contains("<string>SuperNutrition</string>")) {
            "iOS visible identity must match the canonical manifest"
        }

        val interaction = files.single { it.first == "config/quality/interaction-policy.toml" }.second
        require(interaction.contains("defaultLocale")) {
            "Interaction policy must declare the closed default allowlist"
        }
        require(interaction.contains("allowlist = [\"defaultLocale\", \"measurementSystem\", \"catalogProposal\", \"standardServing\"]")) {
            "Interaction policy allowlist must remain closed"
        }
        require(interaction.contains("remote_upload = \"never-preselected\"")) {
            "Remote consent must never be a defaultable interaction field"
        }

        validateManifestGraph(mobileRoot, files)
    }

    private fun validateGradleMemoryPolicy(mobileRoot: Path) {
        val productProperties = mobileRoot.resolve("gradle.properties")
        val buildLogicProperties = mobileRoot.resolve("build-logic/gradle.properties")
        require(Files.isRegularFile(productProperties)) {
            "Missing product Gradle memory profile: gradle.properties"
        }
        require(Files.isRegularFile(buildLogicProperties)) {
            "Missing build-logic Gradle memory profile: build-logic/gradle.properties"
        }

        val product = Files.readString(productProperties)
        requirePropertyContains(product, "org.gradle.jvmargs", "-Xms256m")
        requirePropertyContains(product, "org.gradle.jvmargs", "-Xmx4g")
        requirePropertyContains(product, "org.gradle.jvmargs", "-XX:MaxMetaspaceSize=1536m")
        requirePropertyContains(product, "org.gradle.jvmargs", "-XX:+UseG1GC")
        requireProperty(product, "org.gradle.workers.max", "4")
        requireProperty(product, "org.gradle.daemon.idletimeout", "1800000")
        requireProperty(product, "org.gradle.configuration-cache", "true")
        requireProperty(product, "org.gradle.configuration-cache.problems", "fail")
        requireProperty(product, "org.gradle.vfs.watch", "true")
        requirePropertyContains(product, "kotlin.daemon.jvmargs", "-Xms256m")
        requirePropertyContains(product, "kotlin.daemon.jvmargs", "-Xmx2g")
        requirePropertyContains(product, "kotlin.daemon.jvmargs", "-XX:MaxMetaspaceSize=1g")
        requirePropertyContains(product, "kotlin.daemon.jvmargs", "-XX:+UseG1GC")
        requireProperty(product, "kotlin.daemon.useFallbackStrategy", "false")

        val buildLogic = Files.readString(buildLogicProperties)
        requirePropertyContains(buildLogic, "org.gradle.jvmargs", "-Xms128m")
        requirePropertyContains(buildLogic, "org.gradle.jvmargs", "-Xmx1g")
        requirePropertyContains(buildLogic, "org.gradle.jvmargs", "-XX:MaxMetaspaceSize=512m")
        requirePropertyContains(buildLogic, "org.gradle.jvmargs", "-XX:+UseG1GC")
        requireProperty(buildLogic, "org.gradle.daemon.idletimeout", "1800000")
        requireProperty(buildLogic, "org.gradle.workers.max", "2")
        requireProperty(buildLogic, "org.gradle.configuration-cache", "true")
        requireProperty(buildLogic, "org.gradle.configuration-cache.problems", "fail")
        requireProperty(buildLogic, "org.gradle.vfs.watch", "true")
        requirePropertyContains(buildLogic, "kotlin.daemon.jvmargs", "-Xms128m")
        requirePropertyContains(buildLogic, "kotlin.daemon.jvmargs", "-Xmx768m")
        requirePropertyContains(buildLogic, "kotlin.daemon.jvmargs", "-XX:MaxMetaspaceSize=512m")
        requirePropertyContains(buildLogic, "kotlin.daemon.jvmargs", "-XX:+UseG1GC")
        requireProperty(buildLogic, "kotlin.daemon.useFallbackStrategy", "false")
    }

    private fun validateQualityToolCatalog(mobileRoot: Path) {
        val catalogPath = mobileRoot.resolve("gradle/libs.versions.toml")
        require(Files.isRegularFile(catalogPath)) {
            "Missing pinned quality-tool catalog: gradle/libs.versions.toml"
        }
        val catalog = Files.readString(catalogPath)
        listOf(
            "detekt = \"2.0.0-alpha.5\"",
            "kover = \"0.9.8\"",
            "dev.detekt:detekt-gradle-plugin",
            "dev.detekt:detekt-rules-ktlint-wrapper",
            "id = \"dev.detekt\"",
            "id = \"org.jetbrains.kotlinx.kover\"",
        ).forEach { expected ->
            require(catalog.contains(expected)) {
                "Quality-tool catalog must contain $expected"
            }
        }
        require(!catalog.contains("io.gitlab.arturbosch.detekt")) {
            "Legacy Detekt coordinates are forbidden"
        }

        val detektConfig = mobileRoot.resolve("config/detekt/detekt.yml")
        require(Files.isRegularFile(detektConfig)) {
            "Missing pinned Detekt configuration: config/detekt/detekt.yml"
        }
        val config = Files.readString(detektConfig)
        require(config.contains("validation: true") && config.contains("active: true")) {
            "Detekt configuration must enable validation and ktlint rules"
        }
    }

    private fun requireProperty(text: String, key: String, expected: String) {
        require(Regex("(?m)^${Regex.escape(key)}\\s*=\\s*${Regex.escape(expected)}\\s*$").containsMatchIn(text)) {
            "$key must be set to $expected in the Gradle memory profile"
        }
    }

    private fun requirePropertyContains(text: String, key: String, expected: String) {
        val value = Regex("(?m)^${Regex.escape(key)}\\s*=\\s*(.*)$")
            .find(text)
            ?.groupValues
            ?.get(1)
        require(value?.contains(expected) == true) {
            "$key must contain $expected in the Gradle memory profile"
        }
    }

    private fun validateDependencyLockPolicy(mobileRoot: Path) {
        val settingsLockfile = mobileRoot.resolve("settings-gradle.lockfile")
        require(Files.isRegularFile(settingsLockfile) && Files.size(settingsLockfile) > 0) {
            "Missing Gradle settings lockfile: settings-gradle.lockfile"
        }
        val projectDirectories = findProjectDirectories(mobileRoot)
        require(projectDirectories.isNotEmpty()) {
            "No Gradle subprojects were found for dependency-lock validation"
        }
        projectDirectories.forEach { directory ->
            val lockfile = directory.resolve("gradle.lockfile")
            require(Files.isRegularFile(lockfile) && Files.size(lockfile) > 0) {
                "Missing dependency lockfile for Gradle project: ${mobileRoot.relativize(directory)}"
            }
        }
    }

    /**
     * Walk only source/configuration trees. Gradle and Kotlin may delete and
     * recreate generated output while a separate quality task is running; a
     * policy scan must not race those caches or treat generated files as
     * independent Gradle projects.
     */
    private fun findProjectDirectories(mobileRoot: Path): List<Path> {
        val projectDirectories = linkedSetOf<Path>()
        val generatedDirectories = setOf("build", ".gradle", ".git", "graphify-out")
        Files.walkFileTree(
            mobileRoot,
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(
                    dir: Path,
                    attrs: BasicFileAttributes,
                ): FileVisitResult = if (
                    dir != mobileRoot && dir.fileName.toString() in generatedDirectories
                ) {
                    FileVisitResult.SKIP_SUBTREE
                } else {
                    FileVisitResult.CONTINUE
                }

                override fun visitFile(
                    file: Path,
                    attrs: BasicFileAttributes,
                ): FileVisitResult {
                    if (
                        file.parent != mobileRoot &&
                        file.fileName.toString() in setOf("build.gradle.kts", "build.gradle")
                    ) {
                        projectDirectories.add(file.parent)
                    }
                    return FileVisitResult.CONTINUE
                }
            },
        )
        return projectDirectories.toList()
    }

    private fun validateManifestGraph(mobileRoot: Path, files: List<Pair<String, String>>) {
        val evidencePath = "config/quality/evidence-bindings.toml"
        val boundPaths = requiredManifests.filterNot { it == evidencePath }.sorted()
        val evidence = files.single { it.first == evidencePath }.second
        require(extractInt(evidence, "manifest_count") == boundPaths.size) {
            "Evidence binding manifest_count must cover every policy manifest"
        }
        require(extractString(evidence, "manifest_digest") == digest(mobileRoot, boundPaths)) {
            "Policy manifest digest is stale; regenerate the read-only evidence binding"
        }

        val moduleMatrix = files.single { it.first == "config/quality/module-matrix.toml" }.second
        require(moduleMatrix.contains("ios_task = \"iosSimulatorArm64Test\"")) {
            "Module inventory must keep iosSimulatorArm64Test as the canonical Apple task"
        }
        require(moduleMatrix.contains("local_compatibility_task = \"iosX64Test\"")) {
            "Module inventory must declare the Intel x64 compatibility task"
        }
        require(moduleMatrix.contains("local_compatibility_only = true")) {
            "Module inventory must not promote Intel x64 compatibility evidence"
        }
        val units = blocks(moduleMatrix, "unit")
        require(units.isNotEmpty()) { "Module inventory must contain at least one unit" }
        require(units.mapNotNull { extractString(it, "id") }.distinct().size == units.size) {
            "Module inventory contains duplicate unit IDs"
        }
        units.forEach { block ->
            val path = extractString(block, "path") ?: error("Inventory unit is missing path")
            require(path.startsWith("mobile/")) { "Inventory unit escapes the mobile root: $path" }
            require(Files.exists(mobileRoot.parent.resolve(path))) {
                "Inventory unit path does not exist: $path"
            }
        }

        val productionUnitIds = units
            .filter { extractBoolean(it, "production") == true }
            .mapNotNull { extractString(it, "id") }
            .toSet()
        val coverage = files.single { it.first == "config/quality/coverage.toml" }.second
        require(extractInt(coverage, "production_source_count") == productionUnitIds.size) {
            "Coverage production_source_count must match the production inventory"
        }
        val denominators = blocks(coverage, "denominator")
        require(extractInt(coverage, "denominator_count") == denominators.size) {
            "Coverage denominator_count must match its entries"
        }
        val denominatorUnitIds = denominators.mapNotNull { extractString(it, "unit_id") }.toSet()
        require(denominatorUnitIds == productionUnitIds && denominators.size == productionUnitIds.size) {
            "Every production unit must have exactly one coverage denominator"
        }
        val nativeCoverageEvidenceIds = denominators.flatMap { block ->
            val coverageKind = extractString(block, "coverage_kind")
            require(coverageKind == "jvm" || coverageKind == "native-equivalent") {
                "Coverage denominator must declare jvm or native-equivalent coverage_kind"
            }
            val evidenceIds = extractStringList(block, "evidence_ids")
            if (coverageKind == "native-equivalent") {
                require(evidenceIds.isNotEmpty()) {
                    "Native-equivalent coverage must declare evidence_ids"
                }
            }
            evidenceIds
        }.toSet()
        denominators.forEach { block ->
            require(extractString(block, "source_path")?.startsWith("mobile/") == true) {
                "Coverage denominator source must remain inside mobile/: ${extractString(block, "source_path")}"
            }
            require(!extractString(block, "test_task").isNullOrBlank()) {
                "Coverage denominator must declare a test task"
            }
            require(extractInt(block, "line_minimum_percent") == 85)
            require(extractInt(block, "branch_minimum_percent") == 85)
            require(extractInt(block, "changed_line_minimum_percent") == 100)
            require(extractInt(block, "changed_branch_minimum_percent") == 100)
            require(extractString(block, "status") == "pending") {
                "Scaffold coverage denominators must not make an active claim"
            }
        }

        val nativeGaps = files.single { it.first == "config/quality/native-gaps.toml" }.second
        val gapBlocks = blocks(nativeGaps, "gap")
        val gapIds = gapBlocks.mapNotNull { extractString(it, "id") }.toSet()
        require(gapIds.size == gapBlocks.size) { "Native-gap inventory contains duplicate IDs" }
        require(gapBlocks.all { (extractInt(it, "max_age_days") ?: 0) > 0 }) {
            "Native-gap evidence must define a positive freshness window"
        }
        require(nativeCoverageEvidenceIds.all { it in gapIds }) {
            "Coverage denominator references an orphaned native gap"
        }
        gapBlocks.filter { extractString(it, "platform") == "ios" &&
            extractString(it, "id")?.startsWith("photo-ios-") == true
        }.forEach { block ->
            require(extractString(block, "required_capability") == "physical-ios-device") {
                "iOS photo native gaps must require a physical iOS device"
            }
            require(extractBoolean(block, "compatibility_only") == true) {
                "Intel iOS compatibility gaps must be explicitly non-canonical"
            }
            require(!extractString(block, "compatibility_task").isNullOrBlank()) {
                "iOS photo native gaps must declare their compatibility task"
            }
        }

        val platformContracts = files.single { it.first == "config/quality/platform-contracts.toml" }.second
        val contractBlocks = blocks(platformContracts, "contract")
        require(contractBlocks.isNotEmpty()) { "Platform contract inventory must be nonempty" }
        require(extractInt(platformContracts, "contract_count") == contractBlocks.size) {
            "Platform contract count must match its entries"
        }
        require(contractBlocks.mapNotNull { extractString(it, "id") }.distinct().size == contractBlocks.size) {
            "Platform contract inventory contains duplicate IDs"
        }
        contractBlocks.forEach { block ->
            val gapId = extractString(block, "native_gap_id")
            require(gapId != null && gapId in gapIds) {
                "Platform contract references an orphaned native gap: $gapId"
            }
        }

        val scenarios = files.single { it.first == "config/quality/positive-scenarios.toml" }.second
        val journeys = files.single { it.first == "config/quality/journeys.toml" }.second
        Regex("native_gap_ids\\s*=\\s*\\[([^]]*)]").findAll(scenarios).flatMap { match ->
            Regex("\"([^\"]+)\"").findAll(match.groupValues[1]).map { it.groupValues[1] }
        }.forEach { gapId ->
            require(gapId in gapIds) { "Scenario references an orphaned native gap: $gapId" }
        }

        val accessibilityContractIds = contractBlocks
            .filter { extractString(it, "id")?.contains("accessibility") == true }
            .mapNotNull { extractString(it, "id") }
            .toSet()
        val supportedLocales = extractStringList(platformContracts, "supported_locales")
        require(supportedLocales.isNotEmpty()) {
            "Platform contract inventory must declare supported locales"
        }
        val accessibilityModes = extractStringList(platformContracts, "accessibility_modes")
        require(setOf("talkback", "voiceover").all { it in accessibilityModes }) {
            "Platform contract inventory must cover TalkBack and VoiceOver"
        }
        contractBlocks.filter { extractString(it, "platform") == "ios" &&
            extractString(it, "id")?.startsWith("photo.ios.") == true
        }.forEach { block ->
            require(extractString(block, "required_capability") == "physical-ios-device") {
                "iOS photo platform contracts must require a physical iOS device"
            }
        }

        val interaction = files.single { it.first == "config/quality/interaction-policy.toml" }.second
        val defaultFields = blocks(interaction, "default_field")
        val expectedDefaultFields = setOf("defaultLocale", "measurementSystem", "catalogProposal", "standardServing")
        require(defaultFields.mapNotNull { extractString(it, "field") }.toSet() == expectedDefaultFields) {
            "Interaction policy must declare provenance/context rules for every allowlisted field"
        }
        defaultFields.forEach { block ->
            require(extractStringList(block, "allowed_provenance").isNotEmpty()) {
                "Every defaultable field must declare allowed provenance"
            }
            require(extractStringList(block, "context_axes").isNotEmpty()) {
                "Every defaultable field must declare independent context axes"
            }
            require(!extractString(block, "override_path").isNullOrBlank())
            require(!extractString(block, "undo_path").isNullOrBlank())
            require(!extractString(block, "persistence_scope").isNullOrBlank())
        }
        listOf("scenario" to scenarios, "journey" to journeys).forEach { (blockName, manifest) ->
            blocks(manifest, blockName).forEach { block ->
                listOf(
                    "tap_budget",
                    "scroll_budget",
                    "field_budget",
                    "choice_budget",
                    "auxiliary_text_entry_chars_budget",
                    "auxiliary_choice_surface_budget",
                    "auxiliary_back_budget",
                    "auxiliary_undo_budget",
                    "auxiliary_cancel_budget",
                    "auxiliary_permission_prompt_budget",
                ).forEach { key ->
                    require(extractInt(block, key)?.let { it >= 0 } == true) {
                        "$blockName is missing a non-negative interaction budget: $key"
                    }
                }
                val declaredAccessibility = extractStringList(block, "accessibility_contract_ids")
                require(declaredAccessibility.isNotEmpty() && declaredAccessibility.all { it in accessibilityContractIds }) {
                    "$blockName must bind declared accessibility contracts"
                }
                val declaredLocales = extractStringList(block, "locale_ids")
                require(declaredLocales.isNotEmpty() && declaredLocales.all { it in supportedLocales }) {
                    "$blockName must bind supported locale evidence"
                }
                require(extractStringList(block, "proposal_ids").isNotEmpty()) {
                    "$blockName must bind proposal IDs"
                }
                require(extractStringList(block, "decision_ids").isNotEmpty()) {
                    "$blockName must bind decision IDs"
                }
                require(!extractString(block, "default_provenance").isNullOrBlank()) {
                    "$blockName must declare default provenance"
                }
                require(!extractString(block, "override_path").isNullOrBlank()) {
                    "$blockName must declare an override path"
                }
                require(!extractString(block, "undo_path").isNullOrBlank()) {
                    "$blockName must declare an undo path"
                }
                val resolvedDigest = extractString(block, "resolved_value_digest")
                require(resolvedDigest == "pending" || resolvedDigest?.matches(DIGEST_PATTERN) == true) {
                    "$blockName must bind a resolved-value digest or explicit pending state"
                }
                require(extractStringList(block, "forbidden_default_tests").isNotEmpty()) {
                    "$blockName must declare forbidden-default tests"
                }
            }
        }

        val scenarioBlocks = blocks(scenarios, "scenario")
        val scenarioIds = scenarioBlocks.mapNotNull { extractString(it, "id") }
        require(scenarioIds.size == scenarioIds.toSet().size) {
            "Positive-scenario catalog contains duplicate IDs"
        }
        require(extractInt(scenarios, "scenario_count") == scenarioBlocks.size) {
            "Positive-scenario count must match its entries"
        }
        val journeyBlocks = blocks(journeys, "journey")
        val journeyScenarioIds = journeyBlocks.mapNotNull { extractString(it, "scenario_id") }
        require(journeyScenarioIds.size == journeyScenarioIds.toSet().size) {
            "Journey catalog maps a scenario more than once"
        }
        require(journeyScenarioIds.toSet() == scenarioIds.toSet()) {
            "Every positive scenario must map exactly once to a journey"
        }
        require(extractInt(journeys, "journey_count") == journeyBlocks.size) {
            "Journey count must match its entries"
        }
        journeyBlocks.forEach { block ->
            val flowId = extractString(block, "maestro_flow_id") ?: error("Journey is missing maestro_flow_id")
            require(Files.isRegularFile(mobileRoot.resolve(".maestro/photo-analysis/$flowId.yaml"))) {
                "Journey references a missing Maestro flow: $flowId"
            }
        }

        val profile = extractString(
            files.single { it.first == "config/quality/ui-evidence-profile.toml" }.second,
            "profile",
        ) ?: error("UI evidence profile is missing profile")
        val evidenceBlocks = blocks(evidence, "evidence")
        require(evidenceBlocks.mapNotNull { extractString(it, "id") }.toSet() == gapIds) {
            "Evidence bindings must contain exactly one record for every native gap"
        }
        evidenceBlocks.forEach { block ->
            require(extractString(block, "profile") == profile) {
                "Evidence binding uses a different UI evidence profile"
            }
            listOf("commit_sha", "artifact_digest", "fixture_digest", "device_os", "task_version", "executed_at")
                .forEach { field ->
                    require(extractString(block, field)?.isNotBlank() == true) {
                        "Evidence record is missing attestation field $field"
                    }
                }
            val id = extractString(block, "id") ?: error("Evidence record is missing id")
            val status = extractString(block, "status") ?: error("Evidence record $id is missing status")
            if (status == "success") {
                require(extractString(block, "commit_sha") != "pending")
                require(extractString(block, "artifact_digest")?.matches(DIGEST_PATTERN) == true)
                require(extractString(block, "fixture_digest")?.matches(DIGEST_PATTERN) == true)
                val executedAt = Instant.parse(extractString(block, "executed_at"))
                val maxAge = gapBlocks.first { extractString(it, "id") == id }
                    .let { extractInt(it, "max_age_days") ?: error("Gap $id has no max age") }
                require(executedAt.isAfter(Instant.now().minus(maxAge.toLong(), ChronoUnit.DAYS))) {
                    "Native-gap evidence is stale: $id"
                }
            }
        }
    }

    private fun blocks(text: String, name: String): List<String> =
        Regex("(?ms)^\\[\\[$name]]\\s*\\n(.*?)(?=^\\[\\[|\\z)")
            .findAll(text)
            .map { it.groupValues[1] }
            .toList()

    private fun extractString(text: String, key: String): String? =
        Regex("(?m)^${Regex.escape(key)}\\s*=\\s*\"([^\"]*)\"\\s*$")
            .find(text)
            ?.groupValues
            ?.get(1)

    private fun extractInt(text: String, key: String): Int? =
        Regex("(?m)^${Regex.escape(key)}\\s*=\\s*(\\d+)\\s*$")
            .find(text)
            ?.groupValues
            ?.get(1)
            ?.toInt()

    private fun extractStringList(text: String, key: String): List<String> =
        Regex("(?m)^${Regex.escape(key)}\\s*=\\s*\\[([^]]*)]")
            .find(text)
            ?.groupValues
            ?.get(1)
            ?.let { values ->
                Regex("\"([^\"]+)\"").findAll(values).map { it.groupValues[1] }.toList()
            }
            ?: emptyList()

    private fun extractBoolean(text: String, key: String): Boolean? =
        Regex("(?m)^${Regex.escape(key)}\\s*=\\s*(true|false)\\s*$")
            .find(text)
            ?.groupValues
            ?.get(1)
            ?.toBoolean()

    private fun digest(mobileRoot: Path, paths: List<String>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        paths.forEach { relative ->
            digest.update(relative.toByteArray(Charsets.UTF_8))
            digest.update(0)
            digest.update(Files.readAllBytes(mobileRoot.resolve(relative)))
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private val DIGEST_PATTERN = Regex("[a-f0-9]{64}")

    private fun validateCommonManifest(relative: String, text: String) {
        require(Regex("(?m)^schema\\s*=\\s*\\\"[^\\\"]+\\\"\\s*$").containsMatchIn(text)) {
            "$relative must declare a schema"
        }
        require(text.contains("lifecycle = \"scaffold-pending\"")) {
            "$relative must remain scaffold-pending"
        }
        require(text.contains("source_digest = \"pending\"")) {
            "$relative must remain bound to a pending source digest"
        }
    }
}
