---
baseline_commit: db802a13b84658e5c4cfc1d1107148b6fe573877
---

# Story 4.4: Distribute gateway attempt state

Status: proposed

## Story

As a backend owner,
I want the gateway's replay, effect, rate-limit and transfer state to be shared
across instances,
so that the service can be run with more than one replica without one attempt
reaching a provider twice.

## Context

Split out of handover-3 finding **H-07**. It is not a defect in the current code
— every store behaves correctly — it is a limitation of a composition that only
holds for one process, and it was neither declared nor enforced.

`REDIS_URL` is required by `apps/api/src/config/env.schema.ts` and validated at
boot, and `apps/api/src/config/index.ts` maps it to `config.redis.url`. Nothing
reads it. `buildApplication` composes `InMemoryPayloadSource`,
`TransferStateTracker`, `InMemoryRateLimiter` and two `InMemoryEffectStore`s, all
of which live in the process that built them.

So a required configuration value advertised a shared backing store the service
did not have. Read at face value, that says the deployment is safe to replicate.
It is not:

| state | per-process consequence of a second replica |
| --- | --- |
| request IDs (`requestIds`) | a replayed request ID is unspent on the other instance, so AD-21's replay window covers one instance and not the deployment |
| transfer effects (`effects`) | an effect claimed on A can be claimed again on B — AD-18's at-most-once becomes at-most-once-per-process |
| rate limits (`limiter`) | the effective limit is the configured limit times the replica count |
| transfer state (`transfers`) | an attempt started on A is unknown to B: status and cancellation answer for the wrong instance, or answer `unknown_attempt` for an attempt that exists |
| payload bytes (`payloads`) | a transfer routed to the instance that does not hold the bytes cannot complete |

The user-visible failure is a photo sent to a provider twice for one attempt the
user made once — billed twice, processed twice, and dependent on which instance
the retry happened to land on.

## Interim state — already landed (2026-08-14)

The limitation is now declared and enforced rather than assumed, on the same
commit that records this story:

- `APP_INSTANCE_COUNT` is a validated configuration value defaulting to `1`.
- `assertSingleInstanceState` refuses to boot when it exceeds `1`, alongside the
  existing test-mode fence, with a message naming what is per-process and why.
- The composition root and `.env.example` state that `REDIS_URL` is composed
  into nothing and that these stores are process-local.

That closes the silence, not the limitation. This story closes the limitation.

## Acceptance Criteria

1. Request IDs, transfer effects, rate-limit counters, transfer state and
   attempt ownership are shared across instances through Redis. Two instances
   sharing one Redis reach the same decision for the same identifier.
2. **No image bytes are written to Redis, logged, or persisted (AD-8).** Payload
   bytes stay in the process that received them; Redis coordinates identifiers,
   ownership, expiry and cancellation only. A test asserts that no value written
   to Redis contains payload bytes.
3. Claiming an effect is atomic across instances. Two concurrent claims of one
   identifier from different instances produce exactly one provider call
   (AD-18), demonstrated against a real Redis, not a mock.
4. Every key carries an expiry no longer than the retention the request's market
   policy allows, so nothing outlives its own deletion obligation.
5. A transfer whose bytes live on another instance fails closed with the typed
   taxonomy error rather than being routed to an instance that cannot serve it.
   The bytes are never forwarded between instances to make this work.
6. Redis being unreachable fails closed: the gateway refuses the attempt with a
   typed error and does not fall back to per-process state. A degraded shared
   store must not silently become the unsafe deployment this story removes.
7. `assertSingleInstanceState` is lifted only when 1–6 hold. The lift is a
   deliberate change with the tests above green, not a default that drifts.
8. `contracts/mobile-api/v1` is unchanged. This is an implementation of the
   published behaviour, not a renegotiation of it.

## Tasks / Subtasks

- [ ] Define a shared-store port per concern (effects, rate limits, transfer
      state) with the in-memory implementations as the local adapters, so the
      composition root chooses rather than the call sites knowing.
- [ ] Implement the Redis adapters. Atomic claim via `SET NX` with expiry;
      rate limits via a counter with a window expiry; transfer state as a
      record carrying the owning instance and no bytes.
- [ ] Compose `config.redis.url` in `buildApplication` — the value that is
      currently required and unused.
- [ ] Fail closed on an unreachable Redis, with a typed taxonomy error.
- [ ] Route or refuse transfers whose bytes are held elsewhere; never ship bytes
      between instances.
- [ ] Integration tests against a real Redis: concurrent claim, replay across
      instances, cancellation across instances, rate limit across instances,
      key expiry, and an assertion that no stored value contains payload bytes.
- [ ] Lift the instance fence and update `.env.example`, the composition-root
      comment and this story in the same change.

## Dev Notes

- **Scope boundary.** Backend only. No `mobile/` or `web/` file is touched
  (AD-36).
- **G-4 is unaffected.** This story does not enable a production market, accept
  production authentication, or make a production-readiness claim.
- **Not in scope:** the transfer state machine itself. `downstreamDeletionRequested`
  was made truthful under H-02 without changing the state machine; the redesign
  — not marking a transfer complete until the provider boundary accepts the
  payload — is tracked separately and would change this story's shape if it
  landed first. Sequence that decision before implementing task 1.
- **Why not sticky sessions instead.** Pinning an installation to an instance
  would make `payloads` work and leave replay and at-most-once still per-process,
  because a restart or a rebalance moves the pin. It narrows the window rather
  than closing it, and it hides the remaining hole behind an infrastructure
  setting no test can see.

## Interaction-budget attestation

Non-UI story. No tap, scroll, field, choice, consent, default, or navigation
budget changes. No new user-facing default is introduced; the added
configuration is operator-facing.

## References

- [Source: handover-3 finding H-07, PR #44 review comment]
- [Source: `_bmad-output/planning-artifacts/architecture/architecture-SuperNutrition-2026-07-26/ARCHITECTURE-SPINE.md`, AD-8, AD-18, AD-21, AD-36]
- [Source: `backend/mobile-api/apps/api/src/main.ts`, `buildApplication`]
- [Source: `backend/mobile-api/apps/api/src/config/test-mode-fence.ts`, `assertSingleInstanceState`]
- [Source: `backend/mobile-api/docs/12-future-scaling-strategy.md`]

## Dev Agent Record

### Agent Model Used

Claude Opus 5

### Completion Notes List

- Interim fence and documentation landed 2026-08-14. Implementation not started.
