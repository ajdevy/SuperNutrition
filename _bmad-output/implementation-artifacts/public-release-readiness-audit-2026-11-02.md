# Public release readiness audit — 2026-11-02

## Scope

Prepare the existing SuperNutrition GitHub repository for a future public
release while preserving the project history and linked author identities as
far as the requested cleanup permits. The repository remains private during
this work.

## Findings and acceptance conditions

### PRR-1 — detector and AGPL-dependent research artifacts remain in the tree/history

In addition to the production integration, research tooling still includes an
FPB/Ultralytics runner and generated predictions, a standalone Android
detector activity, and a Nutrition5k detector-fusion harness with generated
outputs and presentations. These are outside the selected dish-classification
runtime but would remain part of the public repository and its history.

**Acceptance:** the app and benchmark harness contain no executable
AGPL-dependent integration or detector model assets; the FPB runner/tests,
fusion harness, and generated model outputs are removed; Food-101 dish
classification remains usable; all removed source, predictions, and model
binaries are absent from every authorized GitHub branch and tag history.

### PRR-2 — private runtime source remains in public-project history

The Android runtime source has a separate private repository, but the original
Java implementation and its tests remain reachable from SuperNutrition history.

**Acceptance:** canonical runtime implementation and AAR remain in the private
library repository; all historical copies are removed from SuperNutrition.

### PRR-3 — user meal photographs remain tracked

Five timestamped meal photographs are tracked under
`mobile/fixtures/photo-analysis/user-samples/` and are not marked as synthetic.

**Acceptance:** remove those photographs from the current tree and all
reachable SuperNutrition branch/tag history; update any fixture documentation
to use made-up examples.

### PRR-4 — AAR Maven metadata publishes a license entry

The checked-in local Maven POM for runtime `0.1.1` contains a MIT `<licenses>`
entry. The AAR itself contains no standalone `LICENSE` file.

**Acceptance:** the distributed AAR and its Maven metadata contain no license
file or license entry, while the private source repository retains its source
license.

### PRR-5 — GitHub history rewrite affects many refs

The GitHub repository is private, has many remote branches and tags, and has one
open pull request (#64). A history rewrite changes commit IDs and may invalidate
existing PR diff comments. GitHub notes that cached views, PR refs, forks, and
other clones can retain old objects after a force-push.

**Acceptance:** preserve logical commits, authors, and unaffected files while
rewriting every authorized branch and tag; verify the new remote refs and scan
their reachable history. Keep the repository private until review completes.

### PRR-6 — personal and machine-specific identifiers remain in project files/history

Setup and evidence files contain a real Apple developer account name/email,
fixed Team ID, physical-device UDID, and generated simulator UDID. Earlier
generated review metadata also records the developer's absolute machine path.

**Acceptance:** require contributors to provide their own Apple signing identity;
remove the account details, fixed Team ID, physical-device UDID, simulator UDID,
and developer home paths from current files and every reachable history.

### PRR-7 — YoloX Nano benchmark remains after D-13

The prepared `codex/public-readiness` tree still tracks
`tools/g3-benchmark/android-runner/.../YoloXNanoActivity.java` and the generated
`ingredient-weight-research/yolox-nano-*` evidence. The activity remains
reachable from public branch history, including `main` and
`codex/public-readiness`; the evidence remains reachable from
`codex/public-readiness`, despite D-13's removal decision for the standalone
Android detector benchmark and generated outputs.

**Acceptance:** remove the standalone YoloX Nano activity and its generated
evidence from the current tree and every reachable GitHub branch and tag, while
preserving the remaining commit sequence, author identities, and Food-101
classification integration.

### PRR-8 — reward cards show through the floating navigation side gaps

The Android screenshot test `BrandNavigationVisualInstrumentationTest` fails
at the right-edge backdrop check in all three app flavors. The captured Rewards
screen shows a white card behind the bottom bar's right side gap, where the
backdrop should remain `#F6F7F5`.

**Acceptance:** cover the full-width bottom-bar area with the selected screen
backdrop before drawing the floating pill, and pass the instrumentation check
in `poc`, `production`, and `uiAcceptance` flavors.

## Current status — 2026-11-03 follow-up

The public-readiness files are present: root README, MPL-2.0 LICENSE, and ROADMAP
with currently open issues. README covers purpose, audience, stack, and run
instructions. `backend/mobile-api/.env.example` uses example values, and
`.gitignore` excludes `.env` and local credential/config files. The current
commit uses the GitHub-linked `ajdevy` noreply address.

The Android app now resolves the inference AAR from the private GitHub Packages
repository, with credentials kept outside the checkout. `read:packages` access
was authorized for this local build. The AAR and its POM contain no license
metadata, as requested. Contributors still need access to the private package
to build the app.

The rewrite removed the YOLO-World/AGPL detector assets and integrations,
YoloX Nano and FPB detector research artifacts, Nutrition5k fusion benchmark
outputs, the five user meal photographs, and the recorded machine/device
identifiers from reachable branch and tag history. All 51 branches and 2 tags
were updated atomically with exact leases; a post-push comparison confirms all
53 refs match the rewritten mirror. The repository remains the existing
GitHub repository; no fork or replacement repository was used. History was
rewritten to remove paths, not intentionally squashed. Five pairs of commits
became identical Git objects after their changed trees and ancestry converged.

GitHub exposes 55 read-only `refs/pull/*/head` refs and one additional
`refs/pull/64/merge` ref. The rewrite's `changed-refs` report lists all 55 PR
head refs as affected. Only one head has the same tip as a rewritten branch
or tag; the other 54 heads are stale. Fetching the pull refs confirmed that
they still reach six user-sample paths (including the five meal photos), the
YOLO-World ONNX model, three YoloX Nano files, five FPB files, and 28
Nutrition5k benchmark/presentation paths. Those objects are absent from the
rewritten branches and tags but remain reachable in GitHub-hosted PR history.
GitHub documents that `refs/pull/` is read-only and that Support can remove
affected PR references for qualifying sensitive data; Support will not remove
non-sensitive data. See [Removing sensitive data from a
repository](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository).
The repository has zero forks. The filter-repo output did not report orphaned
LFS objects. A Support request was prepared but not submitted. On 2026-11-03,
the owner declined the request and instructed that the existing pull-request
refs remain unchanged. Keep the repository private while those refs still
expose the five real user meal photos and removed model and benchmark artifacts;
this is not approval to make the repository public with those photos reachable.

At pushed commit `db953b5c`, Gitleaks scanned 1,227 commits reachable from the
branches, tags, and fetched pull-request refs and reported 185 heuristic `generic-api-key`
matches. Review classified them as generated UI key/hash metadata, intentional
fake secret-scanner fixtures, the repository file index, and the literal
`REDACTED` placeholder in historical benchmark examples. No confirmed
credential was found. Two older credential-shaped benchmark values had
already been replaced by `REDACTED`; their original validity cannot be
established from this history, so revoke them if either was a live key. The
only secret-like filename in reachable history is the placeholder
`backend/mobile-api/.env.example`; no private-key or non-example `.env` file
was found.

The full command
`./mobile/gradlew -p mobile :apps:androidApp:connectedAndroidTest --no-configuration-cache --console=plain`
completed successfully on `SuperNutrition_API_35_x86_64(AVD) - 15` (API 35,
x86_64). The XML reports show 65 tests, 0 failures, 0 errors, and 0 skipped in
each of `poc`, `production`, and `uiAcceptance` at
`mobile/apps/androidApp/build/outputs/androidTest-results/connected/debug/flavors/`.

The root LICENSE now contains the official MPL 2.0 text, selected as an
accepted copyleft license. Its source-sharing obligation applies when covered
files are distributed; it does not require public release of private
modifications. See owner decision D-14. The owner declined GitHub Support
cleanup in D-15, so public release remains blocked while real user meal photos
are reachable in GitHub pull-request history. The two credential-shaped
benchmark values are redacted from the rewritten history; no live credential
was confirmed, and their original validity cannot be established from the
retained data.

## Updated status — 2026-11-03 after D-16 and D-17

D-17 records the owner's clarification that the five retained meal photographs
are public, not private. The earlier D-15 description of them as private
personal data is superseded. The owner still directed that GitHub pull-request
refs remain unchanged; this clarification does not change repository
visibility. The current SuperNutrition repository is still private.

D-16 is implemented in the SuperNutrition working branch
`codex/local-inference-optional`. Default and hosted builds no longer resolve
or fetch the private AAR. A contributor with access to the private source can
opt into a local Gradle composite build using
`-Psupernutrition.attachLocalFoodModel=true` and
`-Psupernutrition.foodModelSource=/absolute/path/to/private/repository`. Without
the AAR, the app remains usable; requesting photo identification shows a
dismissible unavailable-model dialog, and manual meal entry remains available.
The root README documents the optional build. These SuperNutrition changes are
not yet merged as of this status update.

The private AAR repository README now contains full local assembly and
integration steps. Its GitHub Packages publishing workflow and Maven publication
configuration were removed. PR #1 was merged with a merge commit, preserving the
new commit and prior history: `b3b85a3` is the feature commit and `c86621f` is
the merge commit on private-repository `main`. The source repository remains
MIT-licensed; the locally generated AAR contains no license file or metadata.

Validation for the optional local-model change:

- Default `productionDebug` assembly passed without private source access.
- Opt-in `productionDebug` assembly passed using the private source checkout.
- Private AAR `clean testDebugUnitTest assembleRelease` passed; all 5 tests
  passed. The AAR SHA-256 is
  `36cec9481a84ff939ea05c17c4b1b246f6146f8b4889ac14d7e26842b488abf0`.
- Android emulator instrumentation passed in `production`, `poc`, and
  `uiAcceptance`: 65 tests each, 0 failures, 0 errors, 0 skipped (195 total).
  Reports are under
  `mobile/apps/androidApp/build/outputs/androidTest-results/connected/debug/flavors/`.
- Emulator smoke confirmed the missing-model dialog appears on photo action and
  manual text entry remains usable after dismissing it.
- `git diff --check` and `scripts/verify-owner-decisions.sh --strict` passed;
  the owner-decision validator reports 17 valid entries.

Secret-scan evidence:

- A fresh Gitleaks scan of the current default branch scanned 319 commits and
  reported 91 `generic-api-key` heuristic matches. Manual review found these to
  be screen-size labels in generated UI review JSON, generated hash metadata,
  the `key-screens` repository file-index row, and deliberate fake values in
  `scripts/secret-scan-fixtures.sh`. No credential was confirmed.
- Gitleaks reported no findings in the current SuperNutrition working diff or
  the untracked UX finding. The private AAR repository scan covered its 5
  commits and current diff with no findings.
- The prior complete scan at `db953b5c` covered 1,227 commits reachable from
  the then-current branches, tags, and fetched PR heads; its 185 heuristic
  matches were reviewed with no confirmed credential. The current `pull/65`
  head is identical to the already-scanned `codex/public-readiness` branch.
- A supplementary scan over all local refs timed out after 1,264 of 1,383
  commits. Its partial report contained 265 generic matches in the same known
  generated metadata, intentional scanner fixtures, file index, and redacted
  benchmark examples. It is not represented as a complete all-local-ref scan;
  the earlier complete GitHub-ref scan and the fresh current-default-branch and
  working-diff scans are the applicable evidence for the repository being
  prepared.

## Hosted PR validation — 2026-11-03

PR #66 is open at `14126892893575e5f50b091d5386fd55a9b2463a`, authored with
the GitHub-linked `ajdevy` noreply address. The required
`mobile-unit-coverage` run `41686335941` failed at `:verifyMobilePolicy` because
the committed `bootstrap-android-host` evidence was executed on
2026-09-12 and exceeded its configured 30-day age. The CI checkout had no
physical Android device attached; the earlier API 35 emulator pass does not
renew physical-device evidence. Do not refresh this evidence with emulator
results or weaken its age policy.

The workflow uses `--continue`, so module tests and report generation continued
after the policy task failed. Uploaded JUnit XML contains 1,178 tests across 12
modules, with 0 failures, 0 errors, and 0 skipped. The coverage dashboard
generation passed. A separate README snapshot check found stale source counts;
the README metrics were regenerated from this run's uploaded dashboard data to
1,182 tracked source files and 212,273 code lines. The local check against that
artifact passes. The PR remains open and must not be merged while its required
policy check is failing.

## Optional private food-model follow-up — 2026-11-03

The owner clarified that the private food-identification source and its AAR
must stay out of the public project tree. Contributors assemble and integrate
the library locally from the private source repository. The root README now
includes the private-repository clone and build commands, and `.gitignore`
excludes `*.aar`. A current tracked-file check found no AAR binary in the
SuperNutrition checkout.

A read-only audit of 58 fetched GitHub pull-request refs found no AAR binaries,
but 16 PR heads still contain the earlier runtime implementation source
(`PocInferenceEngine.java`, `PocInferenceMath.java`, or
`PocYoloWorldContract.java`). These refs remain unchanged under D-15. Making
SuperNutrition public would make that source reachable alongside the PR
history, contrary to the private-source requirement. Keep the repository
private unless the owner separately authorizes exposing that source or changes
the decision to leave the old refs untouched. The source repository is
[`flexsent-labs/supernutrition-food-identification-android`](https://github.com/flexsent-labs/supernutrition-food-identification-android);
its README has the full assembly and integration procedure.

## Public visibility follow-up — 2026-11-04

The owner authorized public visibility after reviewing the retained PR-source
exposure (D-18). GitHub now reports
[`flexsent-labs/SuperNutrition`](https://github.com/flexsent-labs/SuperNutrition)
as public; an unauthenticated GitHub API request returned HTTP 200. No branch,
tag, or pull-request history was rewritten. The 58 fetched PR refs still
contain no AAR binaries; 16 still expose the earlier Java runtime source, as
accepted by D-18. The built AAR remains outside the SuperNutrition repository.

PR #66 (`codex/local-inference-optional`) remains open at
`3b1cb2614594dbc9dbb5abcefc88efdefb929b1b`. It contains the updated README
instructions for local-only AAR assembly and the app's no-model behavior, but
those updates are not on `main` yet. Its required `mobile-unit-coverage` check
failed at `:verifyMobilePolicy` because `bootstrap-android-host` evidence is
older than the configured 30-day limit. The connected host currently has only
an emulator, which cannot refresh that physical-device evidence. Do not merge
PR #66 until the required policy check passes; do not substitute emulator
results for physical-device evidence.

## Android device verification follow-up — 2026-11-04

The owner connected a physical device and requested device verification. The
full Gradle task
`./mobile/gradlew -p mobile :apps:androidApp:connectedAndroidTest --no-configuration-cache --console=plain`
completed successfully in 14m17s on both attached targets: Samsung SM-F946B
(Android 17/API 37) and the Android 15/API 35 x86_64 emulator. The `pocDebug`,
`productionDebug`, and `uiAcceptanceDebug` lanes each ran 65 tests on each
target: 390 tests total, with 0 failures, 0 errors, and 0 skipped. All six XML
reports under
`mobile/apps/androidApp/build/outputs/androidTest-results/connected/debug/flavors/`
were fresh for this run. This includes the Samsung Rewards carousel regression
and the meal-diary save/edit/delete journey. No screenshots were captured.

The Android device guidance was corrected to state that Gradle runs on every
connected ADB device; the `deviceSerial` runner property does not filter the
task. The physical-evidence emitter records the device serial. Keep that
serial out of the public repository and store only sanitized attestation
fields in the policy binding.

At verification time GitHub still reported PR #66 open at
`3b1cb2614594dbc9dbb5abcefc88efdefb929b1b`; its required
`mobile-unit-coverage` run from 2026-11-03 remained failed because the committed
physical evidence had expired. The passing local run above is not a hosted CI
result. Refreshing sanitized evidence and pushing the validated changes is
required before the PR can be merged.

## Sanitized physical evidence refresh — 2026-11-04

After commit `20920de28681e3c609a88638f52888b28aacac88`, the physical evidence
emitter completed with a clean worktree using the passing Samsung run. The
six Android attestations now use the fresh Android 17/API 37 device model,
Gradle 9.5.0 task versions, current artifact/fixture digests, and the
2026-11-04 execution time. The physical device serial from the emitter output
was deliberately omitted from the committed policy binding. Android emulator
reports remain available for local verification, but the binding uses the
physical-device attestation.

`verifyMobilePolicy` passed with these refreshed records. The complete local
`qualityCheckJvmAndroid --continue --no-configuration-cache` run still exits
with two Detekt task failures for three existing findings in
`PhotoAnalysisComposeActivity.kt`: `PhotoAnalysisTabs` is 71 lines (limit 60),
and `PhotoAnalysisCaptureTab` has complexity 17 (limit 14) and 67 lines (limit
60). All other tasks continued. JVM coverage passed at 99.50% line and 90.56%
branch overall, with `domain:nutrition` at 99.99% line and 100.00% branch.
These source findings are outside the PR's optional local AAR and Android test
fixes. A new hosted PR run is still needed to establish current required-check
status before merging.
