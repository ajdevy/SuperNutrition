# inference-evidence/v2

Immutable, language-neutral, data-only contract release for visible food
component evidence and honest mass states. A component identifies only what is
visible; its mass is either an estimated finite gram range with stated
calibration assumptions, typed `not-evaluable`, or typed `abstained`. It never
means a measured or exact mass.

Inputs are a closed photo-or-text discriminator. Photo input reuses the opaque,
attempt-bound `PhotoRef`; text is future-safe input only. Results and telemetry
never contain raw photos, photo paths, user text, masks, model bytes, transport
bodies, catalog bindings, or nutrition values. Text outputs cannot carry an
estimated mass.

This v2 release deliberately coexists with immutable
`photo-inference-engine/v1`. They use separate ports; no v1 adapter implicitly
advertises v2 support. This release selects no model, runtime, public dataset,
or application download policy.

Canonical fixtures cover ready capability, photo estimates, photo and text
`not-evaluable` evidence, abstention, failure, and allowlisted telemetry.
Unknown fields are ignored by Kotlin decoders, while checked-in fixtures are
strictly typed and minified.

The digest in `compatibility-matrix.toml` is SHA-256 over the sorted release
documentation, schema, compatibility matrix, and canonical JSON fixture files.
While hashing the matrix, its digest value is replaced by the literal
`<release-digest>` to avoid a self-reference.

Validate both releases with:

```text
scripts/verify-inference-contract.sh
```
