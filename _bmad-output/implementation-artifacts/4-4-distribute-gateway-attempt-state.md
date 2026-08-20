---
baseline_commit: db802a13b84658e5c4cfc1d1107148b6fe573877
---

# Story 4.4: Distribute gateway attempt state

Status: implemented except criterion 4, which is an OPEN OWNER DECISION
(criteria 1-3 and 5-9 verified against a real Redis and a real PostgreSQL)

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
- `assertSingleInstanceState` fails the boot when it exceeds `1`, alongside the
  existing test-mode fence, with a message naming what is per-process and why.
  **This is a self-report, not enforcement** (P-01): each process reads its own
  environment, so two containers each declaring `1` both start and both call the
  provider. Single-instance operation must be enforced in the deployment control
  plane with auditable evidence — that is a precondition of this story, not a
  substitute for it.
- The composition root and `.env.example` state which stores are process-local.
  (Superseded 2026-08-16: `REDIS_URL` is now composed when
  `GATEWAY_STATE_BACKEND=redis`. This section records the interim state as it
  was, not as it is.)

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
4. **⚠ UNRESOLVED — OWNER DECISION REQUIRED. NOT SATISFIED.** "Every key carries
   an expiry no longer than the retention the request's market policy allows, so
   nothing outlives its own deletion obligation."

   TTLs are a fixed 24 hours and are not derived from market policy. Recorded as
   open rather than resolved, because resolving it means choosing between two
   real obligations and that choice is not the implementer's.

   Market retention governs the PHOTO. The identifiers these TTLs bound —
   attempt id, effect id, request id — are coordination values that exist so one
   attempt cannot execute twice (AD-18) and a spent request id cannot be
   replayed (AD-21). The fixture markets carry `retentionDays = 0`. Read
   literally against a zero-day market, this criterion gives those keys a
   zero-second expiry, deleting the at-most-once guard at the moment it is
   needed and making a duplicate provider call — a second upload of the user's
   photo — possible again. **Enforcing the privacy clause literally produces the
   privacy harm.**

   Two readings, and the owner picks:

   - **(a) Retention governs user content only.** Coordination identifiers keep
     the at-most-once horizon; this criterion is amended to say so.
   - **(b) Retention governs everything keyed to a user's action.** The
     at-most-once window shrinks to the retention period, and where retention is
     zero the guarantee is accepted as weaker.

   (b) has a legal dimension this code cannot settle. Whichever is chosen,
   either this criterion or the constants must change: as things stand they
   contradict each other, and no test asserts the criterion. Do not derive the
   value without the decision.
5. A transfer whose bytes live on another instance fails closed with the typed
   taxonomy error rather than being routed to an instance that cannot serve it.
   The bytes are never forwarded between instances to make this work.
6. Redis being unreachable fails closed: the gateway refuses the attempt with a
   typed error and does not fall back to per-process state. A degraded shared
   store must not silently become the unsafe deployment this story removes.
7. Single-instance operation is enforced in the deployment control plane, with
   auditable evidence, until this story lands. A unit test setting
   `APP_INSTANCE_COUNT=2` is not topology evidence.

   **Product idempotency is now shared (2026-08-19).** It was the last protected
   store living in one process: two replicas each accepted the same
   Idempotency-Key and each created a durable meal event. `RedisIdempotencyStore`
   shares the claim; the fence lifts because the reason for it is gone.

   Owner decision on placement: Redis stores the STATUS, the request digest and
   the meal's ID — never the response body. The body is a diary entry carrying
   `userId`, dish names, times and nutrition, and Redis is a coordination store
   whose retention rules were written for identifiers. A replay re-reads the meal
   from Postgres through the controller, so the owner check runs again on that
   path and a reference alone is never authority.
8. `assertSingleInstanceState` is lifted only when 1–7 hold. The lift is a
   deliberate change with the tests above green, not a default that drifts.
9. `contracts/mobile-api/v1` is unchanged. This is an implementation of the
   published behaviour, not a renegotiation of it.

## Tasks / Subtasks

- [x] Define a shared-store port per concern (effects, rate limits, transfer
      state) with the in-memory implementations as the local adapters, so the
      composition root chooses rather than the call sites knowing.
- [x] Implement the Redis adapters. Atomic claim via `SET NX` with expiry;
      rate limits via a counter with a window expiry; transfer state as a
      record carrying the owning instance and no bytes.
- [x] Compose `config.redis.url` in `buildApplication` — the value that is
      currently required and unused.
- [x] Fail closed on an unreachable Redis, with a typed taxonomy error.
- [x] Route or refuse transfers whose bytes are held elsewhere; never ship bytes
      between instances.
- [x] Integration tests against a real Redis: concurrent claim, replay across
      instances, cancellation across instances, rate limit across instances,
      key expiry, and an assertion that no stored value contains payload bytes.
- [x] Lift the instance fence and update `.env.example`, the composition-root
      comment and this story in the same change. The lift requires shared state
      to be COMPLETE, asserted at the composition root from the bindings
      themselves — declaring `redis` is not by itself sufficient.
- [ ] **OPEN:** resolve criterion 4 (TTL vs market retention). Owner decision.

## Dev Notes

- **Scope boundary.** Backend only. No `mobile/` or `web/` file is touched
  (AD-36).
- **G-4 is unaffected.** This story does not enable a production market, accept
  production authentication, or make a production-readiness claim.
- **Not in scope:** the transfer state machine itself. `downstreamDeletionRequested`
  was made truthful under H-02 without changing the state machine; the redesign
  — not marking a transfer complete until the provider boundary accepts the
  payload — is tracked in issue #48 and would change this story's shape if it
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
- [Tracked: issue #49 — this story]
- [Blocked on: issue #48 — transfer state-machine redesign]
- [Source: `_bmad-output/planning-artifacts/architecture/architecture-SuperNutrition-2026-07-26/ARCHITECTURE-SPINE.md`, AD-8, AD-18, AD-21, AD-36]
- [Source: `backend/mobile-api/apps/api/src/main.ts`, `buildApplication`]
- [Source: `backend/mobile-api/apps/api/src/config/test-mode-fence.ts`, `assertSingleInstanceState`]
- [Source: `backend/mobile-api/docs/12-future-scaling-strategy.md`]

## Dev Agent Record

### Agent Model Used

Claude Opus 5

### Completion Notes List

- Interim fence and documentation landed 2026-08-14. Implementation not started.

## Verification against a real Redis (2026-08-16)

Criterion 3 required the concurrent-claim property demonstrated against a real
server, not a fake. Done: Redis 8.10.0, disposable instance on port 6399,
`REDIS_TEST_URL=redis://127.0.0.1:6399 npm test`.

**It immediately found a defect the fake could not.** `createRedisClient`
returned before the socket was writeable while `enableOfflineQueue: false` was
set, so every command issued during the connect window failed with
"Stream isn't writeable" — surfaced as `RedisUnavailableError`. The first
requests after every boot would have been refused as an outage that was not
happening. The fake has no socket, so it has no window in which there is not one.
Fixed by connecting explicitly (`lazyConnect` + `await connect()`).

Two further defects found in the same pass:

- The Redis client was never released on shutdown, so a held socket kept the
  event loop alive and a "stopped" process would not exit. The comment beside
  the database handle warns against exactly this.
- The concurrency test raced two claimants, which a broken implementation can
  win by luck. Now twenty, alternating across two connections, none awaited
  early.

Added alongside: an explicit test that the `effect` and `request-id` keyspaces
cannot collide (AD-21 replay IDs vs AD-18 transfer effects sharing one server),
and that an unreachable Redis is refused at connect time so a misconfigured
deployment fails to boot rather than serving and refusing every request.

Suite: 1097 passing / 1 skipped with `REDIS_TEST_URL` set; 1092 / 6 without.

`GATEWAY_STATE_BACKEND` still defaults to `memory`. The default is deliberately
unchanged — flipping it would break every deployment without a Redis — but the
`redis` path is no longer an unverified one.

## Idempotency release path — defect found by route-level retry test (2026-08-20)

Criterion 7's "a failed creation can retry" was covered only against
`InMemoryIdempotencyStore`. Adding the same assertion at route level against the
shared store found a defect in `createRedisClient` that no unit test could:

```ts
const raw = client as unknown as { …; del(key: string): Promise<number> };
return Object.assign(client as unknown as RedisLike, {
  del: (key: string): Promise<number> => raw.del(key),   // raw IS client
});
```

`Object.assign` wrote the wrapper onto the object it wrapped, so `del` called
itself until the stack gave out. Every `RedisIdempotencyStore.fail()` therefore
threw, and `withIdempotency`'s catch path replaced the caller's `AppError` with
a 500 — a client sending an invalid meal with an Idempotency-Key got "internal
error" and the key stayed claimed for 24 hours. The in-memory adapter has no
such wrapper, so the whole existing suite was green.

Fixed by not re-adding `del`; ioredis's own method already matches the port.
Mutation-checked: restoring the self-reference fails exactly the new test and
nothing else.

Coverage added, both stores now asserted at route level:
- a failed creation releases its key and a later request may reuse it
- two different users may hold the same unscoped client key concurrently

Suite: 1175 passing / 3 skipped with `REDIS_TEST_URL` and `DATABASE_TEST_URL`
set; 1154 / 24 without. `./quality-check` green.

Criterion 4 (TTL vs market retention) remains an OPEN OWNER DECISION and is
deliberately not resolved here.
