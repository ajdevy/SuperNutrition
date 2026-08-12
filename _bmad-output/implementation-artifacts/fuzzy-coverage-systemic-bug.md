# A systemic ranking defect: short unrelated form beats long correct form

Found by hand-probing common English typos before the slang-scenario workflow
returned, then confirmed on the real validated engine model.

## The mechanism

`coverage = (tokens of a FORM the query accounted for) / (total tokens in
that FORM) * 1000`, taken as the BEST over all of a record's forms. This
structurally rewards matching a large fraction of a SHORT alternate name over
a small fraction of a LONG, correctly-qualified display name -- even when the
short name belongs to an ADJACENT, DIFFERENT concept and the long name is the
exact food being typed.

This is the R1/R2 shape (масло/свиней, fixed in 17413c34) recurring in a
different tier and without any lemma fold involved at all: FUZZY near-matches
one token, and coverage math alone decides the winner.

## Four confirmed instances, on the real engine model (validated against
Gradle-measured numbers earlier this session)

| query | wins today | coverage | should win | coverage | why the wrong one wins |
|---|---|---:|---|---:|---|
| `chiken` | Egg, whole, raw, fresh | 500 | Chicken, broilers... | 142 | Egg's alt `chicken egg` is 2 tokens (1/2=500); Chicken's own longest-qualified name is 7 tokens and its best short form `chicken meat` is also 2 tokens (500) -- a TRUE TIE broken by canonical id, and Egg's id sorts first |
| `café`/`cafe` | Syrup, Cane | 500 | (no Coffee record is reachable) | -- | `cafe`(4) is within edit distance 1 of the UNRELATED word `cane`(4); real Coffee records are all 5+ tokens with no short form, and `cafe` vs `coffee` fails the LENGTH-DIFF bound before edit distance is even computed (4 vs 6, diff 2 > allowance 1) -- coffee is unreachable, so the spurious match wins by default |
| `yogert` | Tofu yogurt | 500 | Yogurt, plain, whole milk | 250 | `tofu yogurt` is 2 tokens (500); the real yogurt's bare alt names (`yoghurt`,`yogur`,`йогурт`) do NOT near-match `yogert` at all (unverified exactly why -- needs a precise edit-distance check), so it falls back to its 4-token qualified display name (250) |
| `sammon` | Fish oil, salmon | 333 | Fish, salmon, chinook, raw (or any actual salmon-as-food record) | 250 | no salmon-as-food record carries a bare `salmon` token; the closest short form belongs to the OIL byproduct (3 tokens, 333) vs the food's own 4-token qualified name (250) |

## Why this needs a designed fix, not a quick patch

This session already burned one cycle on an "obvious" ranking fix (a LEMMA
tier, then form-multiplicity) that looked right on 3-4 examples and broke
15-50 others elsewhere in the same 3519-record catalog. The coverage formula
is used by EVERY tier and every language; a fix here has a much larger blast
radius than the Russian-only TOKEN_SUBSET tiebreak in 17413c34, which was
deliberately scoped narrow for exactly this reason.

Next step: gather more instances (the concurrent 20-lens scenario sweep) into
a real battery, then run a judge-panel workflow the same way ranking-fix-round2
did -- multiple independent candidates, each swept against the full catalog,
adversarially cross-checked, before anything ships.

---

## Correction: this is not FUZZY-only, and not typo-only

Found while triaging the 875-scenario slang sweep: bare, correctly-spelled
"chicken" and "milk" -- the two most ordinary English protein/dairy words --
resolve wrong on the real engine, with no typo involved at all.

```
chicken -> TOKEN_SUBSET, Egg, whole, raw, fresh (500)   -- not Chicken
milk    -> TOKEN_SUBSET, Whey, acid, fluid (500)         -- not Milk
творог  -> TOKEN_SUBSET, Tofu, firm... (333)             -- not cottage cheese
```

This is the SAME coverage-tie mechanism as масло/свиней (fixed in 17413c34),
but the 17413c34 fix does not reach it: that fix only breaks a tie between a
LITERAL match and a LEMMA-FOLDED match. Here, BOTH sides are literal --
"chicken" is a genuine token of Chicken's own alternate names ("chicken
meat", "cooked chicken") AND of Egg's alternate name ("chicken egg") with no
folding involved on either side. The tie survives 17413c34 untouched and
falls to canonical id, which happens to favor Egg (usda-srl-01123 sorts
before usda-srl-05011).

This widens the scope of what the coverage-fix panel (wf_089c9da3-f3c) needs
to solve. The panel's brief scoped one candidate ("fuzzy-specific-guard")
around the assumption that all four original instances (chiken/yogert/
sammon/cafe) reach the wrong record via FUZZY specifically -- true for those
four, but chicken/milk/творог prove the SAME failure shape also reaches
through TOKEN_SUBSET on tokens the query typed *exactly right*. A fix scoped
to FUZZY alone would leave this open. The panel's own broad sweep
(instructed to sample the catalog's own vocabulary) should have surfaced
this independently; verify explicitly that whichever candidate ships also
resolves plain "chicken" and "milk" correctly before treating the panel's
recommendation as complete.
