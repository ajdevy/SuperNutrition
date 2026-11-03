# Include staged main Rewards redesign — 2026-10-17

Final scoped verification: 167 shared UI tests pass with zero failures and the
changed feature iOS Arm64 source compiles. UIKit runtime is unavailable and the
whole quality gate retains its separate native-evidence expiry. The human
explicitly retained ten medals and prototype levels; See all remains an
unresolved nonfunctional prototype action. Details and exact evidence follow
the integration history below.

The human explicitly selected “Include also uncommitted Rewards from main in the push” after the first main publication. Preserve the exact 26-file staged slice from `~/workspace_ai/SuperNutrition` at e1271c6 and integrate it into the owned current branch without altering that dirty checkout. The source is a previously prepared ten-card Rewards redesign, with copied native/iOS labels, medals/background, section headers and Back action.

## Findings and acceptance before integration

The earlier separate work was unfinished: its last known UI run had three failures around the background expectation and final-card/navigation overlap. This history is not current passing evidence. Reproduce current failures after combining, then fix actual viewport/end-clearance behavior without removing accessibility or refresh assertions. Keep the already required transparent background behind floating navigation on both Home and Rewards, current photo/gallery/permission/IME behavior, and the ongoing Home no-scroll work. Preserve and inspect all current/untracked Home evidence and focused regression files while resolving shared test conflicts. Do not copy an entire old host or test file over current fixes.

The exact staged binary patch, 26 content hashes and current Home work hashes are saved under ignored `mobile/build/rewards-main-staged-2026-10-17/`. Snapshot the staged tree into an isolated commit parented to its real e1271c6 baseline, then merge that commit into the owned source branch. Source/test validation and explicit iOS compilation follow before authorized normal publication. The physical native-evidence expiry remains an unrelated policy failure; never lower it to publish.

The exact staged tree was captured as `7d5971c0d87e3a2c87b8ab35b86628f0ce7a17f9` on isolated ref `codex/rewards-main-staged`, parent e1271c6. Its primary checkout, staged binary patch and all 26 hashes remain unchanged. Ordinary merge into 7ec6150 exposed two conflicts only, archived before resolution. The new RewardsScreen replaces the old list with the requested ten-card design. The shared visual test retains the current Home/overlay regressions and opaque-strip pixel assertion while adopting the new section/card alignment and 16dp Figma gutter. Only obsolete LazyColumn ScrollToIndex import/setup was removed; model/state/refresh checks remain. No Home font/control or current gallery/permission/IME source was overwritten. Current untracked Home regression/capture helper hashes were preserved. Fresh verification follows before final merge publication.

## Current combined verification findings before corrections

Full current shared UI has **157 tests / 5 failures / 0 errors / 0 skipped**, 29 fresh suites. All Home fitting/legacy cases pass. New Rewards fails two last-card clearance preparations, one palette/background expectation, and two legacy LazyColumn ScrollToIndex calls in additional/overlay suites. Preserve exact assertion meaning: test reachability at the actual end of the new regular vertical-scroll canvas, retain whole final-card/control clearance, and use a single truthful new-design background token for root/body/native checks. Imported Rewards rendering also has lint findings for method length, parameter shape, helper separation and formatting; new Home test diagnostics have two formatting findings. Full details are archived in `full-combined-valid/actionable-findings.json` before fixes.

Overall Gradle exit is1, 498 tasks /7m58s. Shared UI/feature `iosArm64` compilation and Android assembly are distinguishable from whole sharedKit compilation: its existing photo-ios cinterop dependencies fail `MissingXcodeException` (`xcrun xcodebuild -version` cannot find xcodebuild). Inspect existing Xcode installations/tool selection before diagnosing source; no iOS whole-host pass is claimed. The unchanged physical-evidence expiry is a separate root-policy failure. No policy lowering, blanket suppression or assertion deletion is authorized.


### Apple prerequisite and actual source compilation

On2026-10-18, this Intel host selects`/Library/Developer/CommandLineTools`; no Xcode app is present in the two configured Applications folders. The full sharedKit Gradle compile remains blocked before Kotlin compilation by four existing iOS cinterop tasks requiring`xcrun xcodebuild`. This is a prerequisite failure, not a passed module or Apple-device result.

The actual changed`IosPhotoAnalysisCopy.kt` was separately compiled to an iOS Arm64 KLIB with the bundled Kotlin/Native2.4.10 compiler, Foundation platform library, and the real updated presenter/domain/core KLIBs (no stubs or fake SDK headers). This source-only compilation exited0. Exact command, source digest and raw log are in`mobile/build/home-no-scroll-2026-10-17/apple-copy-source-check/`. It proves this changed source typechecks; it does not replace full sharedKit linking, Xcode, or device verification.

## Final scoped integration verification — 2026-10-18

The final `full-final-scoped` run (2026-10-17T23:57:30.559541Z through
2026-10-18T00:01:03.374332Z) has **167 shared UI tests / 0 failures / 0
errors / 0 skipped**, verified from 30 fresh XML suites. The retained Figma
alignment, background/transparent-navigation pixels, whole-final-card
clearance, copy/state/refresh checks and Home/gallery interaction regressions
remain in the combined suite. Rewards R01–R04 now have witnessed red-to-green
coverage for complete header/card text at normal and enlarged sizes, a 48dp
Back hit region preserving the 38dp visual circle, working Compose Back, and
link/backdrop contrast at least 4.5. The fit measurer and both Rewards renderers
share the same typography implementation.

Exact command, input hashes, raw log and fresh test XML are under
`mobile/build/home-no-scroll-2026-10-17/full-final-scoped/`. The overall command
still exited 1 at `:verifyMobilePolicy`: `Native-gap evidence is stale:
bootstrap-android-host`. The shared UI result is green; the complete quality
entrypoint remains red on that independent expired-evidence requirement.

R05's changed iOS feature source actually compiled: the final
`:feature:photo-analysis:compileKotlinIosArm64` task executed successfully after
the missing `navigationController` import was fixed. The iOS Back adapter
preserves the two-argument factory and supplies pop/dismiss wiring. UIKit
runtime validation remains unavailable, and the sharedKit/Xcode prerequisite
limitation above remains distinct from this feature-module source pass.

The human answered the representation question in direct conversation on
2026-10-18: «Сохранить 10 медалей; новые уровни пока оставить как макет».
R07–R09 therefore preserve the ten-medal prototype intentionally. Static level,
points, visual goal text, higher-tier duplicates and index-based section
grouping remain prototype presentation; the existing seven domain criteria
are unchanged and no new reward/level criteria are promised.

R06 is unresolved prototype behavior: See all remains a nonfunctional label,
without an actual earned collection or reversible dismissal. Passing layout,
readability and existing interaction regressions does not establish a clean
whole-interaction pass while that action and UIKit runtime validation remain
open. The review status is tracked in
`home-rewards-review-triage-2026-10-18.md`.

This report update is documentation-only. Production source/tests remain frozen
for the Android APK used by the current 94-image native matrix; device results
and publication state are tracked separately by the parent.
