# ADR 0001 — Pinned toolchain tuple

**Status:** accepted (2026-07-29)
**Context:** AD-16, AD-26, AD-37

## Decision

One reviewed tuple, pinned exactly, installed frozen from this product's own
manifest into a product-local cache:

| Component                         | Version                                 |
| --------------------------------- | --------------------------------------- |
| Node.js                           | >=22.11.0 <25.0.0 (verified on 24.16.0) |
| TypeScript                        | 5.9.3                                   |
| Drizzle ORM / drizzle-kit         | 0.45.2 / 0.31.10                        |
| postgres (driver)                 | 3.4.9                                   |
| Zod                               | 4.4.3                                   |
| Fastify / @fastify/multipart      | 5.10.0 / 10.1.0                         |
| Pino / pino-http                  | 10.3.1 / 11.0.0                         |
| ioredis                           | 5.11.1                                  |
| Vitest / @vitest/coverage-v8      | 4.1.10                                  |
| ESLint / typescript-eslint        | 10.8.0 / 8.65.0                         |
| eslint-plugin-boundaries          | 7.1.0                                   |
| eslint-import-resolver-typescript | 4.4.4                                   |
| Prettier                          | 3.9.6                                   |

## Why TypeScript 5.9.3 and not 7.x

TypeScript 7.0.2 is published, and `typescript-eslint@8.65.0` declares
`typescript >=4.8.4 <6.1.0`. Taking the newest TypeScript would mean dropping
the lint layer that enforces the module boundaries — the tuple would be
individually current and collectively broken. AD-16's point is that these
versions are reviewed together, so the constraint decides the version.

Revisit when `typescript-eslint` supports 7.x.

## Consequences

`save-exact=true` and `engine-strict=true` in `.npmrc`; `cache=./.npm-cache`
keeps installation product-local so parent-directory resolution is impossible
(AD-26). Any upgrade is a dedicated change that re-runs the full gate.
