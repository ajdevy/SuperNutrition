# G-1 readiness: what the launch sequence must fix

**Owner:** product + privacy/legal. **Blocks:** public store launch.

As with G-2, this approves nothing. It turns *"exact country order, language plan,
unit/label rules, privacy jurisdictions, store claims"* into a list someone can
tick, and records which parts the code has already been built to accept so the
decision is about markets rather than about mechanism.

The mechanism is deliberately inert until this gate answers. An unresolved market
gets the local path rather than the nearest provider region — the null is treated
as an unanswered question, not a value to fill in — and that default is what
makes the app usable while the gate is open rather than blocked by it.

## What must be supplied

| Input | Why the code needs it | State today |
| --- | --- | --- |
| **Exact country order** | Determines which market rows exist and therefore which dish attributions matter | No launch list; every market defaults to `unresolved` |
| **Language plan per market** | The matrix keys synonyms and morphology per *(catalog, input language)* pair, not per language | English, Russian, Spanish are targeted; the matrix declares their morphology profiles |
| **Unit and label rules** | Decides whether a market reads grams, ounces, or both, and what a label must show | Nothing formats a number yet; quantity words are stripped and re-asked |
| **Privacy jurisdictions** | Sets each market's processing region and therefore whether remote text is permitted at all | `MarketTextPolicy` carries a nullable consent copy and region; a null is unresolved rather than a default, and yields `RemoteTextDecision.NotPermitted` |
| **Store claims** | Constrains what the listing may assert about accuracy and grounding | Product identity is fixed; accuracy claims are gated separately by G-5 |

## What the code already commits to

- **Country is independent of language (AD-12).** A Russian speaker in the United
  States searching Cyrillic against an English catalog is the ordinary case. A
  launch plan that assumes one language per country will be wrong about real
  users, and the language matrix is keyed to make that representable.
- **Locale ranks, it never filters.** A dish outside the active market moves down
  and stays reachable. Someone travelling eats what is in front of them.
- **Nothing infers nationality.** Device locale is not nationality and an IP
  address is not a home. The market is a setting the user can see and change, and
  nothing else is stored.
- **Unresolved is a safe state, not a broken one.** Every market starts there and
  the deterministic floor still answers. The gate can stay open without the
  product being unusable, which is why it should not be rushed to unblock work.

## How we will know the gate is satisfied

1. Each launched market has a row in the language matrix with its catalog
   language, accepted input languages, script, and morphology profile.
2. Each launched market supplies both a text consent copy version and a
   processing region, so the decision can reach `RemoteTextDecision.Offer`
   rather than `NotPermitted`. Either one missing keeps the market local-only.
3. Each launched market has a G-2 package, or is explicitly launched
   floor-only — which is a decision, not an oversight, and should be recorded
   as one.

## The dependency worth naming

G-1 and G-2 are independent gates that fail together. A market with no package
cannot ground anything, and a package for a market nobody launches is wasted
licensing. Sequencing them so the first launch market is also the first licensed
package is the cheapest order, and neither gate can see that on its own.
