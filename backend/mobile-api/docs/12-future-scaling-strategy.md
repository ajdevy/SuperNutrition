# 12. Future Scaling Strategy

## 12.1 Position

The MVP target is a single container image, one managed PostgreSQL, one managed
Redis. That is not a placeholder to be apologised for — it is the correct
architecture for a product whose primary KPI is daily meal logging retention and
whose user count is currently zero.

What this document commits to is not a schedule of upgrades. It is a set of
**triggers**: the measurement that promotes each change from speculation to
work. Scaling decisions taken on a calendar rather than on a signal produce
systems that are simultaneously over-built and wrong.

The design property that makes this affordable is that every stage below is a
change of *adapter or deployment topology*, not of domain code. Ports exist so
that the answer to "can we scale this later" is yes without a rewrite.

## 12.2 Stage 0 — MVP (now)

```
[client] → [api container × N] → [PostgreSQL primary]
                              → [Redis]
                              → [OpenRouter]
```

Stateless application containers behind a load balancer, scaled horizontally.
Session state, rate limits, nonces, and idempotency records live in Redis;
nothing lives in a container.

> **As of 2026-08-16 this shape is implemented, but it is not the default.**
>
> `GATEWAY_STATE_BACKEND=redis` composes the shared stores — replay detection,
> at-most-once transfer effects, rate limits, and attempt ownership and
> cancellation — and lifts the single-instance refusal. Story 4.4
> (`_bmad-output/implementation-artifacts/4-4-distribute-gateway-attempt-state.md`,
> backlog **B-49**, issue #49) is that work, and it has landed.
>
> The default remains `memory`, where all of that state is per-process. There
> `N` above is 1 and the service refuses to start when `APP_INSTANCE_COUNT` says
> otherwise, because a second replica would share none of it and one attempt
> could reach a provider twice. The refusal lifts only when every protected
> store has a shared binding — declaring `redis` is not by itself sufficient,
> and the composition root asserts the difference.
>
> One line above stays untrue by design even with the shared backend:
> `nothing lives in a container` does not hold for photo bytes, which are
> process-local and ephemeral and never enter Redis (AD-8). An instance that
> does not hold an attempt's bytes fails closed rather than having them sent to
> it.

Capacity at this shape is comfortably beyond early product-market fit. The
binding constraint will not be the database — it will be provider cost per
inference, which is a commercial question rather than an architectural one.

## 12.3 Triggers and responses

| # | Trigger (measured) | Response | Cost |
| --- | --- | --- | --- |
| 1 | Read load measurably affects write latency | Read replica; route journey, progress, achievements, catalog to it | Replica lag becomes visible; queries must tolerate it |
| 2 | Post-commit handler failure is user-visible, or handler latency affects meal-creation response time | Move the event bus to a durable queue; handlers become workers | Operational surface; handlers are already idempotent, so no logic change |
| 3 | Gateway p99 couples to product API load, or provider spend needs independent scaling, or a residency posture requires regional separation | Extract Surface A as its own deployable | Two artifacts, two pipelines. The module boundary already exists |
| 4 | `meal_events` index maintenance becomes the constraint | Partition by month on `occurred_at` | Migration on the largest table; do it before it is urgent |
| 5 | `SUM(xp_transactions)` appears in slow-query logs | Materialize XP balance, keeping the ledger as source of truth | A cache to invalidate |
| 6 | Catalog search latency exceeds budget on the server path | Dedicated search index | Another system to operate. Note the client's local package makes this unlikely |
| 7 | A market's residency posture forbids processing in the current region | Regional gateway deployment; `MarketPolicyResolver` already routes by `processingRegion` | Multi-region operations; the resolver does not change |
| 8 | Provider cost per inference dominates unit economics | Tune the model roster; measure second-pass yield; consider a cheaper pass-1 model | Configuration change only — no code change (document 7) |

## 12.4 Data growth

Rough per-active-user figures, for planning rather than precision:

| Table | Rate | Notes |
| --- | --- | --- |
| `meal_events` | ~3/day | ~1 KB with items |
| `meal_items` | ~6/day | |
| `nutrition_snapshots` | ~3/day | |
| `inference_evidence` | ~1/day | Photo-sourced meals only |
| `daily_progress` | 1/day | Bounded; rebuildable |
| `inference_runs` | ~1–2/day | **Retained 90 days**, then rolled up |
| `coach_messages` | ~1/day | |
| `xp_transactions` | ~4/day | |

At 100k active users this is on the order of 10 GB/year of durable meal data —
comfortably within a single well-indexed PostgreSQL instance for years. The
growth question is answered by trigger 4, not by a distributed database.

`inference_runs` is the only table with a retention policy from day one, because
it is the only one that grows with *operations* rather than with user-owned
history, and because its value decays: 90-day granular data answers "which model
is better"; three-year-old rows answer nothing that a rollup cannot.

## 12.5 What scaling must not change

Some properties are not performance trade-offs, and no scaling stage may relax
them:

- **No durable raw photos** (AD-8). A cache, a queue, or a replica that holds
  payload bytes violates this exactly as much as a table would. Trigger 2's
  queue carries identifiers, never payloads.
- **Nutrition authority stays with the catalog** (AD-7, AD-19). No amount of
  read pressure justifies a denormalized nutrient number without provenance.
- **`inference_runs` gains no user foreign key** (§5.4). Not for analytics, not
  for cost attribution, not for debugging. If per-user cost data is genuinely
  needed, it is a separate G-6-gated design.
- **Telemetry stays allowlisted** (AD-14). Sampling and rollups reduce volume;
  they never widen the field set.
- **Markets stay fail-closed** (AD-41). A regional deployment adds capacity, not
  permission.
- **Product boundaries hold** (AD-36). A shared cache, a shared queue, or a
  shared deployment across product roots is a boundary violation regardless of
  the efficiency argument.

## 12.6 Costs of extraction, recorded now

If trigger 3 fires and Surface A is extracted, these are the costs — recorded
while the decision is cheap to reason about rather than during the incident that
forces it:

- Auth becomes a shared concern. Either a shared library with synchronized key
  material, or the gateway verifies tokens the product API issues.
- Two deployment pipelines, two artifacts, two rollback paths, two on-call
  surfaces.
- Consent receipts are written by the gateway and read by the product API — a
  cross-service read, or a copy.
- The contract between them becomes a real network contract with versioning.

None of these is prohibitive. All of them are avoidable for as long as trigger 3
has not fired, which is the argument for not extracting on day one.

## 12.7 Operational maturity, in order

Each stage assumes the previous one. Skipping ahead produces dashboards nobody
reads and alerts nobody trusts.

1. **Now** — structured allowlisted logs, correlation IDs, health and readiness
   probes, error tracking.
2. **Before production remote** — the full AD-24 checklist (§9.11): SLOs, alerts,
   cost dashboards, runbooks, kill switch, recorded rollback drill.
3. **At scale** — distributed tracing across gateway and provider calls,
   per-model cost attribution at the environment level, capacity planning from
   observed growth.
4. **Multi-region** — only if trigger 7 fires. Regional data-flow inventory and
   per-region deletion evidence become mandatory, and both are substantial work.

## 12.8 The honest summary

The most likely scaling story for this product is that none of triggers 1–7
fires for a long time, and that the real constraint is provider cost per
inference — trigger 8, which is a configuration change.

The second most likely story is that trigger 2 fires first, because a
gamification handler failing after commit is user-visible in a way that a slow
query is not.

Planning for either is cheap. Building for both now is not.
