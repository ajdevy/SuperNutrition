# Gallery photo fitting: verified checkpoint

Worktree: `/Users/REDACTED/.codex/worktrees/a704/SuperNutrition`.
Branch: `codex/figma-camera-flow`; HEAD `5ee5de077866feed70b418ee2760af24322b1d22`.
Changes are local and uncommitted; unrelated WIP is preserved.

## Result and scope

Retained gallery/camera photos now use one centered bounded frame in shared
UI. Native hosts supply the normalized display image's aspect ratio. Android
and iOS render the complete source with Fit inside that frame. The former
fullscreen crop plus additional 1.2 review scale is removed. Live CameraX
preview remains fullscreen; controls and stable automation IDs are preserved.
The bounded rule also handles valid one-pixel-wide/high images without Compose
creating unrepresentable constraints. No bytes, normalization, inference,
food assets, consent or domain contracts were changed.

The corrected production APK is installed on the physical HONOR. Its normal
gallery picker imported the owned banana fixture; the full photo is visible
with the correct proportions and letterboxing. Only banana was suggested,
its checkbox changed from unchecked to checked, and Add to meal became enabled.
The October 15 gallery Android run passed **120/0/0/0**, with **40/0/0/0 for
each of POC, production and uiAcceptance**. Later manual work verified Home,
Rewards, real sensor capture, Retake/Close and real OS permission recovery.
It also found an additional Android IME layout defect, N-17. The gallery-photo
correction is verified; N-17 is tracked separately below and its rebuilt APKs
must not inherit those earlier native results.

Scoped diff: ignored `mobile/build/gallery-photo-fit-final.diff`.
Finding/spec: `spec-gallery-photo-fit.md`; both actual reviews and resolution:
`gallery-photo-fit-review-triage-2026-10-15.md`.

## Regression and source evidence

Counts are tests/failures/errors/skipped:

| Run | Counts | Evidence |
| --- | --- | --- |
| Initial fit regressions before production correction | 8/6/0/0 | `mobile/build/gallery-photo-fit-red-results/` |
| Initial focused correction | 8/0/0/0 | `mobile/build/gallery-photo-fit-focused-results/` |
| Thin-image cases before N-16 correction | 10/2/0/0 | `mobile/build/gallery-photo-fit-thin-red-results/` |
| Final focused correction | 10/0/0/0 | `mobile/build/gallery-photo-fit-thin-focused-results/` |
| Final complete shared UI | 93/0/0/0 | `mobile/build/gallery-photo-fit-thin-ui-suite-results/` |

Current module XML counts: core UI 4/0/0/0, presenter 19/0/0/0, shared UI
93/0/0/0, feature 25/0/0/0. Unchanged reports/inputs retain their genuine earlier
passes; they are not presented as fresh reruns. The feature tests re-executed
in the final combined command. Final scoped source lint covers seven tasks,
including AndroidMain and IosMain, with zero findings. Native Fit source was
unchanged after its passing lint; final shared guard lint re-executed.

The earlier combined command (owned session 50272) ran:

```
./mobile/gradlew -p mobile \
  :apps:androidApp:connectedAndroidTest \
  :feature:photo-analysis:compileKotlinIosArm64 \
  qualityCheckJvmAndroid \
  --init-script mobile/build/agent-ui-runtime.gradle \
  -Psupernutrition.enableIosDeviceTarget=true \
  -Pandroid.experimental.testOptions.installApkTimeout=900 \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
  -Pandroid.testInstrumentationRunnerArguments.deviceSerial=adb-AU3N025429006787-97BOdK._adb-tls-connect._tcp \
  -Pkotlin.compiler.execution.strategy=in-process \
  --dependency-verification strict --no-watch-fs --continue \
  --no-configuration-cache --console=plain
```

It used the existing isolated Gradle/Android/Konan homes, read-only dependency
cache and explicit installed Kotlin/Native 2.4.10 home. The exact expanded
command is in `mobile/build/gallery-photo-fit-complete-native-command.txt`;
full output is in `mobile/build/gallery-photo-fit-complete-native.log`.
All three Android APKs packaged; shared UI and feature IosArm64 source compiled
on x86_64. Full Swift/sharedKit/photo-ios host compilation is not claimed;
full Xcode remains absent.

Overall command exit **1**, 3m 11s: root policy still reports stale committed
`bootstrap-android-host`, and all three connected tasks report **No connected
devices**. No new device tests executed. There are no remaining current source
lint failures in that run. Do not reuse the October 14 native 40/0/0/0 reports
as evidence for these new APKs or run the emitter against those stale reports.

## Completed physical-device run

The October 15 user enabled wireless debugging again. Discovery outside the
restricted sandbox found `192.168.0.150:38107`; ADB's default sandbox attempt
could not start its localhost listener (`Operation not permitted`). The
approved external ADB connection restored the physical endpoint. The duplicate
IP endpoint was disconnected, preserving the single mDNS device identity.

Owned retry 95273 started at `2026-10-15T08:06:16Z` and finished at
`2026-10-15T08:25:20Z`: **exit 0, BUILD SUCCESSFUL in 19m 3s**. It ran the
complete entrypoint below, using the same init script, isolated runtime homes,
strict dependency verification, explicit physical serial and 900-second
installation timeout shown above. There was one Gradle build in this worktree.

```
./mobile/gradlew -p mobile :apps:androidApp:connectedAndroidTest \
  --dependency-verification strict --continue \
  --no-configuration-cache --console=plain
```

Full expanded arguments and timing are recorded in ignored
`mobile/apps/androidApp/build/reports/final-device-review-2026-10-15/gallery-fit-current-run.json`;
the full log is `mobile/build/gallery-fit-device-current.log`. Current XML
was re-parsed after completion: each flavor has nine class suites totalling
**40 tests, 0 failures, 0 errors, 0 skipped**. Root and class totals agree,
and all three report mtimes follow this run's start. Reports live under
`mobile/apps/androidApp/build/outputs/androidTest-results/connected/debug/flavors/`.
The adjacent `gallery-fit-current-native-counts.json` records each XML path.
No October 14 XML contributes to this result.

All six APKs at the time of this run matched
`gallery-fit-artifact-digests.json`, including that production APK SHA-256
`8a5f7b9ad19a485bc87676d08f2dd1c66cccd1e0e8fa1f37f2704020dff2481c`.
These are the pre-N-17 artifacts; the subsequent Android host correction
rebuilt all three app APKs. See the superseding checkpoint below.

The previously pending screen-timeout cleanup was verified first. The actual
new baseline was persisted before changing it for the owned run; its finally
handler restored **600000 ms**, read it back and recorded
`restore_required=false` at `2026-10-15T08:25:20Z` in
`device-setting-backup.json`. No timeout restoration remains pending. The CLI
inspector was stopped before instrumentation; manual inspection ran after
Gradle finished.

The official emitter subsequently produced six attestations under the same
report directory's `native-evidence/`. Typed parsing verifies all six are
**local-only**, **dirty**, bound to this HEAD and the current APK/test APK
digests, with 40/0/0/0 in their referenced POC or uiAcceptance XML. Production's
separate 40/0/0/0 report is preserved as well. Attestation emission happened
later than the recorded test execution; it did not rerun the suite. These
local attestations do not replace committed clean activation evidence or
make the root quality gate green.

## Manual gallery and surrounding flow verification

The installed production build imported only the licensed QA file
`banana_single_2026_10_13.jpg` through the normal gallery picker. The fresh
1264x2736 screenshot was inspected immediately:
`mobile/apps/androidApp/build/reports/final-device-review-2026-10-15/banana-selected-native.png`.
The complete banana is visible inside the centered retained-photo frame,
with black letterboxing and no extra review zoom. Only one checkable food,
banana, is present. Native XML in `banana-selected.xml` records its successful
unchecked-to-checked transition and enabled Add to meal. No meal was committed.

An earlier keyboard attempt (session 42730) was interrupted without a
screenshot. The user's subsequent availability reply enabled a new manual
run. That run selected the top custom plus, focused the bottom manual field
and typed `apple` using the real keyboard. It exposed N-17: Close and the top
of the review were panned outside the screen. Preserved screenshot/XML:
`keyboard-before.png` and `keyboard-before.xml`. The acceptance checker failed
before the production correction because Close had zero visible bounds.

Gallery Retake cleared the old review and returned to the live shutter; Close
returned Home. Fresh Home and Rewards screenshots were immediately inspected:
the capybara's feet overlap the panel, CTA groups and Do it fit, the reward
grid aligns, and all seven reward cards scroll fully above navigation.
Evidence: `retake-close-home.png`, `gallery-fit-rewards.png`,
`gallery-fit-rewards-bottom.png` and their native XML in the report directory.

The isolated POC exercised nine actual OS permission phases: first system
request, denied retry, rationale, second system request, permanent-denial
Settings action, own app Settings, its Camera permission page, grant and
return to preview without an implicit shutter. These were real dialogs rather
than the controlled bridge used by instrumentation. `permission-real-flow.json`
records all nine verified phases. Its helper ultimately exited 1 because it
incorrectly expected food recognition from a nonfood camera frame; the app
correctly returned "No food was found in the photo". The helper command as
a whole is not claimed green. Exact POC permission flags/preferences and both
package/UID Camera AppOps were restored and read back; no mutation is pending.

A separate complete sensor-only replay accepted that valid no-food result and
passed preview → explicit physical shutter → no-food recovery → Retake
with no stale food choices → Close/Home. Evidence:
`camera-real-sensor-flow.json` and `sensor-*.xml`. No meal was committed.

## Superseding Android IME checkpoint

N-17 is recorded in [the keyboard finding](keyboard-review-pan-2026-10-15.md).
The Android host now explicitly requests resize while Compose consumes IME
insets. All three Android app APKs rebuilt; the focused Android source check
passes. The combined root gate still reports expired committed
`bootstrap-android-host` evidence, and is not green.

The new production APK installed successfully over Wi-Fi. A read-only SHA-256
check of its installed `base.apk` matches
`adee032a96ad2367564b39efa297412842472e6048dd32144febe1b78ca54c04`.
`keyboard-fix-artifact-digests.json` inventories the rebuilt artifacts.
The pre-N-17 120 native passes and their six attestations remain historical
evidence for the gallery APKs; they are not current N-17 evidence.

After the user stopped the parallel phone check, the installed production
build passed the real IME regression: Close/input/Retake/Add remain visible,
food art is contained, and dismissing the keyboard restores the header and
full photo while retaining `apple`. Both screenshots were immediately
inspected. `ime-before-after-evidence.json` records native bounds and hashes;
`ime-regression-green.log` records checker exit 0. No meal was committed.

The first complete N-17 replay stopped at its QA guard before tests, exit
130; its timeout was restored. The separate corrected retry then completed
**120/0/0/0**, with 40/0/0/0 in each of POC, production and uiAcceptance,
at 21:23:39–21:37:18 UTC (October 16 locally), exit 0 in 13m 38s.
Fresh root/class XML and all six new artifact hashes were cross-checked.
Six official native attestations were emitted as local-only/dirty for these
new artifacts. Screen timeout 600000 and exact POC permission/preferences/
AppOps restoration were read back; no cleanup remains pending. Production
is left on Home with a freshly inspected screenshot. Full current evidence
is in [the N-17 report](keyboard-review-pan-2026-10-15.md) and its separate
`keyboard-fix-native/` artifact directory. No phone question or Android device
check remains open. Root policy and full Apple host limits remain unchanged.

## Earlier device interruption

Before the current retry, the user explicitly made the HONOR DNY-NX9 available in the October 15 live
conversation for approximately 35 minutes. This is a bounded window, not
standing phone-control permission for later hourly runs. ADB initially showed
one endpoint but disconnected before tests. Subsequent device/mDNS lists are
empty and the then-last-known `192.168.0.150:43037` timed out. The current
retry above supersedes that interruption; it does not change the earlier
zero-test command result.

The disconnected run had left restoration unverified. The completed retry
above restored and verified the original 600000 timeout and supersedes that
cleanup state. Neither Gradle run nor the interrupted QA session remains
active. Preserve production data and use only the owned licensed banana
fixture in further gallery checks.

## Graph and recurring continuation

`graphify update .` passed again after the Android IME source correction:
20,547 nodes, 27,458 edges, 1,972 communities. Its graph/Markdown inventory
is updated.
253 empty-AST sources and missing SQL parser for two files are recorded
limitations; HTML visualization was skipped above the 5,000-node limit.
No semantic extraction of those absent sources is claimed.

The requested hourly heartbeat was created successfully after the user chose
Ask for approval: id `super-nutrition`, name `Продолжать Super Nutrition`,
status ACTIVE, destination this chat. The saved automation TOML was parsed and
verified as an hourly heartbeat. No duplicate schedule or shell timer exists.
