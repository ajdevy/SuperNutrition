# Storage repair follow-up — static review, 2026-10-24

This is a focused follow-up of the storage and instrumentation-isolation repairs for the existing R1/R2/R4/R5 triage. It is not another independent review of the original 185-file change.

The real working directory was confirmed as `/Users/REDACTED/.codex/worktrees/f2ff/SuperNutrition`. The parent supplied branch `codex/mvp-completion`; Git inspection was deliberately not repeated because this follow-up expressly prohibited Git operations while parent verification session 86124 was active.

## New findings

```json
[]
```

No new concrete correctness defect was established in the bounded source inspected. This result does not close the original findings or substitute for the parent's runtime acceptance evidence.

## Paths inspected

- `RevisionCheckedPrivateDocumentStore.kt:14-27`: successful reads publish the exact observed revision; failed reads leave observation state unchanged. A stale loaded owner returns false before its writer runs. False or throwing writes do not advance the expected revision. Null and empty remain distinct. First saves without a prior successful observation deliberately read before writing, as specified by the new common tests; they are not incorrectly reported here as stale observed owners. Callback cancellation is not caught.
- `AndroidPrivateDocumentStore.kt:25-32`: the same process-wide lock surrounds the helper's complete read/compare/write operation, not just each I/O callback. `IOException` is translated to false on save, while read failures remain visible to the owner. `readDocument()` rejects the obstructing backup-directory case before invoking `AtomicFile.openRead()` at lines 35-43.
- `AndroidPrivateDocumentStore.kt:46-65`: encoding, staged write, file sync and stream close precede the `Os.rename()` commit point. No fallible verification or `failWrite()` follows a successful rename. I/O/syscall failures return false through pending-file cleanup. Cleanup targets only the fixed `.new` sibling and does not delete a base or traverse directories.
- `IosPrivateDocumentStore.kt:34-38,101-109`: the existing shared native recursive lock surrounds the same helper protocol and is released in `finally`. Backup exclusion, size/UTF-8 checks, file protection and the atomic NSData replacement remain on their existing paths. Exceptions and cancellation can leave the helper only before it advances the expected revision.
- `IsolatedPrivateDataApplication.java:10-31`: each test application uses a UUID-named subtree under the real no-backup directory, created lazily under synchronization. The fixture path is cached. Cleanup names only the closed document-ID set and its two known sidecars, then attempts to remove that fixture directory; it does not recurse into unexpected files or remove the real parent.
- `IsolatedPrivateDataTestRunner.java:14-27`: application substitution exists in test source only, and `super.finish()` is reached from a `finally` block even if fixture cleanup throws. The production manifest still names `BootstrapApplication`; the inspected Gradle setting selects the isolated runner for instrumentation. Making the production application subclassable introduces no alternate production manifest identity.
- `InstrumentationStorageIsolationTest.java:26-66`: the tests distinguish target-context real storage from application-context fixture storage before fixture writes. The interruption fixture uses a uniquely owned sibling sentinel, restores its prior fixture bytes in `finally`, checks sidecar absence and removes the sentinel. It does not read or overwrite a production document filename in the real parent.
- `PrivateDocumentStoreInstrumentationTest.java`: all current test methods and their cleanup paths were read, including stale owners, definitive commit reporting, obstructed-backup reads for both IDs, malformed/oversize bytes, normal Unicode/empty round trips and ordinary legacy-backup restoration. File fixtures now live beneath the isolated application directory. The common revision tests and native iOS fixtures were also read as supporting assertions, not executed.

The remaining regular-`.bak` branch was considered separately from an obstructing directory. `AtomicFile` treats a regular legacy backup as committed authority; the supplied regular-backup fixture explicitly requires it to replace an interrupted base. A corrupt regular backup's bytes remain present after recovery/rename. Falling back to a different valid-looking base would require an additional recovery policy, so this pass does not invent that behavior or count it as a new finding.

## Source snapshots

SHA-256 values identify the exact files read for this follow-up:

| File | SHA-256 |
| --- | --- |
| `mobile/core/config/src/commonMain/kotlin/com/supernutrition/core/config/RevisionCheckedPrivateDocumentStore.kt` | `76237bcd5205d6d90aaa4c89685e36ed57e1469bc125ed9c818532f22cfb9a74` |
| `mobile/platform/photo-android/src/androidMain/kotlin/com/supernutrition/platform/photoandroid/AndroidPrivateDocumentStore.kt` | `7c9efdeb277d0f773053d5f41a331f5a4e588823f3bd1b3d374adb8602265459` |
| `mobile/platform/photo-ios/src/iosMain/kotlin/com/supernutrition/platform/photoios/IosPrivateDocumentStore.kt` | `e7ed79d459b65d0d47920796e1fcc4ed56884430f2c6a0c7d571f851ba5c23c4` |
| `mobile/apps/androidApp/src/androidTest/java/com/supernutrition/android/IsolatedPrivateDataApplication.java` | `120b0d1e79697c9ddaf9086f306ff7fc6792383813a7059c4c07a4cd4f644431` |
| `mobile/apps/androidApp/src/androidTest/java/com/supernutrition/android/IsolatedPrivateDataTestRunner.java` | `585c49055aa5dd2c645e48f192b375326a36c1833fc5827c75f7fc7c9c6af18d` |
| `mobile/apps/androidApp/src/androidTest/java/com/supernutrition/android/InstrumentationStorageIsolationTest.java` | `daac7dae3ca7ace34c09dfa1295fb11475c4f1d38c6dc88d9b7278cb728accb8` |
| `mobile/apps/androidApp/src/androidTest/java/com/supernutrition/android/PrivateDocumentStoreInstrumentationTest.java` | `553053369c98c3be108622dbbf005692c16bd42afa882a02a7390f48d187d020` |
| `mobile/core/config/src/commonTest/kotlin/com/supernutrition/core/config/RevisionCheckedPrivateDocumentStoreTest.kt` | `ab8366a8032d9494be36b1fb237500a63c7eee3f521f2d9a00dc204997876356` |
| `mobile/platform/photo-ios/src/iosTest/kotlin/com/supernutrition/platform/photoios/IosPrivateDocumentStoreTest.kt` | `3cbfce1851b04d5895cb4465781bcad6d9bfdd867a24c46d06714dac987b6e59` |

## Limits

This was source inspection only. No build, unit test, filesystem fault injection, ADB/device command, Xcode/Native execution, Git command, staging, commit or remote operation was performed. The parent's reported RED archive and running verification were treated as context, not as evidence generated or independently replayed by this reviewer. There are zero executed test results to attribute to this follow-up.

The review establishes source-path handling within the listed files and the one-process locking contract. It does not establish OS crash/power-loss durability, inter-process coordination, runtime runner/classloader behavior or iOS filesystem execution. It does not reassess catalog/UI changes, expand the spec or claim a clean result for the complete original change. No source, test, policy or existing report was edited; this report is the follow-up's only authored file.
