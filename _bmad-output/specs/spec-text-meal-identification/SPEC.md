---
id: SPEC-text-meal-identification
companions:
  - search-mechanics.md
  - contract-modality.md
  - failure-modes.md
  - ../../planning-artifacts/architecture/architecture-SuperNutrition-2026-07-26/ARCHITECTURE-SPINE.md
  - ../../planning-artifacts/prds/prd-SuperNutrition-2026-07-26/prd.md
  - ../../planning-artifacts/ux-designs/ux-SuperNutrition-2026-07-26/EXPERIENCE.md
sources: []
---

> **Canonical contract.** This SPEC and the files in `companions:` are the complete, preservation-validated contract for what to build, test, and validate. Source documents listed in frontmatter are for traceability only — consult them only if you need narrative rationale or prose color this contract intentionally omits.

# Text Meal Identification

## Why

**A pain to solve, on a load-bearing path.** Manual catalog search is the terminal fallback for every failure in SuperNutrition: a wrong candidate (UJ-3), no engine available (UJ-4), camera permission permanently denied (FR-3), and remote consent declined (UJ-2 edge case) all end in the same place. Its entire specification is FR-14, UX-DR19, and one row in the EXPERIENCE surface table — no rule defines match semantics, ranking, index strategy, or query normalization anywhere in the planning corpus. A user facing a catalog of hundreds of thousands of records with an unspecified search is the weakest link in an otherwise rigorous plan, and it is the link carrying the product's cross-cultural claim, since UJ-3 exists to prove SuperNutrition works beyond mainstream US meals.

Typing what you ate is also a better interaction than paging a result list, and it degrades gracefully in exactly the conditions where the photo path has already failed. This spec makes deterministic search a specified floor that works on every supported device, and adds an optional on-device language model above it that turns a natural description into ranked candidates — without either path ever producing a nutrition number.

## Capabilities

- id: CAP-1
  intent: A user can find a canonical catalog record by typing food text, on the lowest supported device tier, offline, with no model present.
  success: Given the bundled country package and no network, a documented query set resolves to expected canonical IDs under the match and ranking rules in `search-mechanics.md`; the same set passes on a lowest-tier device with every inference engine reporting `unavailable`.

- id: CAP-2
  intent: A user can describe a whole meal in natural language and receive one ranked candidate set per detected food item, instead of composing catalog queries themselves.
  success: A multi-item description yields one candidate group per detected item, in Candidate, Abstention, provenance, and typed-error shapes identical to those returned by photo engines and containing no calories, macros, or micronutrients; a description the engine cannot segment yields Abstention with a plain-language reason rather than a silently partial result.

- id: CAP-3
  intent: The system can run an on-device language model to serve CAP-2 where device capability permits, and degrade to CAP-1 where it does not.
  success: The text engine reports the FR-6 capability states (`unavailable`, `model-required`, `downloading`, `ready`, `temporarily unavailable`, `unsupported`); when it is not `ready`, the deterministic floor remains reachable in one action and no journey dead-ends.

- id: CAP-4
  intent: Inference engines can accept either a photo or a text description through one versioned contract, so identification modality is a contract property rather than a parallel subsystem.
  success: Photo and text engines both implement a modality-aware `contracts/inference/v2`; v1 stays byte-identical and its recorded digest still verifies; the compatibility matrix declares both releases; a request carrying neither or both modality payloads fails closed.

- id: CAP-5
  intent: A text-derived candidate binds to a canonical catalog record only through explicit user confirmation, never automatically.
  success: No code path maps engine output to a canonical record without a user confirmation event; a negative test proves a high-confidence text candidate cannot reach a Grounded Nutrition Result unconfirmed.

- id: CAP-6
  intent: A user can see that a result came from a text description rather than a photo, and which engine produced it.
  success: The result surfaces a distinct Inference Route value for the text path alongside `local` and `remote`, preserved through confirmation per FR-12 and recorded in redacted telemetry per FR-22.

- id: CAP-7
  intent: A user can correct how a description was split into items — merging, splitting, removing, or adding one — before confirming any of them.
  success: Every proposed segmentation is editable before confirmation; tests prove an item the engine missed can be added and an item it invented can be removed, and that neither action restarts the Analysis Session or discards already-confirmed items.

- id: CAP-8
  intent: A user can send a typed description for remote identification when on-device identification cannot resolve it, after agreeing to a disclosure written for text rather than photos.
  success: Remote text transfer occurs only after an explicit consent receipt bound to a text-specific `consentCopyVersion`; the existing photo consent copy cannot authorize a text transfer, and a test proves a description reaches the gateway on no other path.

- id: CAP-9
  intent: A user can search and describe food in the languages of the supported markets, including where the app's language differs from the food database's language and where the two use different scripts.
  success: English, Spanish, and Russian each pass a documented query set keyed to the catalog they are searching, covering inflected forms, regional name variants, and cross-script entry; Spanish resolves regional variants of one food to one canonical ID, and Russian resolves inflected noun forms and Latin-script entry of Cyrillic records.

## Constraints

- Engines return identification evidence only. The text path emits no calories, macros, or micronutrients; every displayed number continues to come from the versioned catalog through the AD-7/AD-19 confirmation and calculation chain (FR-17).
- The deterministic floor (CAP-1) must function with zero models installed, zero network, and the lowest supported device tier. UJ-4 places Noah on a lower-tier device while offline, so the scenario that needs this path most is the one least able to run a model. The floor cannot depend on CAP-3.
- Modality ships as a new major contract release, never as an edit. Story 3.1 has shipped `contracts/inference/v1` as an immutable, digest-pinned release whose `contract_id` is `photo-inference-engine` and whose own rule is `incompatible_change = "new-major-release"`. Story E7.1 therefore publishes `contracts/inference/v2` with a modality-neutral id and migrates consumers under AR-33; v1's files and digest are untouched.
- Food identity is never defaultable and never preselected. AD-40 forbids defaulting food identity, preparation, recipe, and materially uncertain values; a text description does not relax this because the user typed it.
- Segmentation is a proposal, never a commitment. The engine's split of a description into items is editable before any item is confirmed, and a meal is never silently reduced to fewer items than the user described.
- On-device model resources are governed by NFR-3 and gated by G-3. Storage, download, memory, thermal, and battery budgets are measured per device tier before activation, and parameter count is not accepted as a resource proxy.
- Remote text identification reuses the existing escalation triggers and adds none. Remote is offered for a typed description only on the closed AD-6 set — local unavailability, local abstention, or a Gate G-5 low-confidence result — exactly as for photos. Typing does not create a new route to upload.
- Text transfer requires text-specific consent copy. `consentCopyVersion` currently denotes the reviewed *photo*-transfer explanation; a description is not a photo and the photo disclosure cannot authorize sending one. Consent remains per-attempt, unselected, and equal-prominence.
- Catalog country is independent of language (AD-12). Search and text identification must state which language they match against, and a catalog whose records are not in the user's app language must still be searchable.
- Language support is a per-market matrix, not a global switch. Each supported market declares its catalog language, its accepted input languages, and its script, and each combination carries its own query fixtures. English-only behaviour is not a valid default for a market whose catalog is not in English.
- Text input is user-authored free text and is subject to the FR-22 telemetry rules: it is never logged, never placed in analytics, and never sent remotely without the CAP-8 consent receipt.
- The plain search box ships before the model path. CAP-1 is the first delivery and does not wait on CAP-2, CAP-3, or CAP-7; it already closes the specification gap that motivated this spec and is the only path that works on every device.

## Non-goals

- Voice or video capture as an input modality. PRD §5 excludes voice, video, barcode, and nutrition-label OCR from the first release; this spec adds typed text only.
- Free-text nutrition estimation. The model never answers "how many calories is this"; it proposes food identity and the catalog does the arithmetic.
- A language model as the only or primary identification path. PRD §5 forbids treating a generic foundation model as the sole identification path; CAP-3 is an enhancement above CAP-1, never a replacement.
- Conversational or multi-turn refinement. A description produces candidates once; correction happens through the existing Candidate review and catalog search surfaces, not a chat thread.
- Remote text identification *before* an on-device attempt. Remote is a fallback on the existing triggers (CAP-8), never the first thing tried and never automatic.
- A parallel text UI. Text identification reuses Candidate review, serving confirmation, and the grounded result; it does not introduce a second workflow or state machine.

## Success signal

Noah — offline, on a lower-tier device, with no local vision engine available — types "two boiled eggs and a slice of rye toast", confirms the proposed records and servings, and reaches a Grounded Nutrition Result showing real catalog provenance and dataset version. He reaches it with no network, no downloaded model, and no wrong-but-plausible record silently confirmed on his behalf.

## Assumptions

- **Identification is tried on the phone first.** Remote text identification exists (CAP-8) but only as a fallback on the same triggers that already govern photos, after explicit consent. Local-first is preserved.
- **"Multilingual for Tier 1 and Russia" means the markets in the corpus plus Russia.** Tier 1 is currently recorded as the United States, Canada, United Kingdom, Australia, and New Zealand, and the beta is English-first. Adding Russia adds a non-English catalog language, a second script, and a market outside the current high-income Tier 1 framing. The exact launch list is owned by Gate G-1, so this spec names the language and script requirements rather than fixing the country list.
- **The plain search box must work on every supported phone.** Taken from the Noah journey, which puts the offline fallback on a low-end device — so the fallback cannot require a model that low-end device can't run.
- **Typed input reuses the screens that already exist.** Candidate review, serving confirmation, and the result screen are shared with the photo path rather than duplicated, because the product deliberately has one workflow and no extra screens.
- **The engine interface gets renamed.** It is currently called `PhotoInferenceEngine`, which stops making sense once it also accepts text. The rename happens as part of the version-2 migration rather than as separate work.

## Open Questions

<!-- OQ-1, OQ-2, OQ-4 answered; OQ-3 and OQ-6 closed. See .decision-log.md. -->

**OQ-5 — resolved structurally; the remaining question belongs to G-1 and G-4.**

The original question was whether Russian users' typed text may be processed outside Russia. That is not a question this spec can answer, and it turned out not to be a question this spec needs to answer.

The mechanism now lives in `contracts/market-policy/v1`, specified in `market-processing-policy.md` and bound by AD-41, AR-53, NFR-27, and story E1.6. Every market defaults to `unresolved` and permits no remote transfer of any modality. Russia enters as `unresolved` like everywhere else, so nothing here blocks on it, and the deterministic floor plus on-device identification keep the market usable in that state.

Two things this separated out:

*Language is not a market position.* Supporting Russian is a product capability that costs synonyms, inflection handling, and fixtures. Operating in Russia is a legal position. NFR-26 and AD-41 make these independent in both directions, so Russian-language support ships in story E7.3 without waiting on any residency determination.

*The question predates typed identification.* The product already transfers photos to a first-party gateway under AD-13, so any market with residency constraints constrains remote photo exactly as it constrains remote text. This was never an E7 question; typing surfaced it. It belongs to G-1, which owns the launch list and privacy jurisdictions, and G-4, which owns provider regions and DPA terms.

What remains for counsel, if Russia is pursued as a market: whether an account-free typed description or photo, carrying an opaque attempt ID and a short-lived device attestation token, constitutes personal data of a Russian citizen such that in-country primary storage is required. That determination sets one market's posture. It changes no rule here.
