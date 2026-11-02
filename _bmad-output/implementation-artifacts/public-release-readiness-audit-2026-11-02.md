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

## Current status — 2026-11-03

D-11 and D-13 record the approved privacy and license boundary. The prepared
tree removes the detector path from the Android/iOS app, removes the remaining
executable detector/FPB research integrations, the model fusion benchmark, and
their generated outputs, removes the checked-in AAR and POM, and points Android
builds at the private GitHub Packages repository. The private library source
repository has the `v0.1.1` tag and its workflow published the AAR successfully;
read access from SuperNutrition workflows and this local checkout is not
configured yet. The five user-sample photographs are absent from the prepared
tree.

The rewrite preserved the 669-commit sequence and existing author identities,
removed the detector/runtime source and binary paths, scrubbed the exact
machine/device identifiers, and redacted two historical credential-shaped
benchmark assignments. An exact scan of 18,704 reachable Git objects found
none of the nine replaced values. Gitleaks reported 91 history matches, all
heuristic `generic-api-key` findings in reviewed design geometry, hash fields,
deliberate scanner fixtures, and the repository file index; it found no
benchmark assignment after redaction. The earlier current-tree scan reported
89 reviewed heuristic findings. Repository history alone cannot establish
whether the two removed values were live credentials; revoke them if either
was valid.

All 50 existing branch refs and both tags were atomically force-updated with
exact leases, and `codex/public-readiness` was added. A post-push comparison
matches all 53 branch/tag refs to the rewritten mirror. GitHub still exposes 55
read-only `refs/pull/*/head` refs: only one matches the rewritten local PR refs
and 54 still point at prior commit IDs. Those references may retain old PR
views or objects, so full removal from GitHub-hosted PR history is not
confirmed. GitHub documents that pull refs are read-only and that Support can
purge cached views for sensitive data, while stating it will not remove
non-sensitive data; see [Removing sensitive data from a repository](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository).
PR #64 remains open, has no reviews, and its updated coverage run is queued.

The repository remains private. Before making it public, resolve the stale PR
refs and confirm whether the historical credential-shaped values need
revocation. Android build access also remains unverified because the private
package credentials are not configured locally or in SuperNutrition workflows;
the iOS compile could not run because this host has no `xcodebuild`. GitHub
accepted the 88.47 MB Food-101 ONNX file but warned that it exceeds its 50 MB
recommended size. No tests were run during this preparation pass.
