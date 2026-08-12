# market-policy/v1

Immutable, language-neutral, data-only contract release declaring where each
market's user content may be processed.

## Why this is a contract rather than configuration

A market's processing position is a legal fact with a named human behind it. The
three ways such a position actually moves without anyone deciding to move it are
a story that needed one more market, a provider migration that changed regions,
and a catalog change mistaken for a jurisdiction change. Each is forbidden here
explicitly rather than left to reviewer attention.

The contract is owned by a principal independent of the products consuming it.
A product team that can approve its own processing position has not been
reviewed.

## Unresolved is the default, and it is not a degraded product

A market absent from the instance, one declared `unresolved`, and one whose legal
review has expired are the same state: no transfer, either modality. There is no
partial permission and no inherited one.

This is safe to leave in place because the product works without transfer. The
deterministic catalog search and on-device identification are available under
every posture, so an unresolved market gets a working app rather than a broken
one. Nothing here should ever be rushed to unblock a release; leaving a market
unresolved costs a feature, and guessing costs a legal position.

## What a market must declare

Jurisdiction, residency posture, permitted processing regions, per-modality
remote permission, per-modality reviewed consent-copy version, accepted input
languages, and a named legal approver with an expiry date. All eleven fields or
the market is unresolved.

The expiry is the part that is easy to omit and the reason the rest stays true.
Privacy law moves; an approval with no expiry becomes a claim about the present
made by someone who was reading about the past.

## Independence rules

**Per modality.** A typed description and a photo carry different content and
different risk, so permission is declared twice. A market may permit one and
refuse the other, and one modality's reviewed consent copy can never authorise
the other.

**Independent of catalog country.** Which food data describes a meal and where
that meal's description may be processed are different questions. Changing one
leaves the other untouched.

**Independent of language.** Supporting a language is a product capability;
operating in a market is a legal position. Neither implies the other, in either
direction — the same rule AD-12 already applies to country and language.

## What this release does not decide

Which markets exist and what any of their postures are. That is a legal and
product decision recorded in the instance, reviewed by a named approver, and
carrying an expiry date. This release is the shape that decision has to fit.
