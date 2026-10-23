# Emulator verification findings — 2026-10-23

## IOS-SETUP-1: documented SharedKit host slice has an incomplete dependency registry

Acceptance: the current source can configure and link `:apps:sharedKit:linkDebugFrameworkIosX64` on this Intel host. The documented `-Psupernutrition.hostSlice=sharedKit` invocation fails during configuration because `:feature:photo-analysis` now requires `:domain:nutrition`, which that slice omits. No Swift build or iOS test ran in that attempt. Retry uses the full checked-in module registry; no source or policy is changed to hide this failure.

## IOS-SETUP-2: no installed simulator runtime

Both full Xcode installations (16.4 and 26.6) report an empty runtime inventory. An official `xcodebuild -downloadPlatform iOS -buildVersion 18.5` download was attempted under Xcode16.4, then interrupted after the unsupported Intel Compose target was established and the user accepted that limitation. A successful framework compile alone will not be reported as a simulator launch.

## Verification boundaries

This host is Intel x86_64. The checked-in iosX64 acceptance host is a SwiftUI compatibility renderer, not the Compose production UI. Existing Android AVDs are untouched; Android runs use the new task-owned SN_Screenshots_20261023 AVD (API35 x86_64, 360×780dp, host OpenGL).

## IOS-BUILD-1: Intel dependency metadata is not enrolled in strict verification

Acceptance: link the current Intel compatibility framework with `--dependency-verification=strict`. The full-registry invocation fails before linking at `:core:ui:downloadKotlinNativeDistribution`, listing six metadata artifacts with missing checksums (not mismatches). Their cached bytes were compared with the public Maven Central artifacts and published checksums before adding exact SHA-256 entries. No verification mode, dependency version, trust wildcard, or existing checksum is changed. `ios-dependency-checksums.json` records the exact coordinates, sources and digests; AtomicFU publishes SHA-1 at this version, the other five also publish SHA-256.

## IOS-RUNTIME-1: the pinned Compose release has no Intel iOS variants

After exact checksum enrollment, the full framework link reaches `:feature:photo-analysis-ui:compileKotlinIosX64` and fails because Compose 1.11.1 runtime, foundation, UI and components-resources have no `ios_x64` variant. The checked-in SwiftUI compatibility host still links the shared feature graph, so the old runbook is not an executable workaround on current source. No simulator test, launch or iOS screenshot was produced. The user confirmed that only Intel is currently available and explicitly asked to record that limitation. The task-owned iOS 18.5 runtime download was interrupted (exit130) after this finding; no global Xcode selection or simulator policy was changed. All47 experimental checksum additions were reverted; the dependency verification file is exactly its original HEAD content.

## PHOTO-SMOKE-1: Review navigation is verified independently from model accuracy

A native picker import of the checked-in chicken/salad fixture reached the designed Review screen using on-device analysis. Its unconfirmed dish candidate was `pork_chop` and its component candidates included repeated carrot/broccoli/orange suggestions. This is runtime navigation/rendering evidence, not an inference accuracy pass. No candidates were confirmed or written into meal history.

## IOS-SETUP-3: Xcode scheme has no eligible generic iOS destination

The production scheme build exits70 before Swift compilation under Xcode26.6: the Any iOS Device destination reports “iOS26.5 is not installed”. Xcode16.4 destination inspection similarly reports iOS18.5 unavailable. Both toolchains list their corresponding SDKs; that SDK inventory does not establish an eligible scheme/runtime destination. The separate arm64 SharedKit framework link succeeds. A direct production-target SDK compilation is checked separately; no scheme or runtime success is inferred from it.

## IOS-BUILD-2: Xcode device builds force the unsupported Intel simulator target

Acceptance: given KOTLIN_TARGET=iosArm64 on Intel, both production and acceptance build phases invoke only the arm64 framework graph, preserve strict dependency verification and compile the real Xcode host without enabling iosX64. The direct production target build failed exit65 in its Gradle build phase after Swift/link commands had already run, because both duplicated project scripts hardcode `enableIntelIosX64=true`, resolving Intel dependencies even for an iPhone build. That run immediately failed strict verification for unenrolled Intel artifacts and reported configuration-cache serialization; the Compose no-variant failure was established in the separate earlier diagnostic experiment. Exact red command/output are preserved in ios-production-target-red.json/log. Use one host-local helper for both phases to keep target selection consistent; do not modify Kotlin/Compose versions or native evidence policy.

## IOS-PACKAGE-1: existing Xcode host omits shared Compose resources

After the target-selection fix, the real unsigned production target compiles and links arm64 with exit0. Its fresh `.app` contains nine files, including the executable, static SharedKit and three legacy capture PNGs, but no Compose resource directory or the Fraunces/Nunito fonts and Profile/Home/Rewards artwork consumed by the current UI. The pre-existing Xcode resource phase lists only those legacy files; SharedKit applies no Compose resources plugin. `ios-app-package.json` preserves the exact inventory. This is a pre-existing packaging defect exposed by inspection, separate from the architecture-selection correction. The build is compilation evidence only; a usable iOS UI package and runtime are not verified. Resource packaging needs a focused follow-up on a supported Apple runtime.

IOS-BUILD-2 resolution: both phases now share the validated helper. The first direct production-target SDK rerun passed exit0 in45s; its first-pass json/log are preserved. After phase-order correction, the final17.5s rerun and unchanged repeat both passed with Gradle preceding Swift/link and the original trust metadata. Runtime remains unexecuted; IOS-PACKAGE-1 remains open.

## IOS-BUILD-3: helper-only Xcode dependency input can skip real Kotlin changes

Acceptance: each Xcode host build invokes Gradle so Gradle can track all transitive Kotlin/build/dependency inputs. Both independent reviewers found that adding only the helper to inputPaths, while retaining a framework output, lets Xcode skip the phase once that binary is newer than the helper. Kotlin edits are not declared Xcode inputs. Reproduce with a fresh prior arm64 target build, a newer framework output timestamp and a still-newer Kotlin source timestamp; then rebuild the same target. Fix both phases to run unconditionally and let Gradle own incremental input tracking.

## IOS-DOC-1: historical command failure stage was described incorrectly

Acceptance: documentation states the actual first failure of its retained command. With `hostSlice=sharedKit`, configuration stops at missing `:domain:nutrition`; removing that slice first exposes strict Intel checksums, and only the recorded temporary enrollment reaches the unsupported Compose variant. Clarify the separate diagnostic stages without changing the dependency registry or trust policy.

## IOS-BUILD-4: framework phases follow Swift compilation/linking

Acceptance: both Xcode app targets build SharedKit before Sources and Frameworks. Review of the original red log shows Swift and link commands preceded the framework phase; the old wording claiming failure before Swift was incorrect and is corrected. A real incremental rebuild with only newer framework/Kotlin timestamps failed exit65 with an Xcode dependency cycle between the executable linker and the late Gradle phase (`ios-incremental-red.log`). This inherited phase ordering can also consume a stale framework. Move both Gradle phases first, alongside the unconditional-run correction, then repeat the same build. No Kotlin/framework bytes were changed by the timestamp probe.

## IOS-EVIDENCE-1: original device-build error stage needs precision

Acceptance: the original device red result states its immediate failure, independent of the earlier Intel architecture experiment. The device build reported missing checksums for Intel dependency artifacts and configuration-cache serialization, after Swift/link commands. It did not itself reproduce the no-variant error. Preserve the original log and distinguish these observations.

## ANDROID-EVIDENCE-1: manual record start time predates later screenshots

Acceptance: a multi-stage verification record does not imply all checks finished at its creation time. The original `at` value is its initial recording time; the large-font captures came later. Retain that start time, add per-file filesystem creation timestamps and a final consolidation timestamp without inventing missing capture events.

Resolution of review findings: IOS-BUILD-3 and IOS-BUILD-4 are corrected by unconditional first-position framework phases. The final timestamp probe passes with Gradle preceding Swift/link; an unchanged repeat also invokes Gradle and passes. IOS-DOC-1 and IOS-EVIDENCE-1 wording now identifies each actual failure stage. ANDROID-EVIDENCE-1 records start, consolidation and filesystem creation times separately. IOS-PACKAGE-1 remains unresolved.
