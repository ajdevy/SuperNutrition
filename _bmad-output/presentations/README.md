# SuperNutrition BMAD presentation set

The HTML artifacts below are self-contained and can be opened directly from a
checkout. They are evidence/reporting artifacts, not production UI.

- [Bounded fusion — short explainer](bounded-fusion-explainer/index.html)
- [Nutrition5k fusion benchmark](nutrition5k-fusion-benchmark/fusion-vs-no-fusion-nutrition5k.html)
- [Uncertainty-aware dish fusion research](uncertainty-aware-dish-fusion/technical-research-report.html)
- [Epic 3 manual testing](epic-3-manual-testing/epic-3-manual-testing.html)

The short explainer describes the production-safe policy now represented by
`BoundedDishIngredientFusion`: dish priors may add separately labelled
composition hints, but cannot remove or relabel `VisibleComponent` evidence.
