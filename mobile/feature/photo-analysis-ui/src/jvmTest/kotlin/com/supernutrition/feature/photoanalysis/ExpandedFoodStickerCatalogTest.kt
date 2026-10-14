package com.supernutrition.feature.photoanalysis

import com.supernutrition.feature.photoanalysis.resources.Res
import com.supernutrition.feature.photoanalysis.resources.allDrawableResources
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** A finite food-icon catalog contract; no native UI or detector-accuracy claim is made here. */
class ExpandedFoodStickerCatalogTest {
    @Test
    fun `popular fruits and berries have distinct complete English and Russian identities`() {
        assertIdentities(fruitIdentities)
    }

    @Test
    fun `popular vegetables have distinct complete English and Russian identities`() {
        assertIdentities(vegetableIdentities)
    }

    @Test
    fun `common grains have distinct complete English and Russian identities`() {
        assertIdentities(grainIdentities)
    }

    @Test
    fun `common meats have distinct complete English and Russian identities`() {
        assertIdentities(meatIdentities)
    }

    @Test
    fun `common fish and seafood have distinct complete English and Russian identities`() {
        assertIdentities(fishIdentities)
    }

    @Test
    fun `plant foods and four plant milks retain their own complete identities`() {
        assertIdentities(plantIdentities)
    }

    @Test
    fun `dairy foods retain their own complete English and Russian identities`() {
        assertIdentities(dairyIdentities)
    }

    @Test
    fun `common nuts olives and brown rice have their own complete identities`() {
        assertIdentities(stapleIdentities)
    }

    @Test
    fun `the finite catalog preserves every old identity and has unique normalized aliases`() {
        val expectedIds = originalIdentityIds + expandedIdentityGroups.flatMap { it.keys } + stapleIdentities.keys
        assertEquals(152, expectedIds.size)
        assertEquals(expectedIds.toSet(), FoodStickers.map { it.id }.toSet())
        assertEquals(152, FoodStickers.size)
        val aliases = FoodStickers.flatMap { sticker ->
            sticker.aliases.map { it.trim().lowercase().replace('_', ' ') }
        }
        assertEquals(aliases.size, aliases.toSet().size, "A normalized alias has more than one food owner")
        assertEquals("leafy_greens", foodStickerFor("leafy greens")?.id)
        assertEquals("leafy_greens", foodStickerFor("зелень")?.id)
    }

    @Test
    fun `mixed dishes and ambiguous generic labels never receive a constituent illustration`() {
        listOf(
            "almond milkshake",
            "oat milk porridge",
            "soy milk tofu soup",
            "coconut milk curry",
            "peanut butter cookie",
            "tuna sandwich",
            "fish soup",
            "chicken liver pâté",
            "banana bread",
            "миндальный молочный коктейль",
            "каша на овсяном молоке",
            "карри с кокосовым молоком",
            "суп из тофу",
            "томатный суп",
            "рыбный суп",
            "салат",
            "овощи",
            "фрукты",
            "plant milk",
            "растительное молоко",
            "nut milk",
            "масло",
        ).forEach { label ->
            assertNull(foodStickerFor(label), "Keep the actual label when its complete identity is unknown: $label")
        }
        assertEquals("milk", foodStickerFor("milk")?.id)
        assertEquals("milk", foodStickerFor("молоко")?.id)
    }

    private fun assertIdentities(expected: Map<String, List<String>>) {
        expected.forEach { (identity, labels) ->
            labels.forEach { label ->
                val sticker = assertNotNull(foodStickerFor(label), "Missing food icon for $label")
                assertEquals(identity, sticker.id, "Incorrect food identity for $label")
                val expectedArtwork = assertNotNull(Res.allDrawableResources["food_sticker_$identity"])
                assertEquals(expectedArtwork, sticker.artwork, "Incorrect drawable bound to $label")
                val normalizedVariant = " ${label.uppercase().replace(' ', '_')} "
                assertEquals(identity, foodStickerFor(normalizedVariant)?.id, "Normalization changed $label")
            }
        }
    }
}

private val fruitIdentities = mapOf(
    "mandarin" to listOf(
        "mandarin",
        "mandarins",
        "tangerine",
        "tangerines",
        "mandarin orange",
        "mandarin oranges",
        "мандарин",
        "мандарины",
    ),
    "grapefruit" to listOf("grapefruit", "grapefruits", "грейпфрут", "грейпфруты"),
    "lime" to listOf("lime", "limes", "лайм", "лаймы"),
    "cherry" to listOf("cherry", "cherries", "вишня", "черешня"),
    "plum" to listOf("plum", "plums", "слива", "сливы"),
    "apricot" to listOf("apricot", "apricots", "абрикос", "абрикосы"),
    "nectarine" to listOf("nectarine", "nectarines", "нектарин", "нектарины"),
    "persimmon" to listOf("persimmon", "persimmons", "хурма"),
    "pomegranate" to listOf("pomegranate", "pomegranates", "гранат", "гранаты"),
    "melon" to listOf("melon", "melons", "дыня", "дыни"),
    "raspberry" to listOf("raspberry", "raspberries", "малина"),
    "blackberry" to listOf("blackberry", "blackberries", "ежевика"),
    "cranberry" to listOf("cranberry", "cranberries", "клюква"),
    "blackcurrant" to listOf(
        "blackcurrant",
        "blackcurrants",
        "black currant",
        "black currants",
        "чёрная смородина",
        "черная смородина",
    ),
    "fig" to listOf("fig", "figs", "инжир"),
    "dates" to listOf("date", "dates", "финик", "финики"),
    "coconut" to listOf("coconut", "coconuts", "кокос", "кокосы"),
    "papaya" to listOf("papaya", "papayas", "папайя", "папайи"),
    "passion_fruit" to listOf("passion fruit", "passion fruits", "passionfruit", "passionfruits", "маракуйя"),
    "guava" to listOf("guava", "guavas", "гуава", "гуавы"),
    "lychee" to listOf("lychee", "lychees", "litchi", "litchis", "личи"),
)

private val vegetableIdentities = mapOf(
    "cabbage" to listOf("cabbage", "cabbages", "white cabbage", "капуста", "белокочанная капуста"),
    "red_cabbage" to listOf("red cabbage", "purple cabbage", "краснокочанная капуста", "красная капуста"),
    "eggplant" to listOf("eggplant", "eggplants", "aubergine", "aubergines", "баклажан", "баклажаны"),
    "zucchini" to listOf("zucchini", "zucchinis", "courgette", "courgettes", "кабачок", "кабачки", "цукини"),
    "beet" to listOf("beet", "beets", "beetroot", "beetroots", "свёкла", "свекла"),
    "pumpkin" to listOf("pumpkin", "pumpkins", "тыква", "тыквы"),
    "radish" to listOf("radish", "radishes", "редис", "редиска"),
    "garlic" to listOf("garlic", "чеснок"),
    "corn" to listOf("corn", "maize", "sweetcorn", "sweet corn", "кукуруза"),
    "peas" to listOf(
        "pea",
        "peas",
        "green pea",
        "green peas",
        "горох",
        "горошек",
        "зелёный горошек",
        "зеленый горошек",
    ),
    "green_beans" to listOf(
        "green bean",
        "green beans",
        "french bean",
        "french beans",
        "стручковая фасоль",
        "зелёная фасоль",
        "зеленая фасоль",
        "спаржевая фасоль",
    ),
    "asparagus" to listOf("asparagus", "спаржа"),
    "celery" to listOf("celery", "сельдерей"),
    "lettuce" to listOf("lettuce", "lettuces", "латук", "салат латук", "салат-латук", "листовой салат"),
    "kale" to listOf("kale", "кейл", "кале", "кудрявая капуста"),
    "brussels_sprouts" to listOf("brussels sprout", "brussels sprouts", "брюссельская капуста"),
    "green_bell_pepper" to listOf(
        "green bell pepper",
        "green bell peppers",
        "зелёный болгарский перец",
        "зеленый болгарский перец",
        "зелёный перец",
        "зеленый перец",
    ),
    "leek" to listOf("leek", "leeks", "лук-порей", "лук порей", "порей"),
    "artichoke" to listOf("artichoke", "artichokes", "артишок", "артишоки"),
    "fennel" to listOf("fennel", "фенхель"),
    "turnip" to listOf("turnip", "turnips", "репа"),
    "spinach" to listOf("spinach", "spinaci", "шпинат"),
)

private val grainIdentities = mapOf(
    "buckwheat" to listOf("buckwheat", "гречка", "гречневая крупа", "гречиха"),
    "barley" to listOf("barley", "перловка", "перловая крупа", "ячмень", "ячневая крупа"),
    "millet" to listOf("millet", "пшено", "просо"),
    "bulgur" to listOf("bulgur", "булгур"),
    "couscous" to listOf("couscous", "cous cous", "кускус", "кус-кус"),
    "semolina" to listOf("semolina", "манка", "манная крупа"),
    "cornmeal" to listOf("cornmeal", "corn meal", "кукурузная крупа"),
    "wheat" to listOf("wheat", "пшеница", "пшеничная крупа"),
    "rye" to listOf("rye", "рожь"),
)

private val meatIdentities = mapOf(
    "turkey" to listOf("turkey", "turkeys", "turkey meat", "индейка", "индейки", "индюшатина", "мясо индейки"),
    "pork" to listOf("pork", "свинина"),
    "lamb" to listOf("lamb", "lamb meat", "баранина", "ягнятина"),
    "duck" to listOf("duck", "ducks", "duck meat", "утка", "утки", "утиное мясо"),
    "rabbit" to listOf("rabbit", "rabbits", "rabbit meat", "кролик", "кролики", "крольчатина"),
    "liver" to listOf("liver", "печень"),
    "ham" to listOf("ham", "ветчина"),
    "bacon" to listOf("bacon", "бекон"),
    "sausage" to listOf("sausage", "sausages", "колбаса", "сосиска", "сосиски"),
)

private val fishIdentities = mapOf(
    "tuna" to listOf("tuna", "тунец"),
    "trout" to listOf("trout", "форель"),
    "herring" to listOf("herring", "herrings", "сельдь", "селёдка", "селедка"),
    "mackerel" to listOf("mackerel", "mackerels", "скумбрия"),
    "sardines" to listOf("sardine", "sardines", "сардина", "сардины"),
    "cod" to listOf("cod", "треска"),
    "pollock" to listOf("pollock", "минтай"),
    "tilapia" to listOf("tilapia", "тилапия", "тиляпия"),
    "shrimp" to listOf("shrimp", "shrimps", "prawn", "prawns", "креветка", "креветки"),
    "squid" to listOf("squid", "кальмар", "кальмары"),
    "mussels" to listOf("mussel", "mussels", "мидия", "мидии"),
    "oysters" to listOf("oyster", "oysters", "устрица", "устрицы"),
    "crab" to listOf("crab", "crabs", "краб", "крабы"),
)

private val plantIdentities = mapOf(
    "tofu" to listOf("tofu", "тофу"),
    "tempeh" to listOf("tempeh", "темпе", "темпех"),
    "chickpeas" to listOf(
        "chickpea",
        "chickpeas",
        "chick pea",
        "chick peas",
        "garbanzo bean",
        "garbanzo beans",
        "нут",
        "турецкий горох",
    ),
    "hummus" to listOf("hummus", "houmous", "хумус"),
    "seitan" to listOf("seitan", "сейтан"),
    "soy_milk" to listOf("soy milk", "soya milk", "соевое молоко", "соевый напиток"),
    "oat_milk" to listOf("oat milk", "овсяное молоко", "овсяный напиток"),
    "almond_milk" to listOf("almond milk", "миндальное молоко", "миндальный напиток"),
    "coconut_milk" to listOf("coconut milk", "кокосовое молоко", "кокосовый напиток"),
    "peanut_butter" to listOf("peanut butter", "арахисовая паста", "арахисовое масло"),
    "chia_seeds" to listOf("chia seed", "chia seeds", "chia", "семена чиа", "чиа"),
    "flax_seeds" to listOf(
        "flax seed",
        "flax seeds",
        "flaxseed",
        "flaxseeds",
        "linseed",
        "linseeds",
        "семена льна",
        "льняные семена",
    ),
    "pumpkin_seeds" to listOf("pumpkin seed", "pumpkin seeds", "семена тыквы", "тыквенные семечки"),
    "sunflower_seeds" to listOf(
        "sunflower seed",
        "sunflower seeds",
        "семена подсолнечника",
        "подсолнечные семечки",
        "семечки подсолнечника",
    ),
)

private val dairyIdentities = mapOf(
    "cottage_cheese" to listOf("cottage cheese", "творог"),
    "kefir" to listOf("kefir", "кефир"),
    "sour_cream" to listOf("sour cream", "сметана"),
    "butter" to listOf("butter", "сливочное масло"),
    "cream" to listOf("cream", "сливки"),
    "mozzarella" to listOf("mozzarella", "mozzarella cheese", "моцарелла"),
    "feta" to listOf("feta", "feta cheese", "фета"),
    "cream_cheese" to listOf("cream cheese", "сливочный сыр", "творожный сыр", "крем-чиз", "крем чиз"),
    "parmesan" to listOf("parmesan", "parmesan cheese", "пармезан"),
)

private val expandedIdentityGroups = listOf(
    fruitIdentities,
    vegetableIdentities,
    grainIdentities,
    meatIdentities,
    fishIdentities,
    plantIdentities,
    dairyIdentities,
)

private val originalIdentityIds = listOf(
    "salmon",
    "broccoli",
    "quinoa",
    "avocado",
    "leafy_greens",
    "carrot",
    "red_bell_pepper",
    "cucumber",
    "olive_oil",
    "yellow_bell_pepper",
    "cauliflower",
    "edamame",
    "tahini",
    "soy_sauce",
    "salt",
    "blueberries",
    "sweet_potato",
    "apple",
    "banana",
    "orange",
    "tomato",
    "potato",
    "rice",
    "chicken",
    "egg",
    "cheese",
    "yogurt",
    "strawberry",
    "grapes",
    "beans",
    "mushroom",
    "bread",
    "beef",
    "lentils",
    "pear",
    "oats",
    "pasta",
    "milk",
    "watermelon",
    "lemon",
    "nuts",
    "fish",
    "onion",
    "peach",
    "mango",
    "pineapple",
    "kiwi",
)

private val stapleIdentities = mapOf(
    "brown_rice" to listOf("brown rice", "wholegrain rice", "бурый рис", "коричневый рис", "нешлифованный рис"),
    "almonds" to listOf("almond", "almonds", "миндаль"),
    "peanuts" to listOf("peanut", "peanuts", "арахис"),
    "walnuts" to listOf("walnut", "walnuts", "грецкий орех", "грецкие орехи"),
    "cashews" to listOf("cashew", "cashews", "кешью", "кэшью"),
    "pistachios" to listOf("pistachio", "pistachios", "фисташка", "фисташки"),
    "hazelnuts" to listOf("hazelnut", "hazelnuts", "фундук", "лесной орех", "лесные орехи"),
    "olives" to listOf("olive", "olives", "оливка", "оливки", "маслина", "маслины"),
)
