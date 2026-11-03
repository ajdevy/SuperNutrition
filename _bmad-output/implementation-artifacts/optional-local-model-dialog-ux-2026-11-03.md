# Optional local model dialog interaction finding

## Finding

The missing-model dialog was shown immediately when the user opened the capture
surface. That modal covered the manual text-entry action and caused the emulator
journey tests `textMealCanBeSavedCorrectedAndDeletedThroughProfile` and
`backFromCaptureReturnsToHomeWithoutBottomBarInCapture` to fail before those
flows could interact with the capture screen.

## Acceptance conditions

- Opening the capture surface without the private AAR leaves its controls
  accessible, including manual text entry and Close/Back.
- Requesting photo capture or photo import without the private AAR displays a
  dismissible dialog that clearly says local identification is not attached.
- Dismissing the dialog leaves the capture surface usable; the meal diary and
  manual-entry flow remain available.
- The local AAR opt-in build continues to route photo actions to the attached
  runtime without showing the missing-model dialog.
