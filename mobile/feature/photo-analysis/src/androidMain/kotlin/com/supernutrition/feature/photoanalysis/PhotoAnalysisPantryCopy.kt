package com.supernutrition.feature.photoanalysis

import android.content.Context

internal fun Context.pantryIngredientLabels(): Map<String, String> = mapOf(
    "salmon" to getString(R.string.capture_pantry_salmon),
    "broccoli" to getString(R.string.capture_pantry_broccoli),
    "quinoa" to getString(R.string.capture_pantry_quinoa),
    "avocado" to getString(R.string.capture_pantry_avocado),
    "leafy_greens" to getString(R.string.capture_pantry_leafy_greens),
    "carrot" to getString(R.string.capture_pantry_carrot),
    "red_bell_pepper" to getString(R.string.capture_pantry_red_bell_pepper),
    "cucumber" to getString(R.string.capture_pantry_cucumber),
    "olive_oil" to getString(R.string.capture_pantry_olive_oil),
    "yellow_bell_pepper" to getString(R.string.capture_pantry_yellow_bell_pepper),
    "cauliflower" to getString(R.string.capture_pantry_cauliflower),
    "edamame" to getString(R.string.capture_pantry_edamame),
    "tahini" to getString(R.string.capture_pantry_tahini),
    "soy_sauce" to getString(R.string.capture_pantry_soy_sauce),
    "salt" to getString(R.string.capture_pantry_salt),
    "blueberries" to getString(R.string.capture_pantry_blueberries),
    "sweet_potato" to getString(R.string.capture_pantry_sweet_potato),
)
