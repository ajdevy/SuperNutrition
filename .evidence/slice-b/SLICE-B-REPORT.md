# Slice B — real consent before any remote transfer

Worktree `/Users/jj/Work/SuperNutrition-integration`, branch `integration-test`, base `d33888f`.
Primary checkout `/Users/jj/Work/SuperNutrition` (`feat/e43-gateway-impl` @ `5a4781a`) untouched and clean.
No push, no PR, no remote refs created. Host: Intel `x86_64`, macOS. Device: `Pixel_9_Pro` AVD (API 17 build tag), emulator only.

---

## What

**The synthesised consent is gone.** Slice A's `testModeConsent()` asserted a granted receipt the user
was never asked for. Nothing synthesises one now: the store is empty until a person presses a button,
and an empty store yields `granted = false`, which `RemoteInferenceEngine` turns into a typed failure
without opening a socket.

### 3.1 Consent (highest priority) — done

| Layer | File | What it holds |
|---|---|---|
| domain | `domain/photo-analysis/…/RemoteTransferConsent.kt` | `TransferConsentReceipt` (all 14 contract fields + patterns), `RemoteTransferDisclosure`, `RemoteTransferConsentPolicy`, `AttemptScopedConsentStore` |
| presenter | `feature/photo-analysis-presenter/…/RemoteTransferConsentPresenter.kt` | `RemoteTransferConsentState`, `ConsentCopy`, disclosure rows |
| UI | `feature/photo-analysis-ui/…/RemoteTransferConsentScreen.kt` | the surface, both answers neutral |
| host | `feature/photo-analysis/androidMain/…/PhotoAnalysisConsentWiring.kt` (in `PhotoAnalysisEngineWiring.kt`) + Activity `ConsentGate` | gate, receipt, store binding |

- **Explicit, no default, no pre-selection.** `RemoteTransferConsentState.Askable` has no `selected`
  field at all — a model that cannot express a pre-selection cannot drift into one. Both answers are
  `OutlinedButton`s; neither is filled, because emphasis *is* a pre-selection.
- **Declined is recorded.** A declined receipt is constructible with neither region nor copy version;
  a **granted** one with either missing fails construction.
- **Market and region shown before the decision**, first in row order, from
  `MarketPolicyRegistry.mayTransfer(...)` with the presented copy version passed in (not read back).
- **Digest binding** over the scrubbed normalized bytes (`digest_covers`), part of the store *lookup*
  rather than a check a caller can forget.
- **Three market states all handled**: `MarketPolicyRegistry.unresolved` is empty, so this build shows
  “Remote analysis is not available in your market yet. Your photo stays on this device.” and offers
  **no** answer. Granted-but-refused and granted-and-permitted are covered by tests against a fixture
  registry.
- **Interaction budget** recorded in `positive-scenarios.toml` / `journeys.toml` (UJ-2): `tap_budget = 1`,
  `choice_budget = 1`, proposal/decision ids, `override_path = "explicit-choice-only"`,
  `undo_path = "scan-again"`, and the complete forbidden-default inventory including `remote-consent`.
  The forbidden-default test is `RemoteTransferConsentScreenComposeTest.'rendering the surface decides
  nothing until someone presses'`.

### 3.3 / 3.4 / 3.6 — done

- `positive-scenarios.toml` 1 → **3** scenarios (UJ-1 capture-recovery, UJ-2 remote-consent, UJ-3
  candidate-review); `journeys.toml` 1 → **3**; `ui-evidence-profile.toml` robot_ids, maestro_flow_ids
  and claims extended; `monotonic = true` / `downgrade_allowed = false` untouched; evidence-binding
  manifest digest recomputed (`7a77e89…`).
- `.maestro/photo-analysis/photo-analysis-remote-consent.yaml` written, plus consent locators. Uses
  resource ids throughout (`testTagsAsResourceId` is set).
- **Carried item — the bare `@Suppress("CyclomaticComplexMethod")` is removed**, not justified. Obeying
  the rule broke no contract, so per AGENTS.md the real fix applied: `deserialize` now delegates
  per-element dispatch to `readElement` + a `ManifestFields` holder.

---

## How verified

All counts read from XML, never from BUILD SUCCESSFUL.

**`qualityCheckJvmAndroid --continue` → BUILD SUCCESSFUL.**
`JVM host coverage: line=99.63%, branch=90.57% across 11 production modules.`
`:domain:nutrition coverage: line=99.99%, branch=100.00%` (strict floor untouched).

**`qualityCheckApple --continue` → BUILD SUCCESSFUL** (`uname -m` = `x86_64`; this is lint over
`iosMain`, not execution).

**`jvmTest --rerun-tasks` → 711 tests, 0 failures** (was 683; +28).

| module | tests | module | tests |
|---|---:|---|---:|
| domain/nutrition | 244 | inference/remote | 46 |
| domain/inference | 155 | core/config | 32 |
| **domain/photo-analysis** | **118** | inference/local-android | 26 |
| **feature/photo-analysis-presenter** | **13** | inference/local-ios | 22 |
| feature/meal-search-ui | 12 | feature/photo-analysis | 11 |
| **feature/photo-analysis-ui** | **9** | testing/compose-support | 5 |
| core/ui | 4 | apps/sharedKit | 3 |
| platform/photo-ios | 2 | testing/scaffold-probe | 2 |

`:domain:photo-analysis` branch coverage **84.68% → 87.26%** after covering the rejection paths my new
code added (it dipped below the floor first; that was fixed by tests, not by a floor change).

**Connected instrumentation**, `:apps:androidApp:connectedAndroidTest` on `Pixel_9_Pro(AVD)`, from
`build/outputs/androidTest-results/connected/`:

| flavor | tests | failures | errors | skipped |
|---|---:|---:|---:|---:|
| productionDebug | 21 | 1 | 0 | 0 |
| uiAcceptanceDebug | 21 | 1 | 0 | 0 |

Both flavors ran (the second needed its own invocation — the aggregate task fails fast). 21 is up from
7: my 6 consent tests plus the existing suite. The single failure is analysed under **Open**.

**Contract validators** — all exit 0: `verify-inference-contract`, `verify-inference-v2-contract`,
`verify-inference-v2-binding`, `verify-mobile-api-contract`, `verify-product-identity`,
`verify-root-boundaries`. `git diff --check` clean.

**Tests watched failing first** (AGENTS.md §38), by mutating production code and confirming the
*named* test failed:

| mutation | test that failed |
|---|---|
| drop `&& it.digest == digest` from the store lookup | `aDecisionAuthorisesOnlyTheAttemptAndBytesItWasMadeFor` |
| `if (decision == GRANTED)` → `if (false)` in the receipt | `aGrantedReceiptMustRecordWhatTheUserWasShown` |

Both mutations were reverted and the module re-run green (14/14, then 118 module-wide).

Two defects were caught by tests during the work, not by inspection: the disclosure rows were tagged
without `mergeDescendants`, so the automation node exposed no text to a test, UI Automator or a screen
reader; and the consent surface first rendered as a second root with its own
`testTagsAsResourceId`, which is now one tree.

---

## Architecture decision — the v2 component slot stays a fixture

`FixtureComponentEngine` remains, declared as `fixture-component-engine`. It is **not**
`LocalAndroidComponentInferenceEngine`, because that engine's `enforceProductionActivation()` correctly
refuses any manifest whose activation is not `PRODUCTION`, and satisfying it would mean stamping a
fixture with product, engineering, legal, corpus, benchmark and rollback approval refs it does not
have. Forging those to make a demo run would disarm the control that matters.

**Swap point.** The real v2 component slot becomes available when the client supplies a real model
artifact: model binary + taxonomy, a weighed corpus reference, benchmark evidence, and a mass
calibration — together with product/engineering/legal approval refs and a rollback target. At that
point `FixtureComponentEngine` is replaced by `LocalAndroidComponentInferenceEngine` with a
`PRODUCTION` manifest, and `enforceProductionActivation()` starts passing on its own terms. Nothing in
this slice needs to change for that swap. `enforceProductionActivation()` was not touched.

## Handoffs

- **Market declaration (client + legal).** `MarketPolicyRegistry.unresolved` is empty, so this build
  can offer no remote transfer to anyone. A real market needs `market_id`, jurisdiction, residency
  posture, permitted processing regions, per-modality permission, per-modality consent copy version, a
  **named** `legal_review_approver` and an expiry date. No real jurisdiction was invented here; the
  fictional `ZZ` fixture is the only declared market and it exists in `contracts/market-policy/v1/fixtures`.
- **Consent copy (legal + content).** `photo-transfer-copy-v1` is a version string, not reviewed text.
  The surface shows the version; the disclosure wording itself needs review before any real market.

---

## Open / deviations

- **One connected test fails, and it is pre-existing.**
  `PhotoAnalysisComposeSemanticsInstrumentationTest.captureSurfaceExposesStableAutomationIdsAndLabels`
  fails with `missing Compose semantic id: photo_analysis_title`. I proved it is not mine: with my
  `ConsentGate(state)` call commented out and everything else unchanged, the test fails **identically**.
  Direct device evidence contradicts the test, not the app — launching the activity explicitly and
  dumping the hierarchy shows `resource-id="photo_analysis_title"` with `text="Capture"` present. The
  test launches via `getLaunchIntentForPackage`, and during the run `monkey -c LAUNCHER` reports
  “No activities found to run”, so the failure is in how the suite launches the app, not in the
  surface. Slice A never ran the connected suite (it launched the app by explicit component), which is
  why this surfaced now. **Not fixed in this slice** — it is outside the consent scope and deserves its
  own diagnosis.
- **Maestro flows are written but NOT executed.** The CLI is not installed on this host. The consent
  flow is fingerprinted for 2.7.0 per `.maestro/maestro-cli-2.7.0.sha256` but carries no run evidence.
- **No physical device.** Camera and native-gap evidence require one; none is attached. Not faked.
- **3.2 review states — assessed, not extended.** `PhotoAnalysisReviewContent` already renders dish
  candidates, visible components with provenance, and composition hints labelled "Dish hint, not visual
  evidence"; Slice A proved this end-to-end on the emulator. I added the UJ-3 scenario and journey so
  the catalog covers it, but wrote **no new review presenter/UI code** — I found no unreachable review
  state to justify it. If you expected new review surface work here, this is the gap.
- **`verify-release-immutability` not re-run.** It reports `contracts/mobile-api/v1` modified since
  `c091a43`; pre-existing, not from Slice A or B, and a contract-owner call. That release was not touched.
- **iOS `TrackedPhotoPickerViewController`** remains dropped from the #29 merge with no equivalent. It
  cannot be compiled or run on this Intel host; iOS-owner call, not hand-spliced. No `iosMain` file was
  changed in this slice, so no `compileKotlinIosArm64` run was required.
- **`graphify update .` not run.** There is no `graphify-out/graph.json` in this worktree and no
  `graphify` on PATH, so there is nothing to update here.
- **Consent is currently reachable only in test mode.** `ConsentGate` returns early unless
  `PhotoAnalysisEngineWiring.isTestMode(...)`, because outside test mode no engine is bound and there is
  no transfer to consent to. The consent machinery itself is production code with no fence; only its
  entry point is gated, and it will need unfencing in the same story that unfences the engines.
