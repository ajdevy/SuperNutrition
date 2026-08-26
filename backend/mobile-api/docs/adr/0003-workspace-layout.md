# ADR 0003 — npm workspaces inside the product root

**Status:** accepted (2026-07-29)
**Context:** AD-26, AD-36, docs/03 §3.1

## Decision

`backend/mobile-api/package.json` declares workspaces for `packages/shared`,
`packages/contracts`, `packages/database`, and `apps/api`. TypeScript project
references mirror the graph.

## Why not a flat `src/`

The spine's structural seed sketches `backend/mobile-api/src/`. A flat layout
puts Drizzle in the same dependency graph as the domain layer, which makes
"domain imports no ORM" a convention rather than a resolvable fact. The
`packages/` split lets the domain layer's package depend on nothing but the
shared kernel.

## AD-26 compliance

The workspace is entirely inside one product root. There is no root or
cross-product workspace, no hoisting beyond this directory, and `.npmrc` pins
the cache to `./.npm-cache`. Each product still installs, compiles, and tests
from its own directory without either other product's dependencies.

`product-inventory.toml` enumerates the real paths, superseding the seed sketch.
