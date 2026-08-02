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

## Two findings from building it

**The existing `MarketPolicyResolver` does not resolve market policy.** At
`mobile/core/config/.../InteractionDefaults.kt:84` it copies four fields off a
`ConfigurationSnapshot` into a `PolicyContext` — no policy lookup, no approver, no
expiry. Worse, `ConfigurationSnapshot` requires `marketJurisdiction` and
`processingRegion` to be non-blank, so **`unresolved` is currently
unrepresentable** — the exact opposite of the contract's default. Wiring the
resolver to the contract is the next slice and is not done.

**`MarketTextPolicy` is a second, narrower policy type.** It lives in
`domain/nutrition` with three fields and no expiry, and `RemoteTextEligibility`
reads it. Once the resolver reads the contract, these are two sources of truth
for one legal position — the pattern this repository keeps paying for. They
should become one.
