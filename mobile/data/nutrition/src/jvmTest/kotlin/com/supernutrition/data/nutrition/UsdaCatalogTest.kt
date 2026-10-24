package com.supernutrition.data.nutrition

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.supernutrition.data.nutrition.db.UsdaCatalogDatabase
import com.supernutrition.domain.nutrition.CatalogMealBinding
import com.supernutrition.domain.nutrition.CatalogMealNutritionResolution
import com.supernutrition.domain.nutrition.CatalogMealNutritionResolver
import com.supernutrition.domain.nutrition.NutritionCatalogIdentity
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class UsdaCatalogTest {
    @Test
    fun `frozen package grounds actual USDA values without replacing unknowns`() {
        val source = assertNotNull(openBundledUsdaCatalog(::newDriver, ::sha256))
        val product = assertNotNull(source.lookup(source.snapshot, "food:usda-srl-01227"))
        assertEquals("Cheese, dry white, queso seco", product.displayName)
        assertEquals(325.0, product.nutrientsPer100Grams.energyKcal)
        assertEquals(24.51, product.nutrientsPer100Grams.proteinGrams)
        assertEquals(2.04, product.nutrientsPer100Grams.carbohydrateGrams)
        assertEquals(24.35, product.nutrientsPer100Grams.fatGrams)
        assertNull(product.nutrientsPer100Grams.fiberGrams)
        assertEquals(0.55, product.nutrientsPer100Grams.sugarGrams)
        assertEquals(1808.0, product.nutrientsPer100Grams.sodiumMilligrams)
        val result = assertIs<CatalogMealNutritionResolution.Grounded>(
            CatalogMealNutritionResolver(source).resolve(
                CatalogMealBinding(source.snapshot, product.canonicalItemId),
                50.0,
            ),
        ).result
        assertEquals(162.5, result.nutrients.energyKcal)
        assertNull(result.nutrients.fiberGrams)
        assertEquals(904.0, result.nutrients.sodiumMilligrams)
        val groundcherries = assertNotNull(source.lookup(source.snapshot, "food:usda-srl-09138"))
        assertNull(groundcherries.nutrientsPer100Grams.sodiumMilligrams)
        assertNull(groundcherries.nutrientsPer100Grams.sugarGrams)
        assertNull(groundcherries.nutrientsPer100Grams.fiberGrams)
        val butter = assertNotNull(source.lookup(source.snapshot, "food:usda-srl-01001"))
        assertEquals(0.0, butter.nutrientsPer100Grams.fiberGrams)
    }

    @Test
    fun `identity and licence coverage are explicit and complete`() {
        val source = assertNotNull(openBundledUsdaCatalog(::newDriver, ::sha256))
        assertEquals(NutritionCatalogIdentity("usda-sr-legacy", "2018-04"), source.snapshot.identity)
        assertEquals("US", source.snapshot.catalogCountry)
        assertEquals(BundledUsdaPackage.DIGEST, source.snapshot.contentDigest)
        assertEquals(7793, BundledUsdaPackage.FOOD_COUNT)
        assertEquals(BundledUsdaPackage.DIGEST, sha256(BundledUsdaPackage.payload.encodeToByteArray()))
        assertTrue(source.provenance.sourceName.contains("USDA"))
        assertTrue(source.provenance.coverageDescription.contains("7,793"))
        assertTrue(
            source.provenance.attributions.any {
                it.name == "FAO AGROVOC" && it.notice.contains("Food and Agriculture Organization")
            },
        )
        assertTrue(source.provenance.attributions.all { it.licenceUrl.startsWith("https://") })
    }

    @Test
    fun `search reuses multilingual aliases and preserves preparation warnings`() {
        val source = assertNotNull(openBundledUsdaCatalog(::newDriver, ::sha256))
        assertTrue(source.search("сыр").any { it.product.displayName.startsWith("Cheese,") })
        assertTrue(source.search("leche").isNotEmpty())
        assertTrue(source.search("").isEmpty())
        assertTrue(source.search("zzzznonexistentfoodzzzz").isEmpty())
        assertTrue(source.search("grilled groundcherries").any { "grilled" in it.unmatchedPreparation })
        assertNull(source.lookup(source.snapshot.copy(contentDigest = "b".repeat(64)), "food:usda-srl-01001"))
        assertNull(source.lookup(source.snapshot, "food:missing"))
    }

    @Test
    fun `every frozen source row survives the SQLDelight round trip`() {
        val source = assertNotNull(openBundledUsdaCatalog(::newDriver, ::sha256))
        val rows = readVerifiedUsdaPackage(defaultPayload(), ::sha256)
        assertEquals(562, rows.count { it.nutrientsPer100Grams.fiberGrams == null })
        assertEquals(1786, rows.count { it.nutrientsPer100Grams.sugarGrams == null })
        assertEquals(84, rows.count { it.nutrientsPer100Grams.sodiumMilligrams == null })
        rows.forEach { row -> assertEquals(row, source.lookup(source.snapshot, row.canonicalItemId)) }
    }

    @Test
    fun `lost SQL rows cannot produce numbers from the retained search index`() {
        val driver = newDriver()
        val session = assertNotNull(openBundledUsdaCatalog({ driver }, ::sha256))
        assertTrue(session.search("сыр").isNotEmpty())
        driver.execute(null, "DELETE FROM catalog_food", 0)
        assertTrue(session.search("сыр").isEmpty())
        assertNull(session.lookup(session.snapshot, "food:usda-srl-01227"))
        session.close()
    }

    @Test
    fun `failed verification or driver creation cannot activate a source`() {
        assertNull(openBundledUsdaCatalog(::newDriver) { "0".repeat(64) })
        assertNull(openBundledUsdaCatalog({ error("Unavailable driver") }, ::sha256))
    }

    @Test
    fun `bad replacement preserves the old source and its live queries`() {
        val activation = UsdaCatalogActivation(::newDriver, ::sha256)
        assertTrue(activation.activateBundled())
        val previous = assertNotNull(activation.active)
        assertFalse(activation.activate(defaultPayload().copy(payload = "tampered")))
        assertSame(previous, activation.active)
        assertNotNull(previous.lookup(previous.snapshot, "food:usda-srl-01227"))
        assertTrue(activation.activateBundled())
        assertNotNull(previous.lookup(previous.snapshot, "food:usda-srl-01227"))
    }

    @Test
    fun `failed SQL stage rolls back every candidate row and retains previous handle`() {
        val staged = newDriver()
        var attempts = 0
        val failingDriver = object : SqlDriver by staged {
            override fun execute(
                identifier: Int?,
                sql: String,
                parameters: Int,
                binders: (SqlPreparedStatement.() -> Unit)?,
            ): QueryResult<Long> {
                if (sql.startsWith("INSERT INTO catalog_food") && ++attempts == 2) error("Fixture write failure")
                return staged.execute(identifier, sql, parameters, binders)
            }

            // Keep the real candidate open only in this fixture so its rollback can be inspected.
            override fun close() = Unit
        }
        var useFailure = false
        val activation = UsdaCatalogActivation({ if (useFailure) failingDriver else newDriver() }, ::sha256)
        assertTrue(activation.activateBundled())
        val previous = activation.active
        useFailure = true
        assertFalse(activation.activateBundled())
        assertSame(previous, activation.active)
        assertEquals(2, attempts)
        assertEquals(0L, UsdaCatalogDatabase(staged).catalogQueries.foodCount(BundledUsdaPackage.DIGEST).executeAsOne())
        assertNull(
            UsdaCatalogDatabase(staged).catalogQueries.findPackage(BundledUsdaPackage.DIGEST).executeAsOneOrNull(),
        )
        staged.close()
    }

    @Test
    fun `cancellation propagates without turning into unavailable source`() {
        assertFailsWith<CancellationException> {
            openBundledUsdaCatalog({ throw CancellationException("cancelled") }, ::sha256)
        }
    }

    @Test
    fun `cancellation after creating the candidate closes it and propagates`() {
        val staged = newDriver()
        var closed = false
        val driver = object : SqlDriver by staged {
            override fun execute(
                identifier: Int?,
                sql: String,
                parameters: Int,
                binders: (SqlPreparedStatement.() -> Unit)?,
            ): QueryResult<Long> = throw CancellationException("Cancelled candidate")
            override fun close() {
                closed = true
                staged.close()
            }
        }
        assertFailsWith<CancellationException> { openBundledUsdaCatalog({ driver }, ::sha256) }
        assertTrue(closed)
    }

    @Test
    fun `cleanup failure cannot mask the original cancellation`() {
        val staged = newDriver()
        val cancelled = CancellationException("Original cancellation")
        val driver = object : SqlDriver by staged {
            override fun execute(
                identifier: Int?,
                sql: String,
                parameters: Int,
                binders: (SqlPreparedStatement.() -> Unit)?,
            ): QueryResult<Long> = throw cancelled
            override fun close() {
                staged.close()
                error("Cleanup failed")
            }
        }
        val failure = assertFailsWith<CancellationException> { openBundledUsdaCatalog({ driver }, ::sha256) }
        assertSame(cancelled, failure)
    }

    @Test
    fun `owning session closes once and cannot provide numbers after disposal`() {
        val staged = newDriver()
        var closes = 0
        val driver = object : SqlDriver by staged {
            override fun close() {
                closes++
                staged.close()
            }
        }
        val session = assertNotNull(openBundledUsdaCatalog({ driver }, ::sha256))
        assertSame(session, session.catalog)
        val snapshot = session.snapshot
        assertNotNull(session.catalog.lookup(snapshot, "food:usda-srl-01227"))
        session.close()
        session.close()
        assertEquals(1, closes)
        assertFailsWith<IllegalStateException> { session.catalog.search("cheese") }
        assertIs<CatalogMealNutritionResolution.Unknown>(
            CatalogMealNutritionResolver(session.catalog).resolve(
                CatalogMealBinding(snapshot, "food:usda-srl-01227"),
                50.0,
            ),
        )
    }

    @Test
    fun `session stays closed when driver close fails`() {
        val staged = newDriver()
        var closes = 0
        val driver = object : SqlDriver by staged {
            override fun close() {
                closes++
                staged.close()
                error("Fixture close failure")
            }
        }
        val session = assertNotNull(openBundledUsdaCatalog({ driver }, ::sha256))
        assertFailsWith<IllegalStateException> { session.close() }
        session.close()
        assertEquals(1, closes)
        assertFailsWith<IllegalStateException> { session.lookup(session.snapshot, "food:usda-srl-01227") }
    }

    @Test
    fun `SQL schema has an explicit synchronous version and no pending migration`() {
        val driver = newDriver()
        assertEquals(1L, usdaCatalogSchema.version)
        usdaCatalogSchema.migrate(driver, 1L, 1L)
        driver.close()
    }

    @Test
    fun `post insert food and package counts are checked before publication`() {
        for (trigger in listOf(
            "CREATE TRIGGER discard_food AFTER INSERT ON catalog_food BEGIN " +
                "DELETE FROM catalog_food WHERE canonical_id = NEW.canonical_id; END",
            "CREATE TRIGGER alter_count AFTER INSERT ON catalog_package BEGIN " +
                "UPDATE catalog_package SET food_count = 0; END",
        )) {
            val driver = newDriver()
            driver.execute(null, trigger, 0)
            assertNull(openBundledUsdaCatalog({ driver }, ::sha256))
        }
    }

    private fun newDriver(): SqlDriver = JdbcSqliteDriver("jdbc:sqlite::memory:").also {
        usdaCatalogSchema.create(it)
    }

    private fun defaultPayload() = UsdaPackagePayload(usdaCatalogSnapshot(), BundledUsdaPackage.payload, 7793)
}

internal fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
    .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
