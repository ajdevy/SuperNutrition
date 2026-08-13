# 13. Risks and Tradeoffs

## 13.1 Conflicts between the stage brief and the architecture spine

The brief in `backend/01-architecture.md` and the architecture spine disagree in
four places. Each is resolved above; each resolution is recorded here with its
reasoning, so a later reader can reopen the decision knowingly rather than
discover it as a surprise.

### C-1 — Scope: gateway or product backend

**Brief:** a full product backend — meals, journey, progress, achievements,
coaching, nutrition, USDA.
**Spine:** AD-13 and AD-21 describe `backend/mobile-api` as the first-party
inference gateway; AD-25 states the backend is "the separately deployed
first-party gateway/API".

**Resolution:** two surfaces in one deployment (§1.2). The gateway is
contract-bound, stateless with respect to user content, and governed by the
existing ADs. The product API is a separate surface with its own versioning and
its own contract release requirement.

**Why not defer the product API:** the spine scoped photo-analysis MVP. The
product genuinely needs meal history, progress, and gamification, and those are
not covered by any AD because no AD was written about them — absence of a rule
is not a prohibition.

**Residual risk:** the product API has no contract release and no AD coverage.
Recorded as **B-19**, and no client may consume it before that release exists.
The risk of proceeding is that the product API's shape is decided by
implementation rather than by review.

### C-2 — Nutrition authority

**Brief:** the backend owns `nutrition_snapshots` and a `USDAProvider`, implying
server-side nutrition computation.
**Spine:** AD-7 makes the versioned on-device catalog the only source of
displayed nutrient numbers; AD-19 makes its calculation canonical.

**Resolution:** the backend **records** grounded results verbatim with their
provenance and does not recompute them (§1.5, §8.4). USDA integration is scoped
to catalog package curation plus a gated lookup returning facts, not verdicts.
The server-side calculation path stays disabled until a shared calculation
contract with golden fixtures exists.

**Why:** two calculators that agree today diverge the first time one is patched,
and the divergence is invisible until a user notices two different numbers for
one banana. One authority, provenance on every number.

**Residual risk:** meals created without a client-side grounding step have no
path to nutrition until **B-29** lands. Accepted — the client always grounds in
the MVP flows, so this affects no shipping path.

### C-3 — `TEST_MODE` authentication

**Brief:** run in test mode where sessions are handed out immediately without
verification, gated by a flag.
**Spine:** AD-21 requires attested installations, short-lived scoped tokens,
one-time request IDs, and replay detection.

**Resolution:** fenced, not a mode (§9.2). Boot assertion that exits non-zero in
production; `authMode` on every token and telemetry row; test-mode tokens cannot
authorize a real remote transfer.

**Why:** "gated behind a flag" is a control that fails the moment someone sets
the flag. A process that refuses to start is a control that cannot be set wrong
silently. The development need — a client with no auth integration can still
work end to end — is fully served.

**Residual risk:** low. The main one is a future contributor relaxing the boot
assertion to unblock a staging experiment. Mitigated by a CI test that asserts
the process refuses to start, which fails loudly if the assertion is weakened.

### C-4 — Two-pass inference and AD-6 routing

**Brief:** confidence routing with a second pass when confidence is low.
**Spine:** AD-6 makes routing a device-side decision — local-first, remote only
after explicit consent and a typed eligibility reason.

**Resolution:** these operate at different levels and do not conflict. The
device decides *whether to go remote*. The gateway's two passes happen *inside*
the remote leg, on bytes already transferred under one consent receipt. Pass 2
is not a new transfer and requires no new receipt (§7.5).

**Residual risk:** a future change could make pass 2 fetch additional data or
route to a different region, which *would* be a new transfer. Guarded by an
AD-31 conformance test asserting pass 2 performs no new inbound transfer.

## 13.2 Accepted risks

| ID | Risk | Severity | Why accepted | Mitigation |
| --- | --- | --- | --- | --- |
| R-1 | Modular monolith becomes coupled as the team grows | Medium | Two services now cost more than they save at this size | Mechanically enforced boundaries (§4.9); extraction triggers recorded (§12.3) |
| R-2 | In-process event bus loses events on crash between commit and dispatch | Medium | XP and progress are recoverable by rebuild; meal data is not lost | Handlers idempotent; `ProgressProjector.rebuild`; trigger 2 promotes to a durable queue |
| R-3 | Provider outage degrades the remote path | Medium | The product works without this service — local engine and manual entry | Kill switch; typed `503`; client fallback is a designed path, not a hope |
| R-4 | Provider cost runaway | High | Inherent to paid inference | Per-request, per-principal, per-environment caps; kill switch; cost dashboards required before production |
| R-5 | Prompt injection via voice transcript or food label | Medium | User text must reach the model to be useful | Delimited data field, never concatenated into instructions; strict response schema; malformed output is a typed failure |
| R-6 | Contract drift between client and backend | High | Two independently released products | AD-36 compatibility testing against every supported release; updating both at HEAD is explicitly not sufficient |
| R-7 | `daily_progress` drifts from `meal_events` | Medium | Any projection can drift | Rebuildable by construction; `computed_at` watermark; periodic reconciliation job |
| R-8 | USDA data quality — missing nutrients, ambiguous servings | Medium | It is the best available MVP source | Absent is never zero; `partial: true` on aggregates; documented record precedence |
| R-9 | Markets fail-closed means remote inference ships disabled | Low | This is correct behaviour, not a defect | Local and manual paths work in every market; postures arrive via G-1 and G-4 |
| R-10 | Coach messages read as medical advice | High | Coaching is a core product promise | Prohibited-claim lint over the message catalog; `message_key` + params rather than free-form generation |
| R-11 | Timezone handling produces wrong daily totals | Medium | Travellers and DST are genuinely hard | `local_date` + IANA timezone stored explicitly at creation, never derived later from UTC |
| R-12 | Refresh token theft | Medium | Inherent to long-lived refresh | Hashed at rest; rotation with family revocation on reuse |
| R-13 | Achievement rule engine is over-engineered for five achievements | Low | Deliberate — hardcoding five means rewriting when the sixth arrives | Rule expressions are data; engine is small and fixture-tested |
| R-14 | Documentation drifts from implementation | Medium | Universal | OpenAPI generated from Zod with a CI diff check; ADRs in Stage 2 record changes; these documents are cited from `product-inventory.toml` at activation |

## 13.3 Rejected alternatives

Recording what was *not* chosen, and why, so the same ground is not re-litigated
without new information.

| Alternative | Rejected because |
| --- | --- |
| Microservices from day one | Distributed transactions and operational cost for a product with no users. Module boundaries give the same isolation; §12.6 records the extraction cost |
| Separate gateway deployment now | Same reasoning. The property that matters — the gateway cannot persist — is achieved by dependency graph, not by process boundary |
| GraphQL | Three screens with well-known shapes. REST plus cursor pagination is simpler to cache, rate-limit, and version. GraphQL's flexibility is a cost here, not a benefit |
| Event sourcing for meals | Genuinely attractive for an audit-heavy nutrition domain, and rejected as too much machinery for MVP. Revisions plus a supersede chain give the auditability actually needed |
| Storing photos briefly "for debugging" | Violates AD-8 and Principle 3. Debugging value is real and the answer is better telemetry, not retained photos |
| Provider SDK used directly for speed | Violates Principle 4 and AD-9. The port costs one indirection; provider swaps cost a rewrite without it |
| Backend as the nutrition authority | Violates AD-7 and AD-19; creates two calculators (C-2) |
| Offset pagination | Drifts on insert. The journey feed grows at the end the user reads from |
| `user_id` on `inference_runs` | Turns operational metrics into a per-person record of what was photographed. Benchmarking does not need it (§5.4) |
| Soft-deleting by mutating meal rows | Progress and gamification already reacted to the original fact. Revisions preserve the explanation |
| Storing rendered coach text | Cannot be re-rendered in another locale. `message_key` + params can |
| Down migrations | Written once, tested never, run in an incident. Forward-only plus expand/migrate/contract |
| Float for nutrients | Totals that do not match their parts. Decimal throughout |
| A single global rate-limit policy | Gets either the paid gateway path or the free read path wrong. Layered limits with deliberate fail-open/fail-closed asymmetry (§9.3) |

## 13.4 Open questions

Genuinely undecided. Each names its owner, because an open question without one
stays open.

| ID | Question | Owner | Blocks |
| --- | --- | --- | --- |
| Q-1 | Which markets receive a posture, and what is each one? | G-1 + legal | Remote inference in any market |
| Q-2 | Which provider, region, and DPA terms? Zero-retention available? | G-4 | Production remote release |
| Q-3 | What confidence threshold is validated, and what is the second pass actually worth? | G-5 | Public accuracy claims; threshold tuning |
| Q-4 | Which catalog packages, under which licenses, for which countries? | G-2 | Grounding beyond fake catalogs |
| Q-5 | Does the product API need its own contract release before or alongside its first client story? | Contract principal | B-19, B-20 |
| Q-6 | Where do shared nutrition-calculation golden fixtures live, and who authors them? | Contract principal | B-29, server-side calculation |
| Q-7 | Is per-user provider cost attribution ever needed, and under what consent basis? | Product + privacy | B-47 |
| Q-8 | Does the client or the server own journey feed ordering across heterogeneous items? | Product + client owner | B-27 |
| Q-9 | Are the D-1/D-2 policy-file changes the resolution their owners want? | Backend + repository policy | Nothing; already applied and reversible |

## 13.5 The three things most likely to go wrong

If this architecture fails, these are the likeliest reasons. Stated plainly
because a risk register that treats everything as equally weighted is not a
ranking.

**1. The contract release is scheduled late.** B-5 blocks two client stories and
is cheap to produce. It looks like documentation, so it will feel lower priority
than the gateway itself. It is not — it is the critical path for the whole epic
(§10.5), and the failure mode is two teams blocked on a schema proposal.

**2. Nutrition authority erodes gradually.** Not in one decision, but in a
sequence of reasonable-looking ones: a convenience endpoint that returns a
computed total, a cached number without provenance, a "correction" written
server-side. Each is defensible alone. Together they produce two authorities and
a number nobody can explain. The defence is that `NutritionFact` without
provenance must fail serialization — an invariant enforced at the type level, not
a rule people remember.

**3. `TEST_MODE` reaches a real environment.** Not production, most likely — the
boot assertion covers that. A staging environment pointed at a real provider with
a real market posture is the realistic version. The mitigation is that test-mode
tokens cannot authorize a real transfer regardless of environment, which makes
the blast radius bounded even when the fence is bypassed.
