package com.supernutrition.android;

import com.supernutrition.domain.nutrition.ApprovedNutritionCatalog;
import com.supernutrition.domain.nutrition.CatalogAttribution;
import com.supernutrition.domain.nutrition.CatalogMealBinding;
import com.supernutrition.domain.nutrition.CatalogMealNutritionResolution;
import com.supernutrition.domain.nutrition.CatalogMealNutritionResolver;
import com.supernutrition.domain.nutrition.CatalogProduct;
import com.supernutrition.domain.nutrition.NutrientDisplayNumberKt;
import com.supernutrition.domain.nutrition.PartialNutrientProfile;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Independent golden values for USDA SR Legacy01227, not a mock nutrition source. */
final class AndroidCatalogFixture {
    private static final String PRODUCT_ID = "food:usda-srl-01227";

    private AndroidCatalogFixture() { }

    static void assertGolden(ApprovedNutritionCatalog catalog) {
        assertEquals("usda-sr-legacy", catalog.getSnapshot().getIdentity().getCatalogId());
        assertEquals("2018-04", catalog.getSnapshot().getIdentity().getVersion());
        assertEquals("US", catalog.getSnapshot().getCatalogCountry());
        CatalogProduct product = catalog.lookup(catalog.getSnapshot(), PRODUCT_ID);
        assertNotNull(product);
        assertEquals("Cheese, dry white, queso seco", product.getDisplayName());
        assertEquals(325.0, product.getNutrientsPer100Grams().getEnergyKcal(), 0.0);
        assertNull(product.getNutrientsPer100Grams().getFiberGrams());
        assertFalse(catalog.search("сыр").isEmpty());
        CatalogMealNutritionResolution resolution = new CatalogMealNutritionResolver(catalog).resolve(
                new CatalogMealBinding(catalog.getSnapshot(), PRODUCT_ID), 50.0);
        assertTrue(resolution instanceof CatalogMealNutritionResolution.Grounded);
        PartialNutrientProfile nutrients = ((CatalogMealNutritionResolution.Grounded) resolution)
                .getResult().getNutrients();
        assertEquals(162.5, nutrients.getEnergyKcal(), 0.0);
        assertEquals("12.26", NutrientDisplayNumberKt.nutrientDisplayNumber(nutrients.getProteinGrams()));
        assertEquals(1.02, nutrients.getCarbohydrateGrams(), 0.0);
        assertEquals("12.18", NutrientDisplayNumberKt.nutrientDisplayNumber(nutrients.getFatGrams()));
        assertNull(nutrients.getFiberGrams());
        assertEquals("0.28", NutrientDisplayNumberKt.nutrientDisplayNumber(nutrients.getSugarGrams()));
        assertEquals(904.0, nutrients.getSodiumMilligrams(), 0.0);
        assertTrue(catalog.getProvenance().getSourceName().contains("USDA"));
        Set<String> credits = new HashSet<>();
        for (CatalogAttribution credit : catalog.getProvenance().getAttributions()) {
            credits.add(credit.getName());
            assertTrue(credit.getSourceUrl().startsWith("https://"));
            assertTrue(credit.getLicenceUrl().startsWith("https://"));
        }
        assertEquals(4, catalog.getProvenance().getAttributions().size());
        assertEquals(new HashSet<>(Arrays.asList(
                "USDA FoodData Central", "FAO AGROVOC", "Wikidata", "OpenCorpora")), credits);
    }

    static void assertClosed(ApprovedNutritionCatalog catalog) {
        try {
            catalog.lookup(catalog.getSnapshot(), PRODUCT_ID);
            fail("A disposed handle must not expose nutrient numbers");
        } catch (IllegalStateException expected) {
            // The app wrapper must expose the guarded session rather than its raw delegate.
        }
        try {
            catalog.search("cheese");
            fail("A disposed handle must reject searches");
        } catch (IllegalStateException expected) {
            // Both query paths share the owning session's disposal boundary.
        }
    }
}
