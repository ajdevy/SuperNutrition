# Additional staple food stickers

Eight distinct standalone PNGs generated through the built-in `image_gen` tool
on 2026-10-12. Each is 1254 by 1254 pixels in RGBA with genuine transparency
and the established realistic food texture and thin white cutout outline.

The existing mixed-nut, white-rice and banana assets were inspected as style
references before generation. No existing food image was reused or edited.

| Identity | PNG |
| --- | --- |
| `brown_rice` | [food_sticker_brown_rice.png](food_sticker_brown_rice.png) |
| `almonds` | [food_sticker_almonds.png](food_sticker_almonds.png) |
| `peanuts` | [food_sticker_peanuts.png](food_sticker_peanuts.png) |
| `walnuts` | [food_sticker_walnuts.png](food_sticker_walnuts.png) |
| `cashews` | [food_sticker_cashews.png](food_sticker_cashews.png) |
| `pistachios` | [food_sticker_pistachios.png](food_sticker_pistachios.png) |
| `hazelnuts` | [food_sticker_hazelnuts.png](food_sticker_hazelnuts.png) |
| `olives` | [food_sticker_olives.png](food_sticker_olives.png) |

[prompts.json](prompts.json) preserves the eight separate generation prompts.
[manifest.json](manifest.json) and individual identity JSON records preserve
the generator source path, intended resource path, timestamp, SHA-256, byte
size, alpha histogram/bounds and per-output visual inspection notes.

Validation confirmed eight unique PNG hashes, matching per-file metadata,
RGBA mode, alpha range 0 to 255, transparent corners and complete visible food
silhouettes within the canvas. Outputs were copied unchanged to this folder;
Python was used only for validation and copying.

These files await root-task resource/catalog integration. Their existence does
not expand detector labels or establish recognition accuracy. No production
code, tests, builds, device state or Git staging was changed in this asset task.
