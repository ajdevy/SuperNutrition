# Home → Profile verification

Worktree: `REDACTED_WORKTREE/f2ff/SuperNutrition`
Branch: `codex/home-profile`
Integration baseline: `8062c3f66efd8f749ac7041b6ac97c29b8a0d9dc`

## Integration

- Started from clean detached `26281161`, matching `origin/main`.
- Fast-forwarded the outstanding Home commit `5e4dd347` and merged mascot research PR #40 (`38cd9bf4`).
- Primary checkout's staged Rewards content is byte-identical to the already committed `7d5971c0`. Its staged files were left untouched.
- Historical squash-replaced branches and owner-parked PR #39 were not resurrected. Loose prototype/design scratch was preserved in its original worktrees.
- PR #40's historical hosted failure was README snapshot freshness after its coverage task; it was not a successful CI run or an infrastructure exemption.

## Regression evidence

- `red-production.xml`: the new Home-avatar instrumentation test failed against the old diagnostic placeholder for the expected missing meal-history control: 1 test, 1 failure, 0 errors, 0 skipped.
- `red-review-regressions.xml`: 7 Profile Compose tests, 2 failures, 0 errors, 0 skipped. The failures reproduce missing-browser exceptions and truncated combining-character initials before their fixes.
- Independent blind and edge reviewers inspected the implementation snapshot. Findings and acceptance conditions are recorded in `../home-profile-findings-2026-10-22.md`.

## Host and commands

Host is Intel macOS, JDK 21 from Android Studio. Apple compilation uses Xcode 26.6 through `DEVELOPER_DIR`; the arm64 device target is explicitly enabled because default Intel builds omit it.

Android execution target: `emulator-5554`, Android 15 / API 35, x86_64, 360×780 dp. This is emulator evidence. No physical Android device was connected. On 2026-10-23 the human explicitly chose emulator-only execution and requested this limitation be reported; physical evidence records are not refreshed or relabelled.

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
DEVELOPER_DIR='/Applications/Xcode-26.6.0.app/Contents/Developer' \
./mobile/gradlew -p mobile \
  qualityCheckJvmAndroid qualityCheckApple mobileUnitCoverage \
  :feature:photo-analysis:compileKotlinIosArm64 \
  :apps:sharedKit:compileKotlinIosArm64 \
  :apps:androidApp:connectedAndroidTest \
  -Psupernutrition.enableIosDeviceTarget=true \
  --continue --no-parallel --max-workers=2 \
  --no-configuration-cache --console=plain
```

Full log: `/tmp/supernutrition-profile-evidence/verification-stable.log`. Read exact XML counts per module and Android variant after completion; a build summary alone is not evidence.

The first full check found style issues and the two review regressions above. The second repetition was interrupted (exit 130) after already-superseded style diagnostics and an unresolved pre-report `meal-search-presenter:jvmTest` failure; it is not a passing test run. The final command above uses the corrected, stable source snapshot.

## Existing policy prerequisite

`verifyMobilePolicy` rejects stale `bootstrap-android-host` evidence. Existing physical Android records date from 2026-09-12 with a 30-day validity window. Several native gaps explicitly require a physical device. Emulator execution cannot refresh those records, and their dates, capability requirements and expiry limits are unchanged.

## Legal website

Separate repository: `~/workspace_ai/supernutrition.app`. Legal source was committed as `d433ff9957665ecc24adca32a002b64b4b239d28`; reproducible publication tooling is on `origin/main` at `533b956102c9c80b10cc4d7554d896d1bad044aa`.

- EN/RU/ES app policies at `/privacy-policy/`; app and website terms at `/terms/`.
- Operator supplied by the human: Flexsent labs SIA, Latvia; existing public contact `hello@supernutrition.app`.
- Exact-current-commit GitHub verification passed: https://github.com/flexsent-labs/supernutrition.app/actions/runs/39862006978. Pinned Node 22.23.3, npm 10.9.9, Wrangler 4.103.0; 10 waitlist tests, no failures/skips; line coverage 89.70%, branch/function 100%. Static/SEO verification checks 38 pages, 42 required artifacts and 7 posts.
- Two independent cold builds cleared only checked, ignored output/cache paths. All 79 release files (76 static + 3 Worker) matched: SHA256 `26615f9f729167eaf133df066b12c8e894e89908e1a86dd3906ecfcbd11a331e`. Wrangler timestamp metadata is canonicalized to source provenance and included in the digest. The final prebuilt upload path passed dry-run; production deploy checks branch, commit, clean tree, runtime and exact receipt hashes.
- Production will be published only by Cloudflare Workers Builds from `flexsent-labs/supernutrition.app/main`, using `npm run cloudflare:build` and `npm run cloudflare:deploy`, with preview deployment disabled. The human completed Cloudflare login; GitHub sign-in to connect the existing Worker is pending in the browser. No local production upload succeeded. Source publication and green verification are not live-site deployment evidence.

## Graph update

`graphify update .` extracted the complete code corpus and wrote the initial graph and report; it was interrupted during manifest saving (exit 130). The subsequent `graphify update . --no-cluster` completed with exit 0 after the final source edits: 21,358 nodes and 30,681 edges. `graph.json` is current; the initial community report was not recomputed. The tool reports 457 zero-node metadata/source files and two existing SQL files missing `tree_sitter_sql`. Generated graph/cache files are machine-local and ignored. No semantic extraction or SQL coverage is claimed.

## Final results

Fresh UI XML reports from the stable run show 188 tests, 0 failures, 0 errors, 0 skipped across 34 suites. This includes all 11 Profile tests (7 action/identity, 2 history, 2 rendered-layout tests); the three green Profile XML reports are copied beside this record. `graphify query ProfileScreen` found the new screen, host route, session and tests in the current graph.

The stable full command completed with exit 1 after 42m38s. Exactly three failures: stale physical evidence in `verifyMobilePolicy`, and missing strict runtime lock entries in `apps:sharedKit:jvmTest` and `feature:meal-search-presenter:jvmTest`. The latter two tests initially never started and produced no XML. Targeted Gradle lock refresh under strict dependency verification added the five missing runtime dependencies and moved the resolved meal-search runtime annotations from 13.0 to the already verified 23.0.0. No dependency declaration, version catalog, checksum or locking policy changed. Both full JVM suites then passed: SharedKit 3/0/0/0 and meal-search-presenter 7/0/0/0. Apple arm64 compilation completed for the modified feature, UI and SharedKit sources, with explicit device target and Xcode. Apple lint tasks in this run reported no findings; the aggregate gate remains failed because of policy.

Fresh Android XML: poc 44/0/0/0, production 44/0/0/0, uiAcceptance 44/0/0/0 (tests/failures/errors/skipped). The Profile navigation/edit/recreation/back regression passed in each, taking 19.906s, 22.409s and 16.452s respectively. Copies are stored beside this record. This is Android 15/API35 x86_64 emulator execution, not physical-device or Apple execution evidence.


## Lock repair follow-up

`JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./mobile/gradlew -p mobile :apps:sharedKit:jvmTest :feature:meal-search-presenter:jvmTest --write-locks --dependency-verification=strict --continue --no-parallel --max-workers=2 --no-configuration-cache --console=plain` completed successfully in 4m22s; log `/tmp/supernutrition-profile-evidence/lock-refresh.log`. The subsequent complete read-only gate/report run uses `qualityCheckJvmAndroid qualityCheckApple mobileUnitCoverage mobileCoverageReports -Psupernutrition.runJvmCoverage=true --dependency-verification=strict --continue --no-parallel --max-workers=2 --no-configuration-cache --console=plain`; log `/tmp/supernutrition-profile-evidence/quality-after-locks.log`. It does not repeat unchanged native instrumentation or Apple compilation. The explicit coverage opt-in is included; the earlier complete run omitted it and did not establish an aggregate coverage pass.

The exact stable production APK was installed after instrumentation cleanup. Manual Home-avatar activation opened Profile with the unknown-name placeholder and zero real session meals. Native screenshots were captured at 100% and 200% font scale on 360x780dp; text wrapped and controls stayed reachable. The emulator font scale was restored to 1.0. Screenshots are in `~/.codex/visualizations/2026/10/22/01a16557-276b-7002-b45e-4a79f11f1c89/profile-android-native*.png`.


## Completed read-only follow-up

The final gate/report command completed in 1m07s (exit 1), with exactly one failure: `verifyMobilePolicy` reports stale `bootstrap-android-host` physical evidence. All remaining test, lint and report tasks completed without new failures. The aggregate `qualityCheckJvmAndroid`, `qualityCheckApple` and `mobileUnitCoverage` actions remain blocked by that prerequisite and are **not** reported green. No task filters, policy skips, baselines, coverage exclusions or evidence expiry changes were introduced.

`unit-test-summary.json` records exact XML counts and task state separately for 18 JVM/unit tasks. SharedKit and meal-search-presenter executed in this follow-up; the other tasks were correctly up-to-date against unchanged test/source inputs. Key counts: UI 188/0/0/0; host feature 25/0/0/0; core UI 4/0/0/0; photo presenter 19/0/0/0; catalog/nutrition 257/0/0/0.

`mobileCoverageReports` completed. A read-only TOML/XML observation (`coverage-observation.json`) records nine JVM denominators: 15,664 covered / 71 missed lines (99.54878%), 2,381 covered / 294 missed branches (89.00935%). Every module exceeds its 85% floor. Nutrition has 99.99168% line and 100% branch coverage, above its stricter floor. These are report observations, not a passed aggregate policy gate; native-equivalent denominators remain subject to expired physical evidence.

Production APK SHA256: `feeeaef20572028565888d5d8454c6a0847563ddb5f5e2ce08f508f7f291cea4`. Apple evidence consists of successful shared Kotlin arm64 compilation and its source-set lint in the stable run, plus Swift syntax and strings validation. The final follow-up used the default Intel source targets; it does not add Apple-source lint evidence. No Xcode host build or Apple device/simulator UI run is claimed: this checkout lacks CocoaPods setup and Intel cannot execute the canonical arm64 simulator target.

The final incremental graph refresh also completed (exit 0): 21,361 nodes / 30,684 edges. Evidence XML copies use LF line endings; original generated reports remain in build directories.
