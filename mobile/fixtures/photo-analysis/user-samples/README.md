# User-provided food photo samples

These five JPEGs were supplied for on-device photo-analysis evaluation. The labels below are visual hypotheses for test triage, not calibrated ground truth. Ingredient identity is marked as uncertain where the image does not support a reliable claim.

| File | Provisional dish hypothesis | Visible or likely components |
| --- | --- | --- |
| `2026-08-02_22-30-34-breakfast-bacon-eggs-fruit.jpg` | Breakfast plate | Bacon, scrambled eggs, blueberries, strawberries |
| `2026-08-02_22-30-42-chicken-salad-vegetables.jpg` | Chicken-and-vegetable salad plate | Likely chicken pieces, lettuce, carrots, cherry tomatoes, red pepper; exact protein cut is uncertain |
| `2026-08-02_22-30-47-fruit-cottage-cheese-roasted-vegetables.jpg` | Fruit and cottage-cheese plate with roasted vegetables | Watermelon, raspberries, blackberries, pineapple, cottage cheese, orange roasted vegetable cubes; exact roasted vegetable is uncertain |
| `2026-08-02_22-30-52-green-salad-protein.jpg` | Mixed green salad with cubed protein | Leafy greens, red onion, cucumber, cubed protein likely tofu or chicken; protein identity is uncertain |
| `2026-08-02_22-30-57-mixed-meal-beef-rice-salad-vegetables.jpg` | Mixed buffet plate | Shredded roast meat likely beef or pork, rice, cauliflower, broccoli or Brussels sprouts, lettuce, tomato, cucumber, corn, red onion |

These samples are for regression and exploratory device testing only. They are not consented weighed meals and must not be used as production calibration or G3 approval evidence.

## Android device check

On 2026-08-02 these samples were imported one by one through the system photo picker on the physical `DNY-NX9 - 16` device. All five previews rendered successfully, but all five remained at `Photo ready`; no dish candidates or ingredient regions were produced. This is a historical integration finding, not a model-quality score. The current app composition root provides the Android PoC dish/component runtime through `PhotoAnalysisAndroidRuntimeProvider`; this older five-sample result must not be read as evidence about the current runtime.

The surrounding device gates still pass: `:apps:androidApp:connectedAndroidTest` ran production and uiAcceptance with 15 tests each and zero failures, and `:inference:local-android:connectedAndroidDeviceTest` ran 4 tests with zero failures. These prove the host/adapter smoke paths, not end-to-end food inference on these photos.
