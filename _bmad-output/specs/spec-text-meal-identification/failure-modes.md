# Failure modes

Serves CAP-5 and the safety half of CAP-2/CAP-3. Each entry names the failure, why the existing invariants do not already cover it, and the control.

## FM-1 — Plausible mapping to a wrong but real record

**The failure.** A model maps "biryani" to a real, signed, correctly-versioned catalog record for a different dish. The result then passes every provenance check the architecture has: real record, real dataset version, real deterministic arithmetic, correct rounding, honest uncertainty copy. It is wrong, and it looks more trustworthy than a photo result because the user typed the words themselves.

**Why existing rules miss it.** AD-7 and AD-19 guarantee that numbers are *grounded*, not that identity is *correct*. FR-17 forbids model-generated numbers, and this failure produces none — the numbers are genuinely catalog-derived. The grounding chain is intact; the input to it is wrong.

**Control.** The engine proposes; the user confirms; nothing else may bind a canonical record. AD-7's confirmation step is not shortcut for text input on the reasoning that the user already named the food — naming a dish is not selecting a database record, and the gap between them is exactly where this failure lives. A negative test proves no code path reaches a Grounded Nutrition Result from a text candidate without a confirmation event, including at maximum confidence.

## FM-2 — Typed input read as higher-confidence than it is

**The failure.** Because the user authored the text, the system or the interface treats the match as settled — auto-selecting the top candidate, collapsing the review step, or preselecting a serving.

**Why existing rules miss it.** They mostly do not: AD-40 already forbids defaulting food identity, preparation, recipe, and materially uncertain values, and permits defaults only for `defaultLocale`, `measurementSystem`, `catalogProposal`, and one atomic `standardServing` after canonical-item confirmation. The risk is that a low-friction interaction budget (NFR-24, UX-DR37) reads as license to shorten the typed path specifically.

**Control.** State explicitly that user-authored text does not move a field into the defaultable allowlist. The interaction budget applies to the typed path exactly as it applies to the photographed one.

## FM-3 — Abstention collapsing into a guess

**The failure.** An ambiguous or unparseable description ("something with chicken") returns a confident single candidate rather than abstaining, because returning nothing feels like a broken feature.

**Why existing rules miss it.** The Abstention shape exists in the contract, but nothing currently obliges a *text* engine to use it, and the incentive runs the other way — a text box that often answers "I'm not sure" reads as failure.

**Control.** Abstention is a first-class outcome with a plain-language reason, identical in shape to the photo engines' (FR-5). An abstention routes to the deterministic floor, which is a real answer, not a dead end. Abstention rate is measurable against the evaluation corpus and belongs in the route baselines alongside local, remote, hybrid, and manual (FR-23).

## FM-4 — The fallback unavailable where it is needed most

**The failure.** The model path is treated as *the* text path. On a lower-tier device, offline — UJ-4's exact conditions — the model is not present, and the feature that exists to rescue that journey is the feature that cannot run in it.

**Why existing rules miss it.** NFR-3 and G-3 govern whether a model *may* activate; nothing forces a non-model path to exist beneath it.

**Control.** CAP-1 is the floor and does not depend on CAP-3. When the text engine reports any state other than `ready`, deterministic search remains reachable in one action, and no journey dead-ends (FR-20). This is why the floor is specified first and can ship alone.

## FM-5 — Free text leaking through telemetry or upload

**The failure.** A description reaches logs, analytics, crash artifacts, or a remote payload. Free text can carry more identifying detail than a food photo — a dish name, a restaurant, a home address typed by mistake.

**Why existing rules miss it.** FR-22 and AD-14 forbid "free-form labels" and free-form sensitive payloads, which covers this by intent. But the allowlist was written for a photo-only product and does not anticipate a text input field, and AD-8's ephemeral lifecycle is written around image bytes.

**Control.** The description is attempt-scoped and cleared on the same lifecycle as `PhotoRef`. It is never logged and never placed in analytics. It leaves the device only under the CAP-8 consent receipt bound to text-specific disclosure copy — the photo consent cannot authorize it, because the reviewed photo copy describes sending an image and that is not what happens. Redacted-artifact and forbidden-log capture tests under AD-31 extend to cover the text field.

Now that remote text is in scope, this failure has a second half: **the description may leave the device legitimately and still end up in the wrong jurisdiction.** Processing region resolves from market policy rather than from the food catalog, so a market with data-residency constraints constrains remote text for that market. Where that is unresolved — OQ-5 for Russia — remote text stays off for that market rather than defaulting to whichever provider region is nearest.

## FM-6 — Multi-item text silently reduced

**The failure.** "Two eggs and toast" yields one item, and the user reaches a grounded result for half their meal without noticing the other half was dropped. The result is not wrong about what it shows — it is wrong about what it omits, which is harder to see.

**Why existing rules miss it.** The photo path already produces multiple candidates per meal (FR-13), preserves per-item and aggregate provenance (FR-16), and aggregates per-item to meal totals (AD-19). Nothing extends that expectation to a typed description, and a text engine that quietly returns its single best guess still satisfies the contract's shape.

**Control.** Segmentation is a proposal the user can edit before confirming anything (CAP-7). A dropped item is therefore visible and recoverable rather than invisible and final — the user can add it back without restarting the session or losing already-confirmed items. Two supporting rules:

- An engine that cannot segment a description abstains with a plain-language reason. It never returns a confident subset as if it were the whole meal.
- Partial grounding stays visible, consistent with FR-17's rule that unresolved matches remain partial or unknown rather than being quietly completed.

This is the same instinct as FM-1: the engine proposes, the user decides, and the system never resolves its own uncertainty on the user's behalf.
