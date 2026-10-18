# Independent introduced-source review triage

Final disposition: R01–R04 are fixed by witnessed tests, R05's feature iOS source
compiles with UIKit runtime unavailable, R06 remains an unresolved prototype
action, and R07–R09 intentionally preserve the mock representation selected by
the human. The final shared UI run passes167tests with zero failures; the whole
quality command still fails on separate stale native-gap evidence.

Review checkpoint: baseline7ec615063ac166d69e7f4a81495e20098f25be3c, branch codex/figma-camera-flow, explicitly authorized Rewards snapshot7d5971c. Independent Blind Hunter and Edge Case Hunter read the3017-line source diff. They performed no builds or device interactions. Their static findings were followed by the controlled red/green runs recorded below; the earlier default Rewards screenshot alone was not fresh native evidence. No new Home production defect was established.

The Home spec's frozen no-Rewards-change boundary prevents incidental Home edits changing Rewards. The user's separate explicit instruction to include all26staged Rewards files authorizes the integration tracked in main-staged-rewards-integration-2026-10-17.md; its findings are tracked separately. This does not authorize inventing new nutritional reward/level criteria.

| ID | Finding and acceptance | Route | State |
| --- | --- | --- | --- |
| R01 | Normal header80dp frame clips even Rewards; actual full-character/ink layout must fit or use readable natural layout. | patch/high | Fixed: naturally sized centered header; witnessed normal-font red-to-green test, included in final167/0 UI run. |
| R02 | Normal long card copy clips fixed one-line frames; all actual title/subtitle characters must render within natural bounds. | patch/high | Fixed: actual text-fit fallback and shared measured/rendered typography; normal/enlarged character/glyph/frame tests pass. Enlarged paragraph-overflow/leading flags were refuted as verifier defects. |
| R03 | Back38dp touch frame below48dp; preserve38dp artwork inside48dp clickable frame and working callback. | patch/medium | Fixed:48dp hit region retains38dp visual circle/center; width, height and Compose callback tests pass. |
| R04 | Link color contrast3.407:1; actual small Text/backdrop contrast must meet4.5:1. | patch/medium | Fixed: darker olive#5D7120 yields about4.943:1 on#F7F3EF; actual Text/backdrop test passes. |
| R05 | iOS factory does not pass Back callback; preserve published2arg factory, safely pop/dismiss actual UIKit host. | patch/medium | Source fixed; actual feature iOS Arm64 compile passed after navigationController import. UIKit runtime unavailable/unverified. |
| R06 | See-all label has no action; an actual earned collection with reversible dismissal is absent. | unresolved prototype action | Still nonfunctional. No earned-collection interaction pass or completed action is claimed. |
| R07 | Static Level4/7earned/320points and96/155 bar do not follow real snapshot. | user decision/prototype | Intentionally preserved as mock level/points presentation; user retained10medals and prototype levels. No new domain criteria promised. |
| R08 | Earned/In-progress split by index ignores actual earned booleans. | prototype limitation | Index grouping is intentionally retained with the10-medal prototype; it is not an oracle for actual earned state. Canonical evaluator unchanged. |
| R09 | Visual goal durations/counts and3higher-tier duplicates lack matching domain definitions. | user decision/prototype | Intentionally preserved visual mock copy/slots; no new reward IDs, durations, thresholds or correctness guarantee introduced. |

## Final evidence and human representation decision

Human answer, direct conversation on2026-10-18:
«Сохранить 10 медалей; новые уровни пока оставить как макет».
The representation question is resolved as a retained prototype, not as approval
for new nutritional or level criteria. R07–R09 intentionally remain mock
presentation alongside the unchanged seven canonical domain criteria.

`focused-rewards-normal-and-diagnostic-red` witnessed23tests/9failures. Four
normal-font findings were real: header character loss, long-card character
loss,38dp Back target and3.4069164 contrast. Five enlarged findings were verifier
metadata defects: complete text and actual glyphs fit natural frames despite a
larger paragraph canvas/negative leading. The corrected oracle keeps exact
character coverage, no ellipsis, glyph/line spans and actual frame/clearance
checks with1px raster tolerance. `focused-rewards-complete-green` then passed
23/0/0/0 before shared typography was factored into one implementation.

Final `full-final-scoped` (2026-10-17T23:57:30.559541Z through
2026-10-18T00:01:03.374332Z) verifies167shared UI tests/0failures/0errors/0skipped
from30fresh XML suites, including15/0 PhotoLayout and8/0 RewardsReadability after
the typography and brace corrections. Exact command, hashes, raw log and XML
are under `mobile/build/home-no-scroll-2026-10-17/full-final-scoped/`.
The changed feature iOS Arm64 compilation actually executed and passed after
the missing `navigationController` import; UIKit runtime remains unavailable.
Overall Gradle exit remains1 solely on stale `bootstrap-android-host` evidence
at `verifyMobilePolicy`; whole quality success is not claimed.

Remaining limits are explicit: R06's See all action is unresolved/nonfunctional,
iOS Back has source compilation but no UIKit runtime evidence, and prototype
level/group/goal labels are not live earned-state results. Thus the passing
layout/readability suite is not a clean whole-interaction pass.

Normal-font regressions now exercise the fixed header/canvas and actual long
copy. Existing state/progress and refresh checks remain, while prototype
grouping and goal labels are not used as new domain oracles. Parent native Home
capture classification records all required tags, actual safe bounds,120dp
mascot and48dp action frames alongside full labels, card/control clearance and
native scrollability. Raw before/diagnostic captures stay immutable. This
documentation-only update leaves the source/tests frozen for the current
94-image APK matrix; native matrix results remain parent-owned.
