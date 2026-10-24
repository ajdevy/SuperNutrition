package com.supernutrition.sharedkit

import app.cash.sqldelight.driver.native.inMemoryDriver
import com.supernutrition.data.nutrition.BundledUsdaCatalogSession
import com.supernutrition.data.nutrition.openBundledUsdaCatalog
import com.supernutrition.data.nutrition.usdaCatalogSchema
import com.supernutrition.domain.nutrition.ApprovedNutritionCatalog
import com.supernutrition.feature.photoanalysis.MealCatalogFactory
import com.supernutrition.feature.photoanalysis.MealCatalogHandle
import com.supernutrition.platform.photoios.iosSha256
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSThread

/** Cheap app-root selection. Verification, indexing and schema creation wait for explicit background opening. */
@OptIn(ExperimentalForeignApi::class)
internal class SharedKitUsdaCatalogFactory : MealCatalogFactory {
    override fun open(): MealCatalogHandle? {
        check(!NSThread.isMainThread) { "The bundled catalog must open on the background dispatcher" }
        val session = openBundledUsdaCatalog(
            driverFactory = { inMemoryDriver(usdaCatalogSchema) },
            sha256 = ::iosSha256,
        ) ?: return null
        return SharedKitUsdaCatalogHandle(session)
    }
}

private class SharedKitUsdaCatalogHandle(private val session: BundledUsdaCatalogSession) : MealCatalogHandle {
    override val catalog: ApprovedNutritionCatalog get() = session.catalog

    override fun close() {
        session.close()
    }
}
