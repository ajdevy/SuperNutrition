# Review — G-3 device tier budget mechanism

Adversarial review of the uncommitted G-3 slice. Five lenses (doc honesty, logic,
test quality, requirement conformance, integration), then a refute-by-default
skeptic per deduplicated finding.

**15 raw findings → 11 after dedup → 3 survived, 8 refuted.**

All three survivors are the same defect reached from three directions, which is
the strongest signal this review produced.

## Survived

### F-1 — latency was quoted and then dropped (medium)

`G-3-device-matrix.md:8`, `G-3-device-matrix.md:19-28`, `DeviceTierBudget.kt:84`

The doc quotes the spine's exit artifact verbatim at line 8 — *"signed real-device
benchmark selecting OS/device tiers, runtimes, models, download policy, and
latency/memory/thermal/battery budgets"* — claims to turn it "into a list someone
can tick", and then never mentions latency again. The supply table has a row for
every other clause of that sentence. The satisfaction checklist has four items,
none about latency, and item 3 actively closes the gap: *"Each supported tier's
five budgets are present and positive — the type already refuses anything else,
so this is satisfied by construction or not at all."*

`DeviceTierBudget` has no latency field, and `grep -rniE latenc mobile/` finds no
latency budget type anywhere in the codebase.

The cause is straightforward: I built to NFR-3, which names storage, download,
memory, thermal and battery, and to E7.4's wording, which names four of those.
The *gate's* exit artifact and NFR-1 both add latency, and I quoted the gate
while implementing the NFR.

Consequence, if left: a benchmark operator working from this supply list measures
five dimensions on named devices, ticks every stated condition, and reports G-3's
inputs complete — while NFR-1 (`prd.md:595-597`) and the PRD's own G-3 row
(`prd.md:811`) both require latency budgets there. SM-2 and SM-3 stay unvalidated
assumptions A-4/A-5 as production local-engine stories unblock, and SM-C2 — "do
not run models that violate latency limits" — has no measured limit to enforce.

Both sibling docs cover every item they quote, including items with no code
representation at all: G-1 carries rows for "Unit and label rules" (*"Nothing
formats a number yet"*) and "Store claims" (*"gated separately by G-5"*). So a
row without a field is house style; a dropped row is not.

**Fix:** a sixth budget dimension on the type, a table row, and a checklist item.
The two verifiers narrowed the claim usefully — the runtimes/models/download-policy
row names no symbol and does not imply a field exists, and the
one-budget-per-(tier, platform) rule is deliberate rather than an obstacle to
comparing candidate runtimes, because the matrix is the shipped qualified set and
not the benchmark's workbench.

## Refuted, and worth recording

Eight findings did not survive. Four settle questions rather than dismissing
claims, and three of those defend decisions in this change that looked wrong.

- **The simulator markers match prose, not runtime API identifiers, so the check
  catches nothing on iOS.** Refuted on the premise. `mobile/domain/inference/src`
  contains only `commonMain` and `commonTest` — no platform source sets, no
  expect/actual — so `UIDevice.current.model` and `ro.product.model` are
  unreachable by construction. `BudgetEvidence` is an *authored* record, which
  `measuredOnEpochDay` already implies ("the domain has no clock"). The check
  guards what a person types when transcribing a benchmark. Worth saying out
  loud in the KDoc, since a careful reader had to derive it.
- **"No parameter-count input" is a naming claim, not a control.** Refuted. A
  fabricated budget is inert: the shipped matrix is empty, a test asserts it, and
  the only path to activation is editing that constant. Also, the example given
  (`modelStorageBytes = paramCount * 2`) is the correct storage figure for fp16
  weights — model storage legitimately derives from weight count. What NFR-3
  forbids is gating *by* parameter count instead of measuring, which is what the
  five (now six) per-device numbers demand.
- **Nothing reads `DeviceTierMatrix`, so "before activation" has no enforcement
  point.** Premise correct, conclusion refuted: there is no activation path in
  the repo to wire. No production class implements `PhotoInferenceEngine` or
  `InferenceEngineV2`. Adding the enforcement point means inventing the local
  text engine, which is the work G-3 explicitly blocks.
- **The five-dimensions test only rejects an explicitly-passed zero.** Refuted.
  The constructor has no default arguments today, so no wrong belief exists; the
  demanded assertion would need `kotlin.reflect`, which is JVM-only and not
  resolvable from `commonTest`. The test does catch deletion of any of the
  `require` lines, which is the mutation class a unit test can defend.
