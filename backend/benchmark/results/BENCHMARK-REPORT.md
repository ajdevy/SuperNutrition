# SuperNutrition Model Benchmark Report

- **Date:** 2026-07-29
- **Models tested:** GPT-4o-mini (Pass 1 fallback), Gemini 2.5 Flash (Pass 1 main), GPT-4o (Pass 2), Claude Sonnet 5 (Pass 2 alt)
- **Test images:** 01-salad-bowl.jpg, 02-burger-fries.jpg, 03-pasta-plate.jpg, 04-breakfast-pancakes.jpg, 05-salad-plate.jpg

## Results by Model

### GPT-4o-mini (Pass 1 fallback)

| Metric | Value |
|--------|-------|
| Success rate | 5/5
| Avg latency | 2.35s
| Avg cost/req | $0.00387
| Total cost | $0.01937
| Avg tokens | 25646
| Avg confidence | 87.0%

| Image | Foods Found | Confidence | Latency | Tokens | Cost |
|-------|-------------|-----------|---------|--------|------|
| 01-salad-bowl.jpg | salad (#1) | 90% | 1.68s | 25649 | $0.00388 |
| 02-burger-fries.jpg | paella (#1) | 85% | 1.89s | 25643 | $0.00387 |
| 03-pasta-plate.jpg | shrimp pasta (#1) | 85% | 3.50s | 25646 | $0.00387 |
| 04-breakfast-pancakes.jpg | burger (#1) | 90% | 3.35s | 25646 | $0.00387 |
| 05-salad-plate.jpg | salad (#1) | 85% | 1.30s | 25648 | $0.00388 |

### Gemini 2.5 Flash (Pass 1 main)

| Metric | Value |
|--------|-------|
| Success rate | 4/5
| Avg latency | 2.06s
| Avg cost/req | $0.00045
| Total cost | $0.00179
| Avg tokens | 1973
| Avg confidence | 77.5%

| Image | Foods Found | Confidence | Latency | Tokens | Cost |
|-------|-------------|-----------|---------|--------|------|
| 01-salad-bowl.jpg | ✗ no parse | — | 1.59s | 2405 | $0.00059 |
| 02-burger-fries.jpg | Paella (#1), Jambalaya (#2), Fried Rice (#3) | 95%, 70%, 40% | 2.00s | 2200 | $0.00047 |
| 03-pasta-plate.jpg | Shrimp and Noodle Stir-fry (#1), Shrimp Scampi with Noodles (#2), Spaghetti with Shrimp and Vegetables (#3) | 95%, 80%, 70% | 2.03s | 2200 | $0.00047 |
| 04-breakfast-pancakes.jpg | burger (#1), avocado (#2), radish (#3), spinach (#4), sesame seed bun (#5) | 98%, 90%, 85%, 80%, 75% | 2.37s | 1642 | $0.00037 |
| 05-salad-plate.jpg | salad bowl (#1), tofu (#2), cherry tomatoes (#3), boiled eggs (#4), edamame (#5), corn kernels (#6), cucumber (#7), red cabbage (#8), lettuce (#9), scallions (#10) | 95%, 90%, 85%, 80%, 75%, 70%, 70%, 65%, 60%, 55% | 1.84s | 1851 | $0.00049 |

### GPT-4o (Pass 2)

| Metric | Value |
|--------|-------|
| Success rate | 5/5
| Avg latency | 1.94s
| Avg cost/req | $0.00272
| Total cost | $0.01359
| Avg tokens | 889
| Avg confidence | 95.0%

| Image | Foods Found | Confidence | Latency | Tokens | Cost |
|-------|-------------|-----------|---------|--------|------|
| 01-salad-bowl.jpg | salad bowl (#1) | 95% | 1.75s | 930 | $0.00292 |
| 02-burger-fries.jpg | paella (#1) | 95% | 1.20s | 909 | $0.00271 |
| 03-pasta-plate.jpg | shrimp pasta (#1) | 95% | 2.04s | 911 | $0.00273 |
| 04-breakfast-pancakes.jpg | burger (#1) | 95% | 3.15s | 772 | $0.00238 |
| 05-salad-plate.jpg | salad bowl (#1) | 95% | 1.55s | 921 | $0.00283 |

### Claude Sonnet 5 (Pass 2 alt)

| Metric | Value |
|--------|-------|
| Success rate | 5/5
| Avg latency | 4.11s
| Avg cost/req | $0.00496
| Total cost | $0.02479
| Avg tokens | 1170
| Avg confidence | 77.5%

| Image | Foods Found | Confidence | Latency | Tokens | Cost |
|-------|-------------|-----------|---------|--------|------|
| 01-salad-bowl.jpg | Buddha Bowl Salad (#1), Avocado (#2), Roasted Sweet Potato (#3), Chickpeas (#4), Cherry Tomatoes (#5) | 90%, 95%, 85%, 85%, 90% | 8.60s | 1116 | $0.00554 |
| 02-burger-fries.jpg | Paella (#1), Shrimp Fried Rice (#2), Seafood Rice (#3) | 85%, 55%, 40% | 1.73s | 957 | $0.00418 |
| 03-pasta-plate.jpg | Shrimp Pasta (#1), Garlic Butter Shrimp Spaghetti (#2), Asian-style Noodle Stir Fry (#3) | 90%, 60%, 40% | 1.90s | 1039 | $0.00477 |
| 04-breakfast-pancakes.jpg | Hamburger with beef patty, avocado, radish, and spinach on seeded bun (#1), Coca-Cola glass bottle beverage (#2) | 95%, 90% | 1.64s | 1391 | $0.00457 |
| 05-salad-plate.jpg | Grilled Tofu Salad Bowl (#1), Quail Eggs (#2), Sweet Corn (#3), Edamame (#4) | 85%, 75%, 90%, 80% | 6.70s | 1345 | $0.00574 |

## Summary

| Model | Success | Avg Latency | Avg Cost | Avg Confidence |
|-------|---------|-----------|---------|----------------|
| GPT-4o-mini (Pass 1 fallback) | 5/5 | 2.35s | $0.00387 | 87.0% |
| Gemini 2.5 Flash (Pass 1 main) | 4/5 | 2.06s | $0.00045 | 77.5% |
| GPT-4o (Pass 2) | 5/5 | 1.94s | $0.00272 | 95.0% |
| Claude Sonnet 5 (Pass 2 alt) | 5/5 | 4.11s | $0.00496 | 77.5% |

## Model Recommendations

Based on this benchmark:

- **Pass 1 (fast):** Best cost-performance: Gemini 2.5 Flash (Pass 1 main) ($0.00037/req, 2.37s)
- **Pass 2 (deep):** For low-confidence fallback, use the more capable model
- **Confidence threshold 75%** — will trigger Pass 2 when primary model is uncertain

## Raw Results

Per-request JSON results saved in `results/` directory.
