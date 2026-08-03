# G-4: provider/region/retention decisions, plus draft threat model and ops plan

**Status: mixed.** Sections 1 and 2 (provider/region, retention position) are
now real product decisions, made 2026-08-03 by the person with authority to
make them, in conversation — not fabricated by an agent. They are not,
however, the *legally and technically executed* versions of those decisions:
the branch still needs reconciling, the contract release still needs
shipping, the DPA still needs writing, and per-request opt-in retention still
needs a technical feasibility check against the actual provider API. Sections
3 and 4 (threat model, ops plan) remain drafts nobody has reviewed — treat
those two as a starting point for security/platform, not as sign-off.

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

**What is still NOT done, despite the decision being made:**
- Reconciling/merging `feat/e43-gateway-impl` as the real Epic 4.3
  implementation (`sprint-status.yaml` still shows `epic-4: backlog` and
  doesn't mention this branch — a real coordination gap this document flags
  but cannot itself close).
- Shipping the new major `contracts/mobile-api` release that actually
  describes a text request — `text` is currently `reserved`, not `supported`,
  and that contract is frozen after its first consumer.
- Confirming OpenRouter (as an aggregator routing to underlying models) is
  acceptable from a procurement standpoint given the retention position in
  section 2 below — the provider choice and the retention choice both have to
  hold together, not just each in isolation.

**Owner of what's left:** platform (branch reconciliation, contract release).

## 2. Retention and training terms — DECIDED 2026-08-03 (product owner, in chat)

**Position: default not retained / not trained on, per description, unless the
user explicitly allows it.** Not a blanket policy either way — an opt-in the
user controls per the product's own stated principle (defaults are visible,
sourced, editable, reversible, session-scoped; consent is never silently
defaulted). Someone who never opts in gets the strict position section 1's
predecessor draft proposed (not retained, not trained on); someone who opts in
allows retention/training under whatever terms the provider agreement records.

**What is still NOT done, despite the decision being made:**
- **Technical feasibility is unconfirmed.** This requires the chosen provider
  (OpenRouter, and whichever underlying model it routes a given request to)
  to actually support a per-request retention flag — "sometimes retained,
  sometimes not, decided per request by an end-user's own toggle." That is a
  materially more specific technical requirement than a blanket "provider
  supports zero data retention" clause, and has not been verified against
  OpenRouter's actual API or the specific models `config/model-profiles.toml`
  would name.
- **No code models this yet.** `MarketPolicy` tracks per-modality transfer
  permission and consent-copy version, but nothing today represents a
  separate, user-controlled "may this specific description be retained/
  trained on" toggle. That's new scope for whoever builds the consent
  presenter (section 3 of the G-4 spec's satisfaction list), not something
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
verified in code and tests this session). The other two now have a real
product decision behind them (sections 1 and 2 above), but neither is
*executed* yet: the provider/contract work still needs engineering, and the
retention position still needs a DPA and a technical feasibility check before
it's true rather than intended. Sections 3 and 4 remain unreviewed drafts.
Nothing in this document is a substitute for that remaining work — it exists
so the next person has decisions and a starting point instead of a blank page.
