---
baseline_commit: 44700a77d37bbc2c4cbcf3f37245c09ca8f1b63e
story_key: 3-5-release-v2-visible-component-and-mass-evidence-contract
---

# Story 3.5: Release v2 visible-component and mass-evidence contract

Status: done

## Story

As the contract owner,
I want one immutable v2 release for photo component evidence and optional mass
estimates,
so that Android, iOS, text identification, and evaluation cannot create
competing schemas or fabricate nutrition.

## Acceptance Criteria

1. Given immutable `contracts/inference/v1`, when component and mass evidence
   is added, then it is a separate, modality-neutral
   `contracts/inference/v2` release. v1 files, digest, fixtures, Kotlin types,
   and v1 verification continue to pass unchanged.
2. A v2 photo result has one or more visible component instances. Each instance
   has a unique bounded ID, localized candidate identity/label, calibrated
   confidence, visibility/ambiguity evidence, optional normalized visible region
   reference, and model/taxonomy/calibration/contract provenance.
3. A component has exactly one mass state: `estimated` with a finite,
   non-negative, ordered gram range and calibration assumptions; `not_evaluable`
   with a typed user-safe reason; or `abstained` with a typed user-safe reason.
   No field represents a value as measured or exact.
4. v2 input is a closed discriminator with exactly one modality. Photo input
   reuses the opaque, attempt-bound `PhotoRef`; text input carries only the
   future-safe text value and input locale. Neither/both inputs fail closed as
   a typed contract error. Text output must not fabricate a mass estimate.
5. No v2 Engine result contains calories, macros, micronutrients, nutrition
   values, a canonical catalog binding, raw photo bytes, a photo path, user text,
   model weight bytes, or a transport body. Candidate-to-catalog binding and
   serving/nutrition confirmation remain outside this story.
6. Canonical JSON, Kotlin fixtures, compatibility matrix, release digest, and
   negative validation cover v2. Unknown fields are ignored by Kotlin decoders;
   checked-in fixtures are minified and strictly valid. Invalid ranges,
   duplicate component IDs, invalid regions, missing provenance, invalid sealed
   modality, unsupported enum values, and attempts to inject nutrition fields
   fail closed.
7. `scripts/verify-inference-contract.sh` and its negative-fixture suite verify
   every released version independently and the declared v1/v2 coexistence
   matrix. The changed tooling remains dependency-free and does not weaken the
   v1 checks.

## Scope and sequencing

This is a **contract-only** story. It creates evidence vocabulary and fixtures;
it does not select a model, download Nutrition5k, add a detector/segmenter,
train a mass head, change routing, wire app composition, show UI, or claim a
G-3 result. E3.6 owns the consented/tared corpus; E3.7/E3.8 own platform
runtimes; E3.9 owns staged progress and component review; E5 owns serving and
catalog-grounded nutrition.

`contracts/inference/v1` is a digest-pinned release with
`incompatible_change = "new-major-release"`. Never edit it to “make it v2”.
Existing Android/iOS v1 adapters may continue to return dish candidates while
v2 consumers are introduced release by release.

## Tasks / Subtasks

- [x] Define the v2 language-neutral release (AC: 1, 4, 6, 7)
  - [x] Create `contracts/inference/v2/{schema.toml,compatibility-matrix.toml,RELEASE.md}`.
  - [x] Use a modality-neutral contract ID, explicit `v2` release metadata,
    compatibility policy, release-digest algorithm, and v1/v2 coexistence pairs.
  - [x] Add minified canonical fixtures for ready capability, photo-component
    estimate, photo-component-not-evaluable, abstention/failure, text
    not-evaluable mass, and redacted telemetry.
- [x] Add pure KMP v2 types and codecs (AC: 1–6)
  - [x] Add v2-specific source files in `mobile/domain/inference/src/commonMain`;
    do not mutate or alias-overwrite v1 public types.
  - [x] Define a sealed v2 input and v2 Engine port, a component collection,
    typed mass state/reasons, optional normalized region, provenance, capability,
    outcome/failure, and allowlisted operational telemetry.
  - [x] Preserve opaque `PhotoRef`/`AttemptId` leases; prohibit serializing raw
    content or platform image types.
  - [x] Validate all bounded identifiers/message keys, finite numeric values,
    range order, region bounds, unique component IDs, contract versions, and
    text mass `not_evaluable` invariant in constructors.
  - [x] Configure JSON with an explicit discriminator, `encodeDefaults`,
    `explicitNulls = false`, and `ignoreUnknownKeys = true`, separately from v1.
- [x] Build v2 fixture and negative tests first (AC: 1–7)
  - [x] Add common JVM tests for constructor invariants, JSON round trips,
    minified canonical equality, missing required fields, unknown fields, unknown
    enum rejection, both/neither modality rejection, and no-nutrition payloads.
  - [x] Verify all existing v1 tests and fixtures still run untouched.
  - [x] Extend the shell/Python verifier and negative fixture script to take a
    release root/version intentionally and validate both roots without a broad
    `v1` copy-paste switch.
  - [x] Test a stale/invalid digest, malformed JSON, symlinked fixture path,
    modified v1 fixture, invalid grams, text mass estimate, and duplicate
    component-instance ID as negative cases.
- [x] Register the coexistence release and evidence (AC: 1, 6, 7)
  - [x] Update only the necessary contract catalog/fingerprint, root-boundary,
    module-matrix, coverage, and lock/verification records following the E3.1
    pattern. Do not lower coverage or add an exclusion/baseline.
  - [x] Record precise v2 manifest/hash/version information; do not register a
    runtime/model, public corpus, or Food-101 dish output as component evidence.
- [x] Validate (AC: 1–7)
- [x] Run v1 and v2 contract positive and negative suites, the inference
  module JVM test/Kover lane, and read-only local quality checks appropriate to
  changed inventory/policy files.
- [x] Run `graphify update .`, `git diff --check`, and preserve the untracked
  user presentation directory.

### Review Findings

- [x] [Review][Patch] Defensively copy v2 component and mass assumption lists so callers cannot mutate validated evidence after construction [InferenceV2Models.kt:80].
- [x] [Review][Patch] Enforce request/result modality parity at the v2 engine boundary [InferenceV2Contract.kt:123].
- [x] [Review][Patch] Reject degenerate gram ranges that would contradict the release's no-exact-mass guarantee [InferenceV2Models.kt:230].
- [x] [Review][Patch] Reject duplicate supported and coexistence matrix declarations [verify-inference-contract.sh:360].
- [x] [Review][Patch] Validate the exact v2 schema and compatibility-matrix metadata and port declarations [verify-inference-contract.sh:340].
- [x] [Review][Patch] Validate fixture filenames against their required modality, outcome, and mass semantics [verify-inference-contract.sh:326].
- [x] [Review][Patch] Reject JSON numbers that cannot be represented as finite Kotlin `Double` values [verify-inference-contract.sh:89].
- [x] [Review][Patch] Publish and verify the confidence-band derivation thresholds in the language-neutral schema [InferenceV2Models.kt:142].
- [x] [Review][Patch] Recompute negative-fixture release digests after semantic mutations so each case reaches its intended validator [inference-contract-negative-fixtures.sh:171].
- [x] [Review][Patch] Require explicit model, taxonomy, and calibration version values for unavailable capabilities [InferenceV2Models.kt:21].
- [x] [Review][Patch] Reject visual-region evidence on text component results and
  update the canonical text fixture and shell verifier [InferenceV2Models.kt:64].

## Developer guardrails

### Data-model requirements

- A `MassEstimate` must be a **range**, not a nullable scalar. Use SI grams as
  the canonical unit; UI localization is a later feature concern. The lower and
  upper bounds must be finite, non-negative, and `lower < upper`.
- `not_evaluable` is a successful honest outcome, not `0 g` and not a failure.
  Enumerate user-safe reasons such as ambiguous portion, mixed/occluded content,
  unsupported geometry, or missing calibration; no free-form diagnostic text.
- Region evidence is optional and only describes a normalized visible region.
  Keep masks/images outside the contract; an opaque mask reference cannot be a
  filesystem path, URI, or raw payload.
- Preserve per-engine confidence semantics: confidence is calibrated only within
  a model version and does not prove nutritional correctness.
- Provenance must include contract, engine, model, taxonomy, and calibration
  identifiers. Do not add a nutrition-source ID.
- v2 telemetry stays bucketed/allowlisted: no component label, raw gram value,
  photo/user text, filename/path, headers, token, or request/response body.

### Architecture and ownership

- `mobile/domain/inference` is pure KMP. No Android, Apple, Compose, Koin,
  Ktor/Ktorfit, SQLDelight, model runtime, provider, camera, UI, catalog, or
  routing dependency may be added.
- Only the app composition roots select adapters. This story exports ports and
  data only; it does not change Android/iOS engines or `LocalFirstRouter`.
- Keep v1 and v2 coexistence explicit. Do not change v1 `PhotoInferenceEngine`
  semantics or make an adapter silently advertise v2.
- `contracts/` remains immutable language-neutral data. Keep executable
  verification in `scripts/`, with all digest inputs explicit and sorted.
- All interface changes are compatible with the existing product-root and
  module-boundary policy. Preserve separate Android/iOS/remote owner paths.

### Nutrition5k boundary

Nutrition5k is an attributed, license-reviewed research input only. Its public
archive is 181.4 GB and its protocol is a controlled cafeteria RGB-D rig; it is
not bundled, downloaded by the app, used as a runtime lookup, or accepted as
phone-photo production evidence. A later training environment may use a compact
metadata index and selected shards, but E3.6 must still collect its own
consented, tared, leakage-resistant corpus. This story adds no CDN, Cloudflare
configuration, credentials, dataset files, or network code.

### Existing code to preserve and extend

| Existing path | Preserve | v2 action |
| --- | --- | --- |
| `contracts/inference/v1/**` | Immutable release and digest | Read-only reference; add sibling `v2`, never modify. |
| `mobile/domain/inference/.../InferenceContract.kt` | `AttemptId`, opaque `PhotoRef`, v1 port | Reuse only the safe identity seam; place v2 types in new files. |
| `InferenceModels.kt`, `InferenceContractJson.kt` | v1 serialized outcome/codec behavior | Add separate v2 models/codec; do not make v1 decoder accept v2. |
| `InferenceContractFixtures.kt`, `InferenceContractTest.kt` | v1 canonical fixtures and validation depth | Add v2 fixture/test peers; keep all v1 assertions. |
| `scripts/verify-inference-contract.sh` | strict required fields, minification, digest, symlink defense | Generalize deliberately for version roots, retaining v1 assertions. |
| `scripts/inference-contract-negative-fixtures.sh` | malformed/digest/symlink negative coverage | Run the full negative set per released version plus v2-specific negatives. |
| `mobile/config/quality/{module-matrix.toml,coverage.toml,evidence-bindings.toml}` | nonzero inventory and coverage ownership | Change only if the contract release requires an enumerated v2 artifact. |

### Testing and quality

- Write red tests before implementation. Changed production branches require
  100% coverage and the module/product floors remain at least 85% line and
  branch; do not use a baseline, exclusion, wildcard stability entry, or
  optional coverage flag.
- Contract checks must prove both acceptance and rejection. A schema that merely
  serializes an estimated number is insufficient.
- Cancellation stays an orchestration/reducer event, not an Engine failure.
- This is non-UI work: it must attest that interaction budgets, defaults,
  consent, and navigation are unchanged.
- Android/iOS device evidence is not required for a pure contract release;
  later runtime stories must produce it. Do not claim G-3 closure from JVM tests.

## References

- [Source: `_bmad-output/planning-artifacts/epics.md`, Epic E3 and Story E3.5]
- [Source: `_bmad-output/planning-artifacts/prds/prd-SuperNutrition-2026-07-26/prd.md`, §14, FR-32..FR-36, NFR-28]
- [Source: `_bmad-output/planning-artifacts/architecture/architecture-SuperNutrition-2026-07-26/ARCHITECTURE-SPINE.md`, AD-2, AD-7, AD-9, AD-11, AD-17, AD-22, AD-42]
- [Source: `_bmad-output/planning-artifacts/ux-designs/ux-SuperNutrition-2026-07-26/{DESIGN,EXPERIENCE}.md`, component review and staged analysis]
- [Source: `_bmad-output/implementation-artifacts/3-1-freeze-contract-v1-inference-ports.md`, previous contract release patterns and review fixes]
- [Source: `contracts/inference/v1/{schema.toml,compatibility-matrix.toml,RELEASE.md}`]
- [Source: `mobile/domain/inference/src/commonMain/kotlin/com/supernutrition/domain/inference/{InferenceContract,InferenceModels,InferenceContractJson}.kt`]
- [Source: `mobile/domain/inference/src/commonTest/kotlin/com/supernutrition/domain/inference/{InferenceContractFixtures,InferenceContractTest}.kt`]
- [Source: `scripts/{verify-inference-contract.sh,inference-contract-negative-fixtures.sh}`]
- [Nutrition5k official dataset repository](https://github.com/google-research-datasets/Nutrition5k)

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Completion Notes List

- Added immutable `inference-evidence/v2`, a modality-neutral evidence contract
  for visible component instances and honest mass evidence. A photo can carry
  an estimated gram **range** only with calibration assumptions; text is
  constrained to `not_evaluable` mass. No nutrition/canonical-catalog/raw-photo
  payload is representable.
- Kept v1 unchanged and made v1/v2 coexistence explicit in the release matrix,
  verifier, negative suite, root boundaries, and CI fingerprint. Capability
  model/taxonomy/calibration versions are explicit even while an engine is
  unavailable, preventing inferred release evidence.
- Updated the approved product/architecture/UX/epic artifacts and G-3 status to
  record v2 as re-planning only. Nutrition5k remains research/training-side
  only: no archive, mirror, CDN, index, app download, network code, model, or
  runtime was added.
- Validation passed: v1/v2 positive and negative contract suites;
  `:domain:inference:jvmTest :domain:inference:koverXmlReportJvm` (v2: 100%
  line, 85.04% branch); `scripts/run-local-ci.sh jvm` (98.34% line, 88.38%
  branch across eight production modules); `git diff --check`; and
  `graphify update .`. The known graphify zero-node warning affects unsupported
  data files only and the graph rebuilt successfully.
- This contract-only story does not claim component recognition, mass accuracy,
  device validation, Food-101 evidence, model selection, corpus readiness, or
  G-3 closure. Those remain E3.6 through E3.9.
- Completed the scoped adversarial and edge review hardening: public v2 engine
  calls now enforce request/result modality parity, validated list inputs are
  snapshotted, exact-mass ranges are rejected, and schema/matrix/fixture
  semantics are checked without weakening the v1 lane.
- Revalidated after review fixes: positive v1/v2 contract verification, the
  digest-refreshing negative-fixture suite, `:domain:inference:jvmTest`, and
  inference plus photo-analysis Detekt lanes all pass.

### Debug Log References

- `scripts/verify-inference-contract.sh`
- `scripts/inference-contract-negative-fixtures.sh`
- `scripts/verify-root-boundaries.sh`
- `cd mobile && ./gradlew :domain:inference:jvmTest :domain:inference:koverXmlReportJvm --no-daemon`
- `scripts/run-local-ci.sh jvm`
- `graphify update .`

### File List

- `README.md`
- `_bmad-output/implementation-artifacts/3-5-release-v2-visible-component-and-mass-evidence-contract.md`
- `_bmad-output/implementation-artifacts/g3-benchmark-artifact.md`
- `_bmad-output/implementation-artifacts/sprint-status.yaml`
- `_bmad-output/planning-artifacts/sprint-change-proposal-2026-08-01.md`
- `_bmad-output/planning-artifacts/architecture/architecture-SuperNutrition-2026-07-26/ARCHITECTURE-SPINE.md`
- `_bmad-output/planning-artifacts/epics.md`
- `_bmad-output/planning-artifacts/prds/prd-SuperNutrition-2026-07-26/prd.md`
- `_bmad-output/planning-artifacts/ux-designs/ux-SuperNutrition-2026-07-26/DESIGN.md`
- `_bmad-output/planning-artifacts/ux-designs/ux-SuperNutrition-2026-07-26/EXPERIENCE.md`
- `contracts/inference/v2/{schema.toml,compatibility-matrix.toml,RELEASE.md,fixtures/*.json}`
- `contracts/root-policy/v1/boundaries.toml`
- `mobile/domain/inference/src/commonMain/kotlin/com/supernutrition/domain/inference/{InferenceV2Contract,InferenceV2ContractJson,InferenceV2Models,InferenceV2Telemetry}.kt`
- `mobile/domain/inference/src/commonTest/kotlin/com/supernutrition/domain/inference/{InferenceV2ContractFixtures,InferenceV2ContractTest}.kt`
- `scripts/{emit-ci-fingerprint,inference-contract-negative-fixtures,verify-inference-contract}.sh`

## Change Log

- 2026-08-01: Implemented and validated the v2 visible-component and
  mass-evidence contract; advanced the story to review.
- 2026-08-01: Applied scoped review hardening and advanced the contract-only
  story to done; runtime/model selection, corpus, and G-3 work remain outside
  this story.
