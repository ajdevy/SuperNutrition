# Independent review triage — before fixes

Both reviewers inspected the full tracked/untracked diff from7a141fac with fresh context. Raw findings are in review-blind.md and review-edge.md. No reviewer claimed runtime verification.

All ten distinct findings are implementation patches within the approved presentation goal; no domain/storage/privacy decision is changed:

| ID | Severity | Acceptance condition |
|---|---|---|
| R1 | medium | Production Fiber/Protein bars derive from the same typed demonstration quantities as their displayed values; unavailable Colors/Fats remain unavailable. |
| R2 | medium | RTL summary art reserves the same logical side as the text inset at normal/200% text. |
| R3 | high | The existing note field describes note entry, without promising to add a food ingredient. |
| R4 | high | The session review action says Confirm meal; it does not promise ledger/history storage. |
| R5 | medium | Standalone Rewards retains a visible Back button; the tab-shell presentation follows Figma without it. |
| R6 | medium | After IME dismissal, native Back cancels inline name editing while preserving Profile and the saved name. |
| R7 | medium | Android/iOS factories supply localized group labels and complete summary templates. Preserve the published two-argument iOS factory. |
| R8 | medium | Every actual card visibly displays its supplied progressLabel. Place a compact readable count in the existing footer spacing and retain natural sizing. |
| R9 | medium | Inline editing visibly explains the optional session name, including with IME and200% text. |
| R10 | medium | Input/Clear/Save have registry-backed IDs; native tests query By.res rather than English copy. |

The read-only quality gate additionally found MainTab's six parameters, one unused old visual assertion, and a shadowed test lambda parameter. Fix without suppressions. The formal gate is also blocked by stale physical bootstrap evidence; keep that limitation separate from code findings.

R11 (medium, patch; source recheck before fix): The newly restored standalone Rewards Back is overlaid without title-side clearance. Given a long localized title at200%, title glyphs can overlap its48dp hit frame. Reserve equal title-side space only in standalone mode; keep tab-mode geometry unchanged. Add a long-title200% regression before correcting it.

R12 (medium, patch; source contrast review before fix): The actual white camera glyph on the native bottom-bar circle `#7AB462` has contrast 2.4612:1, below WCAG 2.2 SC 1.4.11's 3:1 requirement for a functional icon. Add a screenshot test of the rendered glyph and circle before correcting it. Preserve the exact camera artwork and geometry, and darken only this circle to a nearby green with at least 3:1 contrast; document this accessibility exception alongside the Review color adjustment.
