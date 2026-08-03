# G-4 draft proposals — provider, DPA/retention, threat model, ops plan

**Status: DRAFT. None of this has been reviewed or approved by platform,
privacy, or legal. Nothing here satisfies G-4's external-input requirements —
it exists so a real reviewer has a concrete starting point instead of a blank
page. Do not cite this document as evidence G-4 is closed.**

Written 2026-08-03 while scoping Story E7.6. The code-level pieces of G-4
(consent-copy mismatch check, a production caller for
`RemoteTextEligibility.decide`) were implemented and verified the normal way
this session — see the commit history and `G-4-remote-gateway.md`'s updated
"how we will know the gate is satisfied" section. The four items below are
different in kind: they are business, legal, and security decisions, and an
agent proposing them does not make them real. Each section says plainly what
would make it real.

## 1. Provider and runtime — reconciliation, not a new choice

**Do not pick a provider from scratch.** `origin/feat/e43-gateway-impl`
(unmerged, commit `24d51378`, Jul 30 2026) already implements a working
first-party gateway against a real provider: `OpenRouterInferenceProvider`
(`backend/mobile-api/apps/api/src/modules/inference/infrastructure/openrouter/`),
537 tests, 94.1%/90.2% coverage. It is architected as a swappable adapter
behind a domain-level `InferenceProvider` port — model IDs live only in
`config/model-profiles.toml`, never in business logic (AD-9) — and its
`ProviderRequest.modality` type already includes `'text'` alongside `'photo'`
at the port level, even though the shipped `contracts/mobile-api/v1` release
on that branch only marks `photo` as `supported` (`text` is `reserved`).

**What this means for G-4:** the "named provider and runtime" decision may
already have effectively been made by whoever built that branch. The live
decision is not "which provider" but:
- Whether to reconcile/merge `feat/e43-gateway-impl` as the real Epic 4.3
  implementation (sprint-status.yaml still shows `epic-4: backlog` and does
  not mention this branch at all — that's a real coordination gap, not
  something this document can resolve).
- Whether OpenRouter itself (as an aggregator routing to underlying models)
  is an acceptable vendor from a procurement/DPA standpoint — that's exactly
  section 2 below, and is unaffected by whether the code is good.

**Owner:** platform (branch reconciliation), procurement/legal (vendor
approval). **This document proposes nothing new here** — it reports what
already exists so nobody builds a second gateway or re-derives a provider
choice the codebase already made.

## 2. DPA, retention, and training terms — PROPOSED, not obtained

G-4's own text: *"'Not retained' is a term to obtain, not an assumption to
make."* Nothing below is that term. This is a proposed position for legal to
either adopt, amend, or reject.

**Proposed position** (mirrors the stance the codebase already takes on-device
— `contracts/inference/v2` marks free text `loggable = false`,
`telemetry_eligible = false` — extended to the vendor relationship):

- Descriptions transferred to the remote provider should not be retained by
  the provider beyond the request/response cycle needed to serve it.
- Descriptions should not be used to train or fine-tune the provider's models,
  including via default opt-in retention policies some providers apply unless
  a request explicitly opts out.
- If the chosen provider (OpenRouter or whichever underlying model it routes
  to) cannot contractually guarantee both of the above, that is a fact for
  legal/privacy to weigh against the product decision to offer remote text at
  all — not a fact this document can substitute a workaround for.
- A DPA (or equivalent contractual instrument) recording whichever position is
  actually adopted should live somewhere a reader can find it — the gate spec
  suggests `contracts/` or a dedicated `docs/legal/` location; this repo does
  not yet have a convention for that, which is itself worth deciding.

**What would make this real:** a named person with the authority to approve a
DPA reviews it, an actual agreement (or explicit written acceptance of the
provider's standard terms) exists, and its retention/training position is
recorded in a form the codebase can point to — ideally as a checked-in fact
(a policy version, a boolean, a date) the same way `MarketPolicy` already
requires a `legalReviewApprover` and an expiry for market-level decisions.

## 3. Staging threat model — DRAFT, for security review

G-4: *"The free-text path is the sensitive one — see the E1.6 posture proposal
on Article 9."* (I did not locate a document literally named that during this
pass; whoever picks this up should confirm where it lives or whether it needs
writing first.)

**Draft threat sketch**, offered as a starting point:

| Threat | Existing mitigation | Gap |
| --- | --- | --- |
| Description contains sensitive health/medical content (Article 9-adjacent — allergies, conditions, dietary restrictions implied by a meal description) | None specific to this path yet; the general non-logging/non-telemetry contract rules apply | No content-classification or redaction step before transfer; relies entirely on the provider's own handling once received |
| Description used to re-identify a user across requests | `attemptId`-scoped requests per the v2 contract's request shape; no persistent user identifier crosses this boundary in the code seen so far | Not independently verified against the actual gateway implementation (on the unmerged branch) — worth confirming there, not assuming |
| Man-in-the-middle interception in transit | None yet — no HTTP client exists in `mobile/` at all | TLS/certificate pinning posture is undecided; standard practice would be TLS 1.2+ with certificate validation, but this needs a real decision, not a default |
| Provider outage or malicious response used to inject a wrong-food result | `InferenceFailure`'s closed typed union (`timeout`, `rate-limited`, `provider-unavailable`, `content-rejected`, `malformed-response`, `cancelled`, `budget-exceeded`) on the gateway branch suggests this was considered there | Mobile-side handling of each failure type is unbuilt (no gateway client exists in `mobile/` yet) |
| Cost/abuse — a compromised or scripted client spamming the gateway | Backend branch has a Redis rate limiter per its commit message | Not verified in this pass; also this is G-4's own listed gap ("Cost and abuse position: Absent") from the mobile/product-policy side |

**What would make this real:** a security reviewer either extends or replaces
this table using the repo's actual threat-modeling method (STRIDE, or whatever
this org standardizes on), reviews the unmerged gateway branch's real
mitigations directly rather than trusting a commit message's self-report, and
signs off.

## 4. Operations plan — DRAFT, for platform review

G-4: *"Who is paged when the gateway fails, and what the app does meanwhile."*

**Draft proposal**, for the "what the app does meanwhile" half only — the
"who is paged" half is an on-call/organizational decision this document has
no basis to propose:

- On any `InferenceFailure` from the remote path (or on the gateway being
  unreachable at all), the app falls back to the deterministic local floor
  (`CatalogSearch`/`DeterministicTextEngine`) rather than blocking the user —
  consistent with this codebase's existing "the floor always answers" design
  principle, already proven for the local-engine-unavailable case.
  `RemoteTextEligibility`'s own `NotOffered`/`NotPermitted` results already
  route back to that floor implicitly, since the offer simply never appears.
- A remote failure should be visible to the user as a plain, honest state
  ("couldn't reach the server, showing what we found locally") rather than a
  silent downgrade — matching the product's stated low-friction-interaction
  principle that defaults must be visible, not silent.
- Kill-switch: the unmerged gateway branch's commit message mentions "a kill
  switch" already exists server-side; confirming its exact mechanism and
  whether the mobile client needs its own independent circuit-breaker (rather
  than relying solely on the server refusing requests) is unresolved.

**What would make this real:** platform names an on-call rotation and paging
policy, confirms the kill-switch mechanism end to end (both sides, not just
the server's), and signs off that the client-side fallback behavior above is
the intended one (or corrects it).

## Summary for whoever picks this up

Two of G-4's four satisfaction criteria are done for real (items 2 and 3 in
`G-4-remote-gateway.md`'s "how we will know the gate is satisfied" list,
verified in code and tests this session). The other two — a named
provider/region/contract release, and DPA/retention terms — are not
implementable by engineering alone, and this document is explicitly not a
substitute for the real sign-off both require. Its only job is to leave the
next reviewer something concrete to react to instead of nothing.
