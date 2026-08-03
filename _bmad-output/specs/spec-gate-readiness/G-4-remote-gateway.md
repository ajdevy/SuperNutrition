# G-4 readiness: what the remote gateway and provider decision must supply

**Owner:** platform + privacy/legal. **Blocks:** production Remote Engine release,
all of epic 4, and story 7.6.

As with G-1 to G-3, this approves nothing. It turns the spine's exit artifact —
*"signed provider/runtime/API/region/cost/abuse decision, DPA and
retention/training terms, staging threat model, and operations plan"* — into a
list someone can tick.

The distinguishing fact about G-4 is how little exists to change. **No production
code performs a remote call, and the Android app could not make one:** there is no
HTTP client anywhere in `mobile/`, and neither `AndroidManifest.xml` declares the
`INTERNET` permission. The eligibility decision exists; the transport does not.

## What must be supplied

| Input | Why the code needs it | State today |
| --- | --- | --- |
| **Provider and runtime** | Nothing names one; `engineId` and `modelVersion` are free strings on every result and telemetry event | Not selected |
| **API shape for text** | `contracts/mobile-api/v1` describes a photo envelope only — no endpoints, no message schema, no auth, no error taxonomy, and nothing for a typed description | Released, but binds no code |
| **Processing regions** | `RemoteTextDecision.Offer` cannot be constructed without a non-null region, so an offer that cannot name where it processes is unrepresentable | Every market unresolved; see G-1 and E1.6 |
| **Cost and abuse position** | No rate limit, quota, or abuse control exists in any form | Absent |
| **DPA, retention, training terms** | Whether a description may be retained or trained on is a contract term, not a code setting | Absent; `retention` appears in no source file |
| **Staging threat model** | The free-text path is the sensitive one — see the E1.6 posture proposal on Article 9 | Not written |
| **Operations plan** | Who is paged when the gateway fails, and what the app does meanwhile | Not written |

## What the code already commits to

- **The trigger set is closed and enforced structurally.** A successful local
  result returns `NotOffered` *before* any market or policy check
  (`RemoteTextEligibility.kt:58-61`), so no configuration value can turn a good
  local answer into an upload prompt. G-5 may tune a threshold; it cannot
  authorise escalation after success.
- **An offer that cannot name its consent copy and processing region is
  unrepresentable.** Both are non-null on `Offer` and reached only past two elvis
  returns. A provider or market supplying region-less policy simply cannot get
  there.
- **Free text is structurally non-loggable.** The contract marks it
  `loggable = false` and `telemetry_eligible = false`, `InferenceRequestV2.Text`
  redacts its own `toString`, and no telemetry field can hold a string a
  description could be written into.
- **The route vocabulary is frozen.** `local`, `local-text`, `remote`, pinned in
  `contracts/inference/v2` and byte-frozen by the immutability gate. A provider
  decision cannot widen it without a v3.

## What is declared and not true yet

- **`RemoteEligibilityReason.LOW_CONFIDENCE` has no producer.** AD-6 names three
  triggers; only two can occur, because no `TextEngineOutcome` carries a score.
  The third is G-5's to define. A reader counting three enum entries overcounts
  the enforced set.
- **No presenter or screen calls `RemoteTextEligibility.decide` yet.** That is
  expected at this stage, not a gap G-4 is waiting on — the rest of this epic
  ships domain logic ahead of any concrete UI, the same way `EngineCapabilityV2`
  shipped ahead of a real engine, and the "domain-level orchestrator only, no
  UI" scope for this stage was a deliberate choice, not an oversight.

Both items below were true when this doc was first written and are stale now,
corrected 2026-08-03:

- `MarketPolicy.kt` (Story E1.6, landed after this doc) added
  `MarketPolicyRegistry.mayTransfer(marketId, modality, presentedConsentCopyVersion,
  today)`, which compares the copy version a caller actually presented against
  the one the market's policy authorises *for that modality* — so a market's
  photo copy presented against a `TransferModality.TEXT` request now genuinely
  fails closed with `TransferRefusal.CONSENT_COPY_MISMATCH`, not merely by the
  absence of a photo field to compare against. Tested by `MarketPolicyTest`'s
  `presenting one modality's consent copy for the other fails closed`. This
  closes criterion 2 below.
- `RemoteTextEligibility.decide` now has a second overload
  (`localOutcome, marketId, registry, todayEpochDay`) that resolves the market
  from `MarketPolicyRegistry` itself and delegates to the original overload
  rather than duplicating its branching, and `RemoteTextEligibilityRegistryTest`
  exercises the full journey from that offer through `mayTransfer`'s
  transfer-time re-check, including the disagreement case (a market-side
  change between offer and transfer is refused, not silently permitted). This
  closes criterion 3 below.

## How we will know the gate is satisfied

1. A named provider, runtime and region set, with the mobile-api contract
   extended to describe a text request — a new release, since v1 describes a
   photo envelope and `incompatible_change = "new-major-release"`. Note as of
   2026-08-03: `origin/feat/e43-gateway-impl` (unmerged) already implements a
   working gateway with a real OpenRouter provider adapter, but its own
   `contracts/mobile-api/v1` release explicitly ships `supported = ["photo"]`,
   `reserved = ["text"]` with `frozen_after_first_consumer = true` — so
   reconciling with that branch, not choosing a second provider from scratch,
   is the live decision here, and enabling text still needs the described new
   major release regardless of which branch that work lands from.
2. **Satisfied 2026-08-03.** The consent-copy mismatch check exists
   (`MarketPolicyRegistry.mayTransfer`) and has a negative fixture
   (`MarketPolicyTest`'s mismatch test, passing).
3. **Satisfied 2026-08-03.** `RemoteTextEligibility.decide(localOutcome,
   marketId, registry, todayEpochDay)` is a real, non-test caller that resolves
   the market from `MarketPolicyRegistry` instead of requiring a hand-built
   `MarketTextPolicy`. The journey from that offer through
   `MarketPolicyRegistry.mayTransfer`'s transfer-time re-check is covered end
   to end by `RemoteTextEligibilityRegistryTest` — including the case where an
   offer that looked fine is still refused at transfer time because the
   presented copy version no longer matches (the two gates are independent by
   design, not merely by accident, so a market change between offer and
   transfer is a refusal rather than a race).
4. DPA terms are recorded where a reader can find them, including whether
   descriptions may be retained or used for training. "Not retained" is a term to
   obtain, not an assumption to make. **Not satisfied.** A draft proposal exists
   at `_bmad-output/implementation-artifacts/g4-remote-gateway-draft-proposals.md`
   for whoever owns this to react to — it is explicitly not the real terms.

## The dependency worth naming

G-4 cannot be exercised in any market until G-1 resolves one, because every
market is unresolved and unresolved forbids transfer. Conversely G-1's remote
clauses are theoretical until G-4 names a region to permit. They gate each other
and should be sequenced together, with E1.6's contract as the shared vocabulary.

## Draft proposals, not decisions

`_bmad-output/implementation-artifacts/g4-remote-gateway-draft-proposals.md`
(2026-08-03) sketches a starting point for criterion 1's provider
reconciliation, criterion 4's DPA/retention position, a staging threat model,
and an operations plan — each explicitly marked as a draft for the real owner
(platform / privacy / legal / security) to accept, amend, or reject. It also
flags that `origin/feat/e43-gateway-impl` (unmerged) already implements a
working first-party gateway that `sprint-status.yaml` does not yet reflect.
