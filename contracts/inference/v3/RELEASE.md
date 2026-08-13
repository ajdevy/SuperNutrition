# inference-evidence/v3

Immutable, language-neutral, data-only contract release combining the
mainline photo evidence shape with Epic 7 typed identification.

Photo results carry visible component evidence: localized candidate identity,
calibrated confidence, visibility and ambiguity, an optional normalized region,
and exactly one typed mass state. A mass range is estimated evidence, never a
measurement or nutrition value.

Text results carry one source-anchored item group per detected food item. Each
group contains ranked candidates, confidence, assumptions, provenance, and the
character span of the user-authored description it came from. Text never
reports a visual region or an estimated mass; its mass semantics are
`not-evaluable-by-definition`.

Both modalities use one closed request discriminator and one capability
envelope. A request carrying neither or both payloads fails closed. Free text,
raw photos, transport bodies, catalog bindings, and nutrition values are never
part of telemetry or result evidence.

## Why v3

`inference-evidence/v2` is immutable and correctly owns the mainline photo
component/mass evidence. Epic 7's ranked text groups and source spans are a
different result shape, so adding them to v2 would change a released contract.
v3 is the explicit major release that carries both modality-specific result
shapes while preserving v2 and the original `photo-inference-engine/v1`.

## Compatibility

Producers and consumers migrate release by release. v1 and v2 remain published
and verifiable; no adapter may advertise v3 support implicitly. The compatibility
matrix records the supported v3 pairs and requires separate ports for v2/v3
coexistence.
