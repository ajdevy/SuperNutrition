# N-17: real keyboard pans photo review above the status bar

**Final Android result:** N-17 is verified on the installed production APK.
The real IME regression passed after first failing on the preserved old
hierarchy; keyboard dismissal restores the full photo/header. The complete
physical run passed **120/0/0/0** across all three flavors. The production
app is left on Home. Root policy expiry and full Apple host build limitations
remain separate from this Android result.

Recorded before any production correction. Worktree
`REDACTED_WORKTREE/a704/SuperNutrition`, branch
`codex/figma-camera-flow`, HEAD `5ee5de077866feed70b418ee2760af24322b1d22`.

## Observation

On the pre-N-17 production APK, the normal picker imported the owned
banana fixture. The top custom plus and bottom manual-ingredient plus both
responded. The real input field received focus and accepted `apple`; the native
keyboard is visible. However the window pans the review upward: the banana
illustration is clipped by the status bar and the header/Close/top custom plus
are outside the visible app area. Large unused space remains above the IME.
Input success alone is not a passing layout result.

The screenshot was visually inspected:
`mobile/apps/androidApp/build/reports/final-device-review-2026-10-15/keyboard-before.png`.
Native focused field/text are preserved in `keyboard-before.xml` in the same
directory. The subsequent `manual-apple-keyboard.png` and
`manual-apple-input.xml` now contain the post-fix replay described below.
No meal was committed.

## Acceptance before correction

- Opening the manual field keeps the review inside the available area above
  the real keyboard, without translating app content beneath the status bar.
- The header/Close remain reachable and food art is contained; shorter usable
  height may scroll the review instead of clipping it.
- Focused input, Retake and Add to meal remain reachable above the keyboard.
- Dismissing the keyboard restores the original centered, full-photo layout.
- Preserve the photo-fitting fix, food identity/selection, native permissions,
  stable automation IDs, normal non-IME geometry and unrelated WIP.

Reproduce with a regression that fails for the observed geometry/window
behaviour before changing production source. A manual-only finding is not
resolved by the earlier 120 passing native tests, which do not cover this real
IME state. Keep the parent native-camera/Home/Rewards task in-review.

## Observed regression before the fix

`scripts/verify-photo-review-ime.py` checks the physical UI hierarchy, focused
QA text and visible action bounds; it does not inspect implementation flags.
The first run against `manual-apple-input.xml` exited 1 for the stated defect:
`capture_close is clipped outside the visible app area: (0, 0, 0, 0)`.
Retake and Add to meal are present, and the field contains focused `apple`.
The native top custom plus is also clipped. The inspected matching screenshot
confirms the real IME is visible.

The Android host leaves its soft-input adjustment unspecified while shared
review already consumes IME insets. The correction explicitly requests resize
insets, preventing the system's additional whole-window pan. No shared photo,
food-selection or Apple source change is required for this host setting.
The same physical keyboard geometry check subsequently passed on the
installed corrected APK, as recorded below.

## Build and installation checkpoint

The Android activity now requests `SOFT_INPUT_ADJUST_RESIZE` after configuring
edge-to-edge drawing. No other production correction was added in this slice.

- The first build selected a nonexistent `ktlintAndroidMainSourceSetCheck`
  task and stopped during task selection. It is not a compile/test failure.
- The corrected `:apps:androidApp:assembleProductionDebug` plus
  `:feature:photo-analysis:detektAndroidMainSourceSet` command exited 0 in
  50 seconds. Log: `mobile/build/keyboard-review-fix-build-retry.log`.
- POC and uiAcceptance debug APKs also assembled. The combined
  `qualityCheckJvmAndroid --continue` run completed its source checks and
  reported only `verifyMobilePolicy: Native-gap evidence is stale:
  bootstrap-android-host`; overall exit 1. Log:
  `mobile/build/keyboard-review-all-android-quality.log`. No policy waiver or
  evidence TTL change was made.
- Production streamed installation returned `Success`, preserving app data.
  Log: `mobile/build/keyboard-review-fix-install.log`. A read-only hash of the
  installed app's own `base.apk` matches the new production artifact:
  `adee032a96ad2367564b39efa297412842472e6048dd32144febe1b78ca54c04`.
  The readback is recorded in `keyboard-fix-installed-production.json`.
  All six new build artifact hashes are in
  `keyboard-fix-artifact-digests.json`, beside the screenshots.

Initial post-install helpers stopped at their foreground guard because a
parallel OneRing check was taking focus. No input was sent there. The user
then explicitly stopped that parallel check and authorized continuation.
Super Nutrition's own exit-info showed no new crash during the interruption.

`graphify update .` passed after the source correction (20,547 nodes, 27,458
edges, 1,972 communities); its known empty-AST/SQL-parser limitations remain.

## Physical keyboard replay, October 16 (Europe/Moscow)

The normal system picker imported the owned banana fixture on the installed
production build. The top custom plus and bottom input plus responded; the
real keyboard focused the field and accepted `apple`. The screenshot was
immediately visually inspected: Close is visible below the status bar, the
food illustration is contained, and Retake/Add to meal are above the IME.

The same checker that failed before correction now exits **0**:

```
python3 scripts/verify-photo-review-ime.py \
  --hierarchy mobile/apps/androidApp/build/reports/final-device-review-2026-10-15/manual-apple-input.xml \
  --keyboard-top 1682
```

Native bounds: Close `[45,148][213,316]`, focused input with `apple`
`[217,1173][1152,1341]`, Retake `[56,1400][588,1568]`, Add to meal
`[623,1400][1208,1568]`. A guarded Back press hid only the keyboard. The
immediately inspected `keyboard-dismissed.png` shows the restored header,
complete proportional photo and full-width action row; `apple` remains in
the input and no meal was committed. Matching XML and screenshot hashes are
recorded in `ime-before-after-evidence.json`; checker output is preserved in
`ime-regression-green.log`.

## Complete Android replay: passed

Owned run 60254 executed the full `connectedAndroidTest` entrypoint from
`2026-10-15T21:23:39Z` to `2026-10-15T21:37:18Z` (October 16,
00:23–00:37 Europe/Moscow): **exit 0, BUILD SUCCESSFUL in 13m 38s**.
Exactly one Gradle build ran in this worktree. The canonical single physical
endpoint was HONOR DNY-NX9, Android 16/API 36, arm64-v8a.

| Flavor | Tests | Failures | Errors | Skipped | Class suites |
| --- | ---: | ---: | ---: | ---: | ---: |
| POC | 40 | 0 | 0 | 0 | 9 |
| production | 40 | 0 | 0 | 0 | 9 |
| uiAcceptance | 40 | 0 | 0 | 0 | 9 |

Fresh root and class XML totals agree, with 40 testcase nodes per flavor;
no report from the earlier gallery run contributes. All six current app/test
APK hashes match `keyboard-fix-artifact-digests.json`. The installed production
`base.apk` hash also matches the new artifact. Reports were copied to the
new run's `xml/` directory to preserve them independently of future builds.
The prior gallery XML remains immutable under `gallery-native-pre-ime/`.

Command (expanded parameters and isolated runtime environment are preserved
in the run metadata and `mobile/build/keyboard-fix-device-run.py`):

```
./mobile/gradlew -p mobile :apps:androidApp:connectedAndroidTest \
  --dependency-verification strict --continue \
  --no-configuration-cache --console=plain
```

Evidence under
`mobile/apps/androidApp/build/reports/final-device-review-2026-10-15/keyboard-fix-native/`:

- `keyboard-fix-current-run.json`: exact command, start/end, exit and 27 class
  suite results; `keyboard-fix-current-native-counts.json`: validated root and
  class counts, report hashes and fresh mtimes.
- `verified-tested-artifacts.json`: all six current APK/test APK digests.
- `verified-device-and-cleanup.json`: physical host/API/ABI, installed production
  hash and verified restoration of the original **600000 ms** screen timeout.
  Its saved backup has `restore_required=false`. POC camera grant/flags and
  permission preference bytes still match their original baseline after the
  complete run; both package and UID Camera AppOps remain `foreground`.
- `native-evidence/`: six newly emitted official TOML attestations, parsed and
  verified as **local-only**, **dirty**, bound to this HEAD/device and the
  tested APK pairs, with 40/0/0/0 each. They do not replace clean committed
  activation evidence. Production's independent 40-test report is preserved.

Full logs: `mobile/build/keyboard-fix-device-current.log` and
`mobile/build/keyboard-fix-evidence-emission.log`. Production was then launched
on Home, verified by native `home_title` and an immediately inspected screenshot:
`keyboard-fix-final-home.png`, with matching XML beside it. No manual actions
ran during instrumentation. No Gradle/device automation or settings restoration
is pending. No meal was committed.

### Earlier interrupted attempt

The first owned replay ended 21:17:27 UTC, exit 130 with zero fresh test XML.
Its foreground guard rejected an unclassified focus window, then restored
and read back timeout 600000. Diagnostics did not preserve the rejected window
identity. OS events show OneRing resumed only after this runner force-stopped
its own production activity; this does not establish a renewed parallel test.
The production app did not crash. The attempt is preserved under
`keyboard-fix-native/attempt1-foreground-guard/`.

Before retry, the QA guard was corrected to recognize app-owned startup/popup
windows by the three exact flavor UIDs while continuing to reject unrelated
owners, and to record safe rejection metadata. The isolated Gradle daemon was
verified IDLE, and the retry started from the system launcher. No product
assertion was weakened and no test was skipped. The successful full replay
above supersedes this interrupted execution state.

## Final limits and handoff

Branch `codex/figma-camera-flow`, HEAD
`5ee5de077866feed70b418ee2760af24322b1d22`, worktree a704. Intended edits and
unrelated WIP remain local/uncommitted; nothing was staged, committed or pushed.
A final read-only `git ls-remote origin refs/heads/main` still returned this
same upstream commit. The separate local main checkout was not changed.

The earlier relevant JVM evidence remains 141/0/0/0 (core UI 4, presenter 19,
shared UI 93, feature 25), not a new JVM rerun for this Android-only flag.
N-17 received physical before/after verification and the complete native
replay; no new independent subagent review is claimed for it.

`qualityCheckJvmAndroid --continue` still reports stale committed
`bootstrap-android-host` evidence, overall exit 1; its source checks completed
without a new finding. Local-only dirty attestations cannot make it green.
Scoped shared/feature IosArm64 sources compiled earlier on Intel, but full
Swift/sharedKit/photo-ios host compilation remains unverified without full
Xcode. No Apple source changed in N-17. No lifecycle, TTL, inventory, coverage
or owner-decision rule was weakened. The parent implementation remains
in-review for these formal limits and user review; all agreed Android device
checks are complete. Do not start unrelated work.

The hourly continuation heartbeat `super-nutrition` was deleted through the
app after the Android handoff (`deleteStatus=deleted`). No additional phone
actions or unrelated background development are scheduled.
