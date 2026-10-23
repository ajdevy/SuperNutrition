# Current mobile MVP assets — 2026-10-23

Exact local exports from file `08zQiW2IIHfQUBN3JDRUcZ`: Home `604:294`, Rewards `604:860`, Review `572:17720`, name editing `604:1056`. Runtime source is `src/commonMain/composeResources/drawable/`; no temporary Figma URL is used at runtime.

- Home: `refresh_home_garden` (473×1024 original JPEG, losslessly re-encoded to PNG for Compose), `refresh_home_sprite` (2241×2664 RGBA sheet), logo40.5, sun16, metric icons20, profile22, tail17×15.5. The native sprite uses the single exported sheet, 71 frames at12fps, nine columns, 249×333 pixel stride and241×325 pixel visible crops. Reduced motion holds frame0.
- Navigation: exact Home22 and trophy16.6148×17.1 artwork inside22dp slots, camera25.4545 inside48dp circle. SVG viewBox dimensions are preserved; exported vectors are rasterized directly at4× with Sharp0.35.5.
- Rewards: summary background1029×366, mascot1254², seven original384² food illustrations, check22.5. Four unearned Figma MCP preview URLs returned transparent32² placeholders; the exact original image fills were recovered with `download_assets` for nodes604:881/882/888/889. Rendering applies the source design’s25% saturation and90% opacity to actual unearned rewards, and never infers earned state from the artwork.
- Review: sixteen original1254² food stickers are retained as `refresh_sticker_*` and copied unchanged to their existing `food_sticker_*` resource names. Dynamic evidence determines which ones appear. Selected glow is a native alpha blur using the original image (7dp at65%,3.5dp at100%, leaf#7AB462); the current22.5 badge remains the explicit selection cue. Exact choice check17, plus20 and pencil20 replace text symbols and older icons.
- Name editor: exact12px clear and18px confirmation vectors inside48dp touch frames. Keyboard remains native.

The downloaded raw files remain unchanged. Runtime PNG vectors are derivatives of those SVGs, not extracted paths or recreated icons. All listed images are real assets, not baked screenshots of screens.
