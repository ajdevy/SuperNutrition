# Home capybara grounding

## HOME-GROUND-1

The user reports that the capybara floats, preferring it on the garden floor (the panel edge is an acceptable fallback). The current native Home screenshot places visible paws close to the garden horizon and has no contact shadow. Foreground height is measured independently from the full-screen FillBounds background; viewport/insets/font changes can change their relationship.

Acceptance: the current mascot is visibly seated on floor pixels, with a restrained contact shadow directly below the paws. The backdrop and blurred glass must share the same scene transform. Preserve measured foreground positions, usable controls and readable scrolling. Baseline: ../emulator-verification-2026-10-23/android-home.png, commit465f1933.

Native reproduction: resizing the owned AVD to824×1830px at320dpi (412×915dp) makes the old paws visibly float over sky/mountains, while the foreground card starts above the garden floor. See native-before-412x915.png. The unchanged production APK SHA-256 is7a7984fa1ef5eed14919e861210d4f232db2da7d16b3d80868970993b81a2a33.

Before production changes, a real rendered regression at412×915 with24dp safe insets failed1/1, sampling blue background beside the paw contact; exact red XML/log are preserved. The360px synthetic JVM test with39dp top already had sand underneath and was not counted as red.

## HOME-GROUND-2

During implementation, inspection found that an isolated glass layer without measured garden bounds could subtract its absolute position from its local fallback image, exposing blank canvas. Acceptance: absent scene coordinates produce a filled local fallback; the real shared-scene path retains the common absolute transform. Correct the missing-source offset and add a rendered standalone/missing-state glass check. Recorded before that correction.

## HOME-COVERAGE-1: broader UI branch coverage remains below the declared floor

A fresh module JVM coverage report covers all19 executable lines and all24 branches in the new pure garden geometry. The whole UI module raw report has4635/5357 lines (86.52%) and1278/1923 branches (66.46%), below the85% branch floor. This narrow visual fix does not establish a green full coverage/quality gate. Preserve the report and track the broader module gap separately; do not mask it with exclusions, baselines or skips.

Detekt per-source-set reports for changed commonMain/commonTest/jvmTest have0 findings. The typed detektMainJvm task separately warns of3 compiler-analysis errors; this limits that analyzer's accuracy. The real JVM, Android and iosArm64 compilation tasks passed.

## HOME-GROUND-3: visibility test reference includes a different garden transform

Independent blind review found that the mascot-visibility test compares anchored Home against an unanchored standalone garden and counts changed pixels over its entire semantic canvas. Garden differences or the new contact shadow could satisfy the existing threshold without the sprite. Acceptance: capture the reference using the same measured backdrop state and compare only the central opaque body region, excluding the contact shadow. This is a bounded test correction; production source and installed APK do not change. Recorded before correction.

Final coverage after the stronger reference/body-region assertion:4635/5357 lines and1274/1923 branches (66.25%) in the broader UI module; new geometry remains19/19 lines and24/24 branches. The initial report is preserved as coverage-initial.xml, while coverage-jvm.xml is the final fresh run. HOME-GROUND-3's corrected test and all237 module tests pass with0 failures/errors/skips; final changed-test lint has0 findings. Production source and native screenshots still match their recorded digests.

## Correction — REPORT-COVERAGE-1 (2026-10-23)

The earlier HOME-COVERAGE-1 classification as a declared JVM floor failure was incorrect. `coverage.toml` already classifies feature-photo-analysis-ui as native-equivalent, and QualityPlugin selects only `coverage_kind = "jvm"` for numerical JVM verification. The raw66.25% observation remains accurate, but is not the reported gate blocker. Six expired required Android evidence records are the confirmed complete-policy blocker; five explicitly require physical Android. See merge-ci-2026-10-23/findings.md. No coverage policy changed.
