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
- **The consent-copy *mismatch* rule is spec text.** `textConsentCopyVersion` is
  required non-null, but nothing compares it against a photo version — and no
  photo version exists anywhere. So "the photo copy cannot authorise a text
  transfer" holds only because there is no way to supply one. AR-51 and E7.6
  AC-2 both require failing closed on mismatch, and nothing would catch a market
  that put the photo string into the text field.
- **Nothing calls `RemoteTextEligibility.decide` outside its own test.** The
  presenter and screen never reach it.

## How we will know the gate is satisfied

1. A named provider, runtime and region set, with the mobile-api contract
   extended to describe a text request — a new release, since v1 describes a
   photo envelope and `incompatible_change = "new-major-release"`.
2. The consent-copy mismatch check exists and has a negative fixture: a market
   presenting the photo version for a text transfer is rejected.
3. `RemoteTextEligibility.decide` has a production caller, and the journey from
   offer to transfer is covered end to end.
4. DPA terms are recorded where a reader can find them, including whether
   descriptions may be retained or used for training. "Not retained" is a term to
   obtain, not an assumption to make.

## The dependency worth naming

G-4 cannot be exercised in any market until G-1 resolves one, because every
market is unresolved and unresolved forbids transfer. Conversely G-1's remote
clauses are theoretical until G-4 names a region to permit. They gate each other
and should be sequenced together, with E1.6's contract as the shared vocabulary.
