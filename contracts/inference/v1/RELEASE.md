# photo-inference-engine/v1

Immutable, language-neutral, data-only contract release for the shared Engine
ports. Engines exchange opaque attempt/photo context and identification
evidence only; nutrition values are calculated later from a confirmed catalog
record and are not part of this release. The release also defines a controlled
operational telemetry allowlist containing only route, capability, outcome,
duration, and resource buckets; raw photos, labels, paths, and transport bodies
are excluded.

Canonical fixtures cover a ready capability, candidate result, abstention,
failure, and telemetry event. Unknown fields are ignored by the Kotlin decoder,
while the checked-in release fixtures are strictly typed and minified.

The digest in `compatibility-matrix.toml` is SHA-256 over the sorted release
schema, compatibility matrix, and canonical JSON fixture files. While hashing
the matrix, its digest value is replaced by the literal `<release-digest>` to
avoid a self-reference.

Validate with:

```text
scripts/verify-inference-contract.sh
```
