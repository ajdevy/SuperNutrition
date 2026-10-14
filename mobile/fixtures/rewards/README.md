# Recovered Rewards reference

The visual source was recovered from the local, unreachable Git tree
`015c5dd90c23df0c97b819cf1dab39f48441ccd0`. It has no associated commit, so its
publication on `main` and its creation date cannot be established. The actual
upstream `main` at `5ee5de077866feed70b418ee2760af24322b1d22` contains the temporary
Material Rewards list. This is a port of recovered visual material onto the
current rewards feature, not a restoration of an identified upstream commit.

[prototype-reference.html](prototype-reference.html) preserves the complete original
`prototype/index.html` blob `aa06746fc0dcfca36e69e95f65777d57ab4a9d5f` byte for byte.
Its Rewards section supplies the two-column white card grid, rounded borders,
light paper background and green accents. The seven illustrated medals and
foliage background were siblings in `design-assets/export-2026-08-30/png/` of the
same tree; the prototype's Rewards markup does not reference the medals.
No complete exported Rewards-screen image was found.

[source-manifest.json](source-manifest.json) records each original path, blob,
SHA256, size, dimensions and independent mobile resource destination. All eight
production PNGs were verified byte for byte against the recovered manifest.
The medals are 1254×1254 RGBA; the hero background is 1935×813 RGB. The production
app loads its own Compose resources and has no runtime dependency on the
prototype or another product root.

| Current domain reward | Mobile resource |
|---|---|
| `PROTEIN_ALL_DAY` | `reward_protein.png` |
| `FATS_PRESENT` | `reward_healthy_fats.png` |
| `FIBER_PRESENT` | `reward_fiber.png` |
| `COLORS_OF_DAY` | `reward_colors.png` |
| `THIRTY_PRODUCTS` | `reward_30_foods.png` |
| `FERMENTED_THREE_DAYS` | `reward_culture_keeper.png` |
| `SEVEN_DAYS_RECORDED` | `reward_7_day_rhythm.png` |
| Decorative header | `rewards_hero_background.png` |

The port preserves all seven evaluator rules, order, titles, descriptions,
actual progress, earned state and automation IDs. The header's earned count
comes from the snapshot and uses the existing localized progress template and
earned label. The prototype's Sprout name, four demo rules, Level 4, XP, and
static progress are not product data and were not imported into production.

The grid keeps equal columns, a 10 dp gap, 20 dp card corners, and 15 dp card
padding. It uses the shared 24 dp Home gutter for consistency rather than the
prototype's 20 px app-shell gutter. Paired card heights follow natural text
measurement; an odd final card fills the row. Narrow screens and larger fonts
use a single column. Original muted text was darkened for contrast while the
paper, ink, border and green colors retain the recovered palette.
