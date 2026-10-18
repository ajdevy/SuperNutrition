# Home fitting and approved Rewards integration

The finite screen review is complete. The measured Home layout fits feasible viewports without reducing font tokens or system text scaling. **40 of 100 final captured cases fit; 60 deliberately stop for a designer decision.** The universal no-scroll requirement is not passed. The user's explicit fallback is fulfilled by preserving readable overflow and full-resolution screenshots rather than making text smaller or hiding content.

Worktree: `/Users/REDACTED/.codex/worktrees/a704/SuperNutrition`, branch `codex/figma-camera-flow`, source baseline `7ec615063ac166d69e7f4a81495e20098f25be3c`. The approved 26-file Rewards snapshot is merge parent `7d5971c0d87e3a2c87b8ab35b86628f0ce7a17f9`. The current source and exact native command are bound by the portable verification records under `home-no-scroll-verification-2026-10-18/`. The explicitly requested normal main publication is recorded separately in `mobile/build/main-merge-2026-10-17/home-final-publication.json`; this report does not treat failed hosted/root checks as green.

## Designer handoff

The portable folder is `_bmad-output/design-review/home-no-scroll-2026-10-17/`. Start with `DESIGNER-NOTES.md` or `index.html`. The Russian gallery offers search, font/result filters, original PNGs, actual dimensions, safe boundaries, and before/after comparisons where a before frame exists. Browser rendering and the compact-screen filter were verified in the in-app browser.

The installed SDK catalog contains 71 mobile/generic definitions represented by 45 exact pixel/density geometries, including four prior app checkpoints. Each geometry was captured at 100% and 200% system text, plus four 130% boundaries: 94 cases. Three independently created compact/large/tablet AVDs add six captures. All 100 after frames and six historical/diagnostic frames were visually inspected and matched by SHA-256 and PNG dimensions. No screenshot is marked fit solely from XML. Final gallery validation checked 509 local links and all 106 original PNG hashes.

The main series uses one API 35 x86_64 guest with viewport overrides. It is not 71 separately executed hardware models. Three extra AVDs were executed sequentially, their configurations preserved, and all three removed through official AVD tooling. Baseline guest settings were restored. The Android app is portrait-locked; the actual orientation is recorded independently of requested landscape presets. Foldable base displays are included, while closed postures, folds and other Android/API combinations are unverified. No physical-phone operation or physical camera/permission claim is made by this series.

Useful boundaries: 360 × 780 dp at 100% fits, but 130% does not; 412 × 915 dp fits at 100%/130%, but 200% does not. Short 320 × 533/568 dp screens fail even at normal text. Some fitting screens have nearly touching lower cards or excess floor space. At 200%, five mdpi captures wrap/clip the Rewards navigation label; two have otherwise fitting Home cards and are consequently classified as designer-required. These findings remain visible rather than being removed from the denominator.

The measured layout reserves natural text/actions and navigation clearance first, then allocates illustration height, padding and spacing. Intrinsic text measurements choose header placement, metric columns and the helpful-step row/column. The same content tree preserves callbacks and state. Font tokens, full words, 48 dp action frames and the mascot overlap are retained. When minimum content cannot fit, readable scrolling remains and the no-scroll outcome is explicitly false.

## Verified source results

| Evidence | Fresh result | Boundary |
| --- | --- | --- |
| Shared UI `:feature:photo-analysis-ui:jvmTest --rerun-tasks` | 167 tests, 0 failures/errors/skipped; 30 XML suites | Initial frame, enlarged text, callbacks, gallery aspect ratio, overlay and Rewards regressions |
| Presenter `:feature:photo-analysis-presenter:jvmTest --rerun-tasks` | 19 tests, 0 failures/errors/skipped; 4 XML suites | Presenter module only |
| Android native `:apps:androidApp:connectedAndroidTest` | 43 tests per variant (poc, production, uiAcceptance), 129 total, 0 failures/errors/skipped | Clean API 35 x86_64 AVD; emulator only |
| Production Android assembly | Passed; final APK SHA-256 `7f7296bd7282a86dc63fe4feaad0d4a19706c8b4ca231c39607ff0e3abc1f26c` | Exact APK used for all 100 after captures |
| Feature `:feature:photo-analysis:compileKotlinIosArm64` | Passed after importing the actual UIKit navigation extension | Source compilation, no UIKit execution |
| Shared UI `:feature:photo-analysis-ui:compileKotlinIosArm64` | Passed/up-to-date with verified unchanged inputs | Source compilation, no Apple runtime claim |
| Actual final `IosPhotoAnalysisCopy.kt` iOS Arm64 compilation | Exit 0 against real KLIBs, final source SHA-256 `47533f52742a26607bc4e105b38b82902e68459369d3ffa5a2a90436a059bbf7` | Source-only check; full SharedKit remains blocked by missing Xcode/cinterop prerequisites |
| `qualityCheckJvmAndroid --continue` | Exit 1: stale `bootstrap-android-host` native-gap evidence | Whole gate remains red; policy dates and TTL were not changed |

The current source was rechecked against all 71 hashes from `full-final-scoped` and all nine presenter hashes from `presenter-final`: no changes since the verified source runs. The production APK digest also matches. Raw commands, hashes, logs and XML are under `mobile/build/home-no-scroll-2026-10-17/`, especially `presenter-final/`, `full-final-scoped/`, `apple-copy-final-source-check/` and `final-input-verification.json`. Generated build output is not staged.

This Intel x86_64 Mac has 16 GiB RAM. Apple device/simulator execution is unavailable. Full SharedKit compilation failed before its Kotlin source step at four missing-Xcode cinterop prerequisites; the successful actual-source KLIB check does not replace that failed whole-module build or app linking.

## Native verification status

The first full three-variant run executed 43 tests per variant with 9 failures each: 129 executed, 27 failed. The nine UI-resource failures were investigated without deleting assertions, extending semantic waits or changing resource-id flags. A one-method diagnostic repeat showed an attached/resumed Compose Activity without focus and Android's ANR window owning the active accessibility root. A visually inspected native screenshot confirmed the SystemUI modal. Resource-id flags were already enabled.

After an interrupted session, a cold start reproduced SystemUI and Quickstep ANRs before Super Nutrition launched. A subsequent retry stopped before app instrumentation because ddmlib could not fetch device properties and reported Unknown API Level; its synthetic TestRunner failure is **zero executed app tests**. Two ADB servers were connected to the standard emulator port. The task guest was moved outside the default ADB scan range, preserving the default server and Maestro Studio. The old guest still reproduced system ANRs, so its data was preserved and a clean temporary API 35 x86_64 AVD was created at the same 720 × 1560 / 320 dpi geometry. The exact same semantic assertion then passed, followed by the complete canonical three-variant suite: **43/0/0/0 per variant, 129/0/0/0 total**, 2026-10-18T09:41:42Z–09:48:13Z, Gradle exit 0. Variant XML is fresh and archived; no assertion, semantic timeout, selection check or production source was changed. The earlier failures remain recorded. The native focus blocker is resolved without substituting emulator evidence for expired physical-device policy records.

The read-only failure diagnostic helper logs target-app view/resource geometry and foreground ownership only when an existing assertion fails. It omits labels from other packages, does not modify service flags or retry assertions, and is not part of production code. The detailed red evidence and recovery conditions are in `home-native-semantics-regression-2026-10-18.md`.

## Approved Rewards and remaining prototype limits

The human retained ten medals and mock levels: «Сохранить 10 медалей; новые уровни пока оставить как макет». The integration retains that representation without inventing new domain criteria. Header/card clipping, the small Back touch frame, link contrast, final-card navigation clearance and UIKit Back wiring were fixed and verified within the boundaries above. Localized key/ID order and the published two-argument iOS factory were preserved.

See all remains a nonfunctional prototype label (R06). Level/points, index-based groupings and higher-tier goal labels remain prototype presentation rather than live earned-state results. UIKit Back source compiles, but its runtime behavior is unverified. The passing layout tests are not a claim of completed Rewards interactions. See `home-rewards-review-triage-2026-10-18.md` for the independent source review and exact dispositions.

## Git, graph and hosted checks

The primary checkout `/Users/REDACTED/workspace_ai/SuperNutrition` remains at `e1271c6` with its original 26 staged Rewards files. Its HEAD, every index mode/blob pair, all 26 source/asset digests, and its empty unstaged patch were rechecked unchanged. The staged patch matches its original digest with explicit `--abbrev=7`; Git’s automatic abbreviated object IDs grew to eight digits as objects were added, changing only the default diff rendering. The exact staged tree is preserved on `codex/rewards-main-staged` at `7d5971c`; the approved snapshot is integrated without unresolved conflicts. No reset, branch switch, blanket staging or destructive cleanup was used.

`graphify update .` completed after the final code changes: 21,138 nodes, 28,631 edges, 2,005 communities. Its code graph was updated; large HTML visualization was skipped by the existing node limit and two SQL inputs remain unparsed because the optional parser is absent. This is not a full semantic-document refresh. Python capture/gallery helpers compile, and `git diff --check` passes.

The last inspected hosted check at baseline `7ec6150` is failed, not billing-only or a zero-step result: the mobile coverage job executed 22 steps and failed resolving `org.jetbrains.compose.components:components-resources:1.11.1` outside the dependency lock state; report upload also failed. It is separate from the local native-evidence expiry. Hosted CI is not claimed green. Remote main and review/check state are refreshed immediately before the explicitly requested normal push; the publication receipt records their actual state.

The gallery ZIP is `mobile/build/designer-handoff/Home-design-review-2026-10-18.zip` (274,281,367 bytes, 456 entries); integrity/CRC and every staged gallery byte were checked. SHA-256: `db26e93778ad085985b6922aef914ed8fd4d772a0bf53838e1284744db113ae1`. The archive stays in ignored build output to avoid committing a redundant large ZIP. A narrow `.gitattributes` rule preserves the verbatim dumpsys spaces, Excel-compatible CRLF CSV and native JUnit XML CRLF; normal source whitespace checks remain unchanged.

## App ready for manual review

The exact production APK was installed and launched successfully after the complete native suite. Home and Rewards were visually inspected at 720 × 1560 px / 320 dpi / 100% text; original screenshots and hierarchies are in `home-no-scroll-verification-2026-10-18/`, with hashes in `manual-review.json`. They are separate from the 100-case gallery denominator. Home is left open in the visible clean `SN_Final_Verification_35_20261018` emulator, serial `emulator-5590`, isolated ADB port 5041. This one task-created AVD remains needed for the user’s requested manual review; delete it afterward. The three matrix AVDs are already removed; the old baseline and unrelated Pixel AVD are stopped and preserved.

## Post-publication CI follow-up

Initial head `b5f17d52` reproduced three concrete CI failures. The Linux feature lock lacked two existing Compose resource dependencies; Gradle generated precisely those two entries. Its strict read-only resolution plus all 25 feature JVM tests (six fresh suites) then passed with 51 tasks executed. iOS stopped before compilation on a missing simulator resource checksum inventory; the four required artifacts were verified against Maven Central publication hashes, added as one exact component, and the same strict simulator-publication resolver changed from red to green. No simulator runtime pass is claimed.

Hosted API36 uiAcceptance instrumentation passed 43/0/0/0 in fresh XML; the following old Maestro entry expected capture immediately while Android now launches Home. The shared flow conditionally enters capture from Home and preserves all original assertions. Its syntax passes, and isolated native execution of the whole journey passes 13 assertions/eight taps. The pinned local Maestro executor cannot safely address the isolated ADB port, so a real Maestro pass remains a hosted-run result, never inferred from this helper. Commands, source hashes, provenance and reports are retained in `home-no-scroll-verification-2026-10-18/ci-followup/`; see `home-linux-lock-regression-2026-10-18.md`. The normal follow-up push receipt and current hosted statuses are recorded in `mobile/build/main-merge-2026-10-17/ci-followup-publication.json`.

Production Home is restored on the retained review emulator and the temporary acceptance package is removed. No production UI source, APK, gallery original or designer ZIP changed during this follow-up.
