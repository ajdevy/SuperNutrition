# Independent introduced-change review

Baseline: `262811616f9a2415c5135c23b74597033d788120`, branch `codex/figma-camera-flow`.

Two independent reviewers ran with no conversation history, using the adversarial-general and edge-case-hunter skills on the same saved 1,722-line source/spec diff and untracked evidence inventory. Neither edited files, ran Gradle, or accessed a device. The edge-case reviewer returned no actionable findings. The adversarial reviewer substantiated three tooling issues; no introduced application defect was found.

## Findings recorded before fixes

1. **Patch, medium:** `scripts/review-home-viewport.py` now requires new header/care tags and 16dp gaps even for `--phase before`. A correctly fitting baseline APK lacks the tags and must fail classification. Acceptance: retain the previous baseline checks for `before`, with new header/spacing requirements applying only to the updated layout.
2. **Patch, medium:** the focused gallery accepts eight profiles or a missing 200% text scale as `complete=true`, while its document claims nine sizes and 18 cases. Acceptance: fail closed before writing generated artifacts unless there are nine distinct geometries and exactly the required 100% and 200% scales.
3. **Patch, medium:** independently valid captures from different APK digests can appear as one complete matrix. Acceptance: a complete matrix must contain one valid production digest and, when an expected digest is declared, every captured case must match it.

The current saved evidence is not defective: it has all 18 cases, one independently verified installed production digest, and matching human inspection entries. Fix the tooling without re-running unchanged application builds or replacing preserved raw captures.

## Fix verification

All three findings were corrected as tooling-only patches. The offline regression task first produced 7 tests with 6 failures for the stated defects, then 7/0/0 after the fix. The complete `python3 -m unittest discover -s scripts/tests` task passes 32 tests. These use mocked command execution and do not connect to any device. An independent read-only follow-up confirmed the three findings resolved, with no remaining issue in that scoped check.

The current matrix now pins the independently verified production digest in `selection.json`; the generator validates nine distinct profiles, six phones/three tablets, ordered 100%/200% scales and one expected APK before declaring completion. The regenerated gallery has 12 fit/6 designer-required cases, all 18 inspections matched, 84 local links validated and 14 actual-inline-JavaScript filter scenarios passed in an isolated Node VM. The browser visual check remains unavailable because the browser security policy rejected local file navigation; no workaround was used.
