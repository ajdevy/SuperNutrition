---
title: SuperNutrition Product Requirements Document
status: final
created: 2026-07-26
updated: 2026-07-26
sources:
  - ../../briefs/brief-SuperNutrition-2026-07-26/brief.md
  - ../../briefs/brief-SuperNutrition-2026-07-26/addendum.md
  - /Users/aj/Downloads/NutriArch
  - /Users/aj/Downloads/report.md
---

# PRD: SuperNutrition

## 0. Document Purpose

This PRD defines the user and product contract for the first SuperNutrition internal
beta. Product, UX, architecture, story, and implementation owners must use the
Glossary terms and stable requirement IDs in this document. Technical mechanism
belongs in the PRD addendum and architecture spine; this document defines
observable behavior and release boundaries.

## 1. Vision

SuperNutrition helps a person understand a meal without performing a conventional
food-log workflow. The user takes or imports a photo, receives one or more food
candidates, corrects what the image cannot prove, and receives a nutrition
estimate grounded in controlled data.

The product is local-first but not local-only. It performs useful analysis on the
device where possible and can offer remote analysis for ambiguous or complex
photos. The user must know when a photo would leave the device and remain able to
continue without uploading it.

SuperNutrition succeeds by reducing effort while preserving honesty. It does not claim
that a single image can reveal exact weight, hidden ingredients, allergens, or
medical truth.

## 2. Target User

### 2.1 Jobs To Be Done

- When I want quick nutritional awareness, help me turn a meal photo into a
  useful estimate without searching a large food database.
- When the image is ambiguous, show me what the app inferred and let me correct
  the food and serving assumptions before showing nutrition.
- When I care about privacy or have weak connectivity, tell me what can be done
  locally and let me decline a remote upload without losing control of the flow.
- When a device or model cannot analyze the photo, give me a clear recovery path
  instead of fabricating a confident result.

The initial commercial segment is users in high-income Tier 1 country markets.
The first internal beta is English-first and must remain ready for
country-specific food data, units, privacy disclosures, and remote-processing
regions. [ASSUMPTION: the reference beta cohort is the United States, Canada,
United Kingdom, Australia, and New Zealand; this is a corpus and configuration
anchor, not the final legal launch list.]

### 2.2 Non-Users for the First Release

- People seeking medical diagnosis, insulin dosing, eating-disorder treatment,
  or allergy guarantees.
- People who need a complete diary, daily targets, coaching, or clinical-grade
  nutrient measurement.
- Users who expect exact portion mass or hidden-recipe reconstruction from one
  RGB image.

### 2.3 Key User Journeys

**UJ-1. Maya identifies a clear lunch locally.**

- **Persona and context:** Maya is a privacy-conscious professional checking a
  simple lunch before returning to work.
- **Entry state:** Fresh internal-beta install with camera permission available.
- **Path:** Maya opens SuperNutrition, grants camera access, frames the meal, and taps
  the shutter. The app runs local analysis and presents food candidates and a
  serving assumption. Maya confirms them.
- **Climax:** SuperNutrition shows a Grounded Nutrition Result with source,
  uncertainty, and `local` as the Inference Route.
- **Resolution:** Maya can scan again without creating an account or saving meal
  history.
- **Edge case:** If the image is dark or blurred, the app asks for a retake before
  attempting expensive inference.

**UJ-2. Liam chooses remote help for a mixed dish.**

- **Persona and context:** Liam photographs a restaurant bowl that the Local
  Engine cannot confidently separate.
- **Entry state:** Network is available; no reusable Remote Upload Consent exists.
- **Path:** Local analysis returns an Abstention with a plain-language reason.
  SuperNutrition explains what remote analysis sends and does not retain, then asks
  for consent. Liam agrees. The Remote Engine returns candidates and unresolved
  assumptions.
- **Climax:** Liam edits the recipe type and serving before SuperNutrition computes a
  Grounded Nutrition Result.
- **Resolution:** The result shows `remote` as the Inference Route and does not
  imply that remote analysis was exact.
- **Edge case:** If Liam declines upload, the flow offers retake and manual food
  search instead of failing.

**UJ-3. Aisha corrects culturally specific food.**

- **Persona and context:** Aisha is evaluating whether SuperNutrition works beyond
  mainstream US meals.
- **Entry state:** She imports a photo from the system photo picker.
- **Path:** SuperNutrition returns an incorrect top Candidate and alternatives. Aisha
  searches for the correct food, selects a regional Nutrition Source record, and
  adjusts the Serving Assumption.
- **Climax:** The corrected Grounded Nutrition Result clearly identifies the
  selected record and database version.
- **Resolution:** Redacted correction telemetry can be counted for evaluation,
  but the photo is not retained by default.

**UJ-4. Noah continues without a usable engine.**

- **Persona and context:** Noah uses a supported OS on a lower-tier device while
  offline.
- **Entry state:** The Local Engine reports unavailable and no network exists.
- **Path:** SuperNutrition explains that photo identification is unavailable, preserves
  the Photo Input only for the active session, and offers retake or manual search.
- **Climax:** Noah can still reach a Grounded Nutrition Result through manual
  selection, or cancel without data leaving the device.
- **Resolution:** The app remains usable and does not loop, crash, or invent a
  result.

## 3. Glossary

- **Abstention** — An Engine decision not to return a usable Candidate because
  evidence or capability is insufficient; includes a typed reason.
- **Analysis Session** — The in-memory lifecycle from one Photo Input through
  result, retry, or cancellation.
- **Candidate** — A proposed visible food identity with Engine provenance,
  Confidence, alternatives, and supporting assumptions. A Candidate is not yet a
  nutrition record.
- **Confidence** — A calibrated Engine score or category used with policy
  thresholds; it is not a probability of nutritional correctness.
- **Confirmation** — The user's acceptance or correction of food identity and
  Serving Assumption before numeric nutrition is calculated.
- **Engine** — A component that accepts the shared inference request contract and
  returns Candidates or an Abstention.
- **Engine Capability** — Runtime availability, model/version state, device
  eligibility, and reason codes for one Engine.
- **Evaluation Corpus** — Consented, versioned photos and ground truth used only
  for accuracy, parity, latency, resource, and cost evaluation.
- **Grounded Nutrition Result** — Numeric nutrition calculated from a confirmed
  food record, serving conversion, and versioned Nutrition Source.
- **Inference Route** — The ordered Engines attempted and the Engine that produced
  the accepted Candidate; values include local, remote, manual, and combinations.
- **Local Engine** — An Engine whose photo inference executes on the user's device.
- **Nutrition Source** — A versioned, licensed food composition dataset and its
  canonical records.
- **Photo Input** — A camera or system-picker image reference and normalized,
  session-scoped metadata. Shared state must not require platform image objects.
- **Remote Engine** — An Engine that sends a minimized image to the first-party
  inference gateway after Remote Upload Consent.
- **Remote Upload Consent** — Informed, revocable permission for the current
  remote-analysis action, including purpose and retention disclosure.
- **Serving Assumption** — The user-confirmable amount, unit, or range used to
  scale a Nutrition Source record.

## 4. Features and Functional Requirements

### 4.1 Capture and Photo Preparation

**Description:** The user can start with the camera or system photo picker and
reach analysis with minimal friction. Photo handling is session-scoped and
privacy-preserving. Realizes UJ-1 and UJ-3.

#### FR-1: Camera-first entry

The user can open SuperNutrition directly into the capture surface and take a still
photo with one primary action after permissions are resolved.

**Consequences:**

- Camera readiness is visibly distinguishable from permission, loading, and
  unavailable states.
- A captured Photo Input starts an Analysis Session without a second confirmation
  tap.

#### FR-2: System photo import

The user can select one image through the platform system picker.

**Consequences:**

- The app requests only user-selected media access where the platform supports it.
- Canceling the picker returns to capture without starting an Analysis Session.

#### FR-3: Permission and camera recovery

The user can understand why access is requested and recover from denial,
restricted access, unavailable hardware, or capture failure.

**Consequences:**

- The app never shows a dead viewfinder as the only recovery surface.
- Permanent denial offers a route to platform settings and manual search.

#### FR-4: Privacy-preserving normalization

The system can create a normalized Photo Input suitable for Engines without
retaining unnecessary metadata or resolution.

**Consequences:**

- Orientation is correct before inference.
- EXIF, GPS, and all unrelated/non-essential metadata are stripped during
  normalization before the consent receipt and remote-payload digest are
  created; the scrubbed normalized payload is the only image representation
  eligible for remote upload.
- Temporary image artifacts are removed when the Analysis Session ends.

### 4.2 Shared Inference Contract and Capability

**Description:** Local and remote developers implement the same versioned Engine
contract without importing or modifying one another's implementation. Realizes
UJ-1, UJ-2, and UJ-4.

#### FR-5: Versioned Engine contract

Every Engine can accept the same inference request and return the same Candidate,
Abstention, provenance, and typed-error shapes.

**Consequences:**

- Shared contract fixtures run unchanged against every Engine adapter.
- An Engine cannot return numeric nutrition as Candidate data.
- Contract changes require an explicit version and compatibility decision.

#### FR-6: Capability reporting

The system can obtain Engine Capability before choosing an inference path.

**Consequences:**

- Capability distinguishes unavailable, model-required, downloading, ready,
  temporarily unavailable, and unsupported states where applicable.
- Capability includes a machine-readable reason suitable for policy and a
  user-safe explanation suitable for UI mapping.

#### FR-7: Local inference

The system can run a Local Engine on eligible Android and iOS devices without
transmitting the Photo Input.

**Consequences:**

- Android and iOS may use different adapters, runtimes, models, and device tiers.
- Local success, Abstention, cancellation, and failure conform to FR-5.
- A local-only Analysis Session produces no image network traffic.

#### FR-8: Remote inference

The system can run a Remote Engine through a first-party gateway after valid
Remote Upload Consent.

**Consequences:**

- No provider credential is embedded in the mobile app.
- The Remote Engine enforces input size, MIME, timeout, cancellation, and
  typed-error behavior.
- The gateway can replace providers without changing the mobile Engine contract.

### 4.3 Analysis Policy and Orchestration

**Description:** One policy-driven orchestrator selects Engines and preserves user
choice, privacy, and understandable recovery. Realizes all User Journeys.

#### FR-9: Local-first policy

The system attempts a ready Local Engine before offering remote analysis unless
the user explicitly selects a different permitted route for that Analysis Session.

**Consequences:**

- Engine order is explicit configuration, not dependency-injection iteration
  order.
- A Local Engine failure cannot silently upload the Photo Input.

#### FR-10: Remote escalation

The system can offer remote analysis after local unavailability, Abstention, or a
policy-defined low-confidence outcome.

**Consequences:**

- The offer explains purpose, data sent, retention, and alternatives.
- After a local attempt, the offer is permitted only for local unavailability,
  Abstention, or a Gate G-5 low-confidence result. Gate G-5 may tune the named
  threshold but cannot escalate a successful high-confidence local result.
- A user may explicitly choose another permitted route before local analysis;
  this is an explicit choice, not an unconstrained policy escalation.
- Declining the offer preserves retake and manual recovery.
- Consent is recorded for the action and can be revoked before transfer begins.
  After transfer begins, cancellation stops downstream processing and result
  delivery where possible but does not claim to recall bytes already sent.

#### FR-11: Cancellation, retry, and replacement

The user can cancel an Analysis Session, retry the same route, retake the photo,
or choose another permitted route.

**Consequences:**

- Cancellation stops active work and cannot later surface a stale result.
- Retry does not duplicate remote uploads after a successful response.
- Replacing the Photo Input invalidates all Candidates from the previous input.

#### FR-12: Provenance and abstention

The system preserves Inference Route, Engine/model contract version, Confidence,
Abstention reason, and unresolved assumptions through Confirmation and results.

**Consequences:**

- The user receives plain-language uncertainty, not raw diagnostic identifiers.
- Internal diagnostics can inspect provenance without accessing raw photos.

### 4.4 Confirmation and Nutrition Grounding

**Description:** The user corrects what the image cannot prove. Numeric nutrition
is calculated only from confirmed, versioned data. Realizes UJ-1, UJ-2, and UJ-3.

#### FR-13: Candidate review

The user can review ranked Candidates, alternatives, Confidence, and unresolved
assumptions before Confirmation.

**Consequences:**

- Low-confidence or ambiguous results are visually distinct from confident
  Candidates without implying medical risk.
- The user can remove a false Candidate.

#### FR-14: Food correction and manual search

The user can replace or add a Candidate through manual search of available
Nutrition Source records.

**Consequences:**

- Manual search remains available when all Engines are unavailable.
- Selecting a record preserves Nutrition Source identity and version.

#### FR-15: Serving confirmation

The user can confirm or adjust the Serving Assumption used for nutrition
calculation.

**Consequences:**

- The app exposes the unit, amount or range, and relevant recipe assumptions.
- The app does not describe an inferred portion as measured fact.

#### FR-16: Deterministic nutrition grounding

The system calculates calories and supported nutrients from the confirmed
Nutrition Source record and Serving Assumption.

**Consequences:**

- The calculation is reproducible from source record, version, unit conversion,
  and serving value.
- Results preserve per-item and aggregate provenance.

#### FR-17: Ungrounded-number prohibition

The system does not display model-generated calories, macronutrients, or other
numeric nutrition as a Grounded Nutrition Result.

**Consequences:**

- A missing Nutrition Source match remains partial or unknown until the user
  selects a record.
- UI and telemetry can distinguish unresolved, grounded, and manually grounded
  outcomes.

### 4.5 Results, Recovery, and Session Lifecycle

**Description:** Results are calm, understandable, and disposable by default.
Realizes all User Journeys.

#### FR-18: Grounded result display

The user can view confirmed food, Serving Assumption, calories, supported
macronutrients, source/version, uncertainty, and Inference Route.

**Consequences:**

- The result labels estimates and ranges in plain language.
- The UI never presents the result as medical, allergen, or exact-measurement
  advice.

#### FR-19: Scan again

The user can end the current Analysis Session and return to capture with one
primary action.

**Consequences:**

- Session-scoped photo data and stale inference state are cleared.
- No account or history decision blocks the next scan.

#### FR-20: Typed failure recovery

The user receives a useful recovery path for no-food, poor-image, model
unavailable, model download, offline, timeout, rate-limit, consent, parsing,
grounding, and unexpected failures.

**Consequences:**

- Failure copy does not expose provider internals or blame the user.
- Retry is shown only when retry can change the outcome.

#### FR-21: Offline continuation

The user can use ready local analysis and manual nutrition search while offline.

**Consequences:**

- Remote-only actions explain network dependence before work is lost.
- Offline state does not prevent ending or restarting an Analysis Session.

### 4.6 Evaluation and Internal Diagnostics

**Description:** The team can compare Engines and releases using reproducible,
privacy-safe evidence before making public accuracy claims.

#### FR-22: Redacted diagnostic events

The system can record session route, capability, timings, Candidate decisions,
correction actions, source coverage, errors, model/provider version, and resource
measurements without raw photos or free-form sensitive payloads.

**Consequences:**

- Diagnostics are disabled or locally inspectable according to beta consent.
- Logs and network tooling redact image bytes, tokens, headers, and provider
  credentials.

#### FR-23: Evaluation Corpus runner

The team can run local-only, remote-only, hybrid, and manual baselines against the
same versioned Evaluation Corpus.

**Consequences:**

- Reports slice results by device tier, OS, cuisine/region, lighting, dish type,
  and route where sample size permits.
- Reports include candidate recall, abstention, correction, gram/calorie error
  after grounding, latency, memory, thermal/battery observations, and remote cost.

#### FR-24: Cross-platform model parity

The team can compare Android and iOS exports or adapters against a golden image
set before activating a model version.

**Consequences:**

- Each model release records format, hash, label/taxonomy version, preprocessing
  contract, minimum runtime, and parity result.
- A failing model remains inactive and the previous compatible model remains
  usable.

#### FR-25: Provider and model configuration

Authorized operators can select approved Engine configurations without a mobile
feature rewrite.

**Consequences:**

- Configuration is versioned, allow-listed, and observable.
- Provider or model changes do not weaken consent, retention, grounding, or
  contract requirements.

## 5. Non-Goals

- Diagnosing health conditions, recommending insulin doses, or guaranteeing
  allergen safety.
- Exact automatic portion mass, recipe reconstruction, or calorie measurement
  from a single photo.
- Treating a generic VLM or device foundation model as the only identification
  path.
- Accounts, cross-device sync, persistent meal history, daily totals, goals,
  streaks, social features, coaching, health-platform integration, or
  monetization in the first release.
- Barcode, nutrition-label OCR, voice, or video capture in the first release.
- Public accuracy claims before the Evaluation Corpus and release thresholds are
  approved.

## 6. MVP Scope

### 6.1 In Scope

- Android and iOS internal builds from one Kotlin Multiplatform project.
- Compose Multiplatform capture, analysis, confirmation, result, and recovery UI
  where platform fit permits.
- Platform camera/import adapters and platform-specific Local Engines.
- One shared Engine contract, Remote Engine, local-first orchestrator, nutrition
  grounding, manual recovery, and evaluation harness.
- English-first copy with country-configurable units, Nutrition Sources, privacy
  disclosures, and provider regions for Tier 1 markets.
- Real-device validation and TestFlight/Google Play internal distribution.
- [ASSUMPTION: the internal beta remains account-free and does not persist meal
  history.]

### 6.2 Out of Scope for MVP

- Production provider commitment, public store launch, or paid scale.
- Full country coverage; the beta validates the architecture and selected
  Nutrition Source packages.
- Downloadable large local VLMs unless a spike satisfies device, product, and
  licensing gates.
- Long-term storage of user meals or photos.

## 7. Success Metrics

### Primary

- **SM-1:** A user reaches initial Candidates in no more than two primary actions
  after permission state is resolved. Target: 100% of happy-path usability
  scenarios. Validates FR-1, FR-2, FR-7 through FR-10, and FR-13.
- **SM-2:** [ASSUMPTION: camera-ready time of at most 2 seconds is a spike
  hypothesis until the supported-device matrix is frozen.] Validates FR-1.
- **SM-3:** [ASSUMPTION: local capture-to-Candidate time of at most 4 seconds and
  remote capture-to-Candidate time of at most 10 seconds are spike hypotheses
  until the physical-device and network matrix is frozen.] Validates FR-7 through
  FR-12.
- **SM-4:** Every displayed numeric nutrition value resolves to a Nutrition
  Source record, version, and Serving Assumption. Target: 100%. Validates FR-15
  through FR-18.
- **SM-5:** Local-only Analysis Sessions transmit zero image bytes and all remote
  uploads have valid Remote Upload Consent. Target: 100%. Validates FR-4, FR-7,
  FR-8, and FR-10.

### Evaluation Gates

- **SM-6:** [ASSUMPTION: a 300–500+ meal Evaluation Corpus is the initial
  benchmark size recommended by the supplied research.] The report compares
  local-only,
  remote-only, hybrid, and manual baselines across corrected candidate accuracy,
  nutrition error, abstention, latency, resource use, and cost. Ground truth
  records weighed portions, recipe or brand, raw/cooked state, oil/sauce, and
  device/cuisine/lighting context. Evaluation splits prevent the same meal,
  person, restaurant, device, or capture session from leaking across train,
  calibration, and test sets. Validates FR-23.
- **SM-7:** Android and iOS Local Engines pass the same Engine contract suite and
  model parity policy. Target: 100% contract cases. Validates FR-5, FR-7, and
  FR-24.
- **SM-8:** Public-beta accuracy, correction-time, resource, and
  cost-per-accepted-meal thresholds are approved from SM-6 before public launch.
- **SM-9:** The release candidate passes an acceptance matrix covering Android
  local, iOS local, Android remote, iOS remote, consent, consent decline, offline,
  Engine unavailable, manual recovery, and cancellation. Target: 100% required
  matrix scenarios. Every positive UJ-1 through UJ-4 path is represented by one
  shared journey definition executed as automated Android and iOS UI
  acceptance. Validates FR-5 through FR-21.
- **SM-10:** The hybrid policy improves corrected Candidate accuracy or time to a
  confirmed Grounded Nutrition Result over both local-only and remote-only
  baselines without a material regression in the other measure. The exact
  materiality threshold is set by Gate G-5. Validates FR-9, FR-10, and FR-23.

### Counter-Metrics

- **SM-C1:** Do not minimize correction rate by accepting uncertain Candidates;
  counterbalances SM-1 and SM-6.
- **SM-C2:** Do not maximize local completion by running models that violate
  latency, memory, thermal, battery, or accessibility limits; counterbalances
  SM-3.
- **SM-C3:** Do not maximize remote completion by increasing photo upload,
  retention, sensitive logging, or provider dependence; counterbalances SM-1 and
  SM-6.

## 8. Cross-Cutting Non-Functional Requirements

### Performance and Resource Use

- **NFR-1:** Evaluate the SM-2 and SM-3 hypotheses on named physical-device and
  network conditions, then replace them with approved release budgets at Gate
  G-3; simulator/emulator results are not performance proof.
- **NFR-2:** Long-running inference is cancellable and cannot publish stale state
  after cancellation or Photo Input replacement.
- **NFR-3:** Model storage, download, memory, thermal, and battery budgets are
  measured per device tier before activation; no universal parameter-count proxy
  is accepted.

### Privacy and Security

- **NFR-4:** Raw photos, thumbnails, base64 bodies, provider prompts/responses,
  credentials, and authorization headers are excluded from application,
  gateway, analytics, crash, and HTTP logs.
- **NFR-5:** Remote photos are minimized, encrypted in transit, not retained
  beyond request processing by default, and not used for training without a
  separate explicit opt-in.
- **NFR-6:** Remote processing supports country-configurable disclosures,
  subprocessors, deletion/retention policy, and regional routing required for
  selected Tier 1 launch markets, including GDPR/UK GDPR where applicable.
- **NFR-7:** Downloaded models and manifests require allow-listed origin,
  integrity verification, compatibility metadata, atomic activation, and
  rollback.
- **NFR-8:** Secrets exist only in authorized server-side configuration and can be
  rotated without a mobile release.

### Reliability and Compatibility

- **NFR-9:** Android and iOS platform adapters pass shared contract and lifecycle
  tests; camera and Local Engine release proof uses physical devices.
- **NFR-10:** The first release supports the minimum OS/device matrix selected by
  the capability spike and provides an understandable unsupported-device path.
- **NFR-11:** Remote retry is bounded, idempotent where applicable, and honors
  timeout, rate-limit, offline, and cancellation semantics.
- **NFR-12:** Nutrition Source and model updates are versioned and reproducible;
  an incompatible update cannot silently replace the active version.

### Accessibility, Localization, and Tone

- **NFR-13:** Interactive controls meet platform target-size guidance, expose
  semantic labels/roles/state to VoiceOver and TalkBack, follow logical focus
  order, and remain usable with large text and reduced motion.
- **NFR-14:** Color is not the only carrier of Confidence, failure, route, or
  consent state; selected themes meet WCAG 2.2 AA contrast where applicable.
- **NFR-15:** User-visible text is externalized. Domain and storage models do not
  hard-code language, country, decimal format, serving unit, or Nutrition Source.
- **NFR-16:** Copy is neutral and nonjudgmental. It states uncertainty and
  recovery in plain language and avoids medical, weight-shaming, or
  guilt-inducing framing. The experience is calm and minimal: generous
  whitespace, native-feeling typography, a subtle analyzing state, and numbers
  presented without praise, blame, or alarm.

### Testing and Maintainability

- **NFR-17:** Every compiled production source belongs to exactly one coverage
  denominator. Tool-measurable mobile, web-landing, and backend production code
  maintains at least 85% unit-test line and branch coverage per module and per
  product aggregate. Changed production lines and branch outcomes maintain 100%
  coverage. Unavoidable Native, Swift, framework, or hardware behavior requires
  explicit executable equivalent evidence and documented physical-device
  acceptance rather than an optional exclusion.
- **NFR-18:** Local, remote, and orchestration modules can build and run their own
  tests without compiling or editing another Engine implementation.
- **NFR-19:** Dependency and tool versions are pinned centrally within each
  monorepo product boundary; repository-root pins are limited to policy and CI
  tooling and cannot become a shared product dependency graph. Implementation
  agents do not perform unrelated upgrades.
- **NFR-20:** A closed, reviewable scenario catalog derives from every UJ-1
  through UJ-4 main, alternative, and recovery path that reaches a valid result
  or privacy-preserving cancellation, plus every SM-9 matrix dimension. Every
  catalog scenario has one shared journey definition executed as deterministic,
  automated UI acceptance on Android and iOS. Tests use controlled Engine,
  network, country, and Nutrition Source fixtures and linked non-visual
  privacy/network oracles; they do not treat a live model or provider response
  as a stable UI assertion.
- **NFR-21:** Repository CI runs the complete host-compatible automated quality,
  unit, build, and UI-acceptance matrix for every push to every branch and every
  pull request, and for every merge-queue commit when merge queue is enabled.
  A missing, skipped, neutral, cancelled, timed-out,
  action-required, stale, startup-failed, runner-unavailable, or failed required
  job cannot produce a passing aggregate status.
- **NFR-22:** The repository is a monorepo with separate root product
  boundaries at `mobile/`, `web/landing/`, and `backend/mobile-api/`. Each
  product independently owns its source, tests, dependency installation and
  lock state, cache, quality entrypoint, artifact, deployment, credentials,
  rollback, and distinct CODEOWNERS principal; source, build, package-manager,
  generated-code, runtime, and deployment dependencies cannot cross product
  boundaries. Cross-product integration uses only immutable, versioned,
  language-neutral releases independently owned under `contracts/`; producers
  and consumers test every supported released-version combination rather than
  only repository HEAD. `web/landing/` has no runtime dependency on the mobile
  backend. Root policy and contract compatibility checks are unavoidable
  inputs to the six-job aggregate on every branch push, pull request, and
  enabled merge-queue commit.
- **NFR-23:** Product identity is exact and testable: the installed application,
  launcher label, and in-app brand are `SuperNutrition` on Android and iOS; the
  Apple App Store and Google Play listing title is
  `SuperNutrition AI nutritionist`. The `AI nutritionist` suffix is store
  metadata only and must not appear as the installed display name. A protected
  identity manifest and automated metadata assertions prevent platform, locale,
  build-profile, landing-page, and store-listing drift.

## 9. Constraints and Guardrails

### Safety

- Confirmation is required before Grounded Nutrition Result calculation.
- Low-confidence, mixed-dish, hidden-ingredient, portion, and allergen-relevant
  ambiguity must support Abstention or an explicit user question.
- A disclaimer does not replace correct uncertainty behavior.

### Privacy

- Local failure never implies remote consent.
- No raw-photo retention by default.
- Evaluation Corpus images require separate informed consent and lifecycle rules.

### Cost and Operations

- Free provider tiers are development aids, not a launch capacity plan.
- Remote cost is measured per accepted result, including retries and rejected
  Candidates.
- Public launch requires provider, regional-routing, rate-limit, abuse, incident,
  and deletion plans.

### Team Ownership

- The shared Engine contract is frozen before local and remote implementation
  starts.
- Mobile, web landing, and mobile backend work stays in separate root product
  directories and can be assigned to independent stories and owners.
- Cross-product API changes are independently owned immutable releases under
  `contracts/`; producer and consumer HEAD changes do not replace supported
  released-version compatibility evidence. No product imports another
  product's source or depends on its build, runtime, cache, artifact, or
  deployment, and the landing does not call the mobile backend.
- Local and remote implementations belong to separate modules, epics, owners,
  and pull requests.
- The local track contains separate Android and iOS implementation stories so
  platform code and device evidence remain bounded even if one local owner
  delivers both.
- The orchestration owner integrates only through the shared contract and cannot
  reach into Engine internals.

### Verification

- Coverage gates reconcile every compiled production source to exactly one
  module and the deduplicated aggregate; moving code, excluding it, or supplying
  a zero/missing report cannot satisfy NFR-17.
- The acceptance-scenario catalog is derived from the PRD, not from whichever
  tests happen to exist. Android and iOS run the same logical journey
  fingerprint and stable scenario IDs, with linked privacy/network and native-
  gap evidence.
- Every pushed commit runs repository CI without path-based omission, and the
  aggregate always reports a non-success dependency as failure. Physical-device
  evidence remains mandatory wherever the native-gap inventory binds NFR-1,
  NFR-3, NFR-9, or accessibility claims.

## 10. Risk Register

| Risk | Product impact | Required mitigation |
| --- | --- | --- |
| Dataset and cuisine shift | Incorrect or exclusionary Candidates | Tier 1 country/cuisine slices, abstention, manual correction, versioned corpus |
| Portion and hidden ingredients | Misleading nutrition | Required Serving Assumption confirmation, ranges, no medical/allergy claims |
| Device fragmentation | Uneven availability, latency, crashes | Capability tiers, physical-device matrix, local fallback and manual path |
| Local export drift | Android/iOS results diverge | Golden corpus, preprocessing contract, parity gate, rollback |
| Remote vendor drift or cost | Outage, behavior change, budget surprise | First-party gateway, provider adapters, allow-listed config, cost telemetry |
| Sensitive photo exposure | Privacy and legal harm | Consent, metadata stripping, no body logs, no retention, DPA/region review |
| Nutrition data mismatch | Wrong numeric result despite correct Candidate | Versioned source, deterministic mapping, record provenance, correction |
| Agent-generated architecture drift | Modules violate ownership or safety rules | Architecture IDs, bounded stories, project override guide, full verification |

## 11. Decision Gates

| Gate | Owner | Blocks | Exit evidence |
| --- | --- | --- | --- |
| **G-1: Tier 1 launch sequence** | Product + privacy/legal | Public store launch | Exact country order, language plan, unit/label rules, privacy jurisdictions, store claims |
| **G-2: Nutrition Source packages** | Product data owner + legal | Grounding implementation beyond a fake catalog | Source/version/license per reference country, canonical IDs, unit conversions, missing-record policy |
| **G-3: Local capability and device matrix** | Local inference owner + QA | Production Local Engine stories | Named minimum devices/OS versions, model/runtime results, latency/memory/thermal/battery budgets |
| **G-4: Remote gateway and provider** | Remote inference owner + security/privacy | Production Remote Engine release | Versioned API contract, provider evaluation, DPA/retention/training terms, region, rate/cost/abuse plan |
| **G-5: Evaluation thresholds** | Product + ML/evaluation owner | Public beta accuracy claims and routing activation | Corpus report, quality/resource/cost thresholds, hybrid materiality rule, approved policy configuration |
| **G-6: Correction telemetry retention** | Product + privacy | Server-side correction telemetry | Consent copy, schema, purpose, retention/deletion period, regional handling; default is no server retention |

Shared contracts, project scaffolding, fakes, UX state work, and test harnesses may
proceed before G-2 through G-6 close. A real Engine, data package, or telemetry
path cannot be release-ready until its corresponding gate closes.

## 12. Open Questions

1. Which countries constitute the first Tier 1 launch sequence, and which
   languages follow the English-first beta?
2. Which Nutrition Sources and licenses cover each selected country, and what is
   the fallback when no canonical record exists?
3. Which local model families and device tiers pass the initial classifier,
   detector, and optional VLM spikes?
4. Which remote providers and gateway runtime pass accuracy, privacy, regional,
   contract, and cost evaluation?
5. What public-beta thresholds for corrected candidate accuracy, gram/calorie
   error, correction time, resource use, and cost follow from SM-6?
6. Which correction events may be retained without an account, for how long, and
   under what beta consent?
7. Which localized store descriptions, subtitles, keywords, and screenshots
   accompany the fixed English listing title without changing the
   `SuperNutrition` installed and in-app name?

## 13. Assumptions Index

- **A-1 (§2.1):** The reference English-speaking Tier 1 beta cohort is the United
  States, Canada, United Kingdom, Australia, and New Zealand; the legal launch
  list remains open.
- **A-2 (§6.1):** The internal beta is account-free and does not retain meal
  history.
- **A-3 (§7 SM-6):** The first benchmark starts with 300–500+ consented, weighed
  meals as recommended by the supplied research; representativeness is reviewed
  before public claims.
- **A-4 (§7 SM-2):** The two-second camera target is a spike hypothesis.
- **A-5 (§7 SM-3):** The four-second local and ten-second remote targets are spike
  hypotheses until the physical-device and network matrix is selected.
