# Dish and ingredient inference

## Why

A user does not eat "chicken, rice, onion, yoghurt". They eat biryani. Every
entry point in the app hits the same wall: a name that means a composition, not a
record. Photo identification proposes a dish, typed identification receives one,
and tapping a suggestion selects one — and all three currently have to pretend
the dish is a single food or give up.

This spec adds one mechanism: a dish resolves to a **proposed** ingredient list
the user edits before anything binds. It is one mechanism deliberately. Three
entry points that must agree about what "biryani" contains will not stay in
agreement if each expands dishes its own way; that drift is exactly what produced
the E7.2 defect where the engine segmented descriptions and the floor did not.

## Gate position, stated first

This work is gated twice, and neither gate is a reason to stop building the
mechanism.

**G-2 covers the dish data.** AR-34 forbids beginning release-ready nutrition
packages before G-2 provides its evidence, and a dish-to-ingredient table with
weights is a nutrition package. `SeedCatalog` already records the workable
reading: G-2 "blocks grounding beyond a fake catalog, not development against
one". A seed dish set is therefore in scope; shipping real dish data is not.

**G-1 covers the locale behaviour.** AR-34 puts release-ready country behaviour
behind G-1. Ranking dishes by market is country behaviour. The mechanism and its
manifest are buildable now; switching it on for a real market list is not.

Neither gate is satisfied by this spec and neither is claimed to be.

## The failure this feature is closest to

FM-1 in `spec-text-meal-identification/failure-modes.md` uses biryani as its
example: a model maps it to a real, signed, correctly-versioned record for a
different dish, and the result passes every provenance check the architecture
has. Dish expansion is that failure with more surface area — one wrong dish match
now proposes five wrong foods, each individually plausible.

So the controls are stricter here than for a single food, not looser.

## Capabilities

- id: DC-1
  intent: A dish name resolves to a proposed ingredient list that the user reviews and edits before any record binds.
  success: No code path reaches a Grounded Nutrition Result from a dish expansion without a per-item confirmation event, including at maximum confidence. Tests prove an inferred ingredient can be removed and a missing one added, and that neither restarts the session.

- id: DC-2
  intent: Photo identification, typed identification, and tapping a dish all expand a dish through the same implementation.
  success: One port, one seed dataset, one search. A test proves the same dish id yields an identical proposal through all three entry points.

- id: DC-3
  intent: An ingredient weight is shown only when an approved source supplies it.
  success: The weight field is nullable and absent by default. A test proves no code path invents, interpolates, or defaults a gram figure, and that an unknown weight renders as unknown rather than as zero or as an average.

- id: DC-4
  intent: A user's market, language, and script inform which dishes rank first, as a visible and editable default.
  success: The signal is displayed with its source, is changeable in one action, is session-scoped, and never filters — a dish outside the active market is ranked lower, never hidden. A test proves a market change reorders results without removing any.

- id: DC-5
  intent: A dish the catalog does not carry degrades to food-level search rather than failing.
  success: An unknown dish name falls through to the E7.2 catalog floor and reports what it could not resolve, exactly as a typed description does.

## Failure modes

**DFM-1 — Plausible expansion of the wrong dish.** The sharpened FM-1. Control:
the dish match and every ingredient it proposes are separately reviewable, and
the dish name the match came from is shown alongside the proposal so a user can
see that "biryani" was read as "birria" before they confirm anything.

**DFM-2 — Invented weights.** A gram figure with no source is fabricated
nutrition wearing a provenance chain. Control: DC-3. Absent is absent.

**DFM-3 — Locale as silent bias.** A user in Russia is shown Russian dishes
first, is never told why, and cannot change it. This violates the product-wide
low-friction rule that defaults are visible, sourced, editable, reversible, and
session-scoped. Control: DC-4, and locale ranks rather than filters — a Russian
speaker in the United States still eats burgers, and someone travelling still
eats what is in front of them.

**DFM-4 — Expansion read as confirmation.** A five-ingredient proposal looks like
work already done, and one tap on "confirm all" is exactly how a user binds five
records they never read. Control: confirmation is per item, and DC-1's negative
test covers the bulk path specifically.

**DFM-5 — Nationality inferred and treated as identity.** Device locale is not
nationality, an IP address is not a home, and neither is a fact about the person.
Control: the signal is named as what it actually is — the active market setting —
and is never presented as a statement about who the user is. Nothing about
nationality is stored.

## Locale signals

Three independent axes, kept independent because AD-12 already makes country and
language independent and collapsing them here would undo that.

| Axis | What it is | What it may do |
| --- | --- | --- |
| Market | The activated country package | Rank dishes carried by that market first |
| Input language | What the user types in | Select the synonym set to match against |
| Script | The writing system in use | Accept cross-script entry of the same dish |

A user in Germany typing Russian looking for a Turkish dish is the normal case,
not the edge case. Any design that reduces these three to one gets that user
wrong.

## Non-goals

- Recipes. This proposes what a dish typically contains, not how to cook it, and
  never claims to describe the specific portion in front of the user.
- Per-restaurant or per-brand composition. That is a licensed-data question well
  beyond a seed set.
- Learning a user's usual composition. Correction retention is behind G-6.

## Open questions

- **DOQ-1** — Does an expanded ingredient carry its own serving, or does the dish
  carry one serving that scales its parts? The second is friendlier and the first
  is honest when a user ate a biryani with extra chicken. Needs a product call.
- **DOQ-2** — When a dish and a food share a name, which wins? "Chicken" is a
  food; "chicken curry" is a dish. Ranking probably resolves it, but the tie case
  is undefined.
- **DOQ-3** — Which market list G-1 will actually approve, and therefore how many
  dish sets the first release carries.
