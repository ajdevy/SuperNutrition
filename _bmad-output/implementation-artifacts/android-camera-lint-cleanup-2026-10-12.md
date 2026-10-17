# Android camera implementation lint cleanup

Baseline: `5ee5de077866feed70b418ee2760af24322b1d22`; branch
`codex/figma-camera-flow`. This record precedes the production cleanup.

The fresh `androidMainSourceSet` detekt report generated at
`2026-10-12 05:34:36 UTC` contains five findings:

1. `PhotoAnalysisComposeActivity.kt:612`: `PhotoAnalysisCaptureTab` contains
   64 statements/lines under the `LongMethod` metric; the maximum is 60.
2. `PhotoAnalysisAndroidHostController.kt:103`: the permission `when` needs a
   blank line before its multi-label undetermined/denied branch.
3. `PhotoAnalysisAndroidHostController.kt:108`: the same `when` needs a blank
   line before its permanent-denial/restricted branch.
4. `PhotoAnalysisLiveCameraPreview.kt:64`: the camera fallback `when` needs a
   blank line before its permission fallback branch.
5. `PhotoAnalysisLiveCameraPreview.kt:42`: the sole non-private top-level
   class `PhotoAnalysisCameraAccess` must have a matching filename.

Acceptance: preserve the data-class fields and defaults, permission decisions,
preview visibility, and callbacks. Keep `collectAsState` and the existing
`LaunchedEffect(host, needsLivePreview)` in the capture tab. Extract the nullable
live-camera composable lambda into a private factory, move the unchanged access
model to its own file, and add the required blank lines. No new suppression or
baseline is allowed.

Verification: source diff and whitespace checks are local to this cleanup.
The root agent owns the complete Gradle quality, build and device verification;
this record is not a passing test or native execution result.

## Implemented cleanup

- Moved `PhotoAnalysisCameraAccess` unchanged to `PhotoAnalysisCameraAccess.kt`.
- Added the three required blank lines between `when` conditions.
- Extracted `captureLiveCamera` from the capture tab. Its nullable composable
  lambda receives the same camera access, recovery labels and controller. The
  capture tab still collects the same flows and uses the same effect keys.
- Scoped `git diff --check` passed for the three modified tracked Android files.
  No build, tests, device action, staging or Git mutation ran in this subtask.

The full lint gate, native permission regressions and final graph refresh remain
with the root task.
