package com.supernutrition.feature.photoanalysis

import com.supernutrition.feature.photoanalysis.resources.Res
import com.supernutrition.feature.photoanalysis.resources.food_sticker_almonds
import com.supernutrition.feature.photoanalysis.resources.food_sticker_brown_rice
import com.supernutrition.feature.photoanalysis.resources.food_sticker_cashews
import com.supernutrition.feature.photoanalysis.resources.food_sticker_hazelnuts
import com.supernutrition.feature.photoanalysis.resources.food_sticker_olives
import com.supernutrition.feature.photoanalysis.resources.food_sticker_peanuts
import com.supernutrition.feature.photoanalysis.resources.food_sticker_pistachios
import com.supernutrition.feature.photoanalysis.resources.food_sticker_walnuts

/** Separate whole-food identities; mixed dishes retain their actual label. */
internal val stapleFoodStickers = listOf(
    FoodSticker(
        id = "brown_rice",
        label = "Brown rice",
        artwork = Res.drawable.food_sticker_brown_rice,
        aliases = listOf("brown rice", "wholegrain rice", "бурый рис", "коричневый рис", "нешлифованный рис"),
    ),
    FoodSticker(
        id = "almonds",
        label = "Almonds",
        artwork = Res.drawable.food_sticker_almonds,
        aliases = listOf("almond", "almonds", "миндаль"),
    ),
    FoodSticker(
        id = "peanuts",
        label = "Peanuts",
        artwork = Res.drawable.food_sticker_peanuts,
        aliases = listOf("peanut", "peanuts", "арахис"),
    ),
    FoodSticker(
        id = "walnuts",
        label = "Walnuts",
        artwork = Res.drawable.food_sticker_walnuts,
        aliases = listOf("walnut", "walnuts", "грецкий орех", "грецкие орехи"),
    ),
    FoodSticker(
        id = "cashews",
        label = "Cashews",
        artwork = Res.drawable.food_sticker_cashews,
        aliases = listOf("cashew", "cashews", "кешью", "кэшью"),
    ),
    FoodSticker(
        id = "pistachios",
        label = "Pistachios",
        artwork = Res.drawable.food_sticker_pistachios,
        aliases = listOf("pistachio", "pistachios", "фисташка", "фисташки"),
    ),
    FoodSticker(
        id = "hazelnuts",
        label = "Hazelnuts",
        artwork = Res.drawable.food_sticker_hazelnuts,
        aliases = listOf("hazelnut", "hazelnuts", "фундук", "лесной орех", "лесные орехи"),
    ),
    FoodSticker(
        id = "olives",
        label = "Olives",
        artwork = Res.drawable.food_sticker_olives,
        aliases = listOf("olive", "olives", "оливка", "оливки", "маслина", "маслины"),
    ),
)
