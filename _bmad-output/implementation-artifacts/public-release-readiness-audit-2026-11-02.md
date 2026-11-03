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

The public-readiness files are present: root README, MIT LICENSE, and ROADMAP
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

GitHub exposes 56 read-only `refs/pull/*/head` refs. Only one has the same tip
as a rewritten branch or tag; the other 55 are stale. Fetching those refs
confirmed that they still reach six user-sample paths (including the five meal
photos), the YOLO-World ONNX model, three YoloX Nano files, five FPB files, and
28 Nutrition5k benchmark/presentation paths. Those objects are absent from the
rewritten branches and tags but remain reachable in GitHub-hosted PR history.
GitHub documents that `refs/pull/` is read-only and that Support can remove
affected PR references for qualifying sensitive data; Support will not remove
non-sensitive data. See [Removing sensitive data from a
repository](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository).
The repository was not made public.

Gitleaks scanned 1,225 commits reachable from the current branches, tags, and
fetched pull-request refs and reported 185 heuristic `generic-api-key`
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

Before making the repository public, the owner still needs to resolve the 55
stale pull-request refs and determine whether the two previously redacted
benchmark values need revocation. The repository remains private until those
items are resolved.
