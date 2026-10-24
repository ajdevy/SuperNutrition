# MVP publication and main CI — 2026-10-24

## Authorization and starting state

The human explicitly requested pushing `codex/mvp-completion`, opening a PR,
merging into `main`, waiting for main CI and repairing every failure except
expired device checks. This authorizes the remote phase after the completed
local-only implementation workflow; its frozen specification remains historical.

- Worktree: `/Users/anastasia/.codex/worktrees/f2ff/SuperNutrition`.
- Initial clean branch/head: `codex/mvp-completion` at
  `509da213ae6e962c470539f7a6e5912999a2af66`.
- Refreshed `origin/main`: `0f1fbb4e599e0a71accaef5c074466436b9e8e0b`;
  feature branch is three commits ahead with no divergent base commits.
- Branch pushed; PR: https://github.com/flexsent-labs/SuperNutrition/pull/61.
- GitHub connector search returned permission/visibility error 422. The
  authenticated `gh` CLI can access this repository and is the fallback.
- Prior main run `40027554695` failed only at `verifyMobilePolicy` with
  `Native-gap evidence is stale: bootstrap-android-host`; prior Android and iOS
  UI workflows succeeded. No device manifest or expiry is changed for this task.

## R-01 — generated README snapshot is stale (recorded before repair)

The committed README still lists 1,040 source files and 325,882 code lines.
Executing the existing `build_coverage_site.count_loc(Path.cwd())` against the
tracked PR tree returns 1,214 files and 350,787 code lines. The CI workflow
regenerates these values and runs `update_readme_metrics.py --check`, so the
snapshot needs regeneration along with the newly measured coverage percentages.

Acceptance: generate the complete dashboard from the PR's real Kover reports,
update only the generated README section using the existing generator, and see
the exact CI `--check` pass. Do not change report definitions, coverage floors,
denominators, source exclusions or the expiry exception.

The first local dashboard attempt was incomplete because the existing XML for
`data-nutrition` had no matching HTML report; it was not a product failure or
successful dashboard check. `mobileCoverageReports` is running to provide the
full report set. The PR workflow is also running; its artifacts will provide
the authoritative hosted snapshot before merge.

## R-02 — missing strict-verification metadata (recorded before repair)

PR run `40108179854` failed before test execution while configuration-cache
serialization resolved `:data:nutrition:jvmTestRuntimeClasspath`:
`junit-bom-5.12.2.module` (`org.junit:junit-bom:5.12.2`) was not trusted in
`mobile/gradle/verification-metadata.xml`. Consequently the dashboard had no
fresh Kover XML and the README check had no `loc.json`; these two failures
are downstream of dependency resolution in this run, not completed tests.
The stale README finding above was reproduced independently from tracked source.

Acceptance: verify the exact missing Maven Central metadata against its
published digest, add that artifact's checksum without broad trust rules or
relaxing strict verification, rerun the exact JVM/report command, then generate
and check the README snapshot. Preserve the original failed run as RED evidence.

Hosted RED log: ignored
`mobile/build/verification/mvp-audit/pr61-ci-failed.log`.

## Repairs and local verification

- Added only `junit-bom-5.12.2.module` SHA-256
  `de70ac5d91a52656d8890a6d23b9e04d99b99b1a0402530decad71bf31b2735e`.
  Bytes and published `.sha256` agree at
  `https://repo.maven.apache.org/maven2/org/junit/junit-bom/5.12.2/`.
  Parsed component coordinates also match `org.junit:junit-bom:5.12.2`.
  No broad artifact trust or verification mode changed. Ignored provenance:
  `mobile/build/verification/mvp-audit/pr61-junit-bom-provenance.json`.
- Strict `mobileCoverageReports` succeeded in 5m32s, 181 tasks (53 executed,
  128 up-to-date). Existing unchanged tests were legitimately up-to-date;
  `platform:photo-ios:jvmTest` executed its host-only tests, not iOS runtime.
- Complete dashboard generation then succeeded. The unmodified README's exact
  `--check` failed with the expected stale-section diff (ignored
  `pr61-readme-red.log`), including the missing `data-nutrition` denominator.
  Ran the existing README generator and the same `--check` passed.
  Final source counts include the three new verification XML lines:
  1,214 files / 350,790 code lines. Optional/native report counters retain their
  measured values; dashboard generation is not a full quality-gate pass.
- The exact CI Gradle command after the checksum change completed with exit 1
  in 2m53s (204 tasks, 22 executed / 182 up-to-date):
  `./mobile/gradlew -p mobile mobileUnitCoverage mobileCoverageReports
  -Psupernutrition.runJvmCoverage=true --dependency-verification=strict
  --no-daemon --configuration-cache --max-workers=1 --continue`.
  Its only failed task is `verifyMobilePolicy`, reporting the accepted expired
  `bootstrap-android-host` record. Dependency resolution and report generation
  succeeded; full aggregate verification behind the failed policy dependency
  is not certified green. Ignored log:
  `mobile/build/verification/mvp-audit/pr61-strict-unit-report-after-checksum.log`.
