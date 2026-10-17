---
title: 'Preserve the full gallery photo in capture review'
type: 'bugfix'
created: '2026-10-14'
status: 'done'
baseline_commit: '5ee5de077866feed70b418ee2760af24322b1d22'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/planning-artifacts/architecture/architecture-SuperNutrition-2026-07-26/ARCHITECTURE-SPINE.md'
---

<frozen-after-approval reason="the user reported gallery-photo enlargement in the ongoing authorized camera/review implementation">

## Intent

The user reports: “фото из галереи очень растягивается почему-то”. The selected
photo must remain recognizable and complete during analysis and review, with
its original proportions. A landscape or square photo must not be cropped
to a tall phone window or receive an additional review zoom. Keep the accepted
Figma controls, food selection, manual plus, contrast, Retake and Close.

This is a correction to the current capture/review work, not a new design or
permission policy. Live camera may continue to fill its preview viewport.
Native photo decoding and normalized image ownership remain behind ports;
the shared UI controls layout using dimension metadata only.

## Boundaries & Constraints

Always preserve all authorized dirty WIP and the process-ephemeral photo
lifecycle. Apply one shared layout rule to retained images on both Android and
iOS; keep native pixels in their existing adapters. Keep unknown/invalid size
metadata safe, while actual native hosts supply valid image proportions.
Preserve existing stable automation IDs, native permission actions, overlays,
safe areas and food confirmation provenance. Do not change photo bytes,
normalization, model recognition, asset generation, consent, catalog identity,
or domain routing. Do not push or merge. Run one Gradle build at a time.

## Acceptance

- Given a landscape, portrait or square gallery photo and a portrait phone,
  when analysis/review displays its retained image, then the image frame is
  centered, fully contained in the viewport and has the source aspect ratio.
- Given the same retained photo before and after candidate review, when the
  presenter state changes, then no additional 1.2 scale is applied and the
  complete picture stays visible without moving control geometry.
- Given live camera with no retained image, when capture opens, then its
  preview still fills the existing viewport and shutter/gallery/Close remain
  available. Backgrounded state must not revive a disposed photo or camera.
- Given missing, zero, negative, infinite or NaN aspect metadata, when the
  surface renders, then it does not crash or apply a zoom. Native valid images
  must carry dimension-derived metadata on both platforms.

</frozen-after-approval>

## Finding recorded before correction

N-15: `PhotoAnalysisCameraScreen` currently ignores `retainedPhotoAspectRatio`,
fills the entire window and scales its photo layer 1.2 in both axes at review.
Android renders the bitmap with center Crop; iOS uses ScaleAspectFill. Neither
native capture host provides the existing aspect-ratio field. A landscape
3030x2670 banana fixture consequently loses most of its context on the tall
1264x2736 phone. This is excessive enlargement/cropping; the observed scale
is uniform, so no nonuniform decoder deformation is established.

Acceptance regression must fail on the current production composables before
the shared fitting correction. Existing native test results 40/0/0/0 for each
flavor describe the previous source and must not be relabeled as new evidence.
Per-file pre-change snapshots are under ignored `mobile/build/gallery-fit-before/`.

## Code Map

- `mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisCameraScreen.kt`: replace extra zoom with centered aspect-preserving retained-image layout; live preview stays full size.
- `mobile/feature/photo-analysis/src/androidMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisComposeActivity.kt`: pass normalized preview bitmap width/height to the existing surface field.
- `mobile/feature/photo-analysis/src/iosMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisViewController.kt`: pass the normalized UIImage size through the same field, using the existing foreign-API opt-in.
- `mobile/feature/photo-analysis-ui/src/jvmTest/kotlin/com/supernutrition/feature/photoanalysis/GalleryPhotoFitTest.kt`: meaningful production-composable geometry regressions for orientation, state continuity and live-preview preservation.

## Tasks & Acceptance

- [x] Add fitting regressions and observe their stated failure on current source.
- [x] Use a single shared retained-photo layout rule and supply native dimensions.
- [x] Run the complete shared UI tests and compile the affected Android/Apple source.
- [x] Perform two independent scoped reviews; record and resolve real findings.
- [x] Build/install and inspect the real owned gallery fixture on the physical phone.
- [x] Run appropriate native evidence, whitespace checks and graph update; report precise limits.

## Verification

Use the current isolated Gradle/Android/Konan homes and read-only shared
dependency cache. Capture real command exits and current per-module XML.
The focused shared UI regressions are followed by the whole shared UI suite,
Android production assembly and explicit IosArm64 feature compilation on this
Intel host. Existing root quality expiry and missing full Xcode remain limits,
not an excuse to invent clean evidence or bypass required source checks.

Phone work uses the user's current availability and exactly one physical ADB
endpoint. Stop the persistent CLI inspector before instrumentation. Import only
the owned licensed banana fixture through the normal gallery picker, inspect
the resulting app screenshot immediately, and preserve production app data.
No private photo browsing or fake meal commit is needed. If another app takes
focus, guarded actions stop rather than tapping it.

The surrounding Home/Rewards/icons/permission work remains unchanged. Its
earlier review and 131 JVM/120 native results are historical evidence for their
own inputs. This new correction needs its own fresh relevant verification.

## Review update, 2026-10-15

Both independent reviewers found N-16: standard Compose `aspectRatio` can
produce invalid oversized constraints when a valid thin image rounds to a
zero-pixel fitted side. See [the recorded triage](gallery-photo-fit-review-triage-2026-10-15.md).
Keep the centered shared rule, complete source image, stable overlay IDs and
successful ordinary-orientation/live-camera/background cases. Replace the
unsafe modifier with bounded fitting and native Fit rendering for rounded
frames. This is an implementation correction within the approved intent.

## Current verification checkpoint

Final UI suite 93/0/0/0 and current relevant JVM total 141/0/0/0. Both actual
reviewers verified N-16's correction and reported no remaining concrete issue.
Current Android artifacts and feature IosArm64 compiled. The later complete
physical run 95273 passed 120/0/0/0 on October 15: POC, production and
uiAcceptance each 40/0/0/0, with nine class suites per flavor. All six current
APK hashes were verified against the tested manifest, and the official emitter
produced six local-only dirty attestations. The installed production build's
normal gallery picker imported the owned banana fixture. Its fresh screenshot
shows the complete photo with correct proportions; only banana is suggested,
selection works and Add to meal becomes enabled. No fake meal was committed.
The original screen timeout was restored and verified.

The earlier zero-test run 50272 remains recorded as an interruption, not a
passing command. Root quality is still blocked by expired committed activation
evidence, and full Xcode host builds remain unavailable. The scoped photo-fit
acceptance is complete; surrounding manual keyboard, sensor and real OS
permission checks remain open in the parent task. See
[the current verification report](gallery-photo-fit-verification-2026-10-15.md)
for exact commands, graph results, source quality and remaining parent checks.
The requested hourly continuation heartbeat is now ACTIVE in this chat.
