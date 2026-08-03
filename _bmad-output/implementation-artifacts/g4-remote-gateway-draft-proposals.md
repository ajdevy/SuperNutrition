# G-4: provider/region/retention decisions, plus draft threat model and ops plan

**Status: mixed, updated again 2026-08-03 after an adversarial review of
sections 3 and 4 against the real `origin/feat/e43-gateway-impl` source.**
Sections 1 and 2 (provider/region, retention position) are real product
decisions, made by the person with authority to make them, in conversation —
not fabricated by an agent. They are not, however, the *legally and
technically executed* versions of those decisions, and the review below
found the remaining engineering scope is materially larger than it looked:
the branch that was recommended for reconciliation turns out to ship a
photo-only gateway with in-memory (not the tested Redis-backed) rate limiting
and no working production authentication path at all. Sections 3 and 4
(threat model, ops plan) have now been checked line-by-line against that
branch's actual code rather than its commit message — several claims were
corrected, and real, previously-unlisted gaps were found. This is still not a
substitute for a human security/platform sign-off; it is a materially more
accurate starting point for one.

Written 2026-08-03 while scoping Story E7.6, updated the same day once the
provider/retention/region decisions were made. The code-level pieces of G-4
(consent-copy mismatch check, a production caller for
`RemoteTextEligibility.decide`) were implemented and verified the normal way
this session — see the commit history and `G-4-remote-gateway.md`'s updated
"how we will know the gate is satisfied" section.

## 1. Provider, runtime, and region — DECIDED 2026-08-03 (product owner, in chat)

**Provider: `OpenRouterInferenceProvider`, reconciled from
`origin/feat/e43-gateway-impl` rather than built fresh.** That branch
(unmerged, commit `24d51378`, Jul 30 2026) already implements a working
first-party gateway against it
(`backend/mobile-api/apps/api/src/modules/inference/infrastructure/openrouter/`),
537 tests, 94.1%/90.2% coverage, architected as a swappable adapter behind a
domain-level `InferenceProvider` port — model IDs live only in
`config/model-profiles.toml`, never in business logic (AD-9). Its
`ProviderRequest.modality` type already includes `'text'` alongside `'photo'`
at the port level, even though the shipped `contracts/mobile-api/v1` release
on that branch only marks `photo` as `supported` (`text` is `reserved`).

**Region: no single privileged first market.** Remote text is enabled per
market as gate G-1 resolves each one, not sequenced behind one region going
first. This matches how `MarketPolicy` is already modeled — one independent
decision per `marketId` — so it needs no new mechanism, just markets actually
being resolved.

**Correction, 2026-08-03, from an adversarial review of the actual branch
(not its commit message):** "already implements a working first-party
gateway" overstates what's true today, in three specific ways confirmed by
reading the real source at `origin/feat/e43-gateway-impl`, not just its
537-test/94%-coverage self-report:

1. **The text path doesn't functionally exist on that branch at all.**
   `POST /gw/v1/inference/text` is documented and has a schema
   (`TextInferenceRequestSchema`), but the actual Fastify route wiring
   (`routes/gateway/routes.ts`) only ever registers `/gw/v1/inference/photo`,
   `validateRequest` hard-rejects any non-photo modality, and
   `OpenRouterInferenceProvider#buildBody` only ever sends the photo
   identification prompt plus an image — never a text description. Everything
   this section's evidence cites is really evidence about the **photo** path,
   projected onto text.
2. **The composition root wires weaker implementations than the ones tested
   and cited as evidence.** `main.ts` instantiates `InMemoryRateLimiter` and
   `InMemoryEffectStore` — not the fully-implemented, unit-tested
   `RedisRateLimiter`/`RedisEffectStore` that exist in the same codebase. Rate
   limits and the at-most-once transfer-dedup guard are therefore per-process:
   they don't hold across horizontally-scaled replicas and reset on every
   restart — a real double-billing risk under an ordinary rolling deploy, not
   just abuse.
3. **There is no working non-test-mode authentication path at all.** In
   `main.ts`'s composition root, `authenticate` unconditionally throws unless
   `TEST_MODE=true`. The fence that's supposed to keep test-mode auth out of
   production (`assertTestModeFence`, real and well-built) is not
   defense-in-depth on top of a working production auth system — it is
   currently the *only* thing between "no gateway" and "gateway with no real
   authentication," because no production auth implementation exists yet to
   defend.

None of this means the branch is poor work — the individual pieces (Redis
stores, the auth fence, the failure taxonomy) are real and well-tested in
isolation. It means **"reconcile with this branch" is a real decision made in
good faith on incomplete information**, and whoever picks up the
reconciliation needs to know it's reconciling with a *photo-only, in-memory,
test-mode-only* gateway today, not the production-ready text gateway the
commit message implies.

**What is still NOT done, deliberately deferred 2026-08-03 (product owner's
choice, not an oversight) — and now known to be a larger scope than it looked:**
- Reconciling/merging `feat/e43-gateway-impl` as the real Epic 4.3
  implementation. Explicitly not attempted this pass. `sprint-status.yaml`
  still flags that it exists and isn't reflected there. **Now known to
  additionally require:** wiring the Redis-backed stores at the composition
  root (not just merging the branch as-is), and building a real production
  auth path — the fence alone is not sufficient to ship.
- Shipping the new major `contracts/mobile-api` release that actually
  describes a text request — `text` is currently `reserved`, not `supported`,
  and that contract is frozen after its first consumer. Explicitly not
  attempted this pass. **Now known to additionally require:** the actual
  route/handler/prompt work for text, since none of that exists yet even on
  the branch that has everything else.
- Confirming OpenRouter (as an aggregator routing to underlying models) is
  acceptable from a procurement standpoint given the retention position in
  section 2 below — the provider choice and the retention choice both have to
  hold together, not just each in isolation.

Both deferred items are real, scoped work for whenever platform picks this
back up — not blocked on anything technical, just not done in this pass, and
now more accurately scoped than before this review.

**Owner of what's left:** platform (branch reconciliation, composition-root
fixes, production auth, text route implementation, contract release).

## 2. Retention and training terms — DECIDED 2026-08-03 (product owner, in chat)

**Position: default not retained / not trained on, per description, unless the
user explicitly allows it.** Not a blanket policy either way — an opt-in the
user controls per the product's own stated principle (defaults are visible,
sourced, editable, reversible, session-scoped; consent is never silently
defaulted). Someone who never opts in gets the strict position section 1's
predecessor draft proposed (not retained, not trained on); someone who opts in
allows retention/training under whatever terms the provider agreement records.

**Technical feasibility — confirmed 2026-08-03 against OpenRouter's public
docs (openrouter.ai/docs/guides/features/zdr, openrouter.ai/docs/guides/privacy/provider-logging,
verified 2026-06-29 per OpenRouter's own page):**
- OpenRouter supports **per-request** Zero Data Retention via a `zdr` boolean
  in `provider` request preferences (`"provider": {"zdr": true}`), restricting
  that specific request to endpoints with a zero-retention policy. This is
  additive only — the per-request flag can force ZDR *on* for one request, but
  cannot force it *off* below an account-wide or guardrail policy.
- Training is a separate control from retention (some providers retain but
  don't train, or vice versa): OpenRouter's `data_collection: deny` setting
  blocks routing to providers that store or train on data at all.
- **Important limitation to carry into the DPA:** OpenRouter enforces this by
  *routing* — refusing to send the request to a non-compliant endpoint — not
  by remotely deleting data after the fact. Once a request is sent to a
  provider whose policy permits training, OpenRouter has no further control
  over that provider's own retention/training behavior. The guarantee is
  "we will only route your strict request to compliant endpoints," not "we
  can revoke a downstream provider's copy."
- **This maps directly onto the decided opt-in design:** default (user has
  not opted in) sets `zdr: true` and `data_collection: deny` on that request;
  opt-in (user allows it) omits those restrictions for that one request,
  letting it route more broadly. No blanket account setting needed — the
  per-request parameter is the exact mechanism this decision requires.

**Correction, 2026-08-03, from the same adversarial review:** "confirmed
technically feasible" was accurate about OpenRouter's own API (it does
support this), but is a claim about the *platform*, not about *this
product's* code — a distinction worth being precise about. `git grep -i
"zdr\|data_collection"` across the entire `origin/feat/e43-gateway-impl`
backend returns zero hits. `OpenRouterInferenceProvider#buildBody` never sets
either flag, for the photo path that's already shipped and running there,
let alone for text. So the gap isn't "this needs building" as abstract future
work — it's a concrete, present fact about code that already exists and
already makes real provider calls under whatever OpenRouter's account-level
default retention setting happens to be, with no per-request consent toggle
threaded through at all yet.

**What is still NOT done, despite the decision being made and the underlying
API mechanism confirmed real:**
- **No code models or wires this yet, for photo or text.** `MarketPolicy`
  tracks per-modality transfer permission and consent-copy version, but
  nothing today represents a separate, user-controlled "may this specific
  description be retained/trained on" toggle, and nothing in the actual
  OpenRouter request-building code sets the flags that would enforce it even
  if the toggle existed. That's new scope for whoever builds the consent
  presenter (section 3 of the G-4 spec's satisfaction list) *and* whoever
  touches `OpenRouterInferenceProvider#buildBody` — not something
  `RemoteTextEligibility`/`MarketPolicyRegistry` already carry.
- **The DPA itself is still unwritten.** G-4's own text: *"'Not retained' is a
  term to obtain, not an assumption to make."* The position above is the
  product's ask; a named person with the authority to approve a DPA still has
  to obtain contract language from the provider that actually supports an
  opt-in retention model, not just accept boilerplate terms. A DPA (or
  equivalent) recording the finalized terms should live somewhere a reader can
  find it — this repo has no existing convention for that location yet.

**What would make this real:** confirm the provider/model combination can
technically honor a per-request opt-in, design the toggle the consent screen
will show (a decision for whoever builds that presenter), and get the DPA
language to match — then record the finalized position as a checked-in fact
the same way `MarketPolicy` already requires a `legalReviewApprover` and an
expiry for market-level decisions.

## 3. Staging threat model — ADVERSARIALLY REVIEWED 2026-08-03, still not signed off

G-4: *"The free-text path is the sensitive one — see the E1.6 posture proposal
on Article 9."* (Not located during this pass; whoever picks this up should
confirm where it lives or whether it needs writing first.)

**Master finding, before the table:** the free-text path this whole threat
model is about does not exist as runnable code on `origin/feat/e43-gateway-impl`
today. The route wiring only ever registers `/gw/v1/inference/photo`;
`validateRequest` hard-rejects any other modality; `OpenRouterInferenceProvider`
only ever builds a photo-identification prompt. Every row below that cites
that branch is really evidence about the **photo** path, projected onto text
— flagged per-row below where that matters.

| Threat | Verified state (2026-08-03) | Gap |
| --- | --- | --- |
| Description contains sensitive health/medical content (Article 9-adjacent) | No content-classification or redaction step exists anywhere, for photo or text | Unmitigated. Relies entirely on the provider's own handling once received. |
| Description used to re-identify a user across requests | **Partially confirmed.** The request *payload* carries no persistent identifier (`attemptId`/`effectId` only; telemetry explicitly bans `userId`/`email` fields). But every gateway call's JWT carries `sub` (userId) and `installationId`, and rate-limiting is explicitly keyed by `installationId` — a device's requests **are** correlated over time at the transport layer, by design, for rate-limiting. "No identifier crosses this boundary" is only true of the JSON body. | Correct the claim's scope: payload-level only, not request-level. Defensible design, imprecise as originally stated. |
| Man-in-the-middle interception in transit | None yet — no HTTP client exists in `mobile/` at all | TLS/certificate pinning posture is undecided; needs a real decision, not a default. |
| Provider outage or malicious response used to inject a wrong-food result | The closed `InferenceFailure` union has **8** members (not 7 — `kill-switch` was missing from the original list), and every branch is explicitly handled with a safe default for anything unmapped. Response shape is validated (`response.mapper.ts`: length-truncated labels, a recursive forbidden-key scan blocking leaked nutrition data). | Real and stronger than originally described, but photo-only: no equivalent mapper or validation exists for a text/description response yet. Mobile-side handling of any failure type remains unbuilt (no gateway client exists in `mobile/`). |
| Cost/abuse — a compromised or scripted client spamming the gateway | **Refuted as currently wired.** The tested, fully-implemented `RedisRateLimiter`/`RedisEffectStore` exist in the codebase, but the actual composition root (`main.ts`) wires the **in-memory** versions instead. Consequence: rate limits and the at-most-once transfer-dedup guard are per-process — they don't hold across replicas and reset on every restart. A legitimate user's retry after a dropped connection, landing on a different replica or after a deploy, is not recognized as a duplicate and produces a second real, billed provider call. Two more rate-limit layers the design docs promise ("daily spend" cost ceiling, "per environment" kill-switch trigger) have no corresponding rule at all — calling the limiter for either silently returns "always allowed." | Unmitigated as actually deployed, despite tested code existing for it. This is the single most important correction in this table: **real, tested mitigations exist in the module tree and are not what's actually running.** |
| **No working production authentication path exists at all** *(not in the original table — found by adversarial review)* | `main.ts`'s composition root: `authenticate` unconditionally throws unless `TEST_MODE=true`. The fence that's meant to keep test-mode auth out of production (`assertTestModeFence`) is real and well-built, but it is not defense-in-depth on top of a working prod auth system — it is currently the *only* thing between "no gateway" and "gateway with no real authentication," because no production auth implementation exists yet. | **Unmitigated / foundational.** Every other row's mitigations assume an attested caller; none of them apply to a real deployment today, because a real deployment cannot authenticate at all yet. |
| **Retention/training opt-out is not implemented in any request-building code** *(not in the original table)* | Section 2 above establishes OpenRouter's `zdr`/`data_collection` flags as the right mechanism. `git grep` across the whole backend for those terms returns zero hits, and `OpenRouterInferenceProvider#buildBody` never sets them — for the photo path that's already shipped and making real provider calls today, not just for a hypothetical future text path. | Unmitigated, and present-tense: this is a gap in code that already runs, not merely unbuilt future work. |
| **Prompt injection / exfiltration-via-encoding in a free-text description** *(not in the original table)* | The photo path's only mitigation is instructional (a system-prompt line telling the model not to follow directives inside `untrustedContext`), with no technical backstop — no classifier, no output-anomaly check. Response shape validation (length truncation, forbidden-key scan) bounds the blast radius for photo labels but doesn't inspect content/entropy, so a short encoded payload could still pass as a plausible "food name." | Soft-mitigated for photo (shape+length bound it); wholly unbuilt for text, since no equivalent prompt or mapper exists for a description field yet. Worth a real prompt-injection review once a text prompt is written, not just reusing the photo one. |
| PII/health-sensitive content fanning out across multiple vendors | OpenRouter here routes to at least four distinct named vendors (`config/model-profiles.toml`: OpenAI, Google, Anthropic models) | Sharpens the first row: a health-adjacent description's retention/training exposure spans multiple distinct commercial AI vendors behind one aggregator, which matters more given the retention-opt-out gap above is present, not hypothetical. |

**Pattern a reviewer should spend their time on, per this review:** don't
re-verify the photo-side citations — verify that the actual composition root
wires *different, weaker* implementations than the ones the docs and this
table cite as mitigations. That gap (tested code exists; something else is
actually assembled and run) is the single fact most likely to make someone
sign off on protections that aren't actually running.

**What would still make this real:** a security reviewer extends or replaces
this table using the org's actual threat-modeling method (STRIDE or
equivalent), independently re-verifies the citations above against the branch
directly (this review did that once; a second pass by an actual security
reviewer, not an agent, is still the real gate), and signs off.

## 4. Operations plan — ADVERSARIALLY REVIEWED 2026-08-03, one claim corrected, real gaps found

G-4: *"Who is paged when the gateway fails, and what the app does meanwhile."*

**Claim 1, corrected: "the app falls back to the floor on any remote
failure" was wrong as originally stated — the floor-fallback that's real
today has nothing to do with remote at all.** `TextIdentification.identify()`
does unconditionally route local-engine `Abstained`/`Unavailable` outcomes to
the deterministic floor, tested and working. But `RemoteTextEligibility.decide`
is a *pre-flight offer gate* that runs on the local outcome, before any remote
call would ever be attempted — `NotOffered`/`NotPermitted` mean "local already
answered" or "market policy disallows an offer," never "a remote call failed."
`RemoteTextEligibility` has zero production callers anywhere in `mobile/`
(confirmed by grep), and there is no HTTP/gateway client in `mobile/` at all.
**There is no code representing "user accepted a remote offer, the remote
call failed, therefore return to the floor" — that entire path doesn't exist
yet, because no remote call can be made yet.** The original sentence
conflated two unconnected mechanisms.

**Claim 2 (kill switch), confirmed as a mechanism, with real nuance:**
`REMOTE_INFERENCE_ENABLED` is an operator-flipped boolean read once at process
boot — flipping it requires a **restart**, not just a config change ("without
a deploy" is not the same claim as "without a restart"). It's enforced before
any parsing/auth/rate-limiting, and new requests get a clean `503` +
`Retry-After: 300`; in-flight requests already past that check are unaffected
until the restart itself. It is **entirely manual** — no automatic
error-rate/latency-triggered trip exists, despite the branch's own docs
claiming one ("exceeding the daily cap trips the kill switch and alerts" is
aspirational documentation, not implemented behavior, even on that branch). A
usable client primitive already exists and is unused: `GET /health/ready`
returns `remoteInferenceEnabled: <bool>`, so a client *could* cheaply check
kill-switch state before an expensive request — but since no mobile HTTP
client exists, nothing does this today.

**Alerting/monitoring/paging — confirmed as a genuine, total gap, not just
organizational.** Zero monitoring/alerting code anywhere in the backend branch
(`apps/api/src`, `packages/`) — no Sentry/Datadog/PagerDuty/webhook/Slack
integration, no metrics emitter, no alert rule. Every mention is an unchecked
doc checklist item ("Named service owner and on-call rotation," "SLOs with
alerts"). The "who is paged" question was already correctly out of scope for
this document, but it's worth platform knowing there's no telemetry pipe to
page *from* yet either — this isn't only a rotation/policy decision waiting
to be made, it's also unbuilt infrastructure.

**New gap found: nothing distinguishes "slow but working" from "actually
down" on the client side, because no timeout/deadline value exists anywhere
in `mobile/`.** Server-side, this distinction is real and well-built
(per-call `AbortController` timeouts, a typed failure taxonomy separating
`timeout` from `provider-unavailable` from `malformed-response`) — but most
of those typed failures come back inside an HTTP 200 envelope as a
failure-shaped body, not an HTTP error; only `kill-switch` is a real 503. A
client that only checked HTTP status would misread a slow-but-answered or
malformed-response case as success. No deadline value has been chosen for the
mobile-to-gateway leg at all, and this wasn't listed as an open item in the
original draft — it should be.

**New gap found: the "honest failure state" UX requirement has no host to
attach to.** No presenter or screen for typed (remote or even floor-only)
meal identification exists anywhere. The one shipped presenter
(`MealSearchPresenter`) calls a plain catalog lookup, not `TextIdentification`,
and has no concept of remote, consent, offer, or failure state at all. The
ops plan's UX requirement and the buildable state of the app currently
disagree — this isn't unbuilt logic inside an existing screen, it's a screen
that doesn't exist yet, calling a domain class that isn't wired to anything
production, observing a client that doesn't exist either.

**What would make this real, revised:** platform names an on-call rotation
and paging policy (and builds something to page *from*, since nothing exists
today); decides whether a fully manual "operator notices and flips an env
var, then restarts" operational model is acceptable for launch or whether
automatic cost/error-rate tripping is a hard prerequisite; chooses a
mobile-to-gateway timeout/deadline value; and signs off on client-side
fallback behavior once there is an actual client, offer flow, and screen for
it to be fallback behavior *of*. Everything else here is downstream of that
first decision (manual vs. automatic operational model) — nothing else can be
built against a target that hasn't been chosen.

## Summary for whoever picks this up

Two of G-4's four satisfaction criteria are done for real (items 2 and 3 in
`G-4-remote-gateway.md`'s "how we will know the gate is satisfied" list,
verified in code and tests this session). The other two now have a real
product decision behind them (sections 1 and 2 above), and the branch
reconciliation work those decisions imply is now known to be bigger than it
looked: not a merge, but a merge plus wiring the composition root to the
tested Redis stores it already has, plus building the text route that
doesn't exist yet, plus building production authentication from nothing.
Sections 3 and 4 have had one adversarial pass each against the real code —
several original claims were wrong or overstated, and this pass found real,
previously-undocumented gaps (no production auth path at all; tested
rate-limiting code sitting unused; the ops plan's core fallback claim
describing a path that doesn't exist yet). This is a materially more honest
starting point than the first draft, and still not a human sign-off. The
single fact most worth carrying forward: **this branch's tests passing is not
evidence its production wiring is safe** — the gap between what's built and
what's assembled is the finding that matters most here.
