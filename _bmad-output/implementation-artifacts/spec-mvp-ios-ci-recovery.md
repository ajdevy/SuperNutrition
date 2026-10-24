---
title: Restore iOS modal isolation and the complete meal UI journey in CI
type: bugfix
created: 2026-10-24
status: in-progress
baseline_commit: 7ff3b197763ad3a15ac648fcdfc5f012c6eba547
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="The human explicitly authorized repairing every main CI failure except expired device checks; this is that bounded repair">

## Intent

**Problem:** The first merged MVP main commit builds on iOS, but three of nine
XCTest cases fail. Retained Home elements remain in the accessibility tree under
Capture/Profile, and the new meal journey cannot reach Add while the first-run
system keyboard introduction covers its whole-window scrolling gesture.

**Approach:** Preserve the retained shared Main controller and diary ownership,
explicitly exclude its view from accessibility and interaction while a modal
is presented, and make the XCTest interaction helper handle the observed system
keyboard introduction and scroll within the app's unobscured area. Run the
unchanged complete journey and modal-isolation assertions on repaired main CI.

## Boundaries & Constraints

**Always:** Share one Main host implementation between production and acceptance.
Preserve session/controller lifetime and restore accessibility and touch when
the modal closes. Keep keyboard input real, maintain all nine XCTest cases and
their save/history/edit/delete checks, use bounded gestures, and retain original
RED artifacts. Keep all policy/coverage denominators and device expiry intact.

**Ask First:** A different privacy posture, published contract or nutrition source
would require owner authority; none is part of this CI repair.

**Never:** Delete or skip failed tests, replace hidden-surface checks with touch-only
checks, disable the keyboard globally, clear production data, fabricate physical
evidence, loosen dependency verification, or describe pending CI as green.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|---|---|---|---|
| Open Capture/Profile | Retained Main and an active modal | Home controls excluded from accessibility and touch | Existing isolation assertions fail if exposed |
| Close modal | Same Main/session retained | Home controls return and navigation remains usable | No controller or diary recreation |
| First text input | System continuous-path keyboard introduction appears | Dismiss the identified system prompt through its Continue action | Unexpected prompts still fail visibly |
| Scroll with keyboard | Save below keyboard | Gesture stays above keyboard and brings Save into view | Bounded attempts, explicit hittability assertion |
| Ordinary gesture | No keyboard/introduction | Visible control tapped normally; lower content reachable | No unbounded retries |

</frozen-after-approval>

## Code Map

- `mobile/apps/iosApp/SuperNutritionApp.swift` — production modal owner and duplicate Main representable.
- `mobile/apps/iosApp/PhotoAnalysisHostSupport.swift` — acceptance modal owner and duplicate Main representable.
- `mobile/apps/iosApp/MainComposeHost.swift` — new sole shared Main representable and UIKit visibility boundary.
- `mobile/apps/iosApp/SuperNutritionUIAcceptance.xcodeproj/project.pbxproj` — shared source membership in both application targets.
- `mobile/apps/iosApp/Tests/BootstrapLaunchTests.swift` — unchanged nine scenarios, bounded visible-control gesture helper.
- `README.md` — regenerate source metrics after Swift/project changes using the existing generator.
- `_bmad-output/implementation-artifacts/mvp-main-ci-2026-10-24.md` — findings, original hosted RED, verification and merge evidence.

## Tasks & Acceptance

**Execution:**
- [x] `MainComposeHost.swift` and both host roots — consolidate representables, apply covered state at creation/update, retain controller/session.
- [x] `project.pbxproj` — include the shared implementation in production and acceptance compilation.
- [x] `BootstrapLaunchTests.swift` — recognize the evidenced keyboard prompt and bound scroll gestures to the app area; keep all existing outcome assertions.
- [x] `README.md` — regenerate dashboard source counts, check exact generated section.
- [x] Verification document — record local native compilation, independent review, original main conclusions and the remaining hosted runtime handoff.

**Acceptance Criteria:**
- Given Capture/Profile is open, when XCTest queries Home actions, then existing `XCTAssertFalse(...exists)` checks pass; after close the existing Home/navigation checks pass.
- Given a fresh simulator keyboard, when authoring the text meal, then Add, history, exact correction and confirmed deletion complete in the original scenario.
- Given production and acceptance hosts, when building arm64 products, then both compile/link using the same Main host implementation.
- Given the latest repaired main commit, when all three CI workflows finish, then only the explicitly accepted expired-device policy check may remain failing.

## Spec Change Log

## Design Notes

The observed accessibility snapshot contains underlying Home controls but does
not establish VoiceOver or touch reachability. The repair strengthens the native
boundary rather than relaxing the existing test contract. The keyboard overlay
is proven by its actual system identifier in the failed result bundle. Generic
alert dismissal and blanket keyboard suppression are unnecessary. The human's
remote/repair authorization already covers this work; no new product decision
or permission checkpoint is introduced.

## Verification

- Original RED: main iOS run `40109951840`, downloaded `.xcresult`, 6/9 passed and the three concrete failures recorded before repair.
- Build production and XCTest arm64 targets with the installed Xcode on Intel, signing disabled; no local simulator execution claim.
- Run exact README generation/check, `git diff --check`, and graph update.
- Independent blind/edge review of the bounded Swift/test/project diff.
- Refresh PR head/base/reviews/checks before push/merge, attach every created PR, fetch main, and await all workflows on its exact commit.

## Local implementation handoff

Production and XCTest arm64 builds, independent review, project validation and README check passed. No local iOS tests executed on Intel. Hosted runtime acceptance is pending in the explicitly authorized publish/merge phase; status done records local implementation/review, not a successful main CI claim.

## Suggested Review Order

**Native modal boundary**

- One shared UIKit boundary preserves Main while excluding covered controls.
  [MainComposeHost.swift:29](../../mobile/apps/iosApp/MainComposeHost.swift#L29)

- Production passes its active destination into that boundary.
  [SuperNutritionApp.swift:100](../../mobile/apps/iosApp/SuperNutritionApp.swift#L100)

- Acceptance uses the identical implementation for Capture and Profile.
  [PhotoAnalysisHostSupport.swift:49](../../mobile/apps/iosApp/PhotoAnalysisHostSupport.swift#L49)

**Test interaction**

- Dismiss only the evidenced system keyboard introduction, independent of its button language.
  [BootstrapLaunchTests.swift:231](../../mobile/apps/iosApp/Tests/BootstrapLaunchTests.swift#L231)

- Scroll through app content above keyboard and assistant windows.
  [BootstrapLaunchTests.swift:248](../../mobile/apps/iosApp/Tests/BootstrapLaunchTests.swift#L248)

**Target membership**

- Both applications compile the shared host source.
  [project.pbxproj:24](../../mobile/apps/iosApp/SuperNutritionUIAcceptance.xcodeproj/project.pbxproj#L24)

## R-04 runtime follow-up

Main08 still fails the original modal isolation assertions; the text flow now
passes save/correction and fails when the first Delete tap produces no dialog.
Keep the frozen product intent and assertions. The native implementation uses
a retained UIKit child with its view physically detached while covered. The
test waits boundedly for a continuously stable target after scrolling. Full
branch iOS execution must pass before merging this follow-up.

- [x] Record exact main08 failure and exported recording before repair.
- [x] Replace root flags with retained child containment and view exclusion.
- [x] Add bounded frame-settling synchronization before a single real tap.
- [x] Export compact XCTest failure summary while preserving failing status.
- [x] Compile both actual arm64 app/test products and refresh source metrics.
- [x] Review the bounded follow-up and resolve concrete findings.
- [ ] Dispatch full iOS workflow on feature head and satisfy all original cases.
- [ ] Merge and await every latest-main workflow, except accepted device expiry.
