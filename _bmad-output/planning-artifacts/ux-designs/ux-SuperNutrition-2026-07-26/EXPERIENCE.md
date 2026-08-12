---
name: SuperNutrition
status: final
created: 2026-07-26
updated: 2026-08-01
sources:
  - ../../prds/prd-SuperNutrition-2026-07-26/prd.md
design: DESIGN.md
---

# SuperNutrition — Experience Spine

## Foundation

SuperNutrition is a phone-first Android and iOS product. Compose Multiplatform owns
one shared photo-analysis surface: capture controls, retained photo, analysis
states, Candidate review, and nutrient profile are sequential in-place states.
Optional serving editing and recovery support that surface. Paid remote consent is a later, entitled
capability and is absent from the free/default flow. Native platform views own
live camera preview and system pickers; platform services own permissions and
image capture.
`DESIGN.md` is the visual identity authority.

The installed and in-product name is `SuperNutrition`. The store-only title is
`SuperNutrition AI nutritionist`; the descriptor is not repeated in app chrome,
accessibility labels, launcher identity, or ordinary product copy.

Compose Multiplatform owns the complete iOS and Android screen surface. UIKit/
AVFoundation and PhotosUI on iOS, and CameraX/Photo Picker on Android, are
bridges only; implementation agents must not generate a parallel SwiftUI/UIKit
feature UI.

The Photo Analysis experience is one component state machine. Permission,
capture, frozen-frame analysis, Candidate review/nutrient profile, serving edit,
error, and retry are states, not navigation destinations. Paid remote consent is
future-only and may enter this machine only after a verified paid entitlement and
explicit user action.
Native Back/gesture behavior either returns to the last safe state or ends the
Analysis Session after confirmation when data would be discarded.

Low-friction interaction is the default behavior. The happy path uses the fewest
safe taps, scrolls, fields, and choice surfaces practical for the context.
Allowlisted locale, measurement system, catalog proposal, and (after canonical
confirmation) one atomic standard serving pair may be prefilled when
deterministic; each is shown inline as a labeled default with source/provenance,
remains editable and reversible, and preserves the user's correction only within
its approved scope. Meal-specific preparation,
oil, sauce, and recipe values are never `DefaultProposal` values. When intrinsic
to a user-confirmed canonical catalog item they are displayed as catalog facts or
labeled assumptions; unresolved meal-specific values require an explicit
confirmation or question.
Remote upload/consent and materially uncertain food identity are never
preselected or silently inferred. The free/default flow does not offer remote
upload. Medical/allergen conclusions and health-effect claims are prohibited.
Policy-resolved market
jurisdiction and processing region are shown in the consent detail; the user
consents to the resulting transfer rather than choosing provider routing.

## Information Architecture

| Surface or state | Reached from | Purpose |
| --- | --- | --- |
| Photo analysis | App open / Scan again / Retake | One stable surface: live camera preview/photo import becomes the retained captured photo, then preparation, Candidate review, and the in-place nutrient profile |
| Permission rationale | Photo analysis when unresolved | Explain camera need before platform prompt |
| Analyzing | Photo analysis after capture or retry | Frozen-frame truthful stage over the retained photo, current route, Cancel; stages are Preparing photo, Finding visible foods, Estimating portions, and Checking results when a v2 component attempt is active |
| Paid remote consent (deferred) | Explicit paid-route action after eligible local recovery | Future-only consent surface; unreachable in free/default flow |
| Candidate review and nutrient profile | In-place in Photo analysis after Engine result or manual search | Explicitly select a main dish and visible foods, then reveal the catalog-grounded supported nutrient/vitamin/mineral profile and descriptive food properties on the same retained-photo surface after an explicit calculation action |
| Food search | Candidate review and calculation / no Engine path | Select a versioned Nutrition Source record |
| Country and catalog | First use / Photo analysis header / Food search | Confirm or change the food catalog country while keeping market jurisdiction and processing region explicit |
| Serving editor (optional) | Candidate review and calculation | Edit an amount, unit/range, or relevant assumption when desired or required; save or close returns to the same photo-first screen |
| Recovery | Any typed failure | Explain outcome-specific options without dead ends |
| Internal diagnostics | Debug/internal build only | Redacted route, capability, timing, version, error evidence |

There is no tab bar, drawer, account surface, diary, or settings hierarchy in the
first release. Privacy detail and source detail open one modal/sheet level deep.
Country and catalog is a contextual sheet reachable from Photo analysis and Food search,
not a general settings destination. It must distinguish `CatalogCountry` from
`MarketJurisdiction` and `ProcessingRegion`: changing the food catalog cannot
silently change the privacy jurisdiction or provider region used for a remote
attempt.

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
| Capture controls | Photo analysis — capture state | Shutter is primary; import is secondary; disabled state explains why. After shutter/import, keep the photo in this surface and move focus to the review state. |
| Route indicator | Photo analysis — capture, analyzing, review, and nutrient-profile states | Text semantics for Local/Manual; the future paid Remote state is never shown or offered in the free/default flow. Never expose technical IDs. |
| Main-dish proposals | Candidate review | Keep the photo visible as the screen background and render at most three ranked, distinct meal-name possibilities as compact text choices over a readable scrim. None is selected by default: choosing one is the explicit food-identity decision. Keep a compact `Other`/manual-search route reachable without removing the photo or inventing a match. |
| Ingredient confirmation icons | Candidate review | Render separately proposed visible-food components as initially unselected, tappable icons positioned over the photo. The visual control carries no ingredient text; its accessible label includes the localized name, confidence/ambiguity, `From this photo` provenance, and selected state. A non-colour source/selection marker and on-demand detail keep the minimal visual surface honest and reversible. A selected icon still does not confirm nutrition. |
| Add-ingredient tray | Candidate review | Put a compact icon-only grid at the bottom of the photo, below the dish-name choices. Before a dish selection it offers only general photo-relevant additions; choosing a dish immediately swaps the unselected offer set for relevant dish-conditional possibilities. Every icon begins unselected and carries an accessible name, suggestion source, confidence/ambiguity when applicable, and selection state. Changing a dish must not remove a food the person explicitly selected. Its distinct outline/plus marker differentiates a suggestion from `From this photo` evidence without relying on colour or adding persistent text. The current MVP must not claim a `Most used by you` ranking because it does not persist user food/correction history; any later opted-in personalization is a separately provenanced, unselected source. |
| Dish-associated suggestions | Candidate review, future catalog integration | A future dish database may, only after the person selects a main dish, offer a separately headed `Also common with this dish` group. Its items use distinct catalog/recipe provenance, are initially unselected, do not claim to be visible in the photo, and may not supply an inferred serving, nutrition, or measured weight. A recipe reference weight must remain explicitly labeled as a recipe reference, editable, and separate from photo mass evidence. This group is out of scope until the database epic is delivered. |
| Candidate list | Candidate review | Preserve user order after an edit; every item is removable. A visible component exposes region/ambiguity, confidence, and editable estimated grams or an honest no-estimate state; it never auto-confirms nutrition. |
| Confidence explanation | Candidate review | Map Engine values to plain language; link to “Why this needs review” |
| Food search | Manual/correction | Search normalized country-aware catalog; source/version retained silently then visible in details |
| Serving editor | Candidate review and nutrient profile | Secondary optional screen opened from the photo-first nutrient home. It edits amount, unit, optional range, or relevant recipe/oil/sauce assumptions without erasing input. Save and Close both return to the same review screen; calculation refreshes only after explicit save. An item with no usable amount makes this editor required before calculation, rather than inventing grams. |
| Paid consent sheet (deferred) | Future paid remote escalation | Equal no-upload/upload access and no preselection; unreachable in free/default flow |
| Country and catalog sheet | First use / contextual change | Suggest a catalog country as an unaccepted proposal, require confirmation on first use, show the separate policy-resolved market jurisdiction and processing-region values, persist only the confirmed catalog choice locally, and allow Not listed/manual selection |
| Privacy detail sheet | Consent | Explain purpose, data categories, retention, provider category/region, cancellation limits, and policy link |
| Source detail sheet | Result / Food search | Nutrition Source name, version, country coverage, serving conversion, and estimate disclaimer |
| Internal diagnostics panel | Debug builds | Redacted route/capability/version/timing/error only; copy action excludes photos and sensitive payloads |
| Nutrient profile | Candidate review and nutrient profile | Keep the same photo-first screen and its selected icons. After successful grounding, show supported nutrients, vitamins, minerals, and catalog-backed descriptive food properties with estimate/source/version/serving provenance. Omit calorie total and any health-effect claim. |
| Recovery panel | Typed failure | Maximum three relevant next actions; no generic Retry if state cannot change |

### Interaction budget and safe defaults

- Give every ordinary decision surface one obvious primary action and keep
  routine values inline; do not make a user open a picker merely to accept an
  allowlisted deterministic default after any required first-use confirmation.
  Consent is the explicit exception: its remote-upload and no-upload actions
  remain equal, unselected, and visually neutral as specified in DESIGN.md.
- Prefer a visible default plus inline Edit/Undo over a choice-first sheet. Use
  standard serving, a confirmed catalog, and localized units only when the
  protected default policy and active snapshot make the value deterministic.
  A confirmed canonical item may supply an atomic standard serving pair through
  the catalog source; preparation, oil, sauce, and recipe values remain displayed
  catalog facts or labeled assumptions, never interaction defaults. Unresolved
  meal-specific values require explicit confirmation.
- Label defaults as `Default` (or equivalent localized copy), expose their source,
  and retain a reversible override. A user correction becomes authoritative for
  the current session and must not be silently overwritten by a later refresh.
  The MVP does not persist meal/preparation/serving corrections as future
  defaults.
- Auto-advance only through reversible local/UI steps such as preparing a photo
  or opening the next review state. Never auto-advance into remote transfer,
  consent, or uncertain food identity. Medical/allergen conclusions are
  prohibited. Jurisdiction and
  processing-region policy may be resolved by approved configuration, but must
  be shown before consent and cannot be chosen or changed by catalog/locale.
- Acceptance evidence records the four required tap/scroll/field/choice counts,
  bounded text-entry/choice-surface/recovery events, proposal/decision IDs,
  resolved-value digest, and default provenance for the default and override
  paths. A lower count never excuses hidden data transfer or an unacknowledged
  assumption.

### Candidate review confirmation envelope

- The normal component-review path is one explicit text dish-name tap/choice,
  followed by one explicit tap/choice for each ingredient icon the person accepts.
  The photo remains visible behind these controls. No meal
  name, ingredient, serving, recipe, preparation, oil, sauce, gram range, or
  recipe weight is preselected. A compact, ranked maximum of three dish names and
  a bounded grid of add-ingredient suggestion icons should normally fit before scrolling;
  any overflow control is an additional counted choice, not a hidden selection.
- The bottom add-ingredient grid is an explicit correction/input route, never proof
  that an item is present. It can be populated by a selected dish's versioned
  reference in a future database integration, but the MVP may offer only
  non-personalized relevant choices. A future `Most used by you` ranking needs
  its own approved persistence/consent/provenance contract, and remains unselected.
- Distinguish evidence sources without cluttering the photo: an icon's
  non-colour source marker and accessible label say `From this photo`; an
  on-demand detail says `Also common with this dish` for a future database
  suggestion or `Recipe reference` for a future catalog weight. The latter two
  are neither photo evidence nor an estimate of what is on the plate. The UI
  never creates a hidden-ingredient, recipe, or measured-weight claim from a
  meal-name selection.
- A proposed meal or ingredient can be removed, changed, or left unconfirmed;
  the user must not be auto-calculated or auto-advanced. An explicit `Calculate
  nutrients` action on this same screen preserves the selected foods, invokes
  catalog grounding, and populates the nutrient profile in place. The profile
  omits calorie total and contains only source-backed nutrients/vitamins/minerals
  and non-medical descriptive properties. The confirmation state remains scoped
  to the active analysis session and invalidates on a new or replacement photo.
- Serving edit is an optional detour from the photo-first calculation home, not a
  mandatory next screen. It becomes required only when a selected food has no
  usable confirmed amount or an unresolved required assumption. Saving or closing
  it returns to the same photo-first screen; it never skips to a separate result
  screen and never fabricates grams.

## State Patterns

| State | Treatment |
| --- | --- |
| Camera initializing | Neutral preview placeholder; “Starting camera…”; import remains available if permitted |
| Permission denied once | Explain camera value; offer platform prompt and system photo import |
| Permission denied permanently/restricted | Offer Open settings, Import photo, and Manual search |
| Poor image | Keep photo visible; specific guidance (blur, darkness, no food); Retake primary |
| Local model required/downloading | Show size/network/storage if known; allow remote offer or manual path |
| Local unavailable | Explain local limitation; do not imply upload consent |
| Analyzing | Frozen frame, route label, truthful current stage, one bounded announcement per stage, and Cancel; no countdown or simulated completion. |
| Low Confidence/Abstention | Plain reason, Retake, or Manual search; paid Remote analysis is not shown in the free/default flow |
| Paid remote consent declined (deferred) | Return to recovery with Retake and Manual search; no nag loop |
| Paid remote consent accepted before transfer (deferred) | User can cancel; no bytes are sent and the flow returns to recovery |
| Remote transfer in progress | Cancel stops further upload/processing where possible, suppresses results, and does not claim already-sent bytes were recalled |
| Remote transfer completed | Cancel suppresses result delivery and requests server-side cancellation/deletion where supported; copy states that transfer already occurred |
| Remote timeout/rate limit/offline | Preserve Photo Input for active session; retry only when condition can change |
| Candidate ambiguity | Show up to three distinct, unselected main-dish alternatives, ingredient chips that begin unselected, and one clear “None of these”/manual-search path |
| Nutrition Source miss | Keep food label but withhold numeric nutrition until record selection |
| Empty Candidate list | Manual search and Retake; never an empty result screen |
| Cancellation | Stop progress, suppress late results, clear transfer intent; explain that bytes already sent cannot be recalled |
| Completed | Calculated Candidate review remains until Scan again, Back confirmation, or app lifecycle cleanup |

## Photo Analysis State Transition Contract

Architecture owns exact sealed types; this table is the behavioral authority for
their transitions.

| Current state | Event | Next state | Required effect |
| --- | --- | --- | --- |
| Permission rationale | Continue | Platform permission pending | Invoke native permission once |
| Permission pending | Granted | Capture | Start native preview |
| Permission pending | Denied/restricted | Recovery | Stop preview; expose import/manual/settings route |
| Capture | Photo captured/imported | Preparing | Freeze session input; normalize orientation and scrub EXIF/GPS/non-essential metadata |
| Preparing | Quality failed | Recovery | Preserve preview image for retake guidance |
| Preparing | Ready | Analyzing local | Start Local Engine once |
| Analyzing local | Candidate result | Candidate review | Suppress later result for same request ID |
| Analyzing local | Abstention/unavailable/low confidence | Remote offer | Map the closed eligibility reason to user-safe copy; a successful high-confidence local result has no remote offer |
| Paid remote offer (deferred) | Continue without upload | Recovery/manual | Do not initialize transfer; this state is unreachable in free/default flow |
| Paid remote offer (deferred) | Consent | Remote ready | Record action-scoped consent only after verified entitlement and explicit paid-route action |
| Remote ready (deferred) | Transfer start | Analyzing remote | Mark bytes-may-leave boundary |
| Analyzing remote (deferred) | Candidate result | Candidate review | End transfer; preserve route/provenance |
| Any active work | Cancel | Prior safe state/recovery | Cancel job, invalidate request ID, suppress late results |
| Any photo state | Replace photo | Preparing | Cancel old request; clear old Candidates and assumptions |
| Candidate review | Edit serving | Serving editor | Preserve chosen foods and the photo; open the secondary editor only on request or when a required amount is unresolved |
| Candidate review | Explicit food confirmation and usable serving values | Grounding in place | Preserve only the user's explicit selections as authority; a future dish-associated suggestion remains separately provenanced |
| Serving editor | Save or Close | Candidate review | Return to the same photo-first screen; retain the draft on Close and recalculate only after an explicit save plus Calculate nutrients action |
| Grounding in place | Success | Candidate review — nutrient profile | Keep the photo and selected food icons visible; populate the in-place catalog-grounded nutrient profile, provenance, Edit serving, and Scan again |
| Any state | App backgrounds | Same or recovery | Pause preview; active transfer follows explicit background/cancellation policy |
| Candidate review — nutrient profile | Scan again | Capture | Clear the prior Analysis Session |

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
- Do not infer country only from language. `CatalogCountry` controls available
  Nutrition Sources and serving conventions; `MarketJurisdiction` controls
  disclosures and user-rights policy; approved policy resolves
  `ProcessingRegion`. A catalog or locale choice never changes the latter two
  axes.
- On first use, suggest the system region as a catalog proposal but require
  confirmation. Persist only the confirmed catalog choice and approved local
  display preferences. If the region is unavailable or unsupported, open Country
  and catalog with “Not listed” and manual recovery; never silently substitute
  the United States or persist a provider-routing choice.
- Support metric weights in all reference markets and localized display units
  where product research requires them; store canonical quantities independently
  of display.
- Evaluation and empty-state examples must include regional foods and avoid a
  US-only definition of a “normal meal.”
- Privacy copy is configuration-backed and reviewed per launch jurisdiction; it
  cannot be hard-coded into a shared screen.

## Key Flows

### Flow 1 — Maya gets a local result

1. Maya opens SuperNutrition.
2. On first use she reads the permission rationale, grants the platform camera
   permission, and confirms the suggested country/catalog.
3. Capture becomes ready; she frames lunch and taps the shutter.
4. Analyzing shows the frozen frame and `On this device`.
5. The Local Engine returns a small ranked set of main-dish proposals and,
   when the v2 component engine is active, visibly separable food components.
6. Maya explicitly taps the dish name that matches her meal, then taps only the
   ingredient icons she recognizes. Each selected ingredient says `From this
   photo`; nothing is silently selected.
7. The first screen shows eligible serving values as editable defaults only after
   canonical confirmation. Maya taps `Calculate nutrients`; if a selected food
   lacks a usable amount, she opens the optional serving editor and returns here.
8. **Climax:** this same photo-first screen now shows source-backed supported
   nutrients/vitamins/minerals and descriptive food properties, source,
   assumptions, and `On this device`; no calorie total or health outcome claim.
9. Maya taps Scan again; the prior Analysis Session is cleared.

Failure: quality gate finds blur → Maya sees why and retakes before inference.

### Flow 2 — Liam uses the future paid remote path (deferred)

1. Liam captures a mixed restaurant bowl.
2. Local analysis Abstains with a plain-language ambiguity reason.
3. Remote consent explains the smaller photo upload, purpose, non-retention
   default, and manual alternative.
4. Liam accepts; Remote analysis begins.
5. Candidates arrive with recipe/serving questions.
6. Liam corrects the dish and serving.
7. **Climax:** the same photo-first review screen shows the confirmed estimate
   and `Remote analysis`.

Decline: Liam chooses Continue without upload and reaches manual search.

### Flow 3 — Aisha corrects a regional food

1. Aisha imports a photo.
2. Candidate review shows an incorrect top result and alternatives.
3. She taps None of these and searches.
4. Food search uses the configured country Nutrition Source package.
5. She selects the right record and confirms serving/oil assumptions.
6. **Climax:** the same photo-first review screen shows her chosen food, source
   version, and estimate.

No match: numbers remain withheld and Aisha can change country/catalog or cancel.

### Flow 4 — Noah is offline without a Local Engine

1. Noah opens Capture offline and takes a photo.
2. Capability state explains that local identification is unavailable.
3. Remote analysis is unavailable rather than offered as an active action.
4. Noah selects Manual search.
5. He chooses a bundled food record and serving.
6. **Climax:** the photo-first calculation screen shows the estimate with
   `Manual` provenance and no upload.

Cancel: Noah ends the Analysis Session and the temporary Photo Input is cleared.

## Mock Coverage

The two layout-sensitive free/default surfaces now have draft, offline HTML
mockups. They illustrate the spine; the spines win on any conflict. Every mock
is phone-portrait and contains explicit large-text/reflow, minimum-target or
equivalent-input-route, reading-order/focus, and non-colour-state annotations.

| Surface | Draft mock | What it anchors |
| --- | --- | --- |
| Unified photo analysis | [.working/key-candidate-review.html#capture](.working/key-candidate-review.html#capture) | One photo-first surface: live preview/import becomes the retained photo, then dish/ingredient confirmation, separate add-ingredient suggestions, and the source-backed nutrient profile in the same lower panel; no calorie total or health outcome claim |
| Serving editor (optional) | [.working/key-serving-confirmation.html](.working/key-serving-confirmation.html) | Secondary edit-and-return screen for an estimated range or explicit no-evidence amount; no fabricated grams |

The unified photo-analysis mock starts with capture controls over a live preview.
After shutter/import, that image remains as the background while the in-place
review state shows unselected main-dish text choices and over-photo tappable
ingredient icons, and preserves `Other`/manual search. Its nutrient profile is
part of that same screen after explicit selection and catalog grounding; it does
not show a calorie total or a health claim. It distinguishes photo evidence from the future-only,
separately provenanced `Also common with this dish` suggestion group; neither
represents a measured gram value.

The unified photo-analysis and optional Serving editor mock set, along with the
UX-A1 palette direction, were approved by Product owner Anastasia on 2026-08-01.
The earlier five-mock approval was superseded by the free/default scope change.
Visual Compose work remains sequenced after E3.9 supplies its typed
result-to-review boundary and E5.1 integrates it. That work must verify the
approved palette under real contrast, dark-theme, image-scrim, responsive, and
accessibility conditions.
