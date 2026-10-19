# Home review for popular screen sizes

Request: create new document and HTML versions containing only current popular screen sizes. Preserve the complete existing review and app source.

Selection: worldwide StatCounter screen-resolution observations for September 2026, the latest complete month. Six leading mobile sizes: 414×896, 360×800, 384×832, 390×844, 393×873 and 360×780. Three leading distinct portrait tablet sizes: 768×1024, 800×1280 and 820×1180. The landscape 1280×800 observation is not a separate portrait composition. Sources are retained in the new gallery selection manifest.

Acceptance:

- New self-contained review folder, document and HTML; original 100-case folder remains byte-identical.
- Exactly nine selected portrait logical sizes, each at 100% and 200% system text. Retain failing cases as well as passing ones.
- Reuse the verified production APK. Capture missing exact sizes on the task-owned API35 x86_64 emulator, using sequential viewport overrides and existing foreground guards. Visually inspect every PNG immediately before another device action.
- A web screen-resolution statistic selects the composition sizes; it is not native installed-device share. Android emulator captures at those logical dp sizes are not iOS or model-specific proof.
- Keep raw PNGs, native hierarchy, runtime dimensions, APK hashes and visual findings. Never resize screenshot pixels or infer a pass from a nearby size.
- Russian HTML with ordinary/large-text comparison, type/status filters, sources and working local links. Accompanying Markdown document with the same exact profiles and outcomes, matching the original document format. The latest user request prioritizes the HTML.
- Restore the review emulator's original size, density and text settings. No new AVD is needed. Do not touch physical phones or the default ADB server.

Implementation: allow the existing capture helper to accept an explicit output directory, preserving its default behaviour and sharing all capture/fit checks. Keep this document-generation task independent of Gradle and product runtime changes.
