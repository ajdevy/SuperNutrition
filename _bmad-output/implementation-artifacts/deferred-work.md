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
