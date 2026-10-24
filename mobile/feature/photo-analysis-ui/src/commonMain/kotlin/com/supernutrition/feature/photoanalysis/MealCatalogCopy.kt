package com.supernutrition.feature.photoanalysis

data class MealCatalogCopy(
    val open: String = "Choose a nutrition source product",
    val query: String = "Search products",
    val description: String = "Optional. Choose the exact product and preparation, then enter your portion in grams.",
    val loading: String = "Loading nutrition source…",
    val unavailable: String = "Nutrition source unavailable. You can still save a meal without nutrient values.",
    val prompt: String = "Enter a product name to search.",
    val noResults: String = "No matching products. Try another product name or continue without a selection.",
    val retry: String = "Retry source",
    val more: String = "Show more products",
    val selected: String = "Selected product",
    val remove: String = "Remove product selection",
    val gramsRequired: String = "Enter your portion in grams. No standard portion is selected automatically.",
    val preparationWarning: String = "Preparation not confirmed: {words}. Check the exact product before saving.",
    val info: String = "Nutrition source and credits",
    val closeInfo: String = "Close source information",
    val source: String = "Source",
    val licence: String = "Licence",
    val linkOpenFailed: String = "Couldn't open this link. Try the link again or close source information.",
)
