package com.supernutrition.data.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class UsdaCatalogProvenanceTest {
    @Test
    fun `Russian matcher attribution identifies OpenCorpora source licence and local adaptations`() {
        val credit = assertNotNull(
            usdaCatalogProvenance.attributions.singleOrNull { it.name == "OpenCorpora" },
            "Expected exactly one OpenCorpora dictionary attribution for the reused Russian matcher",
        )
        assertEquals("https://opencorpora.org/?page=downloads", credit.sourceUrl)
        assertEquals("https://creativecommons.org/licenses/by-sa/3.0/", credit.licenceUrl)
        assertEquals(
            "Contains adapted Russian morphological data from OpenCorpora, " +
                "dictionary 0.92 rev 417150, generated via pymorphy3 2.0.6 and " +
                "pymorphy3-dicts-ru 2.4.417150.4580142. " +
                "Local changes: selected and filtered forms, lowercasing, " +
                "ё→е canonicalization and retained ё/е lookup variants where applicable. " +
                "OpenCorpora publishes its source data under CC BY-SA 3.0.",
            credit.notice,
        )
        assertEquals("CC0 1.0 Universal", usdaCatalogProvenance.licenceName)
        assertEquals("https://creativecommons.org/publicdomain/zero/1.0/", usdaCatalogProvenance.licenceUrl)
    }
}
