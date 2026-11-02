# Rewards iOS adapter cleanup — 2026-10-18

Recorded before production edits in the shared worktree `REDACTED_WORKTREE/a704/SuperNutrition`, branch `codex/figma-camera-flow`, HEAD `7ec615063ac166d69e7f4a81495e20098f25be3c`. The parent owns the pending approved Rewards integration, all Gradle/device verification, graph refresh, and Git mutations. Existing staged Rewards localization and unrelated Home/Rewards work are preserved.

## Findings and acceptance

1. **Rewards Back has no native action.** `RewardsViewController(feature, copy)` invokes `RewardsScreen` without `onBackRequested`; the default callback is empty. Given this factory's returned UIKit controller, when Back is tapped, then it pops itself from a nonroot navigation stack or dismisses its modal presentation. A root controller with no presentation has no previous UIKit destination and remains in place. A controller that is no longer the navigation stack's top must not pop or dismiss a different screen. Preserve the published two-argument factory name, signature, and feature snapshot rendering. The composition callback must not strongly retain its own UIKit controller.

2. **Apple localization fails the method-length rule.** The current `mobile/apps/sharedKit/build/reports/detekt/iosMainSourceSet.xml` reports `localizedRewardsCopy` at 94 lines against a 60-line maximum. Its inline seven-card map and ten-entry visual-card list are responsible for the expansion. Given the same bundle, when localization runs after extraction into private helpers, then every reward ID, localization key, value, and collection order is identical. Do not add a suppression or alter the localization fallback/error behavior.

## Owned code

- `mobile/feature/photo-analysis/src/iosMain/kotlin/com/supernutrition/feature/photoanalysis/RewardsViewController.kt` — native dismissal/pop adapter only.
- `mobile/apps/sharedKit/src/iosMain/kotlin/com/supernutrition/sharedkit/IosPhotoAnalysisCopy.kt` — private localization helper extraction only.

The architecture spine requires platform-neutral common presentation and thin Apple hosts. Navigation remains entirely in `iosMain`; no Home route, domain state, or public callback parameter is invented. The existing `FunctionNaming` suppression protects the published PascalCase Swift factory and remains unchanged.

## Verification boundary

Before edits, inspection confirms the omitted Back callback and the reported method-length failure. This task does not claim a UIKit execution test or independent review beyond the findings supplied by the parent. The parent will recompile the actual copy source against real updated KLIBs and the `:feature:photo-analysis` iOS target, and rerun the relevant source lint. Full sharedKit compilation currently requires missing Xcode/cinterop prerequisites; standalone source compilation does not establish app linking or device behavior.

After edits, compare the complete reward-localization key sequence, reward-ID sequence, and published factory signature with the pre-edit staged/HEAD sources. Parent-owned `git diff --check`, graph update, fresh compiler/lint results, and any runtime evidence remain necessary to complete the integration.

## Scoped implementation result

The two private localization helpers retain the literal collection expressions. A read-only comparison against the staged copy source passed: all 111 localization calls across the file retain the exact key sequence, all 17 reward-ID occurrences retain order, both the seven-card map and ten-card list have identical tokens after whitespace removal, and bundle/error logic is unchanged. A comparison against HEAD confirms the published `RewardsViewController(feature: RewardsFeature, copy: RewardsPresenterCopy): UIViewController` signature and existing suppression count are unchanged. The scoped `git diff --check -- <two owned source files>` exited 0.

`RewardsViewController` supplies `onBackRequested` and stores the real returned UIKit controller in `NSHashTable.weakObjectsHashTable()`. The composition retains only that weak native collection. Back pops a top/nonroot navigation destination, dismisses a modally presented root navigation controller, or dismisses a directly presented controller; it leaves a nonpresented root and a stale nontop callback in place. [UIKit pop](https://developer.apple.com/documentation/uikit/uinavigationcontroller/popviewcontroller(animated:)) and [UIKit dismiss](https://developer.apple.com/documentation/uikit/uiviewcontroller/dismiss(animated:completion:)) define those operations. The installed Foundation `NSHashTable.h` confirms `weakObjectsHashTable`, `anyObject`, and `addObject` declarations; actual target compilation remains the parent's check.

Implementation is ready for the parent's fresh iOS source compile/lint. No Gradle process, device command, Git mutation, staging, commit, or remote write was performed. No UIKit runtime evidence or full sharedKit build pass is claimed.

## Compiler finding recorded before follow-up fix

The parent's fresh `:feature:photo-analysis:compileKotlinIosArm64` failed with six source diagnostics in `RewardsViewController.kt`: `navigationController` at line 28, followed by `topViewController`, `viewControllers`, `popViewControllerAnimated`, `presentingViewController`, and `dismissViewControllerAnimated` on the unresolved navigation receiver. The raw evidence is `mobile/build/home-no-scroll-2026-10-17/full-post-review-verification/gradle.log`, lines 1037–1043. This is a real source failure; the later sharedKit/photo-ios missing-Xcode cinterop failure is a separate prerequisite failure.

A read-only `klib dump-metadata` inspection of the installed real Kotlin/Native 2.4.10 `ios_arm64` UIKit platform library exited 0. It shows `UIViewController.navigationController` as a package-level Kotlin extension, while navigation-stack properties/pop and inherited dismissal/presentation APIs are class members. The factory file imports the controller class but omits `platform.UIKit.navigationController`; the later five errors cascade from the unknown receiver type. Given the actual UIKit library, when that single category-extension import is present, then the helper must resolve the real `UINavigationController` API and the two-argument Swift factory must remain unchanged. Parent recompilation is required before calling this acceptance condition passed. No fake platform library, SDK header, suppression, or navigation-policy change is acceptable.

Follow-up fix: added only `import platform.UIKit.navigationController`. Navigation logic, native weak storage, factory name/signature, and localization helper extraction are unchanged. Ready for the parent's fresh feature iOS compile; no build, device, or Git action was performed during this follow-up.

## Final source verification

The final full-final-scoped Gradle run actually executed and passed `:feature:photo-analysis:compileKotlinIosArm64` after the UIKit extension import. The actual final `IosPhotoAnalysisCopy.kt` (SHA-256 `47533f52742a26607bc4e105b38b82902e68459369d3ffa5a2a90436a059bbf7`) was compiled separately against real iOS Arm64 KLIBs on 2026-10-18, 04:52:05Z–04:52:12Z, exit 0; command and log are in `mobile/build/home-no-scroll-2026-10-17/apple-copy-final-source-check/`. No headers or dependency stubs were substituted. The whole SharedKit compile remains blocked by the missing Xcode/cinterop prerequisite. Neither source result proves UIKit runtime behavior, linking or device compatibility.
