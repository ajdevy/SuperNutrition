# Gallery photo fit: review triage

Worktree a704, branch `codex/figma-camera-flow`, baseline `5ee5de0`.

Both independent scoped reviews ran against `mobile/build/gallery-photo-fit.diff`.
They identified the same introduced layout failure. No other concrete scoped
regression was reported. Their analysis was read-only; it was not a new Gradle
or device run.

## N-16 — valid thin images can crash the new aspect-ratio frame

Recorded before correction. Severity high; patch category. Compose 1.11.1
`aspectRatio` in a 375x812 viewport fits a 1024:1 source by width, rounding its
height to zero. Its fallback then proposes an 831488x812 frame and
`Constraints.fixed` rejects the oversized dimension. The finite-positive
ratio check permits this. Native normalization accepts one-pixel sides, so
this is a real valid-image case, not merely malformed external metadata.

Blind review verified pinned bytecode; edge review reproduced the same
constraints through read-only JShell reflection. Neither changed files or
executed Gradle. Acceptance: every valid thin image must render without a
constraint exception, remain bounded by the viewport, and preserve the full
source in its native renderer even when the frame rounds to one pixel.

Correction: use one bounded shared fitting calculation, clamp a nonzero fitted
side to at least one pixel within the available bounds, and let each native
renderer Fit the original pixels inside that rounded frame. Add thin landscape
and portrait regressions and observe their exception before the fix. Preserve
the existing orientation/state/live-camera/background checks.

## F-01 — new regression test line wrapping

Recorded before correction. The current combined quality/build command exits
1: existing native-policy expiry plus four formatting findings on
`GalleryPhotoFitTest.kt:73` (two detekt tasks report the same line). Wrap its
arguments; add no suppression or baseline. Android assembly and explicit
IosArm64 compilation executed despite the quality failure, but they describe
the pre-N-16 correction and are not final evidence.

After correction, rerun the complete relevant UI suite, source checks,
Android/Apple compilation, scoped reviews and physical verification.

## Verified correction

The new thin-image regressions executed before correction: 10/2/0/0, both
failures are the specified oversized-constraint exceptions. After the bounded
common measure rule and native Fit policy, focused XML is 10/0/0/0 and complete
UI XML is 93/0/0/0 across 17 suites. Final log:
`mobile/build/gallery-photo-fit-thin-ui-lint-verified.log`.

The guard initially exceeded ComplexCondition; explicit when cases fixed it.
Their required blank lines were then corrected. Final seven scoped lint checks
are clean; no suppression, baseline or policy change was introduced.

Both actual reviewers rechecked `mobile/build/gallery-photo-fit-final.diff`.
They verified 375x1/1x812 bounded results, extreme finite Float arithmetic,
native source-preserving Fit, invalid metadata and preserved control/state
paths; each reported no remaining concrete issue. Their review remained
read-only and is not native execution evidence.

The later combined command compiled the gallery Android artifacts and
feature IosArm64 successfully, but its connected tasks executed zero tests
after ADB disconnected. A subsequent complete gallery retry then passed
40/0/0/0 for each of POC, production and uiAcceptance, and physical banana
fitting/selection was verified. The later N-17 Android keyboard correction
has a separate artifact/run checkpoint in
`gallery-photo-fit-verification-2026-10-15.md`; do not reuse the gallery
results as post-N-17 evidence. Root policy expiry remains a separate failure.
