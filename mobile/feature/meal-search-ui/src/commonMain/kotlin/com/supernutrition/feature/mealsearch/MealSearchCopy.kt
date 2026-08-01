package com.supernutrition.feature.mealsearch

/**
 * Localized strings the search surface needs.
 *
 * Copy lives at the UI boundary, as it does for the capture screen, rather than
 * inside the presenter. What the presenter owns is what a row *says* about the
 * data — which food, which unmatched preparation — so that Compose and SwiftUI
 * cannot answer that differently. What a screen owns is the chrome around it.
 *
 * [ingredientCountLabel] carries a `{count}` placeholder rather than being
 * assembled from a number and a noun. Plural forms are language-specific — one
 * ingredient, two ingredients, and Russian's three-way split — and belong with
 * the per-market language matrix as data. A template keeps this type a plain
 * data class of strings, which a formatting lambda would not.
 */
data class MealSearchCopy(val searchLabel: String, val noResults: String, val ingredientCountLabel: String)
