---
title: Sprint Change Proposal — Defer consented v2 G3 corpus to post-release capture
status: approved-pre-release-scope
date: 2026-08-02
scope_classification: major
approved_by: Anastasia (direct product instruction in this task)
engineering_decision: production-v2 activation remains no-go until the post-release evidence bundle exists
---

# Post-release v2 G3 corpus scope amendment

## Decision

The pre-release Epic 3 implementation may finish without inventing a consented
300+ meal corpus or activating a production component/mass runtime. The real
consented weighed pilot will be collected after release through a separate
photo-upload and consent feature. Public datasets remain research inputs only.

This changes delivery sequencing, not the safety or production bar:

- the app remains local/manual and dish-only where no approved component runtime
  is available;
- no public image, label, weight, or model is presented as SuperNutrition
  product evidence;
- no `ComponentModelManifest` with `activation = production` may be created
  until its complete approval, corpus, benchmark, legal, and rollback refs
  exist;
- Product/Engineering G3 remains `NO-GO` for production v2 activation until the
  post-release bundle is complete.

## Epic 3 pre-release closure boundary

Epic 3 is considered implementation-complete for the pre-release scope when
the following repository-owned outcomes are complete and verified:

1. v1/v2 contracts, compatibility fixtures, validators, and fail-closed
   activation/rollback rules pass their quality gates;
2. Android and iOS adapter ports, local-first routing, and E3.9 coordinator/UI
   lifecycle seams are implemented and tested;
3. physical host/bridge correctness evidence is recorded separately from
   model-quality evidence;
4. public research datasets and candidate screens are downloaded or referenced
   with checksums, metrics, license/domain caveats, and explicit non-acceptance
   decisions; and
5. production hosts remain unbound to a component runtime, with no fabricated
   manifest or G3 claim.

The pre-release boundary does not claim component identity quality, gram
accuracy, production model clearance, or user-consented corpus completion.

## Deferred post-release work

Create a separate post-release feature and story for:

- explicit user consent and upload of opted-in meal photos;
- opaque participant/withdrawal references and privacy-safe retention/deletion;
- calibrated-scale capture with vessel tare, per-visible-component readings,
  final-plate reconciliation, and QC review;
- masks/boxes and visibly separable/non-evaluable annotations;
- participant/meal/recipe/device/venue/session-isolated train, calibration, and
  held-out partitions; and
- the strict `--require-g3` corpus manifest, selected commercial model,
  physical Android/iOS benchmark, rollback evidence, and replacement Product/
  Engineering approval.

The existing schema and collection runbook remain the implementation handoff;
they are not evidence that the deferred corpus exists.

## Impact on story status

- E3.6 closes its repository-owned schema/validator/runbook slice; the checked
  collection task moves to the post-release feature.
- E3.7/E3.8 close their manifest, activation, and independent adapter seams;
  model selection and physical component/mass G3 tasks move to the same
  post-release gate.
- E3.9 closes its coordinator/UI/lifecycle seam; production binding remains
  behind the manifest gate as already recorded.
- The Epic 3 audit must retain the production G3 no-go and list the deferred
  corpus/model/device bundle as the release follow-up, rather than calling
  public research data a substitute.

## Authoritative references

- `_bmad-output/implementation-artifacts/epic-3-closure-audit.md`
- `_bmad-output/implementation-artifacts/g3-signoff-decision-2026-08-02.md`
- `_bmad-output/implementation-artifacts/3-6-establish-consented-weighed-component-corpus.md`
- `_bmad-output/implementation-artifacts/3-7-spike-local-android-visible-component-and-mass-inference.md`
- `_bmad-output/implementation-artifacts/3-8-spike-local-ios-visible-component-and-mass-inference.md`
- `tools/g3-benchmark/consented-weighed-corpus.schema.json`
- `tools/g3-benchmark/corpus-collection-runbook.md`
