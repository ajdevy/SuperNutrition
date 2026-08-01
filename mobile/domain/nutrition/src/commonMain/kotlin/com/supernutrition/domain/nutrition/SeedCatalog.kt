package com.supernutrition.domain.nutrition

/**
 * A small fake catalog standing in for a licensed nutrition package.
 *
 * Gate G-2 blocks grounding beyond a fake catalog, not development against one,
 * so this exists to give the search floor something real to search before the
 * licensed package is approved. It carries no nutrient values at all: those come
 * only from an approved package, and a fake calorie count is exactly the kind of
 * plausible-but-wrong number the grounding chain exists to prevent.
 *
 * Target languages are English and Russian. The alternate names are the point:
 * a record reachable only by its display name serves whichever wording one
 * cataloguer happened to choose, which is the failure UJ-3 exists to catch.
 * English alone still needs them — aubergine and eggplant are the same food.
 *
 * Russian entries carry inflected forms explicitly rather than relying on a
 * stemmer. Russian nouns decline across six cases, and enumerating the forms
 * that actually get typed is honest data; a stemmer that half-works is a silent
 * source of misses. The real package should carry a full morphological list or
 * declare lemmatization in its language matrix.
 */
object SeedCatalog {
    val records: List<CatalogRecord> = listOf(
        CatalogRecord(
            canonicalItemId = "food:avocado-raw",
            displayName = "Avocado",
            alternateNames = listOf(
                "avocado raw",
                "авокадо",
                "avokado",
            ),
        ),
        CatalogRecord(
            canonicalItemId = "food:bean-black",
            displayName = "Beans, black",
            alternateNames = listOf(
                "black beans",
                "чёрная фасоль",
                "фасоль",
                "fasol",
            ),
        ),
        CatalogRecord(
            canonicalItemId = "food:chicken-breast-raw",
            displayName = "Курица, грудка",
            alternateNames = listOf(
                // Nominative plus the oblique forms a user actually types.
                "курица",
                "курицы",
                "курицу",
                "куриная грудка",
                // Latin-script entry of a Cyrillic record: a bilingual user or
                // anyone without a Cyrillic keyboard types it this way.
                "kuritsa",
                "kuritsa grudka",
                "chicken breast",
            ),
        ),
        CatalogRecord(
            canonicalItemId = "food:egg-boiled",
            displayName = "Egg, boiled",
            alternateNames = listOf(
                "boiled egg",
                "hard boiled egg",
                "яйцо вареное",
                "яйцо",
            ),
        ),
        CatalogRecord(
            canonicalItemId = "food:bread-rye",
            displayName = "Bread, rye",
            alternateNames = listOf(
                "rye bread",
                // Toast is how this bread gets named at breakfast, and every
                // tier requires all query tokens to land on one record, so a
                // preparation word the catalog does not know sinks the whole
                // query. Naming it here is the narrow fix: a licensed package
                // may well carry toast as its own record, and should, since
                // toasting drives off water and moves the per-gram numbers.
                "rye toast",
                "toasted rye",
                "ржаной хлеб",
                "хлеб ржаной",
                "ржаной тост",
            ),
        ),
        CatalogRecord(
            canonicalItemId = "food:eggplant-raw",
            displayName = "Eggplant",
            alternateNames = listOf(
                // Same food, different English. A British user typing aubergine
                // must not miss a record written in American English.
                "aubergine",
                "баклажан",
            ),
        ),
        CatalogRecord(
            canonicalItemId = "food:coriander-leaf",
            displayName = "Coriander, leaf",
            alternateNames = listOf(
                "cilantro",
                "fresh coriander",
                "кинза",
            ),
        ),
        CatalogRecord(
            canonicalItemId = "food:rice-white-cooked",
            displayName = "Rice, white, cooked",
            alternateNames = listOf(
                "white rice",
                "рис",
                "ris",
            ),
        ),
    )
}
