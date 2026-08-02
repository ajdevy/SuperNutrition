# SuperNutrition Model Benchmark Report

- **Date:** 2026-08-02
- **Coverage:** two benchmark sessions
  - **Paid roster** — run 2026-07-29, results preserved unmodified (`openai/gpt-4o-mini`, `google/gemini-2.5-flash`, `openai/gpt-4o`, `anthropic/claude-sonnet-5`)
  - **Free roster** — run 2026-08-02 (`google/gemma-4-26b-a4b-it:free`, `nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free`, `nvidia/nemotron-nano-12b-v2-vl:free`)
- **Test images:** 5 photos in `backend/benchmark/images/`
- **Pricing/limits source:** OpenRouter `GET /v1/models`, fetched live 2026-08-02 (HTTP 200 on first attempt, no 403 encountered)
- **Prompt:** identical across both sessions (see `run-benchmark.mjs`), `temperature: 0`

> **Session asymmetry — read before comparing.** The paid session used `max_tokens: 512`; the
> free session used `max_tokens: 2048`. Latency also is not comparable across sessions: they ran
> on different days under different upstream load. Accuracy and schema-compliance comparisons are
> valid; head-to-head latency and output-length comparisons across sessions are not.

---

## ⚠️ Ground truth correction — two image filenames are wrong

The filenames do not describe the photos. Verified by opening each image:

| File | Filename implies | Actual content (verified visually) |
|------|------------------|------------------------------------|
| `01-salad-bowl.jpg` | salad bowl | ✅ correct — buddha bowl: avocado, cherry tomatoes, chickpeas, sweet potato, lettuce, red cabbage, watermelon radish, yellow pepper, microgreens |
| `02-burger-fries.jpg` | burger + fries | ❌ **paella** — rice, shrimp, lemon wedges, cilantro, in a black pan |
| `03-pasta-plate.jpg` | pasta plate | ✅ correct — shrimp spaghetti with cherry tomatoes and scallions |
| `04-breakfast-pancakes.jpg` | breakfast pancakes | ❌ **two burgers** — beef patty, avocado, radish, spinach, seeded bun + 2 Coca-Cola bottles |
| `05-salad-plate.jpg` | salad plate | ✅ correct — bowl with **tofu** cubes, boiled eggs, corn, cherry tomatoes, red cabbage, edamame, lettuce, cucumber, scallions |

**All scoring below uses actual image content**, per decision on 2026-08-02. Scoring against the
filenames would have marked every model wrong on photos 02 and 04 — measuring filename accuracy,
not model accuracy. The 2026-07-29 report did not flag this; its "success rate" counted any parsed
response, never checking whether the answer was right.

---

## 1. Quality — accuracy against actual image content

"Top-1 correct" = the rank-1 food matches the real dish. n=5 photos per model.

| Model | Session | Top-1 correct | Usable responses | Avg top-1 conf | Median top-1 conf | Avg conf (all foods) | Avg latency | Median latency |
|---|---|---|---|---|---|---|---|---|
| google/gemma-4-26b-a4b-it:free | free | **4/5** | 5/5 | 97.0% | 95.0% | 93.0% | 4.42s | 3.55s |
| nvidia/nemotron-3-nano-omni-30b:free | free | 3/5 | 4/5 | 96.2% | 95.0% | 97.2% | 1.02s | 0.90s |
| nvidia/nemotron-nano-12b-v2-vl:free | free | **5/5** | 5/5 | 95.0% | 95.0% | 79.0% | 0.68s | 0.60s |
| openai/gpt-4o | paid | **5/5** | 5/5 | 95.0% | 95.0% | 95.0% | 1.94s | 1.75s |
| openai/gpt-4o-mini | paid | **5/5** | 5/5 | 87.0% | 85.0% | 87.0% | 2.34s | 1.89s |
| anthropic/claude-sonnet-5 | paid | **5/5** | 5/5 | 89.0% | 90.0% | 78.2% | 4.11s | 1.90s |
| google/gemini-2.5-flash | paid | 4/5 | 4/5 | 95.8% | 95.0% | 77.3% | 1.97s | 2.00s |

### Failures and defects — all three are harness/schema issues, not vision failures

**`google/gemini-2.5-flash` on `01-salad-bowl` — harness truncation, not a model error.**
`completionTokens` is exactly **512**, equal to the harness's `max_tokens: 512`. The output was cut
mid-JSON and `JSON.parse` failed. The visible `raw` prefix shows a correct answer forming
(`"label": "salad", "confidence": 0.95`). The 2026-07-29 report recorded this as a model failure
(4/5). It is a harness cap. **Gemini's real vision accuracy on this set is 5/5.**

**`nemotron-3-nano-omni-30b` on `04-breakfast-pancakes` — schema violation, correct content.**
Returned a **bare JSON array** instead of the required `{"foods": [...]}` object:
```json
[ { "label": "hamburger", "rank": 1, "confidence": 0.96, ... },
  { "label": "hamburger", "rank": 1, "confidence": 0.94, ... } ]
```
Valid JSON, right answer (hamburger, 96%), wrong shape — so `parsed.foods` is undefined and the
result is unusable downstream. This is the documented "nemotron JSON instability", now
characterised precisely: **the failure mode is schema drift, not malformed JSON.**

**`nemotron-nano-12b-v2-vl` — 50% upstream 504 rate.**
10 requests were needed to obtain 5 results: **5 × `{"message":"Upstream idle timeout exceeded","code":504}`**.
Photos 04 and 05 succeeded only on retry (3rd and 2nd attempt). Note these 504s arrive **inside an
HTTP 200 body**, so status-code-based retry logic does not catch them — the initial run's retry
handler missed every one. Any gateway adapter must inspect the response body, not just the status.

### Confidence is not calibrated — this breaks the current two-pass routing

`model-profiles.toml` sets `confidence_threshold = 0.75` to trigger Pass 2. The observed confidence
values cannot support that decision:

- **`nemotron-3-nano-omni-30b`** returned `confidence = 1.0` for all 9 foods on photo 01 and `0.95`
  for all 9 on photo 05 — **and `rank = 1` for every item simultaneously**. There is no ranking and
  no confidence spread at all.
- **`nemotron-nano-12b-v2-vl`** returned a perfectly linear ramp on both multi-food photos:
  `0.95, 0.90, 0.85, 0.80, 0.75, 0.70, 0.65, 0.60, 0.55`. Confidence tracks list position, not evidence.
- **`gemma-4-26b`** never dropped below `0.80` on any food in any photo, including the one it got wrong.

Every free-roster top-1 confidence (95–100%) sits far above 0.75, so **Pass 2 would essentially never
fire, including on the one genuinely wrong answer.** The escalation path is currently inert.

### The one real vision error — and it is in the production primary

**`gemma-4-26b-a4b-it:free` identified the tofu in `05-salad-plate.jpg` as `salmon` at 95% confidence.**
Both nemotron models correctly identified it as tofu. gpt-4o, gpt-4o-mini and gemini said "salad
bowl"/"tofu"; claude-sonnet-5 said "Grilled Tofu Salad Bowl".

For a nutrition app this is the most consequential result in the report: salmon and tofu differ
completely in macros, and serving "salmon" to a vegan user is a dietary-integrity failure, not a
rounding error. It was delivered at 95% confidence, so no confidence gate would catch it.

### Output detail (same prompt, all models)

| Model | Avg foods/photo | Character |
|---|---|---|
| gemma-4-26b:free | 5.4 | Richest ingredient breakdown (10 items on photo 01); often lists ingredients without a dish label |
| nemotron-omni-30b:free | 4.0 | All-or-nothing: 9 items or 1; flat ranks/confidences |
| nemotron-12b:free | 5.4 | Good breakdown, monotonic confidence ramp |
| claude-sonnet-5 | 3.4 | Best dish-level naming; only model to spot the Coca-Cola bottles |
| gemini-2.5-flash | 5.3 | Detailed when it completes |
| gpt-4o | 1.0 | Single coarse label only — no ingredient breakdown |
| gpt-4o-mini | 1.0 | Single coarse label only |

---

## 2. Price

Live OpenRouter rates (fetched 2026-08-02) × observed average tokens from the result JSONs.

| Model | $/1M input | $/1M output | Avg prompt tok | Avg completion tok | **Est. cost/photo** | Per 1,000 photos |
|---|---|---|---|---|---|---|
| google/gemma-4-26b-a4b-it**:free** | $0.00 | $0.00 | 382 | 279 | **$0.000000** | $0.00 |
| nvidia/nemotron-3-nano-omni-30b**:free** | $0.00 | $0.00 | 640 | 1,090 | **$0.000000** | $0.00 |
| nvidia/nemotron-nano-12b-v2-vl**:free** | $0.00 | $0.00 | 2,087 | 266 | **$0.000000** | $0.00 |
| google/gemma-4-26b-a4b-it *(paid variant)* | $0.07 | $0.34 | 382 | 279 | **$0.000122** | **$0.12** |
| google/gemini-2.5-flash | $0.30 | $2.50 | 1,687 | 373 | **$0.001439** | $1.44 |
| openai/gpt-4o | $2.50 | $10.00 | 822 | 66 | **$0.002715** | $2.72 |
| openai/gpt-4o-mini | $0.15 | $0.60 | 25,586 | 60 | **$0.003874** | $3.87 |
| anthropic/claude-sonnet-5 | $2.00 | $10.00 | 842 | 327 | **$0.004954** | $4.95 |

All three production `:free` models confirmed at `prompt: 0, completion: 0` by the API — **cost = 0
in `model-profiles.toml` is correct.**

**Paid twin of the production primary:** `google/gemma-4-26b-a4b-it` (no `:free`) exists at
$0.07/$0.34 per 1M — **$0.12 per 1,000 photos**. Same model, no free-tier limits. The other two
free models have **no paid variant** on OpenRouter (`nemotron-3-nano-30b-a3b` is a different,
non-vision model — "no data" for a paid nemotron-omni equivalent).

### Two cost bugs in `run-benchmark.mjs`

1. **Gemini cost understated ~3×.** The harness hardcodes `gemini-2.5-flash` at `{in: 0.15, out: 0.60}`
   (line 32); the live API says **$0.30 in / $2.50 out**. Real cost is **$0.001439/photo**, not the
   $0.000477 in the stored JSONs and the old report. Gemini is still cheap, just not *that* cheap.
   Rates for gpt-4o, gpt-4o-mini and claude-sonnet-5 match the API and are correct.
2. **gpt-4o-mini burns 25,586 prompt tokens per photo** — 13–60× every other model on identical
   images (gpt-4o: 822). This is high-detail image tiling. It makes the *cheapest* per-token model
   the *most expensive* per photo — $3.87/1k vs gpt-4o's $2.72/1k, while returning strictly less
   detail. The 2026-07-29 note "GPT-4o-mini more accurate but 13x more tokens" understates it:
   at equal accuracy it costs more than the full-size model.

---

## 3. Limits

From `GET /v1/models`, 2026-08-02.

| Model | Context | Max completion | Input modalities | Provider observed | Per-model rate limit |
|---|---|---|---|---|---|
| gemma-4-26b-a4b-it:free | 262,144 | 32,768 | image, text, video | Darkbloom | no data (`limits: null`) |
| nemotron-3-nano-omni-30b:free | 256,000 | 65,536 | text, audio, image, video | Nvidia | no data (`limits: null`) |
| nemotron-nano-12b-v2-vl:free | 128,000 | 128,000 | image, text, video | Nvidia | no data (`limits: null`) |
| gemini-2.5-flash | 1,048,576 | 65,535 | file, image, text, audio, video | Google | no data (`limits: null`) |
| gpt-4o | 128,000 | 16,384 | text, image, file | OpenAI, Azure | no data (`limits: null`) |
| gpt-4o-mini | 128,000 | 16,384 | text, image, file | OpenAI, Azure | no data (`limits: null`) |
| claude-sonnet-5 | 1,000,000 | 128,000 | text, image, file | Amazon Bedrock, Google | no data (`limits: null`) |

All seven models confirm **vision support**. The `limits` field is `null` for every model — OpenRouter
does not expose per-model rate limits on this endpoint, and the `/v1/key` endpoint was not reachable
in this environment. **Per-account rate limits: no data from the API.**

Observed constraints from this run (measured, not assumed):

- **`nemotron-nano-12b-v2-vl:free`: 5 of 10 requests returned 504** ("Upstream idle timeout exceeded"),
  delivered inside HTTP 200 bodies. Recovered on retry every time.
- **No 403 Cloudflare blocks and no 429s** were observed at a 4s inter-request spacing across 25 total
  inference calls.
- Free-model latency was **not** the expected 10–30s — all three free models returned in 0.5–8s.
  The `gemma-4-31b-it:free` rate-limiting noted in `model-profiles.toml` on 2026-08-02 did not recur
  for `gemma-4-26b` in this session.
- The prior "free-model limit ~20 req/min" figure is **unverified** — not confirmed by API or by this run.

---

## 4. Conclusion and recommendation

### Stay on the free roster for the MVP. The blocker is not price — it is schema and calibration.

The free models are competitive on accuracy: `nemotron-nano-12b-v2-vl` scored 5/5 top-1, matching
gpt-4o and claude-sonnet-5 at zero cost. Paying $2.72–$4.95 per 1,000 photos buys **no measurable
accuracy gain** on this set. Do not move to a paid roster to buy quality — there is none to buy here.

### Fix these three before launch, in order

**1. The two-pass routing is inert — highest priority.**
`confidence_threshold = 0.75` never fires, because every free model reports 95–100% on everything
including wrong answers. Pass 2 currently costs latency and delivers no safety. Either derive
escalation from a real signal (label disagreement between two models, or absence of a dish-level
label) or drop the second pass and be explicit that there is no escalation. Do not ship a confidence
gate that cannot trigger.

**2. Swap the production primary for photo 05's failure mode.**
`gemma-4-26b:free` called tofu "salmon" at 95%. For a nutrition app that is a macro and
dietary-integrity error that no gate would catch. Recommended roster change:

| Profile | Current | Recommended |
|---|---|---|
| `fast-identify` | gemma-4-26b:free | **nemotron-nano-12b-v2-vl:free** — 5/5 top-1, fastest (0.68s), correct on tofu, monotonic confidence |
| `fast-identify-fallback` | nemotron-omni-30b:free | **gemma-4-26b:free** — richest breakdown, 5/5 schema-clean |
| `deep-identify` | nemotron-omni-30b:free | **gemma-4-26b:free** — nemotron-omni's schema drift makes it unfit as the deep-pass authority |
| `deep-identify-alt` | gemma-4-26b:free | **nemotron-omni-30b:free** (demote) |

Trade-off, stated plainly: nemotron-12b needs **retry-on-504 with body inspection** (50% first-attempt
failure). That is a solved engineering problem; a silent 95%-confidence protein misidentification is not.
`nemotron-3-nano-omni-30b` should not hold two profiles while it emits bare arrays and flat ranks.

**3. Harden the response parser.**
Accept a bare top-level array as `foods` (recovers the nemotron-omni failure), and treat
`finish_reason: "length"` / completion-tokens-equals-max as an explicit truncation error rather than
a parse failure (the gemini case). Both are cheap and each recovers one full failure in this set.

### If and when you do move to paid

Use **`google/gemma-4-26b-a4b-it`** (paid, no `:free`) at **$0.12 per 1,000 photos** — 23× cheaper
than gpt-4o, same weights as the model already validated here, and it removes free-tier 504s and
rate-limit exposure. At 10,000 photos/month that is **$1.20/month**. Avoid `gpt-4o-mini`: its 25.6k
prompt tokens per photo make it the most expensive option in the table while returning a single
coarse label. Reserve `claude-sonnet-5` for a genuine deep pass if one is ever needed — it gave the
best dish-level naming and was the only model to notice the beverages — at $4.95/1k.

### Benchmark methodology debt

This set is **5 photos** — too small for the accuracy differences above to be statistically
meaningful, and 2 of the 5 filenames were wrong. Before the roster decision is locked, expand to
50–100 photos with **verified** labels stored in a manifest file rather than encoded in filenames.
Treat the rankings here as directional.

---

## 🔴 Security finding — unrelated to model selection, needs action

**`backend/benchmark/run-benchmark.mjs` line 5 contains a live OpenRouter API key hardcoded in
plaintext, and the file is tracked in git.**

```js
const API_KEY = 'sk-or-v1-ad7bc28420...';
```

`backend/mobile-api/.env` is correctly gitignored, but this key is in the repository history and is
the same key the benchmark uses. **Rotate the key and load it from `.env`** as the free-roster script
does. Note that `USDA_API_KEY` and the RS256 private key in `.env` are protected by gitignore only —
worth confirming they were never committed earlier in history.

---

## Appendix — per-image results

### google/gemma-4-26b-a4b-it:free *(free session, 2026-08-02)*

| Image | Foods found | Confidence | Latency | Tokens | Cost |
|---|---|---|---|---|---|
| 01-salad-bowl | avocado, cherry tomatoes, chickpeas, sweet potato, lettuce, red cabbage, watermelon radish, yellow bell pepper, microgreens, dressing | 100–80% | 3.55s | 816 | $0 |
| 02-burger-fries *(paella)* | paella, shrimp, lemon, cilantro | 95, 90, 98, 85% | 2.36s | 645 | $0 |
| 03-pasta-plate | shrimp pasta, cherry tomatoes, scallions | 95, 98, 90% | 7.95s | 568 | $0 |
| 04-breakfast-pancakes *(burgers)* | hamburger | 100% | 2.91s | 452 | $0 |
| 05-salad-plate | ❌ **salmon** (actually tofu), boiled egg, corn, cherry tomatoes, red cabbage, edamame, lettuce, cucumber, scallions | 95, 98, 98, 95, 95, 90, 95, 90, 85% | 5.35s | 826 | $0 |

### nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free *(free session, 2026-08-02)*

| Image | Foods found | Confidence | Latency | Tokens | Cost |
|---|---|---|---|---|---|
| 01-salad-bowl | avocado, radish, cherry tomato, yellow bell pepper, lettuce, red cabbage, chickpeas, sweet potato, microgreens | all 100%, all rank 1 | 0.58s | 2,666 | $0 |
| 02-burger-fries *(paella)* | paella | 95% | 1.40s | 676 | $0 |
| 03-pasta-plate | shrimp noodles | 95% | 0.90s | 741 | $0 |
| 04-breakfast-pancakes *(burgers)* | ✗ **schema violation** — bare array, content correct (hamburger 96%) | — | 0.68s | 1,356 | $0 |
| 05-salad-plate | tofu ✓, egg, tomato, corn, edamame, lettuce, cucumber, red cabbage, green onion | all 95%, all rank 1 | 1.54s | 3,212 | $0 |

### nvidia/nemotron-nano-12b-v2-vl:free *(free session, 2026-08-02)*

| Image | Foods found | Confidence | Latency | Tokens | Cost |
|---|---|---|---|---|---|
| 01-salad-bowl | cherry tomatoes, avocado, purple cabbage, chickpeas, sweet potato, lettuce, radish, microgreens, yellow bell pepper | 95→55% ramp | 0.82s | 2,306 | $0 |
| 02-burger-fries *(paella)* | Paella | 95% | 0.46s | 1,957 | $0 |
| 03-pasta-plate | Shrimp, Spaghetti, Cherry Tomatoes, Green Onions, Garlic | 95→75% ramp | 0.50s | 2,155 | $0 |
| 04-breakfast-pancakes *(burgers)* | Avocado Burger, Beverage — *after 2 × 504 retries* | 95, 90% | 1.03s | 3,552 | $0 |
| 05-salad-plate | Tofu ✓, Edamame, Cherry Tomatoes, Purple Cabbage, Cucumber, Corn, Green Onions, Hard-Boiled Eggs, Lettuce — *after 1 × 504 retry* | 95→55% ramp | 0.60s | 1,794 | $0 |

### openai/gpt-4o-mini *(paid session, 2026-07-29 — unmodified)*

| Image | Foods found | Confidence | Latency | Tokens | Cost |
|---|---|---|---|---|---|
| 01-salad-bowl | salad | 90% | 1.68s | 25,649 | $0.00388 |
| 02-burger-fries *(paella)* | paella | 85% | 1.89s | 25,643 | $0.00387 |
| 03-pasta-plate | shrimp pasta | 85% | 3.50s | 25,646 | $0.00387 |
| 04-breakfast-pancakes *(burgers)* | burger | 90% | 3.35s | 25,646 | $0.00387 |
| 05-salad-plate | salad | 85% | 1.30s | 25,648 | $0.00388 |

### google/gemini-2.5-flash *(paid session, 2026-07-29 — unmodified)*

| Image | Foods found | Confidence | Latency | Tokens | Cost (as stored) |
|---|---|---|---|---|---|
| 01-salad-bowl | ✗ truncated at `max_tokens: 512` | — | 1.59s | 2,405 | $0.00059 |
| 02-burger-fries *(paella)* | Paella, Jambalaya, Fried Rice | 95, 70, 40% | 2.00s | 2,200 | $0.00047 |
| 03-pasta-plate | Shrimp and Noodle Stir-fry, Shrimp Scampi with Noodles, Spaghetti with Shrimp and Vegetables | 95, 80, 70% | 2.03s | 2,200 | $0.00047 |
| 04-breakfast-pancakes *(burgers)* | burger, avocado, radish, spinach, sesame seed bun | 98, 90, 85, 80, 75% | 2.37s | 1,642 | $0.00037 |
| 05-salad-plate | salad bowl, tofu, cherry tomatoes, boiled eggs, edamame, corn kernels, cucumber, red cabbage, lettuce, scallions | 95→55% | 1.84s | 1,851 | $0.00049 |

*Stored costs use the harness's understated gemini rate — see §2. Corrected: ~$0.00144/photo.*

### openai/gpt-4o *(paid session, 2026-07-29 — unmodified)*

| Image | Foods found | Confidence | Latency | Tokens | Cost |
|---|---|---|---|---|---|
| 01-salad-bowl | salad bowl | 95% | 1.75s | 930 | $0.00292 |
| 02-burger-fries *(paella)* | paella | 95% | 1.20s | 909 | $0.00271 |
| 03-pasta-plate | shrimp pasta | 95% | 2.04s | 911 | $0.00273 |
| 04-breakfast-pancakes *(burgers)* | burger | 95% | 3.15s | 772 | $0.00238 |
| 05-salad-plate | salad bowl | 95% | 1.55s | 921 | $0.00283 |

### anthropic/claude-sonnet-5 *(paid session, 2026-07-29 — unmodified)*

| Image | Foods found | Confidence | Latency | Tokens | Cost |
|---|---|---|---|---|---|
| 01-salad-bowl | Buddha Bowl Salad, Avocado, Roasted Sweet Potato, Chickpeas, Cherry Tomatoes | 90, 95, 85, 85, 90% | 8.60s | 1,116 | $0.00554 |
| 02-burger-fries *(paella)* | Paella, Shrimp Fried Rice, Seafood Rice | 85, 55, 40% | 1.73s | 957 | $0.00418 |
| 03-pasta-plate | Shrimp Pasta, Garlic Butter Shrimp Spaghetti, Asian-style Noodle Stir Fry | 90, 60, 40% | 1.90s | 1,039 | $0.00477 |
| 04-breakfast-pancakes *(burgers)* | Hamburger with beef patty/avocado/radish/spinach on seeded bun, Coca-Cola glass bottle | 95, 90% | 1.64s | 1,391 | $0.00457 |
| 05-salad-plate | Grilled Tofu Salad Bowl, Quail Eggs, Sweet Corn, Edamame | 85, 75, 90, 80% | 6.70s | 1,345 | $0.00574 |

---

## Reproducing

- Paid session: `node backend/benchmark/run-benchmark.mjs` — ⚠️ this **overwrites** all result JSONs
  *and* `BENCHMARK-REPORT.md`, and its `MODELS` array is hardcoded to the four paid models. It was
  **not** run for this report; the 2026-07-29 paid JSONs are byte-for-byte unmodified.
- Free session: run with a separate script that writes only `:free` result files and never touches
  the report — `max_tokens: 2048`, 4s inter-request spacing, retry on 403/429/5xx, key read from `.env`.
- Aggregation: all figures in this report are computed from the JSON files in `backend/benchmark/results/`.
  Anything absent from the JSONs or the API response is marked "no data".
