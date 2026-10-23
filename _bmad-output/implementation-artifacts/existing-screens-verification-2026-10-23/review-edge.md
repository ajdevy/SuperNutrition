[
  {
    "location": "mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/CurrentRewardsScreen.kt:147-151",
    "trigger_condition": "Rewards renders in RTL; summary start padding mirrors while mascot coordinates remain physically left.",
    "guard_snippet": "Canvas(modifier.graphicsLayer { scaleX = if (rtl) -1f else 1f })",
    "potential_consequence": "Summary text and progress overlap the mascot, making actual reward totals harder to read."
  },
  {
    "location": "mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/ProfileScreen.kt:94-95",
    "trigger_condition": "Name editor remains open after IME dismissal; Android system Back is pressed.",
    "guard_snippet": "onBack = { if (nameEditing) cancelNameEdit() else closeProfile() }",
    "potential_consequence": "Profile closes instead of cancelling the name edit and remaining on Profile.",
    "kind": "deletion",
    "confidence": "high"
  },
  {
    "location": "mobile/feature/photo-analysis-ui/src/commonMain/kotlin/com/supernutrition/feature/photoanalysis/CurrentRewardsScreen.kt:266-276",
    "trigger_condition": "A partially completed reward has a numeric progressLabel, such as 18 of 30.",
    "guard_snippet": "Text(card.progressLabel)",
    "potential_consequence": "Exact actual progress counts disappear visually; only accessibility semantics retain them.",
    "kind": "deletion",
    "confidence": "high"
  }
]

## Source recheck — 2026-10-23

- R2: `CurrentRewardsScreen.kt:200-212` mirrors both the mascot image and its clip about the canvas center in RTL. This now follows the logical start reservation in the summary's padding. The LTR branch retains its original coordinates.
- R8: `CurrentRewardsScreen.kt:301-319` paints the supplied `card.progressLabel` as text beside the existing source-derived progress bar. Group sizing still measures natural intrinsic height, including the new footer; invalid progress fractions remain guarded.
- R6 shared state: `ProfileFlow` supplies the same `ProfileFlowState` to `ProfileScreen`. Header cancellation, host cancellation, and accessibility cancellation close editing without changing the saved name. Save rejects blank input, trims the draft, calls the name owner, and closes editing. Entering either destination clears editing, and history cannot open a hidden editor.
- Saver branches: legacy Boolean destinations remain accepted; two-Boolean lists preserve destination/editing mode; malformed shapes/types return null. Restoring simultaneous history/editing normalizes editing off, preserving the destination invariant. Existing constructor callers use defaults or named `currentDay`, so insertion of the optional editing argument does not redirect a caller's day provider.
- No new source defect was found in these patched branches. This is source-only confirmation: this reviewer ran no Gradle, JVM, device, or Apple checks. The Android edit Back handler is intentionally pending the root's native red/green sequence and is not reported again as a new finding. Runtime closure still requires the root's fresh focused/full test and native Back evidence.
