# mobile-api/v1

Immutable, language-neutral, data-only mobile API contract release.

The release digest in `compatibility-matrix.toml` is SHA-256 over the sorted
release files. Each entry contributes its relative path, a zero byte, and file
bytes. While hashing `compatibility-matrix.toml`, its digest value is replaced
by the literal `<release-digest>` to avoid a self-reference.

Validate the release with:

```text
scripts/verify-mobile-api-contract.sh
```
