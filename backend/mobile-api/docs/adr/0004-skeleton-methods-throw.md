# ADR 0004 — Skeleton methods throw rather than return empty

**Status:** accepted (2026-07-29)

## Decision

Every unimplemented repository, provider, and composition-root path throws
`NotImplementedError` naming the operation, rather than returning `null`, `[]`,
or a resolved empty `Result`.

## Why

A skeleton that returns plausible empty values is indistinguishable at runtime
from a working system with no data. A meal list that returns `[]` because the
query is missing looks exactly like a new user's meal list. Stage 3 would then
be integrating against a system that appears to work.

Throwing makes the boundary between "specified" and "implemented" visible at the
first call, and the tests assert it: `repositories.test.ts` proves each method
rejects rather than resolving.

## Consequences

The composition root cannot boot, which is correct for Stage 2 — there is no
business logic to serve. `main.ts` validates configuration and asserts the
`TEST_MODE` fence first, so the parts that _are_ implemented still fail in the
right order.
