# interaction-policy/v1

Immutable, language-neutral interaction-default policy release.

The digest in `compatibility.toml` is SHA-256 over the sorted release files,
using each relative path, a zero byte, and file bytes. The digest field is
replaced with `<release-digest>` while hashing the compatibility file.

Validate with `scripts/verify-interaction-policy-contract.sh`.
