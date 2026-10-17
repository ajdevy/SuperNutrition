---
title: 'Continuous Rewards backdrop behind bottom tabs'
type: 'bugfix'
created: '2026-10-16'
status: 'done'
route: 'one-shot'
---

# Continuous Rewards backdrop behind bottom tabs

## Intent

**Problem:** Rewards paints a separate light strip behind its floating bottom tab bar because its screen and outer viewport use different backgrounds.

**Approach:** Use the existing shared Rewards screen color for the full Rewards viewport. Preserve the navigation illustration, hit targets, scroll clearance and Home backdrop.

## Suggested Review Order

1. [Main viewport backdrop](../../mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisMainLayout.kt) — Rewards now covers navigation and safe areas.
2. [Existing Rewards visual check](../../mobile/feature/photo-analysis-ui/src/jvmTest/kotlin/com/supernutrition/feature/photoanalysis/PhotoAnalysisLayoutVisualTest.kt) — background continuity straddles the navigation boundary.
3. [Verification and emulator follow-up](rewards-tab-backdrop-2026-10-16.md) — red witnessed, full UI suite green; emulator checks tracked separately.
