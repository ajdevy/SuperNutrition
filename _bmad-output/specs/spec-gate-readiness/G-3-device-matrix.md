# G-3 readiness: what the device matrix benchmark must supply

**Owner:** local inference owner + QA. **Blocks:** production Android/iOS
local-engine stories, including E7.4's last acceptance criterion.

As with G-1 and G-2, this approves nothing. It turns the spine's exit artifact —
*"signed real-device benchmark selecting OS/device tiers, runtimes, models,
download policy, and latency/memory/thermal/battery budgets"* — into a list
someone can tick, and records what the code has already been built to accept.

The mechanism is deliberately inert. `DeviceTierMatrix.unmeasured` is empty,
every tier is therefore unqualified on both platforms, no local engine activates,
and every typed description is answered by the E7.2 deterministic floor. That is
not a degraded state waiting to be fixed: it is the UJ-4 journey, on the low-end
offline device least able to run a model, which the floor exists to serve.

## What must be supplied

| Input | Why the code needs it | State today |
| --- | --- | --- |
| **The tier list itself** | Nothing in the code enumerates tiers; `DeviceTierId` is an opaque declared id, because an enum of low/mid/high would be the code guessing this gate's answer | No tiers exist |
| **Per-tier model storage budget** | `DeviceTierBudget.modelStorageBytes` — how much of a user's disk a model may occupy | Unmeasured |
| **Per-tier download budget** | `downloadBytes` — separate from storage, because a compressed download and an unpacked model are different numbers and users pay for one of them on cellular | Unmeasured |
| **Per-tier peak memory budget** | `peakMemoryBytes` — the number that decides whether an attempt is killed mid-inference | Unmeasured |
| **Per-tier thermal budget** | `sustainedAttemptsBeforeThrottle` — thermal headroom expressed in the unit a user feels, rather than in degrees nobody can act on | Unmeasured |
| **Per-tier battery budget** | `batteryMilliPercentPerAttempt` — the cost of one attempt | Unmeasured |
| **Per-tier attempt latency budget** | `attemptLatencyBudgetMillis` — NFR-1 replaces the SM-3 hypothesis with a measured budget here, and SM-C2's "do not run models that violate latency limits" has no limit to enforce until it exists | Unmeasured; SM-3 is still assumption A-5 |
| **Camera-ready latency (SM-2)** | Measured before any engine runs and does not vary with the model, so it is not a per-tier engine budget and has no field here | Still assumption A-4; owned by the camera path |
| **Named physical devices and OS versions** | `BudgetEvidence` — a budget cannot be constructed without them | Nothing measured |
| **Runtimes, models, and download policy** | Which model on which runtime the budget describes; a budget without them describes nothing repeatable | Not selected |

## What the code already commits to

These are decisions the benchmark must live with, not requests.

- **No parameter-count proxy, enforced structurally.** NFR-3 forbids a universal
  proxy, and there is no parameter-count input on any type in
  `DeviceTierBudget.kt`. A number derived from a model's size has nowhere to go,
  because a budget cannot exist without evidence naming the device it was
  measured on.
- **A simulator is not evidence.** `BudgetEvidence` rejects the obvious
  simulator and emulator identifiers. This cannot catch a deliberately
  mislabelled measurement; it catches benchmarking on the emulator that was
  already running, which is how it actually happens.
- **Android and iOS qualify separately.** The matrix is keyed by tier *and*
  platform. A measurement on one platform does not qualify the other, because
  the PRD already says the two may use different adapters, runtimes, models, and
  tiers.
- **All six dimensions or none.** A budget missing thermal because it was
  awkward to measure activates a device on five dimensions and throttles on the
  sixth, which a user experiences as the app becoming slow rather than as a
  missing measurement.
- **An unknown tier fails closed.** The lookup is exact. "Close enough to the
  mid tier" is how a device gets a model it cannot run.
- **Unmeasured is reported as unsupported, never as over-budget.** Those are
  different facts, and reporting the first as the second claims a measurement
  that never happened. `CapabilityReason.RESOURCE_BUDGET` stays reserved for an
  attempt that would exceed a budget that exists.

## How we will know the gate is satisfied

Not "a benchmark was signed" but:

1. `DeviceTierMatrix.unmeasured` is replaced by a matrix with an entry per
   (tier, platform) pair the release supports, and the test asserting it is empty
   is retargeted rather than deleted.
2. Every entry carries `BudgetEvidence` naming a physical device and OS version,
   and the measurement date, so staleness is a question someone can ask.
3. Each supported tier's six budgets are present and positive — the type already
   refuses anything else, so this is satisfied by construction or not at all.
   Six, not five: NFR-3 names storage, download, memory, thermal and battery,
   and the exit artifact quoted above adds latency. An earlier draft of this
   document quoted the artifact and then built the checklist from the NFR, which
   dropped latency from both the type and this list — an operator could have
   ticked every item and closed the gate with SM-3 still unvalidated.
4. SM-2's camera-ready time is separately recorded as measured or explicitly
   deferred. It is not a per-tier engine budget, so nothing here will notice its
   absence; that is precisely why it needs naming.
5. The telemetry `resource_bucket` vocabulary is revisited. It is currently
   absolute — `low`/`moderate`/`high` — precisely because no budget existed to
   be relative to. Once budgets exist, whether the bucket should become
   budget-relative is a v3 question, not a configuration change: the vocabulary
   is pinned in `contracts/inference/v2`.

## The dependency worth naming

E7.4's last acceptance criterion is this gate, and nothing else in E7.4 waits on
it. The identification floor, the segmentation contract, the route, and the
fixture set all ship without a single measured device. That ordering was
deliberate — CAP-1 cannot depend on CAP-3 — and it means G-3 can take the time a
real benchmark needs without holding typed identification hostage.

What G-3 does hold is *activation*. Until it closes, the local text engine is
unsupported on every device, which is a true statement rather than a limitation
being hidden.
