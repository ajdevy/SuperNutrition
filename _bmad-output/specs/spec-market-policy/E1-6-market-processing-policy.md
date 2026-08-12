# E1.6 — the market processing policy, and what is left to decide

**Owner:** privacy and market owner, plus a named legal approver.
**Blocks:** epic 1 closing; remote transfer in every market; OQ-5 (Russia).

This records what was built, what it deliberately does not decide, and the two
findings that came out of building it.

## What exists now

`contracts/market-policy/v1` — schema, release notes, compatibility matrix, and
two canonical fixtures. `mobile/config/quality/market-policy.toml` is the mobile
instance and declares **zero markets**, which is the reviewed state rather than
an omission: every market is therefore unresolved and no transfer is permitted
anywhere, for either modality.

`scripts/verify-market-policy-contract.sh` enforces the rules; fifteen negative
fixtures in `scripts/market-policy-negative-fixtures.sh` prove each one fires,
each asserting the verifier's own message rather than merely a non-zero exit.

The instance is in the digested manifest set, so a change to it fails
`verifyMobilePolicy`. AR-53 calls the file protected; protection means digested,
because a processing position that can change with no gate noticing is not
protected by being called so. Watched a tampered file fail before accepting it.

## What the contract refuses to let happen

Each of these is a way a posture moves without anyone deciding to move it, which
is the failure this gate exists to prevent — and each is invisible in a diff that
looks like ordinary configuration.

- **Omission is not permission.** An absent market, an `unresolved` one, and one
  past `legal_review_expires_on` are the same state.
- **An approval expires.** Privacy law moves; an approval with no expiry is a
  claim about the present made by someone reading about the past.
- **No posture is inherited** from a neighbouring market, a shared language, a
  shared currency or region grouping, or a provider migration.
- **Permission is per modality**, and one modality's reviewed consent copy can
  never authorise the other.
- **A posture cannot disable the local path.** The deterministic floor and
  on-device identification are available under every posture including
  `prohibited`.

## What this does not decide, and cannot

Which markets exist, and for each: jurisdiction, residency posture, permitted
processing regions, per-modality permission, the reviewed consent-copy versions,
a named legal approver, and an expiry date.

None of that can be inferred from code. An invented residency posture is the same
class of failure as an invented nutrition number, except that the consequence is
legal rather than dietary. A research proposal covering candidate jurisdictions
is being prepared separately; it is a briefing for a lawyer and must land here as
`unresolved` until a named human approves it.

## The insight worth keeping

**Publishing a market and permitting remote processing there are separate
decisions.** The app works fully offline through the deterministic floor, so an
unresolved market gets a working product rather than a broken one. That means the
store footprint can be as wide as the stores allow while remote processing stays
gated per jurisdiction — and it means leaving a market unresolved costs a feature
rather than the product, which is what makes it safe not to rush this gate.

## The two findings from building it, and where they stand

**Fixed.** `MarketPolicyResolver` did not resolve market policy — it copied four
fields off a `ConfigurationSnapshot` with no lookup, approver or expiry — and
`ConfigurationSnapshot` requires its jurisdiction and region non-blank, so the
contract's default state was not representable at all. A default that cannot be
expressed is a default nothing can fail closed to.

`MarketPolicyRegistry` in `mobile/core/config/.../MarketPolicy.kt` is now the
contract-backed resolver AR-53 names. The old class was renamed
`PolicyContextResolver`, which is what it always was. Twelve tests cover every
negative case story E1.6 lists, including the one G-4's readiness document
flagged as spec-text-only: **presenting one modality's consent copy for the other
now fails closed.** `mayTransfer` takes the copy the surface actually presented
as an argument rather than reading it off the policy — reading it would compare
the answer with itself and pass always, which is exactly how a photo disclosure
could otherwise authorise a text transfer.

`MarketPolicyRegistry.unresolved` is empty, so every market resolves to
`Unresolved(ABSENT)` and every transfer is refused, for both modalities.

**Still open.** Two things, deliberately not done here.

Nothing loads the TOML instance into the registry. The contract verifier
validates the file at build time and the registry is constructed in code, so the
two agree today by being empty together. Wiring them needs a runtime parser this
module does not have, and inventing one to read a file with no rows would be
mechanism without a purpose. It becomes real work the moment a market is
approved, and should be done then.

`MarketTextPolicy` in `domain/nutrition` remains a second, narrower policy type —
three fields, no expiry, no per-modality split — read by `RemoteTextEligibility`.
It should be replaced by the registry, but `domain/nutrition` does not depend on
`core/config` and adding that edge is an architecture decision rather than a
refactor. Until then these are two sources of truth for one legal position, which
is the pattern this repository keeps paying for. Recorded so the next person does
not have to rediscover it.
