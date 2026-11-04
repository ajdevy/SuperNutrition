# Physical Android Fold rewards scroll finding — 2026-11-04

## Finding

The physical Android instrumentation run on `SM-F946B` (Android 17, API 37)
failed in `BrandNavigationVisualInstrumentationTest.rewardsBackdropAndFinalCardClearFloatingNavigation` because UI Automator could not find `rewards_row_6` after four horizontal swipes. The saved Rewards screenshot showed the first two weekly cards while the third card was offscreen. The PoC, production, and `uiAcceptance` physical-device lanes reproduced the failure; the emulator PoC and production lanes passed.

The cause is the gesture starting at `displayWidth - 32dp`. Samsung's gesture
navigation classifies that start point as its right-edge Back gesture before
Compose's weekly-card carousel can consume the swipe. Logcat records
`performSwipeAction() pos=RIGHT_POSITION ... action=key_back`, and the app returns
to the launcher. The regression test must swipe inside the visible carousel
viewport, away from the system Back edge, while still moving far enough to
reveal the third card.

The emulator `uiAcceptance` lane also failed once in
`MealDiaryJourneyInstrumentationTest.textMealCanBeSavedCorrectedAndDeletedThroughProfile`:
the test could not reach the `meal_details_edit` control.

Reducing the test's swipe distance from half to one-third of the display did not
resolve the emulator failure. UI Automator scrolls in small increments reached
the edit and save steps on both targets, but the targeted journey then failed
while locating Delete. The physical diagnostic reports Delete as visible at
117 px tall, while the helper requires 48 dp (126 px at that device density),
so it rejects a visible target. Lower the visible-height threshold to 24 dp.
The emulator diagnostic shows the helper stopped at the Ingredients section
before later content. Its scroll API returned `false` despite additional
content remaining; do not use that return value as an end-of-content signal.
Keep checking and scrolling until the requested control is visible or the
bounded attempt limit is reached.

The earlier Gradle invocation saw both the phone and `emulator-5554` because
`deviceSerial` was only a runner argument. On the pinned Android Gradle Plugin
9.1, the connected-test task's `--serial` option throws
`UnsupportedOperationException`; it does not provide a working isolation path.
The Gradle instructions now say to disconnect other targets before a
single-device evidence run.

## Acceptance conditions

- The final weekly reward is reachable by horizontal scrolling on the physical
  Fold and remains clear of the floating navigation.
- The regression instrumentation test passes on the physical phone in all app
  flavors.
- The meal-diary save/correct/delete journey reaches the edit control and passes
  on both the emulator and physical phone.
- The complete connected Android suite passes on each target, with zero
  failures, errors, and skips; disconnect other targets before emitting
  current physical-device evidence.

## Reproduction command

```bash
./mobile/gradlew -p mobile :apps:androidApp:connectedAndroidTest \
  --no-configuration-cache --console=plain --continue
```

`connectedAndroidTest` runs on every ADB-connected target. Disconnect other
devices when a single-device run is required; the `deviceSerial` runner
argument does not filter Gradle's device set.

## Verification — 2026-11-04

The swipe was changed to stay inside the visible Rewards carousel viewport.
The meal-diary helper now scrolls the semantic `ScrollView` in bounded small
increments, accepts controls at 24 dp of visible height, and does not treat a
false `UiObject2.scroll` return as proof that the content has ended.

The full connected instrumentation command completed successfully on both
attached targets: Samsung SM-F946B (Android 17, API 37) and
SuperNutrition_API_35_x86_64 AVD (Android 15, API 35). Each of `pocDebug`,
`productionDebug`, and `uiAcceptanceDebug` ran 65 tests per target. All six
fresh XML reports show 65 tests, 0 failures, 0 errors, and 0 skipped (390 tests
total). Reports are under
`mobile/apps/androidApp/build/outputs/androidTest-results/connected/debug/flavors/`.
