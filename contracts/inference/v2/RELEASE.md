# inference-engine/v2

Immutable, language-neutral, data-only contract release for the shared Engine
ports, carrying an identification modality. Engines exchange opaque attempt
context and identification evidence only; nutrition values are calculated later
from a confirmed catalog record and are not part of this release.

## Why v2 rather than an edit to v1

`photo-inference-engine/v1` admits exactly one request payload, an opaque
`PhotoRef`, and names the photo in its own `contract_id`. A text engine cannot
satisfy that shape without claiming a photo it does not have. v1 is a released,
digest-pinned artifact and its own `incompatible_change` rule requires a new
major release, so modality arrives here.

The `contract_id` is modality-neutral, `inference-engine`, because
`photo-inference-engine` cannot name a contract that also accepts text. That
rename is why this is a major release rather than an additive change to v1.

v1 remains published and verifiable. Both releases are declared in the
compatibility matrix; producers and consumers migrate release by release, and v1
is retired only when no implementation remains.

## What changed

**Request carries exactly one modality.** `photo` supplies an opaque `PhotoRef`;
`text` supplies a user-authored description plus the locale it was entered in. A
request carrying neither or both fails closed as a typed contract error rather
than defaulting — an ambiguous request is a defect, not an input.

`inputLocale` is separate from `countryProfileId` because catalog country is
independent of language: a description may be typed in a language the activated
catalog is not written in.

**Results group by detected item.** Text identification returns one candidate
group per detected food item, mirroring the multi-item shape the photo path
already produces. Each group carries the `sourceSpan` of the description it
derived from, which is what lets a user see which words produced which item and
correct a bad split with visible provenance. An engine that cannot segment a
description abstains for the whole request rather than returning a confident
subset as if it were the whole meal.

**Free text is not loggable and not telemetry-eligible.** A description can carry
more identifying detail than a food photo — a dish name, a restaurant, an address
typed by mistake — and v1's telemetry allowlist was written for a photo-only
product.

**Telemetry declares its own fields and vocabularies.** The producer's field list
is closed and every bucketed value is enumerated, so a second binding cannot pick
its own wire values. The route travels with the event and a text attempt may not
report `local`: flattening `local-text` at the telemetry boundary would make an
on-device text attempt indistinguishable from an on-device photo attempt, which
is the distinction the route exists to carry.

## What did not change

Capability states, abstention shape, provenance, typed errors, and the rule that
no modality returns calories, macronutrients, or micronutrients are identical to
v1. The safety properties are modality-independent, which is why modality belongs
in the shared contract rather than in a parallel subsystem.

## Canonical fixtures

Both modalities are covered: `request-photo` and `request-text` pin the
discriminator, `candidate-result-photo` and `candidate-result-text` pin the
grouped result shape, and `capability-ready`, `abstention-result`,
`failure-result`, and `telemetry-event` pin the shapes shared with v1.

The `sourceSpan` offsets in `candidate-result-text` index `request-text`'s
description exactly, so the two fixtures are checkable against each other rather
than merely plausible.

## Digest

The digest in `compatibility-matrix.toml` is SHA-256 over the sorted release
documentation, schema, compatibility matrix, and canonical JSON fixture files.
While hashing the matrix, its digest value is replaced by the literal
`<release-digest>` to avoid a self-reference.

A digest recorded inside the bytes it describes cannot detect a self-consistent
rewrite. Immutability is enforced across revisions rather than by this field
alone.
