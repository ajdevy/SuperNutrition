# MVP publication and main CI — 2026-10-24

## Authorization and starting state

The human explicitly requested pushing `codex/mvp-completion`, opening a PR,
merging into `main`, waiting for main CI and repairing every failure except
expired device checks. This authorizes the remote phase after the completed
local-only implementation workflow; its frozen specification remains historical.

- Worktree: `/Users/anastasia/.codex/worktrees/f2ff/SuperNutrition`.
- Initial clean branch/head: `codex/mvp-completion` at
  `509da213ae6e962c470539f7a6e5912999a2af66`.
- Refreshed `origin/main`: `0f1fbb4e599e0a71accaef5c074466436b9e8e0b`;
  feature branch is three commits ahead with no divergent base commits.
- Branch pushed; PR: https://github.com/flexsent-labs/SuperNutrition/pull/61.
- GitHub connector search returned permission/visibility error 422. The
  authenticated `gh` CLI can access this repository and is the fallback.
- Prior main run `40027554695` failed only at `verifyMobilePolicy` with
  `Native-gap evidence is stale: bootstrap-android-host`; prior Android and iOS
  UI workflows succeeded. No device manifest or expiry is changed for this task.

## R-01 — generated README snapshot is stale (recorded before repair)

The committed README still lists 1,040 source files and 325,882 code lines.
Executing the existing `build_coverage_site.count_loc(Path.cwd())` against the
tracked PR tree returns 1,214 files and 350,787 code lines. The CI workflow
regenerates these values and runs `update_readme_metrics.py --check`, so the
snapshot needs regeneration along with the newly measured coverage percentages.

Acceptance: generate the complete dashboard from the PR's real Kover reports,
update only the generated README section using the existing generator, and see
the exact CI `--check` pass. Do not change report definitions, coverage floors,
denominators, source exclusions or the expiry exception.

The first local dashboard attempt was incomplete because the existing XML for
`data-nutrition` had no matching HTML report; it was not a product failure or
successful dashboard check. `mobileCoverageReports` is running to provide the
full report set. The PR workflow is also running; its artifacts will provide
the authoritative hosted snapshot before merge.

## R-02 — missing strict-verification metadata (recorded before repair)

PR run `40108179854` failed before test execution while configuration-cache
serialization resolved `:data:nutrition:jvmTestRuntimeClasspath`:
`junit-bom-5.12.2.module` (`org.junit:junit-bom:5.12.2`) was not trusted in
`mobile/gradle/verification-metadata.xml`. Consequently the dashboard had no
fresh Kover XML and the README check had no `loc.json`; these two failures
are downstream of dependency resolution in this run, not completed tests.
The stale README finding above was reproduced independently from tracked source.

Acceptance: verify the exact missing Maven Central metadata against its
published digest, add that artifact's checksum without broad trust rules or
relaxing strict verification, rerun the exact JVM/report command, then generate
and check the README snapshot. Preserve the original failed run as RED evidence.

Hosted RED log: ignored
`mobile/build/verification/mvp-audit/pr61-ci-failed.log`.

## Repairs and local verification

- Added only `junit-bom-5.12.2.module` SHA-256
  `de70ac5d91a52656d8890a6d23b9e04d99b99b1a0402530decad71bf31b2735e`.
  Bytes and published `.sha256` agree at
  `https://repo.maven.apache.org/maven2/org/junit/junit-bom/5.12.2/`.
  Parsed component coordinates also match `org.junit:junit-bom:5.12.2`.
  No broad artifact trust or verification mode changed. Ignored provenance:
  `mobile/build/verification/mvp-audit/pr61-junit-bom-provenance.json`.
- Strict `mobileCoverageReports` succeeded in 5m32s, 181 tasks (53 executed,
  128 up-to-date). Existing unchanged tests were legitimately up-to-date;
  `platform:photo-ios:jvmTest` executed its host-only tests, not iOS runtime.
- Complete dashboard generation then succeeded. The unmodified README's exact
  `--check` failed with the expected stale-section diff (ignored
  `pr61-readme-red.log`), including the missing `data-nutrition` denominator.
  Ran the existing README generator and the same `--check` passed.
  Final source counts include the three new verification XML lines:
  1,214 files / 350,790 code lines. Optional/native report counters retain their
  measured values; dashboard generation is not a full quality-gate pass.
- The exact CI Gradle command after the checksum change completed with exit 1
  in 2m53s (204 tasks, 22 executed / 182 up-to-date):
  `./mobile/gradlew -p mobile mobileUnitCoverage mobileCoverageReports
  -Psupernutrition.runJvmCoverage=true --dependency-verification=strict
  --no-daemon --configuration-cache --max-workers=1 --continue`.
  Its only failed task is `verifyMobilePolicy`, reporting the accepted expired
  `bootstrap-android-host` record. Dependency resolution and report generation
  succeeded; full aggregate verification behind the failed policy dependency
  is not certified green. Ignored log:
  `mobile/build/verification/mvp-audit/pr61-strict-unit-report-after-checksum.log`.

## Main publication and R-03 — iOS XCTest failure (before repair)

PR #61 merged normally (no admin override) at 2026-10-24T16:05:33Z.
`origin/main` was fetched and verified as
`7ff3b197763ad3a15ac648fcdfc5f012c6eba547`, with parents the prior main
`0f1fbb4e599e0a71accaef5c074466436b9e8e0b` and PR head
`7a202654f55c59565bccda6eabf84e8227c5d065`. Their source trees agree.

- Main JVM run `40109951845` completed with exactly one failing task:
  `verifyMobilePolicy`, expired `bootstrap-android-host`. Dashboard and README
  passed. Downloaded test XML contains zero failures/errors/skips in all 13
  reported modules; exact per-module counts are retained in ignored
  `mobile/build/verification/mvp-audit/main-7ff3b197-unit-counts.json`.
- Main iOS run `40109951840` successfully compiled and linked
  `iosSimulatorArm64` SharedKit in 4m57s (62 executed tasks), then built the
  XCTest products. Actual simulator testing ended `TEST FAILED` / exit 65
  after 289.994 seconds. The quiet Xcode log omits the assertions.
  This is outside the accepted device-expiry exception and must be repaired.
- Downloading the actual `.xcresult` artifact to identify failures before
  changing code or tests. No product/runtime failure cause is assumed from the
  repeated debugger-version warnings alone. Android main UI run remains active.

R-03 acceptance: identify the actual failed XCTest names and assertions from
the result bundle; fix the demonstrated cause without deleting required tests,
weakening assertions or treating simulator execution as physical evidence;
verify focused/local applicable checks and rerun the full iOS CI on the repaired
main commit. The existing device expiry remains visible and unchanged.

Ignored original log: `mobile/build/verification/mvp-audit/main-7ff3b197-ios-failed.log`.

### R-03 diagnosed assertions (still before repair)

The real result bundle on iPhone 17 Pro arm64 / iOS Simulator 26.4.1 contains
9 cases: 6 passed, 3 failed, zero skipped/expected failures.

1. `testInitialLaunchShowsCaptureSurfaceAndCloseReturnsHome`: `XCTAssertFalse`
   at lines 18 and 30 sees the underlying `camera_action` in accessibility.
2. `testHomeAvatarOpensProfileAndBackReturnsHome`: `XCTAssertFalse` at lines
   168–170 sees underlying `camera_action`, `rewards_tab`, `home_add_food`.
   Arm64 now retains Main under `fullScreenCover` in production and acceptance;
   Intel replaced its root. The exported hierarchy confirms Home's descendants
   are retained under the modal, but does not prove touch/VoiceOver reachability.
   Preserve the existing nonexistence assertions; explicitly exclude the covered
   retained UIKit view from accessibility and interaction in both hosts.
3. `testTextMealSaveHistoryCorrectionAndDeletion` fails the initial Add action
   in `tapVisible` lines 221–222, before correction/deletion: `meal_form_save`
   is at y=615.3–665.3 while the keyboard begins at y=539. The actual hierarchy
   contains `UIContinuousPathIntroductionView` and its Continue button over the
   keyboard. The helper swipes the whole app, starting over this system overlay.
   Dismiss only this known first-run keyboard introduction and scroll inside
   the visible app area above the keyboard. Keep the full name/save/edit/delete
   assertions and ordinary keyboard input; do not suppress the failing scenario.

Distilled independent exploration confirmed the shared diary/session wiring is
consistent. Exact summary, test details, activities and hierarchy are ignored
under `mobile/build/verification/mvp-audit/main-*ios*`.

Android main UI run `40109951839` finished successfully at 16:22:22Z.

### Independent repair review (before any review patch)

Quick Dev blind and edge reviewers independently inspected the complete
bounded repair diff without conversation context. Edge returned no findings;
blind reported no established correctness defect and two explicit uncertainties:

- Whether the system tutorial string was an identifier: rejected from the
  exported original AX tree, which explicitly declares
  `identifier: 'UIContinuousPathIntroductionView'`.
- English-only `Continue` selection in the new helper: low-severity patch.
  The current CI snapshot is English, but matching the sole button inside the
  exact identified tutorial is independent of simulator language. Preserve the
  specific container guard, require exactly one button and keep failure visible
  for a different/ambiguous prompt. This avoids extending dismissal to generic
  system alerts. Record this concern before applying that small test-only patch.

No intent/spec gap or loopback was established; review loop iteration stays 0.

### Local repair validation and hosted handoff

- A single `MainComposeHost.swift` now owns creation/update of the same retained
  controller in both app targets. The UIKit view explicitly excludes its
  descendants from accessibility and touch while covered and restores both
  properties when uncovered. No visual-layer hiding or session recreation.
- All nine XCTest scenarios and the original five `!exists` isolation assertions
  remain. The helper handles only the identified keyboard introduction with
  exactly one action, independent of button language. Gestures clip to the
  earliest keyboard/input/assistant obstruction and remain bounded to eight.
- On this Intel Mac, actual unsigned arm64 Xcode builds succeeded for
  `SuperNutrition` (125.56s) and `SuperNutritionUIAcceptanceTests` (64.30s), using
  Xcode 26.6 / iphoneos26.5 and the unchanged already-linked arm64 SharedKit.
  After the review's test-only selector patch, the full XCTest target build was
  rerun and again exited 0 / `BUILD SUCCEEDED`.
  Logs: `mobile/apps/iosApp/build/verification/mvp-ios-ci-repair/` (ignored).
  No local iOS test execution occurred; runtime acceptance remains pending CI.
- `plutil -lint` on the Xcode project, exact dashboard/README generation and
  `--check`, and `git diff --check` passed. No Kotlin/dependency/policy source
  changed in this repair; their same-tree main results above remain applicable.
- The local Quick Dev implementation/review is complete. The separately
  authorized remote phase must publish this patch and wait for all workflows
  on its exact merged main commit; this handoff does not call hosted CI green.

## R-04 — remaining iOS runtime failure on repaired main (before repair)

PR #62 merged normally at 16:37:24Z into
`08e668bc51ba136a5f9f7c9771349cc41e8053b8`; fetched `origin/main` confirms it.
Its JVM run `40112411970` fails only the unchanged expired
`bootstrap-android-host` policy record. Fresh downloaded XML confirms zero
failures/errors/skips in all 13 modules, and hosted README/dashboard passed
with 1,215 tracked source files / 350,835 code lines.

iOS run `40112411913` compiled and linked, then XCTest ended `TEST FAILED`
after 348.002 seconds, exit 65. The first `--log-failed` request returned an
empty log despite a completed/failure run; fetching the full log supplied the
actual failure. The job summary also temporarily retained stale in-progress
step entries, so the complete log and xcresult are authoritative over that
partial snapshot. No assertion cause is assumed until reading the new bundle.

Downloading artifact `12567557076` (162,823,011 compressed bytes) for exact
test names/assertions. Ignored full log:
`mobile/build/verification/mvp-audit/main-08e668bc-ios-full.log`.
Acceptance remains all original nine XCTest scenarios plus Maestro with no
skips/relaxed assertions, then completed latest main CI except device expiry.

### R-04 exact assertions and native boundary plan (before repair)

The completed main08 bundle again has 9 cases, 6 passed / 3 failed / 0 skipped.
Home isolation still fails the same five `!exists` checks. The keyboard fix did
work: the meal scenario creates, saves, opens history, edits and verifies the
exact corrected title. It now fails because `meal_details_delete_confirm` never
appears after the Delete tap; its final hierarchy still shows the Details view.
The extra empty Window alone does not establish whether a dialog was presented.

Independent exploration of pinned Compose 1.11.1 sources establishes that AX
roots live on internal overlay views and automation elements are published
separately; it found no source evidence of Compose overwriting the root flag.
Replace the insufficient root flags with a plain UIKit parent retaining Main
as a child and detach only the child's view while covered. Keep controller
containment: CMPViewController's in-window test includes its parent, whereas
removing the controller would permit scene disposal. Reattach the same view
with balanced appearance forwarding. Preserve all isolation assertions and
reopen checks. Diagnose the Delete action separately from exported snapshots
before changing its implementation or test interactions.

Android run `40112411928` finished successfully on this same main commit.
The next repair must pass a dispatched full iOS workflow on the feature branch
before merge, then await all workflows on the exact merged main head.

### R-04 recording and failure-reporting gap (before repair)

The exported screen recording contains the actual app pixels (1206x2622).
Frames at 59s and 64s show the Delete button and no confirmation; the recorded
tap is (201,790.333) in the 402x874-point screen and inside the visible button.
This establishes a missing response rather than merely a missing AX locator,
but does not yet distinguish gesture delivery from dialog-layer presentation.
Original video and extracted frames remain ignored under the main08 artifact.

The runner uses `set -e`, so failed xcodebuild exits before exporting its compact
XCTest summary. Preserve the original failing exit code, but always export the
summary when a bundle exists before returning failure. This keeps the gate
strict and exposes the failed assertions without another 163MB download.

### R-04 bounded gesture synchronization plan (before test repair)

Pinned foundation1.11.1 Scrollable/Draggable explicitly consumes the next down
while scroll/overscroll is in progress. This is a supported hypothesis for the
single correctly positioned tap producing no dialog; it is not yet a confirmed
runtime diagnosis. The existing helper taps as soon as AX reports hittable,
which does not establish animation completion. After scrolling, wait boundedly
for a continuously stable, hittable target frame before one tap. Do not retry
Delete or its confirmation, and keep all outcome assertions unchanged. Hosted
branch execution must establish whether this addresses the actual failure.

### R-04 review finding (before correction)

Blind review found that the new stable-frame predicate did not reset its time
baseline when the element became unavailable/non-hittable. That interval must
not count toward continuous stability. Reset both the prior frame and the
clock on that branch, retaining the same bounded timeout and one eventual tap.

Edge review found the moved summary export needs an explicit missing-bundle
failure: otherwise a successful xcodebuild with no bundle could let an old
summary survive in the reusable report directory. Fail closed when this run's
bundle is absent. This preserves the original behavior of xcresulttool failure
on a nonexistent exact bundle. Verify missing-bundle and failure-code paths.

### R-04 local verification before branch publication

Both current unsigned arm64 products built successfully with Xcode26.6 on
Intel: `SuperNutrition` and `SuperNutritionUIAcceptanceTests` (including the
acceptance host). No local iOS runtime execution is claimed. Logs are under
`mobile/apps/iosApp/build/verification/mvp-ios-ci-repair-r04/`.

Independent blind and edge reviews are complete; both concrete findings above
were recorded first and fixed. Bash syntax, six mocked runner exit/report paths
(including a stale summary and absent current bundle), the six Python report/
README unit tests, project plist lint, actual dashboard/README generation/check
and `git diff --check` passed. No Kotlin, dependency or policy source changed.
The next evidence is the full arm64 simulator branch workflow, still pending.
