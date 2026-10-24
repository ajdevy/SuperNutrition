package com.supernutrition.data.nutrition

import com.supernutrition.domain.nutrition.CatalogAttribution
import com.supernutrition.domain.nutrition.NutritionCatalogIdentity
import com.supernutrition.domain.nutrition.NutritionCatalogProvenance
import com.supernutrition.domain.nutrition.NutritionCatalogSnapshot

internal fun usdaCatalogSnapshot(digest: String = BundledUsdaPackage.DIGEST) = NutritionCatalogSnapshot(
    identity = NutritionCatalogIdentity("usda-sr-legacy", "2018-04"),
    catalogCountry = "US",
    contentDigest = digest,
)

internal val usdaCatalogProvenance = NutritionCatalogProvenance(
    sourceName = "USDA FoodData Central · SR Legacy 2018-04",
    sourceUrl = "https://fdc.nal.usda.gov/download-datasets/",
    licenceName = "CC0 1.0 Universal",
    licenceUrl = "https://creativecommons.org/publicdomain/zero/1.0/",
    coverageDescription = "US catalog · 7,793 SR Legacy foods · explicit grams · no recipe estimates",
    attributions = listOf(
        CatalogAttribution(
            "USDA FoodData Central",
            "U.S. Department of Agriculture, Agricultural Research Service. FoodData Central, 2019.",
            "https://creativecommons.org/publicdomain/zero/1.0/",
            "https://fdc.nal.usda.gov/",
        ),
        CatalogAttribution(
            "FAO AGROVOC",
            "Contains information from the AGROVOC Multilingual Thesaurus, " +
                "© Food and Agriculture Organization of the United Nations (FAO). " +
                "Multilingual search aliases are licensed under CC-BY IGO 3.0.",
            "https://creativecommons.org/licenses/by/3.0/igo/",
            "https://agrovoc.fao.org/",
        ),
        CatalogAttribution(
            "Wikidata",
            "Additional search aliases from Wikidata, CC0 1.0.",
            "https://creativecommons.org/publicdomain/zero/1.0/",
            "https://www.wikidata.org/",
        ),
        CatalogAttribution(
            "OpenCorpora",
            "Contains adapted Russian morphological data from OpenCorpora, " +
                "dictionary 0.92 rev 417150, generated via pymorphy3 2.0.6 and " +
                "pymorphy3-dicts-ru 2.4.417150.4580142. " +
                "Local changes: selected and filtered forms, lowercasing, " +
                "ё→е canonicalization and retained ё/е lookup variants where applicable. " +
                "OpenCorpora publishes its source data under CC BY-SA 3.0.",
            "https://creativecommons.org/licenses/by-sa/3.0/",
            "https://opencorpora.org/?page=downloads",
        ),
    ),
)
