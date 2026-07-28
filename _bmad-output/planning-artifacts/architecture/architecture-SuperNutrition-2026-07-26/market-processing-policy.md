# Market Processing Policy — `contracts/market-policy/v1`

Companion to the architecture spine. Defines the policy data that `MarketPolicyResolver` resolves against, so that remote processing of user content is permitted only where a named legal position says it is.

## Why this exists

The spine already separates `CatalogCountry`, `MarketJurisdiction`, and `ProcessingRegion`, and names `MarketPolicyResolver` as the sole resolver of the latter two. What it does not define is the data being resolved. Without it, `ProcessingRegion` resolves from whatever the configuration happens to contain, and there is no rule that stops a market from being served by a provider region its law does not permit.

This matters for the whole remote path, not one feature. The product already transfers photos to a first-party gateway under AD-13, so any market with data-residency constraints constrains remote photo exactly as it constrains remote text. Typed identification surfaced the question; it did not create it.

Follows the `contracts/interaction-policy/v1` pattern: immutable, versioned, language-neutral schema under root `contracts/`, authored by a principal independent of the products that consume it, with a mobile instance at `mobile/config/quality/market-policy.toml`.

## Schema

One entry per market.

| Field | Meaning |
| --- | --- |
| `marketId` | The market this entry governs |
| `marketJurisdiction` | The legal regime applied to users of this market |
| `residencyPosture` | `unresolved` \| `unrestricted` \| `restricted-in-region` \| `prohibited` |
| `permittedProcessingRegions` | Ordered list of regions where user content may be processed; empty unless the posture is resolved |
| `remoteInference.photo` | Whether remote photo identification is permitted |
| `remoteInference.text` | Whether remote text identification is permitted |
| `consentCopyVersion.photo` | Reviewed disclosure version for a photo transfer |
| `consentCopyVersion.text` | Reviewed disclosure version for a text transfer |
| `acceptedInputLanguages` | Languages a user may type in this market; independent of every field above |
| `legalReviewRef` | Reference to the named review establishing the posture |
| `approvedBy` | The principal who approved it |
| `approvedAt` / `expiresAt` | Validity window; a posture is never open-ended |

## Rules

**MP-1 — Fail closed.** A market absent from the policy, or carrying `residencyPosture: unresolved`, permits no remote inference of any modality. This is the default state of every market. Nothing infers a posture from a neighbouring market, a shared language, a currency, or a region grouping.

**MP-2 — Posture is a legal position, not an engineering one.** `residencyPosture` is set only by a named review recorded in `legalReviewRef` and approved by a principal with standing to make it. No story, configuration change, or provider migration may set or widen a posture as a side effect.

**MP-3 — Postures expire.** `expiresAt` is mandatory. On expiry the entry reverts to `unresolved` and remote inference for that market stops until the review is renewed. Law changes; a posture approved once is not approved forever.

**MP-4 — Per-modality independence.** `remoteInference.photo` and `remoteInference.text` are decided separately. A description and an image are different payloads carrying different identifying detail, and permission for one never implies the other. Each carries its own reviewed consent copy, and a transfer presenting the wrong modality's copy fails closed.

**MP-5 — Language is not a market gate.** `acceptedInputLanguages` is independent of `residencyPosture`, `marketJurisdiction`, and `permittedProcessingRegions`. Supporting a language is a product capability; operating in a market is a legal position. A market may accept input in languages whose home markets are `prohibited`, and a resolved market may accept only one language. Neither implies the other.

**MP-6 — Catalog choice never moves these values.** Selecting a food catalog is a food-data preference. It cannot derive, widen, or silently change `marketJurisdiction`, `residencyPosture`, `permittedProcessingRegions`, or `remoteInference`. This restates the existing spine rule at the policy layer so the data cannot express the violation.

**MP-7 — Resolution is shown, not chosen.** The resolved `marketJurisdiction` and `ProcessingRegion` are displayed before any consent decision. Users consent to the resulting transfer; they do not select provider routing.

**MP-8 — Changes are independently approved and provenance-recorded.** A policy change is isolated, attributed, and approved by a principal independent of the product consuming it, on the same footing as a dependency-verification change. CI never writes policy state.

## Market states

| Posture | Remote inference | Local path |
| --- | --- | --- |
| `unresolved` (default) | None | Deterministic search plus any on-device engine |
| `unrestricted` | Permitted to any `permittedProcessingRegions` entry | Unchanged |
| `restricted-in-region` | Permitted only to listed in-jurisdiction regions | Unchanged |
| `prohibited` | None, and not offered in the interface | Unchanged |

In every state the product remains usable. The deterministic catalog search floor and on-device identification do not depend on a posture, which is what makes fail-closed a viable default rather than an outage.

## Evidence

- **Fail-closed:** a market that is absent, `unresolved`, or expired cannot reach transfer for either modality, proven by negative tests over the transfer path rather than by interface state alone.
- **Expiry:** an entry past `expiresAt` behaves as `unresolved` at resolution time, not at build time.
- **Modality separation:** a photo-permitted, text-prohibited market rejects text transfer; presenting a photo consent copy for a text transfer fails closed.
- **Catalog independence:** changing catalog country leaves jurisdiction, posture, regions, and permissions unchanged.
- **Language independence:** a market may declare input languages while `unresolved`, and search behaves identically regardless of posture.
- **Display:** the resolved jurisdiction and processing region appear before the consent decision in every remote flow.

## Open items owned elsewhere

The policy defines how a market's position is expressed and enforced. It does not decide any market's position — that is the reviewed content of `legalReviewRef`.

- **Gate G-1** owns the launch list and privacy jurisdictions, and therefore which markets get a posture at all.
- **Gate G-4** owns provider, region, and DPA terms, and therefore which `permittedProcessingRegions` values are real.
- **Russia**, raised as OQ-5 in `SPEC-text-meal-identification`, is one instance: it enters as `unresolved` like every other market, so nothing is blocked pending that review. If the market is pursued, the question for counsel is whether an account-free typed description or photo, carrying an opaque attempt ID and a short-lived device attestation token, constitutes personal data of a Russian citizen such that in-country primary storage is required. That determination sets the posture; it does not change this policy.
