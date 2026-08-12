# Consented weighed corpus collection runbook

Status: collection package ready; no human corpus is present.

This runbook produces the redacted manifest required by E3.6. It does not
store raw photos, masks, EXIF/GPS, participant identity, account identifiers,
or free-text personal data in the repository.

## Exit target

Deliver a quality-reviewed manifest accepted by:

```bash
python3 tools/g3-benchmark/validate_weighed_corpus.py \
  --require-g3 /path/to/redacted-corpus.json
```

The manifest must contain at least 300 consented cases and all three isolated
partitions: `train`, `calibration`, and `held-out`. The partition allocation
must be approved by the evaluation owner before scoring; a practical starting
allocation is 70/15/15 (210/45/45 cases), subject to review after capture.

After a model has been evaluated, score its redacted prediction handoff with
the fail-closed evaluator:

```bash
python3 tools/g3-benchmark/evaluate_consented_corpus.py \
  --require-g3 \
  --manifest /path/to/redacted-corpus.json \
  --predictions /path/to/redacted-predictions.json \
  --output /path/to/mass-quality-evidence.json
```

The prediction handoff must use
[`g3-corpus-predictions.schema.json`](g3-corpus-predictions.schema.json) and
`supernutrition.g3-corpus-predictions.v1`: an opaque model ID, model SHA-256,
calibration reference, and one redacted case entry per corpus case. Each
estimated component carries a canonical label, normalized box, confidence,
and either measured prediction grams or an explicit mass abstention. The
evaluator performs one-to-one label-plus-IoU matching, computes ingredient
macro/case F1, component and total-mass MAE/relative MAE/p90/bias, coverage and
abstentions, and partition/device/lighting/angle slices. Missing cases,
unknown case IDs, duplicate predictions, raw paths, invalid boxes, or missing
model provenance fail closed. A `pass` is not a production signoff: the
result still needs the model, legal, Product, Engineering, and rollback
approvals described by the component-model manifest. The output binds the
corpus, prediction, and optional policy file SHA-256 digests so the score can
be reviewed against immutable inputs.

The default quantitative policy mirrors the current benchmark policy:
ingredient macro F1 `>=0.85`, per-case F1 `>=0.80`, component and total relative
MAE `<=0.15`, and component and total p90 absolute error `<=50 g`. A stricter
pilot policy may be supplied with `--policy`; the policy used is copied into
the resulting evidence JSON.

## Capture protocol for each case

1. Create opaque references for the case, consent record, capture session,
   device, venue, recipe/meal group, and scale. Keep the reference map outside
   the repository.
2. Confirm active consent covers model training/evaluation and derived masks;
   record the retention policy version and withdrawal status.
3. Calibrate the scale and record its resolution. Tare the empty vessel.
4. Weigh every visibly separable component before plating. Record the final
   plated mass and reconciliation tolerance/delta in grams.
5. Capture at least one top-down and one oblique overview where practical,
   with a reference object when the approved protocol requires it. Scrub
   metadata before creating the photo digest.
6. Create a mask and normalized box for every visible separable component.
   Record canonical taxonomy labels only. Mark hidden, occluded, unknown, or
   inseparably mixed content as non-evaluable with its typed reason.
7. Run independent primary and secondary identity/mask review, then adjudicate
   disagreements. Record privacy review, duplicate review, mask agreement, and
   identity agreement.
8. Assign the case to one partition. A participant, meal, recipe, device,
   venue, or capture-session group must never occur in more than one partition.

## Manifest rules

The checked-in deliverable contains only:

- opaque `caseId`, consent, annotation, device, scale, annotator, and split
  references;
- SHA-256 digests for scrubbed photos and masks;
- dimensions, capture conditions, boxes, mask formats, labels, and measured
  gram values;
- explicit `not_evaluable` states for content that cannot be scored.

Never add raw paths, cloud URLs, EXIF/GPS, names, emails, account IDs, faces,
free-text participant notes, or raw archive files. External datasets may be
listed only as reviewed training or benchmark-design sources and must have
`bundledInApp: false` and `acceptanceEvidence: false`.

## Quality-review gate

Before binding the manifest to E3.7/E3.8, the evaluation owner verifies:

- consent and retention status are active for every case;
- every visible component has a mask, normalized box, label, and matching
  independent scale reading;
- every non-evaluable component has no visible-region or invented-mass data;
- scale calibration passed and component readings reconcile with the final
  plate within the declared tolerance;
- primary/secondary review and adjudication are complete;
- duplicate and privacy reviews passed;
- all six leakage groups are isolated across partitions;
- the strict validator passes without suppressions or task filters.

## Handoff

The collection owner supplies the redacted manifest and a separate evidence
note containing the approved consent/retention reference, protocol version,
partition allocation, quality-review summary, and digest inventory. The raw
capture store remains access-controlled and outside this repository.

No model may be selected, activated, or signed for G-3 from a partial,
synthetic, public-photo-only, or validator-schema-only manifest.
