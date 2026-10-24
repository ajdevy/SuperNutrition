## Deferred from: code review (2026-07-31)

- Dedicated Epic 3 Android physical local-inference adapter evidence now passes `4/4`, and the packaged UI/capture suites pass `15/15` each. Signed v2 G-3 evidence remains blocked until a selected component runtime/model, consented corpus, and clean component-quality scenario exist; the current Food-101 run is semantic dish-only research evidence.
- Physical iOS fixture/Xcode acceptance is now verified on the paired iPhone
  using the configured local Apple development account; the remaining
  production blocker is the signed G-3 runtime/model benchmark.
- A preliminary, unsigned G-3 benchmark now records the candidate matrix and
  proposed resource budgets in
  `_bmad-output/implementation-artifacts/g3-benchmark-artifact.md`. It does
  not close G-3: iOS energy evidence, tier/accuracy/rollback decisions, and
  owner signatures remain required.
- Concrete local-runtime production wiring remains gated by G-3 and must not be invented in the adapter stories.

- source_spec: `_bmad-output/implementation-artifacts/spec-home-rewards-spacing.md`
  summary: Production iOS needs a separate Home/Rewards navigation integration before its acceptance-only Swift spacing can ship there.
  evidence: SuperNutritionApp.swift already hosts only the photo-analysis controller; PhotoAnalysisHostSupport.swift contains the existing Home/Rewards surfaces in the UIAcceptance target. The frozen spacing scope preserves that architecture.

- source_spec: `_bmad-output/implementation-artifacts/spec-review-state-recreation.md`
  summary: Ignored ReplacePhoto or ScanAgain in Backgrounded also clears the retained live review cache.
  evidence: Blind Hunter traced unchanged Backgrounded transition plus unconditional cache invalidation; follow-up authorized broad interaction fix is tracked as N-28 in spec-review-ignored-invalidation.md.

- source_spec: `_bmad-output/implementation-artifacts/spec-profile-meal-history.md`
  summary: Configure typed Detekt KMP analysis to distinguish common expect declarations from platform actuals.
  evidence: The unchanged analyzer setup combines commonMain and jvmMain into one analysis module; diagnostic detektMainJvm reports three expect/actual module errors for currentHistoryReferenceDay, while real JVM/Android/iOS-arm64 compilation and executable tests pass. See meal-history-verification-2026-10-23/detekt-diagnostic.gradle and the verification report. No rule, baseline, task, or source-set exclusion was added.

- source_spec: `_bmad-output/implementation-artifacts/spec-ios-host-target-selection.md`
  summary: Package shared Compose resources into the iOS host and verify Home/Profile/Rewards on a supported runtime.
  evidence: The fresh unsigned arm64 app contains nine files and no Compose resource directory or Fraunces/Nunito fonts; see emulator-verification-2026-10-23/ios-app-package.json and IOS-PACKAGE-1.

- source_spec: `_bmad-output/implementation-artifacts/spec-home-capybara-grounding.md`
  summary: Diagnostic raw UI branch coverage is66.25%; its earlier classification as a declared JVM gate failure was corrected on2026-10-23.
  evidence: REPORT-COVERAGE-1 confirms the pre-existing UI denominator is native-equivalent. The raw report has1274/1923 covered branches; new garden geometry is24/24. Full policy remains blocked by expired mandatory physical Android evidence, not this numerical observation. No policy or threshold changed.

- source_spec: `spec-mvp-local-meal-diary.md`
  summary: Implement correction of grounded-only historical meals after G-2 grounding contracts are approved.
  evidence: ProfileSelectedMeal permits editing only SavedMeal records; grounded-only entries were already read-only before this story. Converting them must preserve unknown capture time and invalidate obsolete nutrient proof without inventing a new catalog binding.
