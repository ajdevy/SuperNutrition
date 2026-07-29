# ADR 0002 — Two-layer boundary enforcement

**Status:** accepted (2026-07-29)
**Context:** AD-1, AD-8, AD-32, AD-36, docs/04 §4.9

## Context

Stage 1 specified the layer rules and stated plainly that "every rule above is
worth exactly as much as its enforcement". Implementing them revealed that one
mechanism is not enough.

`eslint-plugin-boundaries` needs to resolve each import specifier to classify
it. Configured with `eslint-import-resolver-typescript` it correctly rejects:

- a domain file importing `@supernutrition/database` (value or type import)
- a gateway file importing the database package
- a module reaching into another module's internals by relative path

It did **not** flag a relative, type-only import written with a NodeNext `.js`
extension. That is the most common shape of cross-layer reference in this
codebase, so the gap sits exactly where erosion would go unnoticed.

Separately, `no-restricted-imports` glob patterns do not match the leading dot
segments of `../../../mobile/x`: minimatch excludes dot segments, so the glob
silently permits the exact escape it exists to block. And a later flat-config
block _replaces_ a rule's options rather than merging them, so re-declaring
`no-restricted-imports` for `apps/api/**` had been dropping the product-boundary
patterns for most of the product.

## Decision

Enforce twice, with different failure modes.

1. **ESLint** — fast feedback in the editor and on save. Uses the v7
   `boundaries/dependencies` policies plus regex-based `no-restricted-imports`.
2. **`apps/api/src/architecture-boundaries.test.ts`** — authoritative. Reads
   import specifiers as text, normalizes the `.js` suffix, classifies both ends
   by path, and asserts the allowed edges. It needs no resolver, so no import
   kind can bypass it.

The test was verified against deliberate violations before being committed: it
caught all of them, including the one ESLint missed.

## Consequences

Two places to update when a layer rule changes. Accepted: the alternative is one
mechanism with a known hole, and AD-32 requires quality controls that test
themselves.
