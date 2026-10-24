package com.supernutrition.data.nutrition

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import com.supernutrition.data.nutrition.db.UsdaCatalogDatabase
import com.supernutrition.domain.nutrition.CatalogProduct
import kotlin.coroutines.cancellation.CancellationException

/** Android and Native drivers create this schema; JDBC test drivers create it explicitly. */
val usdaCatalogSchema: SqlSchema<QueryResult.Value<Unit>> = UsdaCatalogDatabase.Schema

/** Create off the UI thread; retain one session and serialize its queries/disposal in the owning host. */
fun openBundledUsdaCatalog(driverFactory: () -> SqlDriver, sha256: (ByteArray) -> String): BundledUsdaCatalogSession? =
    UsdaCatalogActivation(driverFactory, sha256).let { activation ->
        if (activation.activateBundled()) activation.active else null
    }

/** Private candidate databases prevent a failed replacement from changing an already published handle. */
internal class UsdaCatalogActivation(
    private val driverFactory: () -> SqlDriver,
    private val sha256: (ByteArray) -> String,
) {
    var active: BundledUsdaCatalogSession? = null
        private set

    fun activateBundled(): Boolean = activate(
        UsdaPackagePayload(usdaCatalogSnapshot(), BundledUsdaPackage.payload, BundledUsdaPackage.FOOD_COUNT),
    )

    internal fun activate(candidate: UsdaPackagePayload): Boolean {
        var driver: SqlDriver? = null
        return try {
            val products = readVerifiedUsdaPackage(candidate, sha256)
            val stagedDriver = driverFactory().also { driver = it }
            val database = UsdaCatalogDatabase(stagedDriver)
            database.transaction {
                products.forEach { insertProduct(database, it) }
                val snapshot = candidate.snapshot
                database.catalogQueries.insertPackage(
                    snapshot.contentDigest,
                    snapshot.identity.catalogId,
                    snapshot.identity.version,
                    snapshot.catalogCountry,
                    products.size.toLong(),
                )
                check(
                    database.catalogQueries.foodCount(snapshot.contentDigest).executeAsOne() == products.size.toLong(),
                )
                check(
                    database.catalogQueries.findPackage(snapshot.contentDigest).executeAsOne().food_count ==
                        products.size.toLong(),
                )
            }
            val staged = SqlDelightUsdaCatalog(database, candidate.snapshot)
            active = BundledUsdaCatalogSession(staged, stagedDriver)
            true
        } catch (cancelled: CancellationException) {
            runCatching { driver?.close() }
            throw cancelled
        } catch (_: Exception) {
            // Cleanup failure cannot turn a rejected candidate into an activation or hide the old handle.
            runCatching { driver?.close() }
            false
        }
    }

    private fun insertProduct(database: UsdaCatalogDatabase, product: CatalogProduct) {
        val nutrients = product.nutrientsPer100Grams
        database.catalogQueries.insertFood(
            product.snapshot.contentDigest,
            product.canonicalItemId,
            product.displayName,
            nutrients.energyKcal,
            nutrients.proteinGrams,
            nutrients.carbohydrateGrams,
            nutrients.fatGrams,
            nutrients.fiberGrams,
            nutrients.sugarGrams,
            nutrients.sodiumMilligrams,
        )
    }
}
