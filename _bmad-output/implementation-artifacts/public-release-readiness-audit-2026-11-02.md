# Public release readiness audit — 2026-11-02

## Scope

Prepare the existing SuperNutrition GitHub repository for a future public
release while preserving the project history and linked author identities as
far as the requested cleanup permits. The repository remains private during
this work.

## Findings and acceptance conditions

### PRR-1 — AGPL-bound region model is still part of the app

`mobile/inference/local-android-poc` verifies and uses the YOLO-World ONNX
asset, and the iOS PoC also requires it. The model binary is 51 MB and appears
in repository history. The Android private runtime contains the matching region
inference path.

**Acceptance:** the SuperNutrition app no longer packages or requires the
YOLO-World asset or region inference path; Food-101 dish classification remains
usable; the AGPL-bound model binary is absent from every GitHub branch and tag
history.

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

## Current status

D-11 records the approved privacy and license boundary. The working tree now
removes the YOLO-World model from the Android/iOS app path, removes the checked-
in AAR and POM, points Android builds at the private GitHub Packages repository,
and updates the runtime and fixture documentation. The private library source
repository has a publishing workflow and the `v0.1.1` tag; package publication
and access from this repository still need confirmation.

The five user-sample photographs are absent from the prepared tree. The
history rewrite, remote ref verification, current-tree/history secret scan, and
final public-readiness review remain outstanding. The repository remains
private throughout this work.
