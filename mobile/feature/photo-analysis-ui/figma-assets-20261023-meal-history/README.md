# Meal history design assets

Source: human-provided Supernutrition App Design Files, Figma file `08zQiW2IIHfQUBN3JDRUcZ`, node `604:893`, downloaded 2026-10-23.

The SVGs and three reference PNGs are unchanged design exports. Static UI SVGs were rendered at 4× into `src/commonMain/composeResources/drawable/history_*.png`. `history_back.svg` is byte-identical to the existing Profile back SVG; production reuses `profile_back.png`.

The salmon, egg and chicken photographs are copied unchanged into `src/jvmTest/resources/meal-history/` for the explicitly synthetic design fixture only. They are never referenced by production source or seeded into the ledger. Production confirmed foods contain no image or time; the screen shows the original placeholder and explains unavailable metadata.

The common Gregorian epoch-day converter in `MealHistoryDates.kt` adapts Howard Hinnant's `civil_from_days` formulas. Primary source: https://howardhinnant.github.io/date_algorithms.html#civil_from_days . The paper's Summary explicitly donates these formulas to the public domain. The Kotlin adaptation bounds supported epoch days before Long arithmetic; no external date runtime is added.
