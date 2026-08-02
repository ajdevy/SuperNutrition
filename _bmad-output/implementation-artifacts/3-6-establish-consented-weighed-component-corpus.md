---
baseline_commit: d1357b4f
story_key: 3-6-establish-consented-weighed-component-corpus
---

# Story 3.6: Establish the consented weighed component corpus

Status: done

## Story

As an evaluation owner,
I want a consented, tared, leakage-resistant component corpus,
so that grams and visible ingredients have valid ground truth.

## Acceptance Criteria

1. A scored case records only opaque consent/reference identifiers, scrubbed
   photo digests, capture/device metadata, scale identity and resolution,
   calibration verification, empty-vessel tare, every visible component's
   independent mass reading, final plate mass, taxonomy, and QC status. Raw
   photo paths, EXIF/GPS, account identifiers, and free-text personal data are
   rejected.
2. Component annotations cover visibly separable food only and contain a
   normalized box, mask artifact digest, canonical label, and measured mass.
   Hidden, occluded, unknown, or inseparably mixed content is represented by a
   typed non-evaluable reason and cannot silently become a scored component.
3. Train, calibration, and held-out partitions are leakage-resistant across
   participant, meal, recipe, device, venue, and capture-session groups. The
   validator rejects a group appearing in multiple partitions and rejects
   missing group declarations.
4. External datasets are attributed training/benchmark-design inputs only;
   they are never bundled in the app or accepted as product corpus evidence.
   Nutrition5k is represented only by a license-reviewed metadata declaration,
   never by raw archive files.

## Scope and sequencing

This story freezes the language-neutral corpus manifest and an offline,
dependency-free validator. It does not collect human meals, store raw photos,
train a model, select LiteRT/ONNX/Core ML, or close the signed G-3 gate. E3.7
and E3.8 consume the validated corpus; E3.9 owns staged progress and review.
No checked-in manifest contains real participant data or claims that a corpus
has been collected. Under the approved 2026-08-02 post-release scope
amendment, the actual consented pilot is a separate post-release upload/consent
feature and is not a pre-release Epic 3 completion condition.

## Tasks / Subtasks

- [x] Define the versioned corpus manifest and privacy-safe field allowlist (AC: 1–4)
  - [x] Publish the JSON schema with opaque references, SHA-256 photo/mask digests,
    tared scale records, explicit mass states, and external-source boundaries.
  - [x] Reject raw paths, identity fields, unknown keys, invalid digests, and
    withdrawn or failed-consent cases.
- [x] Implement the dependency-free validator (AC: 1–4)
  - [x] Validate mask/box geometry, component-to-scale reconciliation,
    visible/non-evaluable semantics, and one-to-one measured readings.
  - [x] Validate participant/meal/recipe/device/venue/session split isolation.
  - [x] Emit a redacted summary suitable for local evidence; never echo labels,
    paths, photo bytes, or participant references in errors.
  - [x] Add a strict `--require-g3` mode requiring the 300-case pilot minimum
    and train/calibration/held-out partitions.
- [x] Add red/green validator tests (AC: 1–4)
  - [x] Cover a valid measured case, privacy rejection, missing scale reading,
    visible-mask requirement, explicit non-evaluable component, failed scale
    calibration, dataset bundling, and cross-partition leakage.
- [x] Validate the story slice
  - [x] Run the focused Python test suite, `git diff --check`, and `graphify update .`.
  - [x] Record that no collected corpus or G-3 model evidence exists until a
    consented weighed dataset and Product/Engineering sign-off are supplied.
- [x] Record the consented pilot and benchmark binding as a post-release
  handoff; do not fabricate a corpus or G-3 manifest before the separate
  upload/consent feature collects it (AC: 1–4).

## Developer Guardrails

- Keep this research/evaluation tooling outside product runtime graphs. Do not
  add a model runtime, network upload, cloud dataset download, database, or
  production dependency.
- Use digests and opaque references only. Never write a raw photo path, EXIF,
  GPS, participant/account identifier, or free-text note into the manifest.
- A pixel mask is evidence of visible footprint, not a scale. Component grams
  must come from a tared, calibrated reading; hidden/occluded/mixed items must
  be `not_evaluable` or abstained.
- A measured corpus record is ground truth for evaluation, not a permission to
  display measured facts in the app. E3.5 v2 output remains estimated/ranged.
- Preserve the existing Food-101 and public research evidence as explicitly
  non-acceptance evidence. Do not mark E3.2/E3.3/E3.7/E3.8 done from this tool.

## References

- [Source: `_bmad-output/planning-artifacts/epics.md`, Story E3.6]
- [Source: `_bmad-output/planning-artifacts/architecture/architecture-SuperNutrition-2026-07-26/ARCHITECTURE-SPINE.md`, AD-8, AD-9, AD-11, AD-42]
- [Source: `ingredient-weight-research/README.md`, Consented weighed corpus blueprint]
- [Source: `ingredient-weight-research/fields.yaml`, `consented_corpus_blueprint`]
- [Source: `_bmad-output/implementation-artifacts/g3-benchmark-artifact.md`, Ingredient and plated-weight benchmark]

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

### Completion Notes List

- Added `consented-weighed-corpus.schema.json` and a dependency-free validator
  covering consent, privacy-safe digests, tared measurements, visible-mask
  semantics, QC, and cross-partition leakage.
- Added eight red/green validator tests. No human corpus, raw photos, or model
  evidence was created; those production inputs remain an explicit post-release
  handoff under the approved scope amendment.
- Added `tools/g3-benchmark/corpus-collection-runbook.md` with the capture,
  privacy, partition-isolation, quality-review, and strict-validator handoff
  procedure. The external collection task remains unchecked because no real
  consented cases exist in this checkout.

### Scope amendment note

The schema, validator, strict `--require-g3` gate, and collection runbook close
the pre-release E3.6 implementation. No real consented corpus is claimed.
Collection, quality review, and model binding are explicitly post-release and
remain required before production v2 activation.

### File List

- `tools/g3-benchmark/consented-weighed-corpus.schema.json`
- `tools/g3-benchmark/validate_weighed_corpus.py`
- `tools/g3-benchmark/test_validate_weighed_corpus.py`
- `tools/g3-benchmark/corpus-collection-runbook.md`
- `ingredient-weight-research/README.md`
