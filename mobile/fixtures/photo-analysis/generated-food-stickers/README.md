# Generated food sticker library

The integrated library contains **152 food identities**: 17 original Figma
identities, 30 initial generated illustrations and 105 additional generated
illustrations. There are 156 runtime PNGs, including four original selected
variants. Availability of artwork never inserts a food into a photo result.

`catalog-152.json` records all identities and 574 unique normalized whole-name
aliases. `expanded-library-manifest.json` records the 105 newly integrated
PNGs, exact source metadata paths, SHA256, dimensions and visual audits.
The per-image JSON files retain the exact generation prompts. Every new image
was generated separately through the built-in ImageGen tool, inspected and
copied into Compose resources without modifying its pixels.

The additional set includes 21 fruits/berries, 22 vegetables, 10 grains,
9 meats, 13 fish/seafood, 21 plant foods and 9 dairy products. Plant milks,
individual nuts, brown rice and olives have distinct identities. Spinach now
has its own artwork; general leafy greens retain their original illustration.
Mixed dishes and unrecognized labels keep their actual text instead of
receiving an illustration of one constituent.

## Initial 30 generated illustrations

The initial library added these 30 illustrations to the original stickers:
banana, apple, orange, tomato, potato, rice, chicken, egg, cheese, yogurt,
strawberry, grapes, beans, mushroom, bread, beef, lentils, pear, oats, pasta,
milk, watermelon, lemon, nuts, fish, onion, peach, mango, pineapple and kiwi.

Each asset was generated through the built-in `image_gen` tool in a separate
call. No CLI or external API runner was used. The salmon, avocado and cucumber
stickers in Compose resources were visually inspected as style references.
The generated images use photorealistic food texture, natural colors and a
thin white die-cut outline on an actual transparent background.

## Files

- `manifest.json` records the first three assets already saved in Compose
  resources, their exact prompts, original generator paths, SHA256 digests
  and alpha statistics.
- `broad-library-prompts.json` records the exact prompts and intended resource
  paths for the 27 additional assets.
- `broad-library-manifest.json` records the completed additional library,
  workspace paths, SHA256 digests, alpha statistics and visual inspection.
- `<food>.json` records each additional asset independently so a partially
  interrupted batch cannot lose provenance.
- `staged/food_sticker_<food>.png` contains the 27 original generated PNGs.
  Copy these to their recorded Compose resource paths at a stable build
  checkpoint; keep their source files here.

All 30 generated PNGs are 1254 x 1254 RGBA. Their alpha channels span 0..255,
with transparent background pixels and a mostly opaque food interior.
Generated alpha was preserved without image editing. The tomato image has
one sampled corner at alpha 1/255; the remaining sampled corners are alpha 0.
This subvisible fringe is recorded in the manifest. The 27 additional PNGs
total 41,964,256 bytes (40.0 MiB).

Every output was visually inspected individually for the intended food,
complete recognizable silhouette, consistent border, transparent background
and absence of text or UI decorations. Generation does not establish detector
coverage: show an illustration only when its identity matches a detected
food. Unknown labels must retain an honest text fallback.

The bundled identity catalog contains 3,519 canonical food records and 582
dish records, derived from the 7,793-record SR Legacy source inventory. These are reusable illustrations of common foods,
not a claim that every catalog variation has a unique icon.
