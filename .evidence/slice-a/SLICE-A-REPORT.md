# Slice A — merged #29, connected the client, ran it on a device

Worktree `/Users/jj/Work/SuperNutrition-integration`, branch `integration-test`, tree clean.
Primary checkout `/Users/jj/Work/SuperNutrition` untouched. Nothing pushed; no PR created or modified.

**Photo → analysis → result ran on the emulator. Yes, for real.** The gateway answered HTTP 200
to a request made by the app on a Pixel_9_Pro emulator, and the review screen rendered the
backend's dish candidates fused with local component evidence.

---

## What

Nine commits on top of `531fee3`:

| | |
|---|---|
| `77bfaa6` | merge #29 — 34 conflicts resolved |
| `80769e6` | fix: gateway I/O dispatcher + attempt-id mapping |
| `1d55d30` | feat: E4.2 wired into the seam, test-mode fenced |
| `c507ced` | feat: Android runtime prerequisites |
| `b5e2cdd` | refactor: hand-written codec + coverage denominator |
| `4c5bdd0` | test: photo → analysis → result Maestro flow |
| `0508462` | refactor: one preparation-failure path |
| `9025e4f` | style: format merged call sites |
| `7788c2e` | docs: device-run evidence |

### The merge

**Contracts — two different contracts collided on one path.** HEAD's `contracts/inference/v2`
declares `contract_id = "inference-engine"`; #29's declares `"inference-evidence"`. They are not
two versions of one contract, so they cannot be unioned — the result would carry two contract_ids
and fail both validators. HEAD's release keeps the path its validator binds to; #29's moved
verbatim to `contracts/inference-evidence/v2`, the path matching the id it declares. No release
content on either side was altered. #29's Kotlin binds the contract_id string, not the path, so
only shell validators needed repointing.

**This contradicts the brief's "do not invent new contract paths", which assumed #29's work was
additive to the same contract. It is not. Needs contract-owner approval.**

Other resolutions: HEAD's grouped `PhotoFormat` is canonical and #29's flat call sites were adapted
across 12 files; #29's richer reducer (staged events, `CandidateReview` carrying a draft) supersedes
HEAD's; the Android host controller took #29's launcher/scrubbing plus HEAD's typed diagnostics;
`CoverageVerifiers` combines HEAD's single read path with #29's per-module floor and branchless
tolerance. One symbol collision (`requireContractV2`) was renamed to `requireEvidenceContractV2`.

### Two defects only a device could find

Both invisible to the existing tests, which drive a fake transport on the JVM.

1. **`NetworkOnMainThreadException`.** `HttpGatewayTransport` is a suspend function doing blocking
   I/O; the host launches on `lifecycleScope`. Every device call died before opening a socket, and
   the engine reported it as an ordinary transport fault, hiding the cause. The transport now owns
   its dispatcher.
2. **Attempt-id shape.** The Android host's attempt id is `"android-photo-attempt"`; the gateway
   requires `^att_[A-Za-z0-9]{16,64}$` and answered 400. The engine now maps it by digest — stable
   across retries, because this is the gateway's per-attempt anchor, and the raw value never travels.

### Coverage

`:inference:remote` had no denominator, which AD-33 forbids. It could not be registered at 55.93%
branch, and 71 of 78 missed branches were generated serializer constructor code. Rather than lower
a floor or exclude generated code — both exclusions AD-33 rules out — the generated code went:
`GatewayCodec` reads and writes the envelope explicitly. **Branch 55.93% → 92.31%, line 99.70%**,
all 9 remaining misses in hand-written code. Registered as a production unit with one denominator.

---

## How verified

| Claim | Evidence |
|---|---|
| Device made a real gateway call | server log `route:/gw/v1/inference/photo`, `statusCode: 200`; request count 0 → 1 across the run |
| Result rendered on device | `device-photo-analysis-result.png`, `device-ui-hierarchy.xml` — "Dish candidates / Mixed salad bowl / Garden salad" |
| Those candidates came from the backend | they are the `StubInferenceProvider` fixtures; no such strings exist in the client |
| Mobile tests | `jvmTest` **645/645**, counted from JUnit XML, not from BUILD SUCCESSFUL |
| `:inference:remote` | 44 tests, branch 92.31% / line 99.70% from the Kover XML |
| Backend gate | `./quality-check` passed — 881/881, 95.25% stmts, 93.78% branches |
| Contract validators | inference-contract, inference-v2-contract, root-boundaries, mobile-api-contract, product-identity all exit 0 |
| Isolation | primary checkout clean; `origin/integration-test` does not exist |

The three intermediate device runs are part of the evidence: the flow first hung at
`Analysis is offline`, then reached the gateway and got 400, then 200. Each step was diagnosed from
the device, not assumed.

---

## Self-review

**Deliberately deferred to Slice B**

- **Production consent UX.** The fenced path synthesises a granted receipt. It is bound to
  debuggable + a debug-source-set manifest flag, and names only the fixture market so the gateway's
  own market policy would reject it even if the fence were bypassed. No consent UI exists yet.
- **The v2 component slot is a fixture, not `LocalAndroidComponentInferenceEngine`.** That engine's
  `enforceProductionActivation()` correctly refuses a non-PRODUCTION manifest. Satisfying it would
  mean stamping a fixture with product, engineering, legal, corpus, benchmark and rollback approval
  refs it does not have. Forging those to make a demo run would disarm the control that matters, so
  the fenced path declares itself a fixture and that gate is untouched. **This deviates from the
  brief's "keep the v2 slot local".**
- **Full UJ-catalog coverage** of the new review states; the added flow asserts the fused result only.
- **Review-screen design** — the rendering is #29's, unchanged.

**Judgement calls worth challenging**

- `feature-host → inference` was added to the module boundary inventory. The host already binds the
  platform adapters and the launcher seam is declared in that module, so without the edge no host
  can bind an engine. Architecture-owner call.
- The iOS bridge and normalizer took #29's version wholesale. HEAD's
  `TrackedPhotoPickerViewController`, which distinguished a user swipe-dismiss from a programmatic
  one, has no #29 equivalent and **was dropped**. iOS cannot be compiled or run on this host, so it
  is flagged rather than hand-spliced. iOS-owner call.
- Item 3 of the brief (`AnalysisFailed → Recovery`) needed no new code: #29's
  `ReviewEvidenceUnavailable(requestId, reason) → Recovery(reason)` already provides it, and it
  fired on the device as `RecoveryReason.OFFLINE` during the first run. Adding a second event would
  have duplicated it.

---

## Open

**The mobile gate is red on exactly one item**, introduced by the merge itself:

```
:qualityCoverageJvm — JVM module :domain:inference branch coverage is 82.33%; minimum is 85%
```

Pre-merge HEAD had **no** per-module branch check; #29 added it. Its own v2 model files plus HEAD's
(`EngineCapabilityV2`, `InferenceResultV2`, `InferenceTelemetryEventV2`, `InferenceSourceSpan`,
`InferenceItemGroup`, …) now share one denominator, and all 112 missed branches are in `<init>` of
`@Serializable` types. I wrote negative tests for every `require` in `ComponentModelManifest` and for
its value semantics: **coverage did not move by a single branch**, so those paths were already
covered and the missing ones are generated serializer constructor code. I removed the redundant
tests rather than leave noise.

Fixing it honestly means doing to `domain/inference` what this slice did to `inference/remote` —
hand-writing the codec for #29's v2 model. That is the domain owner's call and a slice of its own.
I did not lower the floor, exclude the module, or add a suppression: `suppression-registry.toml` is
deliberately empty and entries need named owner approval.

**Also open**

- `verify-release-immutability` reports `contracts/mobile-api/v1` RELEASE.md, compatibility-matrix
  and schema modified since `c091a43`. **Pre-existing, not introduced here** —
  `merge-base(origin/main, HEAD)` is `c091a43` both before and after the merge. I fixed a real shell
  bug in that script (a nested `case` inside a command substitution garbled its own message) but did
  not touch the release. Contract-owner call.
- No iOS verification of any kind; Apple evidence is optional per AGENTS.md.
- The Maestro flow is written but **was not executed** — the device run was driven through adb/
  uiautomator. Running it needs the Maestro CLI on this host.
- `:inference:remote:assemble` fails on lint-tool dependency verification; pre-existing and
  unrelated — `:platform:photo-android:assemble` fails identically on untouched code.
