# Photo analysis Detekt findings — 2026-11-04

## Findings

The local `qualityCheckJvmAndroid --continue --no-configuration-cache` run
reports three Detekt violations in
`PhotoAnalysisComposeActivity.kt`:

- `PhotoAnalysisTabs` is 71 lines; the configured `LongMethod` limit is 60.
- `PhotoAnalysisCaptureTab` is 67 lines; the configured `LongMethod` limit is
  60.
- `PhotoAnalysisCaptureTab` has cyclomatic complexity 17; the configured limit
  is 14.

These are presentation-layer method-size findings. They do not justify changing
the Detekt policy, adding suppressions, or changing capture behavior.

## Acceptance conditions

- `qualityCheckJvmAndroid --continue --no-configuration-cache --console=plain`
  completes successfully with no Detekt findings in the Android photo-analysis
  source set.
- The existing tab transitions, missing-local-model dialog, capture actions,
  and photo-analysis surface wiring retain their behavior.
- The fix extracts cohesive presentation/action helpers without changing
  project-wide lint thresholds or adding suppressions.

## Follow-up analyzer diagnostics

The forced debug run of `qualityCheckJvmAndroid --continue` also reported
compiler diagnostics during Detekt's semantic analysis. These are separate
from Detekt rule findings:

- 18 unresolved generated `serializer()` references across the V1, V2, and V3
  inference JSON codecs in `mobile/domain/inference`.
- The reified API rewrite exposed 15 `FunctionSignature` formatting findings:
  the shorter expression bodies fit on the same line as their signatures.
- 18 repeated `expect`/`actual` diagnostics for the three platform functions in
  `feature:photo-analysis` and `feature:photo-analysis-ui`. Detekt analyzes each
  Kotlin Multiplatform compilation as a flat input rather than preserving
  source-set fragment relationships. The platform source is valid Kotlin and
  is required for local time-zone behavior and Android UI automation semantics.

The latter matches the open upstream Detekt reports
[detekt#9602](https://github.com/detekt/detekt/issues/9602) and
[detekt#7531](https://github.com/detekt/detekt/issues/7531). The repository is
pinned to Detekt 2.0.0-alpha.5; alpha.6 reproduces the KMP fragment issue too.
Do not delete or replace valid platform implementations merely to silence this
analyzer defect.

### Follow-up acceptance conditions

- The three production JSON codecs use serialization APIs that Detekt can
  resolve without compiler-plugin-generated `.serializer()` calls.
- Codec expression bodies follow the configured `FunctionSignature` rule.
- Existing JSON contract fixtures remain byte-for-byte compatible and the
  inference module tests pass.
- The KMP platform contracts and Android automation semantics remain intact;
  any remaining diagnostics are limited to the documented upstream fragment
  analysis defect, with no Detekt rule findings or unresolved generated
  serializer references.

## Resolution and verification

- Replaced production `.serializer()` references in the V1, V2, and V3 codecs
  with reified `encodeToString`/`decodeFromString` calls. The configured JSON
  policies and contract models are unchanged.
- Corrected the 15 resulting `FunctionSignature` findings by keeping short
  expression bodies on the signature line.
- `./mobile/gradlew -p mobile :domain:inference:jvmTest
  --no-configuration-cache --console=plain` passed. Its XML reports show 126
  tests, 0 failures, 0 errors, and 0 skipped.
- `./mobile/gradlew -p mobile qualityCheckJvmAndroid --continue
  --no-configuration-cache --console=plain` passed: 447 tasks, 83 executed, 4
  from cache, and 360 up-to-date. The Detekt XML reports for the three touched
  modules contain 0 findings. Their JVM tests report 535 tests total with 0
  failures, errors, or skips.
- Detekt still logs 18 KMP compiler-analysis diagnostics from the three valid
  `expect`/`actual` pairs across Android and JVM aggregate tasks. No unresolved
  serializer references or Detekt rule findings remain. The upstream fragment
  issue is documented above; changing the platform implementations to hide it
  would remove required local-time and automation behavior.
- `graphify update .` completed and refreshed `graphify-out`; its graph is too
  large for HTML visualization (22,765 nodes), which does not affect code or
  lint verification.
