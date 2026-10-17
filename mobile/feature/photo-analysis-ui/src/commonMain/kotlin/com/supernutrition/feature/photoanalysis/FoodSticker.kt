package com.supernutrition.feature.photoanalysis

import com.supernutrition.feature.photoanalysis.resources.Res
import com.supernutrition.feature.photoanalysis.resources.food_sticker_apple
import com.supernutrition.feature.photoanalysis.resources.food_sticker_avocado
import com.supernutrition.feature.photoanalysis.resources.food_sticker_avocado_selected
import com.supernutrition.feature.photoanalysis.resources.food_sticker_banana
import com.supernutrition.feature.photoanalysis.resources.food_sticker_beans
import com.supernutrition.feature.photoanalysis.resources.food_sticker_beef
import com.supernutrition.feature.photoanalysis.resources.food_sticker_blueberries
import com.supernutrition.feature.photoanalysis.resources.food_sticker_bread
import com.supernutrition.feature.photoanalysis.resources.food_sticker_broccoli
import com.supernutrition.feature.photoanalysis.resources.food_sticker_broccoli_selected
import com.supernutrition.feature.photoanalysis.resources.food_sticker_carrot
import com.supernutrition.feature.photoanalysis.resources.food_sticker_cauliflower
import com.supernutrition.feature.photoanalysis.resources.food_sticker_cheese
import com.supernutrition.feature.photoanalysis.resources.food_sticker_chicken
import com.supernutrition.feature.photoanalysis.resources.food_sticker_cucumber
import com.supernutrition.feature.photoanalysis.resources.food_sticker_edamame
import com.supernutrition.feature.photoanalysis.resources.food_sticker_egg
import com.supernutrition.feature.photoanalysis.resources.food_sticker_fish
import com.supernutrition.feature.photoanalysis.resources.food_sticker_grapes
import com.supernutrition.feature.photoanalysis.resources.food_sticker_kiwi
import com.supernutrition.feature.photoanalysis.resources.food_sticker_leafy_greens
import com.supernutrition.feature.photoanalysis.resources.food_sticker_leafy_greens_selected
import com.supernutrition.feature.photoanalysis.resources.food_sticker_lemon
import com.supernutrition.feature.photoanalysis.resources.food_sticker_lentils
import com.supernutrition.feature.photoanalysis.resources.food_sticker_mango
import com.supernutrition.feature.photoanalysis.resources.food_sticker_milk
import com.supernutrition.feature.photoanalysis.resources.food_sticker_mushroom
import com.supernutrition.feature.photoanalysis.resources.food_sticker_nuts
import com.supernutrition.feature.photoanalysis.resources.food_sticker_oats
import com.supernutrition.feature.photoanalysis.resources.food_sticker_olive_oil
import com.supernutrition.feature.photoanalysis.resources.food_sticker_onion
import com.supernutrition.feature.photoanalysis.resources.food_sticker_orange
import com.supernutrition.feature.photoanalysis.resources.food_sticker_pasta
import com.supernutrition.feature.photoanalysis.resources.food_sticker_peach
import com.supernutrition.feature.photoanalysis.resources.food_sticker_pear
import com.supernutrition.feature.photoanalysis.resources.food_sticker_pineapple
import com.supernutrition.feature.photoanalysis.resources.food_sticker_potato
import com.supernutrition.feature.photoanalysis.resources.food_sticker_quinoa
import com.supernutrition.feature.photoanalysis.resources.food_sticker_red_bell_pepper
import com.supernutrition.feature.photoanalysis.resources.food_sticker_rice
import com.supernutrition.feature.photoanalysis.resources.food_sticker_salmon
import com.supernutrition.feature.photoanalysis.resources.food_sticker_salmon_selected
import com.supernutrition.feature.photoanalysis.resources.food_sticker_salt
import com.supernutrition.feature.photoanalysis.resources.food_sticker_soy_sauce
import com.supernutrition.feature.photoanalysis.resources.food_sticker_strawberry
import com.supernutrition.feature.photoanalysis.resources.food_sticker_sweet_potato
import com.supernutrition.feature.photoanalysis.resources.food_sticker_tahini
import com.supernutrition.feature.photoanalysis.resources.food_sticker_tomato
import com.supernutrition.feature.photoanalysis.resources.food_sticker_watermelon
import com.supernutrition.feature.photoanalysis.resources.food_sticker_yellow_bell_pepper
import com.supernutrition.feature.photoanalysis.resources.food_sticker_yogurt
import org.jetbrains.compose.resources.DrawableResource

/** A finite, exact food-identity mapping; availability never proposes a food by itself. */
internal data class FoodSticker(
    val id: String,
    val label: String,
    val artwork: DrawableResource,
    val aliases: List<String>,
    val selectedArtwork: DrawableResource = artwork,
)

internal val FoodStickers = listOf(
    FoodSticker(
        id = "salmon",
        label = "Salmon",
        artwork = Res.drawable.food_sticker_salmon,
        aliases = listOf("salmon", "лосось", "сёмга", "семга", "salmone"),
        selectedArtwork = Res.drawable.food_sticker_salmon_selected,
    ),
    FoodSticker(
        id = "broccoli",
        label = "Broccoli",
        artwork = Res.drawable.food_sticker_broccoli,
        aliases = listOf("broccoli", "брокколи"),
        selectedArtwork = Res.drawable.food_sticker_broccoli_selected,
    ),
    FoodSticker(
        id = "quinoa",
        label = "Quinoa",
        artwork = Res.drawable.food_sticker_quinoa,
        aliases = listOf("quinoa", "киноа"),
    ),
    FoodSticker(
        id = "avocado",
        label = "Avocado",
        artwork = Res.drawable.food_sticker_avocado,
        aliases = listOf("avocado", "авокадо"),
        selectedArtwork = Res.drawable.food_sticker_avocado_selected,
    ),
    FoodSticker(
        id = "leafy_greens",
        label = "Leafy greens",
        artwork = Res.drawable.food_sticker_leafy_greens,
        aliases = listOf("leafy greens", "зелень"),
        selectedArtwork = Res.drawable.food_sticker_leafy_greens_selected,
    ),
    FoodSticker(
        id = "carrot",
        label = "Carrot",
        artwork = Res.drawable.food_sticker_carrot,
        aliases = listOf("carrot", "carrots", "морковь", "carota", "carote"),
    ),
    FoodSticker(
        id = "red_bell_pepper",
        label = "Red bell pepper",
        artwork = Res.drawable.food_sticker_red_bell_pepper,
        aliases = listOf("red bell pepper", "красный перец", "peperone rosso"),
    ),
    FoodSticker(
        id = "cucumber",
        label = "Cucumber",
        artwork = Res.drawable.food_sticker_cucumber,
        aliases = listOf("cucumber", "огурец", "cetriolo"),
    ),
    FoodSticker(
        id = "olive_oil",
        label = "Olive oil",
        artwork = Res.drawable.food_sticker_olive_oil,
        aliases = listOf("olive oil", "оливковое масло", "olio di oliva"),
    ),
    FoodSticker(
        id = "yellow_bell_pepper",
        label = "Yellow bell pepper",
        artwork = Res.drawable.food_sticker_yellow_bell_pepper,
        aliases = listOf("yellow bell pepper", "жёлтый перец", "желтый перец", "peperone giallo"),
    ),
    FoodSticker(
        id = "cauliflower",
        label = "Cauliflower",
        artwork = Res.drawable.food_sticker_cauliflower,
        aliases = listOf("cauliflower", "цветная капуста", "cavolfiore"),
    ),
    FoodSticker(
        id = "edamame",
        label = "Edamame",
        artwork = Res.drawable.food_sticker_edamame,
        aliases = listOf("edamame", "эдамаме"),
    ),
    FoodSticker(
        id = "tahini",
        label = "Tahini",
        artwork = Res.drawable.food_sticker_tahini,
        aliases = listOf("tahini", "тахини"),
    ),
    FoodSticker(
        id = "soy_sauce",
        label = "Soy sauce",
        artwork = Res.drawable.food_sticker_soy_sauce,
        aliases = listOf("soy sauce", "соевый соус", "salsa di soia"),
    ),
    FoodSticker(
        id = "salt",
        label = "Salt",
        artwork = Res.drawable.food_sticker_salt,
        aliases = listOf("salt", "соль", "sale"),
    ),
    FoodSticker(
        id = "blueberries",
        label = "Blueberries",
        artwork = Res.drawable.food_sticker_blueberries,
        aliases = listOf("blueberry", "blueberries", "черника", "голубика", "mirtilli"),
    ),
    FoodSticker(
        id = "sweet_potato",
        label = "Sweet potato",
        artwork = Res.drawable.food_sticker_sweet_potato,
        aliases = listOf("sweet potato", "батат", "patata dolce"),
    ),
    FoodSticker(
        id = "apple",
        label = "Apple",
        artwork = Res.drawable.food_sticker_apple,
        aliases = listOf("apple", "apples", "яблоко", "яблоки", "mela", "mele", "manzana", "manzanas"),
    ),
    FoodSticker(
        id = "banana",
        label = "Banana",
        artwork = Res.drawable.food_sticker_banana,
        aliases = listOf("banana", "bananas", "банан", "бананы", "plátano", "plátanos", "platano", "platanos"),
    ),
    FoodSticker(
        id = "orange",
        label = "Orange",
        artwork = Res.drawable.food_sticker_orange,
        aliases = listOf("orange", "oranges", "апельсин", "апельсины", "arancia", "arance", "naranja", "naranjas"),
    ),
    FoodSticker(
        id = "tomato",
        label = "Tomato",
        artwork = Res.drawable.food_sticker_tomato,
        aliases = listOf("tomato", "tomatoes", "помидор", "помидоры", "томат", "томаты"),
    ),
    FoodSticker(
        id = "potato",
        label = "Potato",
        artwork = Res.drawable.food_sticker_potato,
        aliases = listOf("potato", "potatoes", "картофель", "картошка"),
    ),
    FoodSticker(
        id = "rice",
        label = "Rice",
        artwork = Res.drawable.food_sticker_rice,
        aliases = listOf("rice", "рис"),
    ),
    FoodSticker(
        id = "chicken",
        label = "Chicken",
        artwork = Res.drawable.food_sticker_chicken,
        aliases = listOf("chicken", "курица"),
    ),
    FoodSticker(
        id = "egg",
        label = "Egg",
        artwork = Res.drawable.food_sticker_egg,
        aliases = listOf("egg", "eggs", "яйцо", "яйца"),
    ),
    FoodSticker(
        id = "cheese",
        label = "Cheese",
        artwork = Res.drawable.food_sticker_cheese,
        aliases = listOf("cheese", "сыр"),
    ),
    FoodSticker(
        id = "yogurt",
        label = "Yogurt",
        artwork = Res.drawable.food_sticker_yogurt,
        aliases = listOf("yogurt", "yoghurt", "йогурт"),
    ),
    FoodSticker(
        id = "strawberry",
        label = "Strawberry",
        artwork = Res.drawable.food_sticker_strawberry,
        aliases = listOf("strawberry", "strawberries", "клубника"),
    ),
    FoodSticker(
        id = "grapes",
        label = "Grapes",
        artwork = Res.drawable.food_sticker_grapes,
        aliases = listOf("grape", "grapes", "виноград"),
    ),
    FoodSticker(
        id = "beans",
        label = "Beans",
        artwork = Res.drawable.food_sticker_beans,
        aliases = listOf("bean", "beans", "фасоль"),
    ),
    FoodSticker(
        id = "mushroom",
        label = "Mushroom",
        artwork = Res.drawable.food_sticker_mushroom,
        aliases = listOf("mushroom", "mushrooms", "гриб", "грибы"),
    ),
    FoodSticker(
        id = "bread",
        label = "Bread",
        artwork = Res.drawable.food_sticker_bread,
        aliases = listOf("bread", "хлеб"),
    ),
    FoodSticker(
        id = "beef",
        label = "Beef",
        artwork = Res.drawable.food_sticker_beef,
        aliases = listOf("beef", "говядина"),
    ),
    FoodSticker(
        id = "lentils",
        label = "Lentils",
        artwork = Res.drawable.food_sticker_lentils,
        aliases = listOf("lentil", "lentils", "чечевица"),
    ),
    FoodSticker(
        id = "pear",
        label = "Pear",
        artwork = Res.drawable.food_sticker_pear,
        aliases = listOf("pear", "pears", "груша", "груши"),
    ),
    FoodSticker(
        id = "oats",
        label = "Oats",
        artwork = Res.drawable.food_sticker_oats,
        aliases = listOf("oats", "oat", "овёс", "овес"),
    ),
    FoodSticker(
        id = "pasta",
        label = "Pasta",
        artwork = Res.drawable.food_sticker_pasta,
        aliases = listOf("pasta", "макароны", "паста"),
    ),
    FoodSticker(
        id = "milk",
        label = "Milk",
        artwork = Res.drawable.food_sticker_milk,
        aliases = listOf("milk", "молоко"),
    ),
    FoodSticker(
        id = "watermelon",
        label = "Watermelon",
        artwork = Res.drawable.food_sticker_watermelon,
        aliases = listOf("watermelon", "watermelons", "арбуз", "арбузы"),
    ),
    FoodSticker(
        id = "lemon",
        label = "Lemon",
        artwork = Res.drawable.food_sticker_lemon,
        aliases = listOf("lemon", "lemons", "лимон", "лимоны"),
    ),
    FoodSticker(
        id = "nuts",
        label = "Nuts",
        artwork = Res.drawable.food_sticker_nuts,
        aliases = listOf("nut", "nuts", "орех", "орехи"),
    ),
    FoodSticker(
        id = "fish",
        label = "Fish",
        artwork = Res.drawable.food_sticker_fish,
        aliases = listOf("fish", "рыба"),
    ),
    FoodSticker(
        id = "onion",
        label = "Onion",
        artwork = Res.drawable.food_sticker_onion,
        aliases = listOf("onion", "onions", "лук"),
    ),
    FoodSticker(
        id = "peach",
        label = "Peach",
        artwork = Res.drawable.food_sticker_peach,
        aliases = listOf("peach", "peaches", "персик", "персики"),
    ),
    FoodSticker(
        id = "mango",
        label = "Mango",
        artwork = Res.drawable.food_sticker_mango,
        aliases = listOf("mango", "mangoes", "mangos", "манго"),
    ),
    FoodSticker(
        id = "pineapple",
        label = "Pineapple",
        artwork = Res.drawable.food_sticker_pineapple,
        aliases = listOf("pineapple", "pineapples", "ананас", "ананасы"),
    ),
    FoodSticker(
        id = "kiwi",
        label = "Kiwi",
        artwork = Res.drawable.food_sticker_kiwi,
        aliases = listOf("kiwi", "kiwis", "kiwifruit", "киви"),
    ),
) + expandedFoodStickers + stapleFoodStickers

internal fun foodStickerFor(label: String): FoodSticker? {
    val normalized = label.trim().lowercase().replace('_', ' ')
    return FoodStickers.firstOrNull { normalized in it.aliases }
}
