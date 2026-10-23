# Meal history implementation findings

- Profile history is currently an AlertDialog; acceptance is a dedicated route with search and header/system Back.
- Ledger has day and grounded product/serving data, but no stored time, image or dish title. Actual rows must expose missing metadata honestly; design meals/images are only explicit fixtures.
- Production photo confirmation does not populate the grounded ledger. This task renders the existing ledger and its honest empty state, without inventing a new confirmation/storage contract.
- Existing Profile count groups only mealId. The history adapter/count must share grouping by (localDay, mealId) to avoid combining repeated IDs on separate days.
- Native regression is added before the fix and expects `meal_history_search` after tapping Profile history.

## Initial design fidelity inspection

- Search prototype differs from supplied design: 16dp corners instead of pill, placeholder wording/font/padding differ. Acceptance: Figma placeholder, 15sp type, 16dp horizontal inset, pill shape; clear action must not change field height.
- List starts 4dp early and adds an extra 16dp before the second date group. Acceptance: at 375x812 with 44dp native top inset, first card starts y198 and Yesterday starts y368.
- Card/header/search soft shadows are absent. Acceptance: restore subtle elevation while preserving actual asset dimensions and readable enlarged text.
- Verified initial layout failures: first card y194 instead of y198; entering search grows the field from48 to72dp. Red layout XML stores3tests/2failures before fixes.
- Existing shared font helpers do not set variable axes. Font metadata defaults are Nunito wght200 and Fraunces wght900; this is visible in the new screen's thin body labels/heavy header. Acceptance: set explicit Nunito400/600/700 and Fraunces600 variation settings locally for history, preserving unrelated screens.

- Full-run test compilation exposed v2 waitUntil argument ordering in the new font-readiness helper. Acceptance: pass timeoutMillis by name; no UI test result is claimed from this failed compile. Native production-source compilation and instrumentation proceed independently under --continue.

- Explicit arm64 compilation caught an unresolved Foundation category extension, secondsFromGMT, in the new Apple day clock. Acceptance: use the Foundation member secondsFromGMTForDate with the same NSDate sample and compile the real iosArm64 sources successfully; JVM success does not substitute for this.

## Independent review acceptance

- Patch, medium: the Figma muted text #A08D80 has roughly3:1 contrast on white/light background, below the existing normal-text AA contract. Add an independent rendered-text contrast assertion, watch it fail, and use a slightly darker warm muted token only for the new text. Keep layout/assets intact.
- Patch, medium: new Apple history reference used local time while the existing Apple ledger uses UTC day. A meal confirmed late in Moscow can immediately appear as Yesterday. Align the reference clock with the existing host ledger convention; Android stays local, Apple stays UTC. Stored dates and the legacy ledger policy remain unchanged.
- Patch, medium: Java/Kotlin regex \s does not split pasted non-breaking/thin spaces, so otherwise valid multi-ingredient queries can return no result. Acceptance: Unicode whitespace uses the same KMP Char.isWhitespace handling as trimming; add NBSP/thin/em/ASCII whitespace cases before changing tokenization. Both independent reviews completed; three deduplicated actionable findings, all scheduled for verification.

- Review regressions reproduced before patches: 12 focused tests,2 failures; date-label contrast2.9535:1 and pasted NBSP search failure. The other10 tests passed, including resource-font readiness and 200% layout. Final patches use #7D6B5F text (4.72:1 on the background,5.07:1 on white), common Unicode whitespace normalization, and a host-convention reference clock. Apple source follow-up independently confirmed the UTC alignment; execution is still unclaimed.

## Final font fidelity correction

- Patch, medium: explicit history-only font settings produce a visibly sans-serif history header while the existing Profile snapshot renders Fraunces correctly. The loaded-resource predicate alone does not verify rendered typeface fidelity. Acceptance: reuse the established Fraunces/Nunito helpers and visually recheck the same fixture; remove redundant platform font hooks. Compose resources1.11.1 already derives default variation settings from weight/style (primary FontResources.kt), so the earlier metadata-only diagnosis did not establish the need for a separate font system. The full205-test module passed before this visual correction; final verification must rerun after it.

- Final read-only lint caught only wrapping/line-length in the new Unicode-search regression assertion. Acceptance: wrap its argument list without changing test logic, then rerun the relevant complete checks; the ongoing native/Apple tasks inspect unchanged production inputs.

- All three independent-review patches were rechecked by their reviewers. Final snapshots show Fraunces plus weighted Nunito through the standard resource API; geometry/200% and rendered contrast pass.
- Typed Detekt emits three compiler-analysis errors because its existing setup analyses common expect and platform actual declarations as one module. Debug output confirms this precisely; real target compilations succeed. This analyzer configuration limitation is deferred, documented without a suppression/skip, and is not presented as complete typed-analysis evidence. Final gates still fail closed on stale bootstrap-android-host evidence.
