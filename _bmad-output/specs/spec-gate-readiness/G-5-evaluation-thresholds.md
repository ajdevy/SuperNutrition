# G-5 readiness: what the evaluation thresholds must supply

**Owner:** evaluation owner + the principal who owns accuracy claims.
**Blocks:** public accuracy claims and confidence-based hybrid routing.

G-5 is the most downstream of the six gates, and saying so plainly is the most
useful thing this document does. Its exit artifact names four baselines —
local-only, remote-only, hybrid, manual — of which **three require another gate
first**, and one reported dimension (nutrition error) requires a fifth.

## What must be supplied

| Input | Why the code needs it | State today |
| --- | --- | --- |
| **Corpus protocol** | Nothing to run and nothing to run it on | `grep -rni corpus` over the repo returns zero matches |
| **The evaluation harness itself** | `settings.gradle.kts` registers `:evaluation:harness`; the directory does not exist and the include is skipped silently | Declared, absent, and nothing fails |
| **Local-only baseline** | Needs a local model, which needs a qualified device tier | Blocked on G-3 |
| **Remote-only and hybrid baselines** | Need a remote engine and a transport | Blocked on G-4 |
| **Manual baseline** | The only arm producible today | Not collected |
| **Quality / resource / cost thresholds** | No constant, config key or comparison exists for any of them | Absent |
| **Confidence threshold for escalation** | Would give `LOW_CONFIDENCE` its first producer | Absent; see below |
| **Materiality rule** | Nothing in code to attach it to | The word appears only in four planning documents |
| **Nutrition-error column** | Needs real gram and calorie data | Blocked on G-2; `SeedCatalog` is explicitly fake |

## What the code already commits to

- **Bands derive from the score at 0.5 and 0.8, in exactly one place.**
  `ConfidenceBand.fromScore` and `contracts/inference/v2` agree, and a
  `Confidence` whose band disagrees with its score cannot be constructed. If G-5
  wants calibrated bands instead, the schema says that is a new major release,
  not a relaxation.
- **Every candidate's provenance must name a calibration version.** The field is
  required and non-blank. Nothing reads or compares it — it is a slot for a G-5
  answer, not a check.
- **A successful local result is terminal.** No threshold G-5 sets may escalate
  it; the early return happens before any policy is consulted.
- **Routing today is confidence-free.** It branches on outcome kind and span
  validity, never on a score. There is no hidden threshold to discover.
- **`MatchStrength` is deliberately not a confidence score.** Tier plus permille
  coverage, comparable only within the one deterministic search that owns it. If
  G-5 later wants a score from the floor, this is the only number available and
  converting it is a decision, not a cast.

## What is declared and not true yet

- **`LOW_CONFIDENCE` is a published vocabulary value with no producer** — the
  exact shape the v2 binding verifier rejects one module over ("a vocabulary
  nothing emits is a claim, not a contract"). Keeping it documents intent;
  removing it makes the closed set honest. Either is defensible; the current
  state is inconsistent with the rule applied next door.
- **The 0.5/0.8 numbers are not machine-checked against the contract.** The
  binding verifier compares telemetry vocabularies, modality values, capability
  vocabularies and both field lists, and never reads `[result.confidence]`. The
  two agree today by having been written together.
- **The abstention number that exists is not the one FR-23 means.** Today's 32%
  is `CatalogSearch.search(query).isEmpty()` over 25 hand-written fixtures.
  FR-23's abstention is engine abstention sliced by baseline, device tier,
  cuisine and route. Adopting the fixture number as the G-5 baseline would be a
  category error. It is also a `println` to test stdout — written to no artifact,
  so a regression would leave no trace.
- **Nothing forbids one `InferenceItemGroup` ranking two engines' candidates**
  against each other with different calibration versions — which is precisely
  what the spine forbids without a validated transform. Cheap to add now,
  expensive to retrofit once a second engine ships.

## Questions to settle before the corpus is designed

1. **Which exit artifact is of record?** The spine says "quality, resource, cost,
   confidence, fallback, and materiality thresholds"; the PRD says "corpus
   report, quality/resource/cost thresholds, hybrid materiality rule, approved
   policy configuration". The spine adds confidence and fallback and drops the
   policy configuration.
2. **What does materiality quantify?** SM-10 frames it over two measures; SM-6
   lists six. A threshold defined over two and reported over six is two rules.
3. **Does G-5 own the calibration transform?** The spine makes cross-engine
   comparison conditional on one, and no gate's exit artifact lists it as a
   deliverable. If G-5 does not produce it, no gate does.
4. **Where does an escalation threshold live?** The band edges are frozen in a
   released contract, so a routing threshold must be a separate value with its
   own home. Today there is no key, constant or type for one.

## The dependency worth naming

G-5 cannot start with its own work. `hybrid` is not even a route the published
contract admits — the binding verifier uses adding it as a *negative* fixture.
The honest sequence is G-2 and G-3 or G-4 first, then a corpus, then thresholds.
What G-5 can do today is decide the four questions above, which cost nothing and
unblock the design.
