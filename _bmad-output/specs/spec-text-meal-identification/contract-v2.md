# Inference contract v2 — modality

Serves CAP-4. Contract v1 is photo-only by construction and cannot carry a text engine, so this is a versioned migration under AR-33, not an edit.

## What v1 fixes

AD-2 states every engine implements `PhotoInferenceEngine` contract version `1`, with input "a `PhotoRef` plus immutable attempt context". The solution-design request invariants make `photo` a required field and require adapters to read bytes only through `PhotoContentSource`. There is no modality discriminator and no optional-payload path. A text engine cannot satisfy v1 without lying about a photo it does not have.

Story E3.1 freezes these ports. The migration cost rises once concrete engines are built against v1, so the modality decision is worth settling before that story lands.

## v2 shape

**Modality discriminator.** The request carries exactly one identification payload, selected by a closed, sealed modality field:

- `photo` — a `PhotoRef`, as in v1.
- `text` — a user-authored description plus the locale it was entered in.

Exactly one. A request carrying neither or both fails closed as a typed contract error rather than defaulting to a modality — an ambiguous request is a bug, not an input.

**Unchanged from v1.** Everything that makes the contract safe is preserved verbatim:

- Output is identification evidence — ranked candidates, per-engine calibrated confidence, assumptions, provenance — and contains no calories, macros, or micronutrients.
- The engine receives no nutrition catalog and cannot emit numeric nutrients.
- `attemptId`, `countryProfileId`, permitted route, and locale remain immutable for the attempt.
- Capability states, Abstention shape, and typed errors are identical across modalities.

The safety properties are modality-independent, which is the reason this belongs in the shared contract rather than in a parallel subsystem.

**Naming.** `PhotoInferenceEngine` becomes modality-neutral under v2. The rename is part of the migration, not a separate refactor.

## Text payload

| Field | Rule |
| --- | --- |
| `description` | User-authored free text. Never logged, never placed in analytics, never included in a remote payload without the same explicit consent that governs photos (FR-22, AD-8, AD-14) |
| `inputLocale` | The locale the text was entered in, distinct from `countryProfileId`, because AD-12 separates country from language |
| Lifetime | Scoped to the attempt and cleared with it, on the same lifecycle as `PhotoRef` under AD-8 |

Free text is treated as sensitive by default. It can carry more identifying detail than a food photo, and nothing in the current telemetry allowlist anticipates it.

## Segmented response

A text request returns one candidate group per detected food item, mirroring the photo path's existing multi-candidate shape (FR-13, FR-16, AD-19). Each group carries its own ranked candidates, confidence, and unresolved assumptions, and each is confirmed independently.

Each group also carries the span of the description it was derived from, so the interface can show which words produced which item. That span is what makes the segmentation editable under CAP-7 — a user merging or splitting items is editing a proposal with visible provenance, not re-typing blind.

An engine that cannot segment abstains for the whole request rather than returning a confident subset. Partial segmentation presented as complete is the FM-6 failure.

## Migration

1. Publish v2 alongside v1 in `mobile/domain/inference`, with the compatibility matrix declaring both.
2. Extend AD-17's canonical fixtures with modality-aware cases. Existing v1 fixtures must pass unchanged — that is the migration's correctness test.
3. Migrate photo engines to v2 with the `photo` modality, behavior identical.
4. Introduce the text engine against v2.
5. Retire v1 only when no engine implements it, as a separate change.

Per AR-33, this is its own story landed before the concrete engine work that depends on it, and the contract lands before either engine.

## Routing and remote text

Local runs first for text exactly as it does for photos. Remote text identification (CAP-8) is offered only on AD-6's existing closed trigger set — `local unavailability`, `Local Engine abstention`, `Gate G-5 low-confidence result` — so `RemoteEligibilityReason` gains no new value from this spec. Typing does not create a new route to upload.

**Consent copy is modality-specific.** `consentCopyVersion` in the country profile currently denotes the reviewed *photo*-transfer explanation, and the reviewed copy states that a smaller copy of the photo is sent. That sentence is false for a description. Remote text therefore requires its own reviewed copy version; the photo disclosure cannot authorize a text transfer, and a request presenting one for the other fails closed.

Everything else about consent is unchanged: per-attempt, unselected, equal-prominence accept and continue-without-upload, bound to a receipt carrying purpose, region and provider policy, retention version, configuration version, and transfer state.

**Processing region resolves from market policy, not from the catalog** (AD-12). A market whose law constrains where its residents' data may be processed constrains remote text for that market too — see OQ-5 in SPEC.md, which needs legal input rather than an engineering decision. Where that question is unresolved for a market, remote text stays off there and the on-device path plus the deterministic floor carry the journey.

A distinct Inference Route value for the text path is surfaced through confirmation per FR-12 and recorded in redacted telemetry per FR-22, so a user and the evaluation harness can both tell a typed result from a photographed one.
