---
name: NutriArch
status: final
created: 2026-07-26
updated: 2026-07-26
sources:
  - ../../prds/prd-SuperNutrition-2026-07-26/prd.md
design: DESIGN.md
---

# NutriArch — Experience Spine

## Foundation

NutriArch is a phone-first Android and iOS product. Compose Multiplatform owns
shared capture controls, analysis states, consent, Candidate review, serving
Confirmation, results, and recovery. Native platform views own live camera
preview and system pickers; platform services own permissions and image capture.
`DESIGN.md` is the visual identity authority.

Compose Multiplatform owns the complete iOS and Android screen surface. UIKit/
AVFoundation and PhotosUI on iOS, and CameraX/Photo Picker on Android, are
bridges only; implementation agents must not generate a parallel SwiftUI/UIKit
feature UI.

The Photo Analysis experience is one component state machine. Permission,
capture, frozen-frame analysis, remote consent, Candidate review, serving
Confirmation, result, error, and retry are states, not navigation destinations.
Native Back/gesture behavior either returns to the last safe state or ends the
Analysis Session after confirmation when data would be discarded.

## Information Architecture

| Surface or state | Reached from | Purpose |
| --- | --- | --- |
| Capture | App open / Scan again / Retake | Live camera preview, photo import, privacy route indicator |
| Permission rationale | Capture when unresolved | Explain camera need before platform prompt |
| Analyzing | Capture or retry | Frozen-frame progress, current route, cancel |
| Remote consent | Local Abstention/unavailable/low confidence | Explain upload and allow informed accept or decline |
| Candidate review | Engine result or manual search | Confirm, remove, replace, or add visible foods |
| Food search | Candidate review / no Engine path | Select a versioned Nutrition Source record |
| Country and catalog | First use / Capture header / Food search | Confirm or change country-specific Nutrition Source configuration |
| Serving confirmation | Candidate review | Confirm amount, unit/range, recipe/oil/sauce assumptions |
| Grounded result | Confirmation | Show estimated nutrition, provenance, uncertainty, Scan again |
| Recovery | Any typed failure | Explain outcome-specific options without dead ends |
| Internal diagnostics | Debug/internal build only | Redacted route, capability, timing, version, error evidence |

There is no tab bar, drawer, account surface, diary, or settings hierarchy in the
first release. Privacy detail and source detail open one modal/sheet level deep.
Country and catalog is a contextual sheet reachable from Capture and Food search,
not a general settings destination.

## Voice and Tone

Microcopy is calm, direct, and nonjudgmental. Brand posture lives in `DESIGN.md`.

| Do | Don't |
| --- | --- |
| “We found a few possibilities.” | “AI identified your meal!” |
| “Check the food and serving before we calculate nutrition.” | “Calories calculated perfectly.” |
| “This photo stays on your device.” | “100% private” when remote remains available |
| “Remote analysis sends a smaller copy of this photo.” | “Continue” without naming upload |
| “We couldn't identify this confidently.” | “Bad photo” |
| “Oil or sauce can change the estimate.” | “Hidden calories detected.” |
| “Nutrition estimate” | “Nutrition facts” or medical language |

## Component Patterns

| Component | Use | Behavioral rules |
| --- | --- | --- |
| Capture controls | Capture | Shutter is primary; import is secondary; disabled state explains why |
| Route indicator | Capture, Analyzing, Result | Text semantics for Local/Remote/Manual; never technical IDs |
| Candidate list | Candidate review | Ranked initially; after user edit, preserve user order; every item removable |
| Confidence explanation | Candidate review | Map Engine values to plain language; link to “Why this needs review” |
| Food search | Manual/correction | Search normalized country-aware catalog; source/version retained silently then visible in details |
| Serving editor | Confirmation | Amount, unit, optional range; validate without erasing input; expose recipe/oil/sauce questions only when relevant |
| Consent sheet | Remote escalation | Accept and Continue without upload have equal visual access; no preselected consent |
| Country and catalog sheet | First use / contextual change | Suggest system region, require confirmation, persist locally, allow Not listed/manual selection |
| Privacy detail sheet | Consent | Explain purpose, data categories, retention, provider category/region, cancellation limits, and policy link |
| Source detail sheet | Result / Food search | Nutrition Source name, version, country coverage, serving conversion, and estimate disclaimer |
| Internal diagnostics panel | Debug builds | Redacted route/capability/version/timing/error only; copy action excludes photos and sensitive payloads |
| Result summary | Grounded result | Estimate label, foods/servings first, nutrients second, provenance accessible |
| Recovery panel | Typed failure | Maximum three relevant next actions; no generic Retry if state cannot change |

## State Patterns

| State | Treatment |
| --- | --- |
| Camera initializing | Neutral preview placeholder; “Starting camera…”; import remains available if permitted |
| Permission denied once | Explain camera value; offer platform prompt and system photo import |
| Permission denied permanently/restricted | Offer Open settings, Import photo, and Manual search |
| Poor image | Keep photo visible; specific guidance (blur, darkness, no food); Retake primary |
| Local model required/downloading | Show size/network/storage if known; allow remote offer or manual path |
| Local unavailable | Explain local limitation; do not imply upload consent |
| Analyzing | Frozen frame, route label, indeterminate progress, Cancel |
| Low Confidence/Abstention | Plain reason; Remote analysis, Retake, or Manual search |
| Remote consent declined | Return to recovery with Retake and Manual search; no nag loop |
| Remote consent accepted before transfer | User can cancel; no bytes are sent and the flow returns to recovery |
| Remote transfer in progress | Cancel stops further upload/processing where possible, suppresses results, and does not claim already-sent bytes were recalled |
| Remote transfer completed | Cancel suppresses result delivery and requests server-side cancellation/deletion where supported; copy states that transfer already occurred |
| Remote timeout/rate limit/offline | Preserve Photo Input for active session; retry only when condition can change |
| Candidate ambiguity | Show alternatives and one clear “None of these” path |
| Nutrition Source miss | Keep food label but withhold numeric nutrition until record selection |
| Empty Candidate list | Manual search and Retake; never an empty result screen |
| Cancellation | Stop progress, suppress late results, clear transfer intent; explain that bytes already sent cannot be recalled |
| Completed | Grounded result remains until Scan again, Back confirmation, or app lifecycle cleanup |

## Photo Analysis State Transition Contract

Architecture owns exact sealed types; this table is the behavioral authority for
their transitions.

| Current state | Event | Next state | Required effect |
| --- | --- | --- | --- |
| Permission rationale | Continue | Platform permission pending | Invoke native permission once |
| Permission pending | Granted | Capture | Start native preview |
| Permission pending | Denied/restricted | Recovery | Stop preview; expose import/manual/settings route |
| Capture | Photo captured/imported | Preparing | Freeze session input; normalize orientation/metadata |
| Preparing | Quality failed | Recovery | Preserve preview image for retake guidance |
| Preparing | Ready | Analyzing local | Start Local Engine once |
| Analyzing local | Candidate result | Candidate review | Suppress later result for same request ID |
| Analyzing local | Abstention/unavailable | Remote offer | Map reason to user-safe copy |
| Remote offer | Continue without upload | Recovery/manual | Do not initialize transfer |
| Remote offer | Consent | Remote ready | Record action-scoped consent only |
| Remote ready | Transfer start | Analyzing remote | Mark bytes-may-leave boundary |
| Analyzing remote | Candidate result | Candidate review | End transfer; preserve route/provenance |
| Any active work | Cancel | Prior safe state/recovery | Cancel job, invalidate request ID, suppress late results |
| Any photo state | Replace photo | Preparing | Cancel old request; clear old Candidates and assumptions |
| Candidate review | Confirm foods | Serving confirmation | Preserve user edits as authority |
| Serving confirmation | Confirm serving | Grounding | Resolve versioned records; never use model nutrition |
| Grounding | Success | Grounded result | Clear unnecessary Photo Input artifacts |
| Any state | App backgrounds | Same or recovery | Pause preview; active transfer follows explicit background/cancellation policy |
| Grounded result | Scan again | Capture | Clear the prior Analysis Session |

## Interaction Primitives

- Tap to capture, select, edit, confirm, retry, or cancel.
- Pinch-to-zoom and tap-to-focus follow native camera behavior where available.
- System back/edge gesture follows the state history; it never silently submits,
  uploads, or saves.
- Swipe is not required for any action. Destructive removal has an explicit
  accessible button and undo where feasible.
- Keyboard actions use Next/Done semantics in serving and search forms.
- Haptics are optional and reserved for capture completion or a user-confirmed
  action; no error vibration by default.
- **Banned:** automatic remote upload, preselected consent, progress countdowns,
  calorie score rings, infinite carousels, gesture-only controls, modal stacks
  deeper than one, and celebratory/disciplinary nutrition feedback.

## Accessibility Floor

- VoiceOver and TalkBack label every capture control, Candidate, edit action,
  route, uncertainty, progress, and nutrition value with role and state.
- Focus order follows visual reading order and moves to the first changed heading
  when the state advances.
- Analyzing state announces once; progress animation does not repeatedly speak.
- Large text reflows Candidate and nutrition cards without truncating primary
  actions or values.
- Tap targets are at least 44pt on iOS and 48dp on Android.
- Reduce Motion removes photo zoom, sheet spring, and progress shimmer.
- High contrast and dark mode preserve legibility over real images using tested
  scrims.
- Color never independently conveys Confidence, error, route, or nutritional
  judgment.
- Camera/photo access has equivalent manual-search recovery for users who cannot
  or choose not to provide an image.
- After capture, screen readers announce photo readiness and quality guidance
  such as blur, darkness, or no food detected. Live camera framing is never the
  only route; import and manual search remain fully labeled alternatives.

## Responsive and Platform Behavior

| Concern | Shared rule | Android | iOS |
| --- | --- | --- | --- |
| Camera preview | Native preview embedded behind Compose controls | CameraX adapter | AVFoundation/UIKit adapter |
| Photo import | System picker only | Android Photo Picker | PhotosUI picker |
| Permission | Shared rationale, native prompt | Android runtime permission/settings | iOS permission/settings |
| Back | State-history rule | System Back/gesture | Navigation/edge gesture or explicit Close |
| Insets | Controls clear system bars/cutouts | Edge-to-edge insets | Safe-area insets |
| Typography | Semantic Material roles and scaling | Android font scale | Dynamic Type behavior verified through Compose/native interop |
| Units/locale | Country-configurable model and strings | Platform locale default | Platform locale default |

Phone portrait is the primary composition. Landscape, tablets, and foldables must
remain functional through reflow and safe insets, but custom large-screen layouts
are not first-release scope.

## Tier 1 Market Experience Constraints

- The English-speaking reference cohort is United States, Canada, United Kingdom,
  Australia, and New Zealand under PRD Assumption A-1 until PRD Gate G-1 replaces
  it.
- Do not infer country only from language. Country controls available Nutrition
  Sources, serving units, disclosures, and remote-processing region.
- On first use, suggest the system region but require confirmation. Persist the
  selection locally. If the region is unavailable or unsupported, open Country
  and catalog with “Not listed” and manual recovery; never silently substitute
  the United States.
- Support metric weights in all reference markets and localized display units
  where product research requires them; store canonical quantities independently
  of display.
- Evaluation and empty-state examples must include regional foods and avoid a
  US-only definition of a “normal meal.”
- Privacy copy is configuration-backed and reviewed per launch jurisdiction; it
  cannot be hard-coded into a shared screen.

## Key Flows

### Flow 1 — Maya gets a local result

1. Maya opens NutriArch.
2. On first use she reads the permission rationale, grants the platform camera
   permission, and confirms the suggested country/catalog.
3. Capture becomes ready; she frames lunch and taps the shutter.
4. Analyzing shows the frozen frame and `On this device`.
5. The Local Engine returns Candidates.
6. Maya confirms food and serving.
7. **Climax:** Grounded result shows the nutrition estimate, source, assumptions,
   and `On this device`.
8. Maya taps Scan again; the prior Analysis Session is cleared.

Failure: quality gate finds blur → Maya sees why and retakes before inference.

### Flow 2 — Liam consents to remote help

1. Liam captures a mixed restaurant bowl.
2. Local analysis Abstains with a plain-language ambiguity reason.
3. Remote consent explains the smaller photo upload, purpose, non-retention
   default, and manual alternative.
4. Liam accepts; Remote analysis begins.
5. Candidates arrive with recipe/serving questions.
6. Liam corrects the dish and serving.
7. **Climax:** Grounded result shows the confirmed estimate and `Remote analysis`.

Decline: Liam chooses Continue without upload and reaches manual search.

### Flow 3 — Aisha corrects a regional food

1. Aisha imports a photo.
2. Candidate review shows an incorrect top result and alternatives.
3. She taps None of these and searches.
4. Food search uses the configured country Nutrition Source package.
5. She selects the right record and confirms serving/oil assumptions.
6. **Climax:** Grounded result shows her chosen food, source version, and estimate.

No match: numbers remain withheld and Aisha can change country/catalog or cancel.

### Flow 4 — Noah is offline without a Local Engine

1. Noah opens Capture offline and takes a photo.
2. Capability state explains that local identification is unavailable.
3. Remote analysis is unavailable rather than offered as an active action.
4. Noah selects Manual search.
5. He chooses a bundled food record and serving.
6. **Climax:** Grounded result is reached with `Manual` provenance and no upload.

Cancel: Noah ends the Analysis Session and the temporary Photo Input is cleared.

## Mock Coverage

No visual mockups were generated in this headless run. All first-release surfaces
are spine-defined. Layout-sensitive surfaces that should receive mocks before UI
implementation are Capture, Remote consent, Candidate review, Serving
confirmation, and Grounded result. The spines win on any future mock conflict.
