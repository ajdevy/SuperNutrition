# Inference contract — identification modality

Serves CAP-4. The contract as designed is photo-only and cannot carry a text engine. **No engine implements it yet**, so the fix is an amendment before the ports freeze, not a version migration.

## What the contract currently fixes

AD-2 states every engine implements `PhotoInferenceEngine` contract version `1`, with input "a `PhotoRef` plus immutable attempt context". The solution-design request invariants make `photo` a required field and require adapters to read bytes only through `PhotoContentSource`. There is no modality discriminator and no optional-payload path. A text engine cannot satisfy that shape without claiming a photo it does not have.

## Amendment, not migration

Story E3.1 — "Freeze contract-v1 inference ports" — has not run. Mobile is still `scaffold-pending`; there is no local engine, no remote adapter, no gateway mapping, and no evaluation consumer built against the contract. Nothing exists to migrate.

So modality is added to contract v1 **before** E3.1 freezes it, and story E7.1 is sequenced ahead of E3.1 for exactly this reason. The cost is editing an unimplemented type rather than versioning a live one, and the migration sequence below is the fallback that applies only if freezing has already happened.

This is worth stating plainly because the opposite reading is easy to reach: "contract v1 is frozen photo-only, therefore text needs v2." That inference is correct in structure and wrong in timing — v1 is *specified*, not *frozen*, and the difference is an entire story.

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

## Delivery — amendment path

The expected path, while no engine implements the contract:

1. Add the modality discriminator and text payload to contract v1 in `mobile/domain/inference`, in story E7.1, before E3.1 freezes the ports.
2. Extend AD-17's canonical fixtures with modality-aware cases. Every fixture authored before the amendment passes unchanged — that is the correctness test either way.
3. E3.1 then freezes a modality-aware contract, and the photo engines in E3.2 and E3.3 are built against it directly.

Per AR-33 this remains its own story landed before the concrete engine work that depends on it. It simply amends rather than versions.

## Fallback — migration path

Applies **only** if E3.1 has already frozen the ports photo-only before E7.1 lands. Then modality becomes a contract-breaking change and AR-33 requires a version:

1. Publish v2 alongside v1, with the compatibility matrix declaring both.
2. Extend fixtures with modality-aware cases; existing v1 fixtures pass unchanged.
3. Migrate photo engines to v2 with the `photo` modality, behaviour identical.
4. Introduce the text engine against v2.
5. Retire v1 only when no engine implements it, as a separate change.

The cost difference is the reason for the sequencing constraint in E7.1: the amendment edits an unimplemented type, while the migration touches every engine, the gateway mapping, and the evaluation consumer.

## Routing and remote text

Local runs first for text exactly as it does for photos. Remote text identification (CAP-8) is offered only on AD-6's existing closed trigger set — `local unavailability`, `Local Engine abstention`, `Gate G-5 low-confidence result` — so `RemoteEligibilityReason` gains no new value from this spec. Typing does not create a new route to upload.

**Consent copy is modality-specific.** `consentCopyVersion` in the country profile currently denotes the reviewed *photo*-transfer explanation, and the reviewed copy states that a smaller copy of the photo is sent. That sentence is false for a description. Remote text therefore requires its own reviewed copy version; the photo disclosure cannot authorize a text transfer, and a request presenting one for the other fails closed.

Everything else about consent is unchanged: per-attempt, unselected, equal-prominence accept and continue-without-upload, bound to a receipt carrying purpose, region and provider policy, retention version, configuration version, and transfer state.

**Processing region resolves from market policy, not from the catalog** (AD-12). A market whose law constrains where its residents' data may be processed constrains remote text for that market too — see OQ-5 in SPEC.md, which needs legal input rather than an engineering decision. Where that question is unresolved for a market, remote text stays off there and the on-device path plus the deterministic floor carry the journey.

A distinct Inference Route value for the text path is surfaced through confirmation per FR-12 and recorded in redacted telemetry per FR-22, so a user and the evaluation harness can both tell a typed result from a photographed one.
