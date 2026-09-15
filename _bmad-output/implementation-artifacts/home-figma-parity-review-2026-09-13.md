# Home Figma parity review — 2026-09-13

## Evidence

- Figma file: `08zQiW2IIHfQUBN3JDRUcZ`, node `14:125`, `375 x 812`.
- Live Android package: `app.supernutrition` on `DNY_NX9`.
- The live frame renders, but its visual hierarchy does not match the Figma frame.

## Findings

1. `HomeScreen` uses a `375 x 730` canvas and is placed below `statusBarsPadding()` and the reserved Scaffold bottom bar. The Figma frame is a full `375 x 812` composition with the background continuing behind system bars and the tab bar.
2. Header, speech bubble, mascot, rhythm card, next-step card, and controls use approximate coordinates and sizes rather than the node geometry from Figma. The live screen therefore has an oversized bubble and card typography and a misplaced header/mascot.
3. Figma uses Fraunces and Nunito typography, while the live screen falls back to the platform default font. The text shapes and line breaks visibly differ.
4. The implementation omits the Figma background layer and uses rasterized 15px metric icons where the design provides vector exports.

## Acceptance conditions

- The Home content uses the Figma `375 x 812` coordinate space and scales uniformly to the available display.
- Header, prompt, capybara, rhythm card, next step, and bottom navigation align with node `14:125`; the background continues behind system bars.
- Fraunces/Nunito font resources and exported Figma assets are used without hand-drawn icon replacements.
- Add a meal, the center camera, Rewards, profile, and View goals retain their existing real callbacks and semantics.
- The common Home tests and Android production/instrumentation compilation remain green.
