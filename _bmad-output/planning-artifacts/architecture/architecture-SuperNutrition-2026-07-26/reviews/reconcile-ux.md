# UX Reconciliation — Updated Architecture Spine

## Verdict

**Conditional pass.** The updated architecture has absorbed the load-bearing UX
shape: Compose owns the product surface, native camera/picker code is bridge-only,
one shared reducer owns the workflow, consent is explicit and attempt-scoped,
country is separate from language, raw-photo handling is bounded, the native
accessibility seam is testable on real devices, and the exact five-surface visual
gate is present.

It is not yet a complete build substrate for the final `DESIGN.md` and
`EXPERIENCE.md`. One country-flow requirement conflicts with snapshot immutability,
and four cross-platform behaviors still have no sufficiently exact invariant or
owner. These gaps can produce valid-looking Android and iOS implementations with
different privacy, navigation, accessibility, localization, or recovery behavior.

## Reconciliation by concern

| Concern | Result | Reconciliation |
| --- | --- | --- |
| UX state | **Partial** | AD-5 establishes one reducer and internal workflow states, but does not settle system Back, backgrounding, or the UX-required remote-transfer substates/effects. |
| Consent | **Partial** | AD-6/AD-8 correctly require explicit per-attempt consent before transfer. The before-transfer / transferring / transferred cancellation contract is not represented. |
| Accessibility | **Partial** | AD-23 strongly covers the native camera/photo seam. Equivalent Compose state-transition, Dynamic Type/font-scale, Reduce Motion, and real-image contrast contracts are absent. |
| Localization | **Partial** | AD-12 separates country from language and conventions define BCP 47. There is no owner/test contract for locale-aware number/unit formatting, text expansion, or bidi behavior. |
| Native bridge | **Landed** | AD-4 and AD-23 preserve Compose screen ownership, opaque `PhotoRef` exchange, native camera/picker behavior, accessibility semantics, and real-device evidence. No contradiction found. |
| Visual gate | **Landed** | Deferred `UX-A1 and visual mocks` exactly blocks visual Compose stories on approved tokens and the five UX-named mock surfaces. |
| Recovery | **Partial** | Typed failures and next-action semantics exist, but the UX rules limiting actions, suppressing impossible Retry, avoiding consent nag loops, and preserving manual alternatives are not architectural invariants. |
| Country behavior | **Contradicted / partial** | AD-12 lands the data/policy separation, but AD-20 freezes the snapshot while UX permits changing country/catalog from Food search during an active Analysis Session. First-use confirmation, persistence, and “Not listed” behavior also lack a state owner. |

## Findings

### UX-1 — High: country/catalog change conflicts with the immutable attempt snapshot

`EXPERIENCE.md` makes Country and catalog reachable from Food search and explicitly
allows a no-match user to change it during the Analysis Session (lines 42, 78,
194–197, and 245). `DESIGN.md` also defines this as contextual configuration with
locally persisted selection and “Not listed” recovery (lines 233–235).

AD-20 says an analysis attempt captures one `ConfigurationSnapshot` and cannot
change it before terminal state (architecture lines 170–174). The spine does not
say whether changing country:

- terminates the current attempt and starts a new one;
- preserves the photo, manual label, and user edits across that boundary; or
- defers the new country until the next Scan.

Those choices are observably different and affect catalog IDs, units, consent
copy, and provider region. Add an explicit country-change reducer event and
transition. The safest reconciliation is to terminate the old attempt, cancel its
effects, capture a new validated snapshot, and state exactly which non-sensitive
user input may be carried forward. Also assign local persistence and the
unsupported/“Not listed” no-US-default path to a port owned by the feature or
country-profile layer.

### UX-2 — High: remote consent and cancellation lack transfer-boundary states and effects

The UX distinguishes consent accepted before transfer, transfer in progress,
transfer completed, and cancellation after bytes may have left the device
(`EXPERIENCE.md` lines 97–105 and 123–127). It requires truthful copy, stopping
further work where possible, suppressing late results, and requesting server-side
cancellation/deletion where supported.

AD-5 names only broad consent/analysis/cancellation states; AD-6 invalidates late
results; AD-8 gates transfer on consent; and AD-18 makes transfer-start at-most-once
(architecture lines 80–102 and 158–162). None owns:

- the durable `bytesMayHaveLeftDevice` boundary;
- distinct ready/transferring/transferred states;
- a gateway cancellation/deletion effect and its idempotency; or
- the user-safe cancellation facts returned to the reducer.

Add these states and effect outcomes to the shared state/effect contract and the
mobile–gateway contract. Otherwise an implementation can satisfy all current ADs
while falsely presenting a post-transfer cancellation as if no upload occurred.

### UX-3 — High: Back/edge and app-background behavior are not defined by the architecture

The UX requires native Back/edge to follow shared state history, confirm before
discarding an Analysis Session, and never submit/upload/save
(`EXPERIENCE.md` lines 26–30, 139–140, and 173–179). It also requires backgrounding
to pause preview and route an active transfer through an explicit
background/cancellation policy (lines 132 and 183–185).

AD-5 says Decompose owns lifecycle and route boundaries, while the reducer owns
internal workflow state (architecture lines 80–84), but it does not specify how
platform Back or lifecycle signals cross that seam. Add shared `BackRequested`
and lifecycle events, a last-safe-state/discard-confirmation rule, and explicit
preview/transfer effects. Bind both app roots and Decompose integration tests to
the same transition fixtures.

### UX-4 — Medium: accessibility and localization stop at the native bridge

AD-23 is a strong camera/photo bridge contract, but the UX accessibility floor
also governs Compose states: focus moves to the changed heading, Analyzing is
announced once, large text reflows without truncation, Reduce Motion disables
specified motion, and dark/high-contrast controls are verified over real images
(`EXPERIENCE.md` lines 150–169). `DESIGN.md` additionally requires locale-aware
number/unit formatters, 30% text expansion, and bidi-safe alignment (lines
163–176 and 194–197).

The capability map assigns Compose accessibility/localization to AD-4, AD-12, and
AD-23 (architecture lines 297–310), but those rules do not own or test these
Compose behaviors. Add a shared presentation accessibility/localization
invariant and evidence gate covering reducer-driven focus/announcement events,
font scale/Dynamic Type, Reduce Motion, real-image contrast, locale formatting,
expansion, and bidi. Keep AD-23 for native bridge specifics.

### UX-5 — Medium: recovery action policy is not enforceable

The UX requires at most three outcome-specific actions, no generic Retry when
conditions cannot change, no remote-consent nag loop, and import/manual-search
alternatives when camera or local inference is unavailable
(`EXPERIENCE.md` lines 83, 89–105, and 165–169).

The architecture convention only says typed failures expose recoverability and
next-action semantics (architecture lines 202–209). It does not define a shared
action vocabulary, eligibility predicates, ordering, or the no-repeat consent
rule. Add these to the reducer contract and golden transition fixtures so local,
remote, camera, and catalog adapters cannot independently invent recovery menus.

## Confirmed landed behavior

- AD-4 and AD-23 match the Compose/native ownership boundary and prohibit a
  parallel SwiftUI/UIKit feature UI.
- AD-6 and AD-8 prohibit silent upload and require explicit attempt-scoped
  consent before transfer.
- AD-12 correctly separates country, language/locale, catalog, units, consent
  copy, and provider-region policy.
- AD-7 and AD-19 preserve the UX rule that identity inference cannot generate
  nutrition numbers.
- The deferred UX gate names the same five required mocks as `EXPERIENCE.md` and
  also requires accessibility/responsive annotations.
