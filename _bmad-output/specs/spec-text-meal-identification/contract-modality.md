# Inference contract — identification modality

Serves CAP-4. v1 is photo-only and v2 freezes the mainline photo component/mass
evidence shape. Both are **released, immutable, digest-pinned artifacts**, so
the ranked text result shape arrives as v3, not as an edit to either release.

## What v1 fixed, and how hard

Story 3.1 shipped `contracts/inference/v1`. Its own files close the question:

| Source | Statement |
| --- | --- |
| `RELEASE.md` | "Immutable, language-neutral, data-only contract release" |
| `compatibility-matrix.toml` | `digest = "3634abd4…"` — SHA-256 over schema, matrix, and canonical fixtures |
| `schema.toml` | `contract_id = "photo-inference-engine"`, `request = "opaque-photo-ref-plus-immutable-attempt-context"` |
| `schema.toml` | `incompatible_change = "new-major-release"` |
| `InferenceContract.kt` | `PhotoInferenceRequest(val photoRef: PhotoRef, …)` — non-nullable |

There is no modality discriminator and no optional-payload path, and a text engine cannot satisfy that shape without claiming a photo it does not have. The compatibility rule means the contract itself mandates a new major release, and AD-36 says the same at the architecture level: releases under `contracts/` are immutable.

An earlier revision of this file added "the digest means any edit to v1 is detectable and invalid." That was false, and commit `83547213` proved it: four published releases were edited in place, every digest was updated in the same commit, and all four validators passed. A digest recorded inside the bytes it describes cannot detect a self-consistent rewrite. Detection now comes from `scripts/verify-release-immutability.sh`, which compares against a base revision from git rather than from the reviewed tree — so the v2 requirement rests on the compatibility rule and on a gate that works, not on a property the digest never had.

## Migration, not amendment

An earlier draft of this file scoped modality as an amendment landed before story 3.1 froze the ports, on the reasoning that nothing implemented the contract yet so nothing needed migrating. **That window has closed.** Story 3.1 has shipped, v1 is released and digest-pinned, and the migration path below is now the only path.

Two consequences the amendment framing did not carry:

**The contract id is itself photo-shaped.** `photo-inference-engine` cannot
describe a contract that also accepts text, so v2 took a modality-neutral id for
the mainline photo evidence, and v3 carries the final modality-specific
photo/text result shapes. The Kotlin bindings gain new release-specific types;
v1 and v2 are not widened.

**The cost decays monotonically.** Exactly one consumer implements v1 today. Every further Engine story — local Android, local iOS, gateway, evaluation — adds another to migrate. Nothing is blocked on this decision, but it gets more expensive with each one, so it is worth settling whether typed identification is happening rather than letting the answer emerge.

## Shape

**Modality discriminator.** The request carries exactly one identification payload, selected by a closed, sealed modality field:

- `photo` — a `PhotoRef`, as originally specified.
- `text` — a user-authored description plus the locale it was entered in.

Exactly one. A request carrying neither or both fails closed as a typed contract error rather than defaulting to a modality — an ambiguous request is a bug, not an input.

**Unchanged.** Everything that makes the contract safe is preserved verbatim:

- Output is identification evidence — ranked candidates, per-engine calibrated confidence, assumptions, provenance — and contains no calories, macros, or micronutrients.
- The engine receives no nutrition catalog and cannot emit numeric nutrients.
- `attemptId`, `countryProfileId`, permitted route, and locale remain immutable for the attempt.
- Capability states, Abstention shape, and typed errors are identical across modalities.

The safety properties are modality-independent, which is the reason this belongs in the shared contract rather than in a parallel subsystem.

**Naming.** `PhotoInferenceEngine` becomes modality-neutral. Since no engine implements it, this is a rename of an unreferenced type rather than a refactor.

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

## Delivery — v3 release and migration

Story E7.1, under AR-33, landed before the engine work that consumes it:

1. Keep `contracts/inference/v1` and `contracts/inference/v2` byte-stable.
   v2 remains the mainline visible-component/mass photo release.
2. Author `contracts/inference/v3` with the modality-neutral id, closed
   photo/text requests, photo component/mass results, ranked text groups with
   source spans, and its own digest.
3. Declare v1/v2/v3 coexistence and supported v3 producer/consumer pairs in the
   v3 compatibility matrix.
4. Add v3 types in `mobile/domain/inference` alongside the v1 and v2 types,
   rather than editing either published binding.
5. Migrate text producers and consumers to v3; photo producers may remain on
   v2 until their v3 migration is explicitly scheduled.
6. Retire an older release only when no producer or consumer implements it, as
   a separate change.

`scripts/verify-inference-contract.sh` must verify v1 and v2, and invoke the
v3 contract verifier; the v3 binding verifier and negative fixtures must also
pass. That is the correctness test: v1 and v2 remain immutable, while v3
combines the mainline photo shape with the Epic 7 text shape.

## Routing and remote text

Local runs first for text exactly as it does for photos. Remote text identification (CAP-8) is offered only on AD-6's existing closed trigger set — `local unavailability`, `Local Engine abstention`, `Gate G-5 low-confidence result` — so `RemoteEligibilityReason` gains no new value from this spec. Typing does not create a new route to upload.

**Consent copy is modality-specific.** `consentCopyVersion` in the country profile currently denotes the reviewed *photo*-transfer explanation, and the reviewed copy states that a smaller copy of the photo is sent. That sentence is false for a description. Remote text therefore requires its own reviewed copy version; the photo disclosure cannot authorize a text transfer, and a request presenting one for the other fails closed.

Everything else about consent is unchanged: per-attempt, unselected, equal-prominence accept and continue-without-upload, bound to a receipt carrying purpose, region and provider policy, retention version, configuration version, and transfer state.

**Processing region resolves from market policy, not from the catalog** (AD-12). A market whose law constrains where its residents' data may be processed constrains remote text for that market too — see OQ-5 in SPEC.md, which needs legal input rather than an engineering decision. Where that question is unresolved for a market, remote text stays off there and the on-device path plus the deterministic floor carry the journey.

A distinct Inference Route value for the text path is surfaced through confirmation per FR-12 and recorded in redacted telemetry per FR-22, so a user and the evaluation harness can both tell a typed result from a photographed one.
