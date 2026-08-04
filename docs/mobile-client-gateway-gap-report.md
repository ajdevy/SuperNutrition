# Mobile client ↔ backend gateway: connection status and gap report

Date: 2026-08-04
Worktree: `/Users/jj/Work/SuperNutrition-integration` (branch `integration-test`)
Primary working copy `/Users/jj/Work/SuperNutrition`: **untouched, clean**
Nothing pushed; no remote PR created, updated or closed.

---

## Verdict

**The transport layer is connected and proven. The product flow is not, and cannot be
without changes the external client developers own.**

The gateway answers a real request from the real client code over real HTTP, end to end.
What is missing is entirely on the client side, and it is missing *by architectural
design*, not by oversight: the photo-analysis feature has no seam through which an
inference engine can be reached, and the module boundary policy forbids creating one
where the state currently lives.

---

## What is proven working

| Claim | How it was verified |
|---|---|
| Gateway accepts a contract-valid request and returns ranked candidates | `curl` against `127.0.0.1:3000/gw/v1/inference/photo`, HTTP 200, two candidates |
| The client engine speaks the released contract correctly against the real server | `LiveGatewayIntegrationTest` → `:inference:remote:jvmTest`, green |
| That test is not vacuous | Negative control: with the server stopped it **fails** (`inference.failure.network`) |
| The class proven is the class that ships in the APK | `HttpGatewayTransport` moved to shared `jvmAndroidMain`; compiled into **both** `build/classes/kotlin/jvm` and `build/classes/kotlin/android` |
| Consent is enforced before the socket opens | `deniedConsentNeverReachesTheNetwork` points at a closed port and still gets `consent_required`, not a connection error |
| Full module suite | **40/40 green**, stable across 3 consecutive `--rerun-tasks` runs |
| Mobile gate | `./gradlew qualityCheckJvmAndroid` → BUILD SUCCESSFUL |
| Backend gate | `./quality-check` → passed (881/881 tests, 95.25% stmts / 93.78% branches) |

Backend runs with `TEST_MODE=true STUB_INFERENCE=true`, so no `OPENROUTER_API_KEY` is
needed. `STUB_INFERENCE` is fenced: `assertStubFence` throws at boot if it is set without
TEST_MODE, so a stub can never answer a real caller.

### Two real defects found and fixed by doing this

1. **`effectId`/`requestId` could never both be valid.** The engine derived both from one
   `idFactory`, but the contract requires `^(eff|req)_[A-Za-z0-9]{16,64}$` — underscores
   are illegal *after* the prefix. Any factory value made one of the two malformed, and
   the gateway rejected the first real call with `validation-error`. The unit tests missed
   it because they used a fake transport that never validated the envelope. Fixed: the
   factory now yields a bare token and the engine applies each prefix. `GatewayContractShapeTest`
   encodes the released patterns so this class of drift fails in `jvmTest`, not on a device.
2. **Locale-dependent consent digest.** `"%02x".format(...)` renders digits per the default
   locale; the digest must be byte-identical to the gateway's. Replaced with a literal hex
   alphabet.

---

## The gap: what the client developers must add

Ordered by dependency. Items 1–3 are hard blockers; nothing works until all three land.

### 1. `feature` may not depend on `inference` — boundary policy

`mobile/config/architecture/module-boundaries.toml`:

```toml
[[allowed_edge]]
from = "feature"
to = "domain|core"          # <- no "inference"
scope = "production"

[[allowed_edge]]
from = "apps"
to = "feature|feature-host|feature-presenter|feature-ui|platform|inference|data|core"
```

Only `apps` may reach `inference`. But `PhotoAnalysisComposeActivity` — the composition
root that owns the state — lives in `mobile/feature/photo-analysis/src/androidMain/`.
So the engine cannot be constructed where the state is.

**Decision required (owner: architecture):** either declare a `feature → inference` edge,
or move the composition root into `apps/androidApp`, or introduce a `feature-host` module
that owns wiring. This is not a client-developer call; it changes an enforced inventory.

### 2. No dependency-injection seam on the feature

```kotlin
// mobile/feature/photo-analysis/src/commonMain/.../PhotoAnalysisFeature.kt
class PhotoAnalysisFeature(
    attemptId: AttemptId,
    leases: PhotoLeaseStorePort,
    sessions: PhotoSessionStorePort,
)   // no engine parameter, and no port for one
```

`RetainedState` inside the Activity constructs this directly, so there is no injection
point at all. **Needed:** a port parameter (e.g. `engine: PhotoInferenceEngine`) and a
composition root that supplies it.

### 3. Nothing triggers analysis

`PhotoAnalysisPresenterAction` has exactly five cases — `Cancel`, `ScanAgain`, `TakePhoto`,
`ImportPhoto`, `OpenSettings`. **There is no `Analyze` action.** The reducer accepts
`AnalysisStarted(requestId)` from `Ready`, but no UI path emits it, which is why the flow
dead-ends at "Photo ready".

**Needed:** either an explicit `Analyze` action, or an auto-advance from `Ready`. Note this
is a low-friction-interaction decision (AD-40) — an extra tap must be justified — so it
needs an interaction budget entry, not just code.

### 4. `Analyzing` has no failure transition — the UI can hang forever

```kotlin
private fun reduceAnalyzing(current, event) = when (event) {
    is PhotoAnalysisEvent.AnalysisSucceeded -> ...
    is PhotoAnalysisEvent.ReplacePhoto      -> ...
    PhotoAnalysisEvent.Cancel               -> ...
    else -> unchanged(current)        // <- PreparationFailed lands here
}
```

`PreparationFailed` falls to `else`. A gateway failure, an abstention, a timeout or an
offline device leaves the user pinned on the "Analyzing photo" spinner with no exit but
Cancel. **Needed:** an `AnalysisFailed(reason)` event and a transition to `Recovery`.
`RecoveryReason` already has `OFFLINE` and `NO_FOOD` with copy wired — they are currently
unreachable from `Analyzing`.

### 5. `CandidateReview` cannot hold a result

```kotlin
data class CandidateReview(val ref: PhotoRef, val requestId: RequestId) : PhotoAnalysisState
data class AnalysisSucceeded(val requestId: RequestId) : PhotoAnalysisEvent
```

Neither the event nor the state carries candidates. Even on success there is nowhere to put
the labels, confidences, provenance or assumptions the gateway returned, and no UI renders
them. **Needed:** candidates on both, plus a review screen. This is the single largest
piece of remaining product work.

### 6. No consent capture

`RemoteInferenceEngine` requires a `RemoteConsentDecision` and refuses to open a socket
without one — correct per AD-8/AD-41. Nothing in the client produces one: there is no
consent screen, no stored decision, no copy version. Per AD-40 remote upload must never be
silently defaulted, so **a consent UI is a prerequisite for any real transfer**, not a
follow-up.

### 7. Android runtime prerequisites — the app cannot open a socket at all

- **No `android.permission.INTERNET`.** The only permission declared anywhere in
  `mobile/**/AndroidManifest.xml` is `CAMERA`. Any network call fails.
- **No cleartext policy.** `targetSdk = 36`; cleartext HTTP is blocked by default and
  neither `usesCleartextTraffic` nor a `networkSecurityConfig` is declared. Talking to a
  local dev gateway over `http://10.0.2.2:3000` needs a debug-only network security config
  scoped to that host — never a blanket `usesCleartextTraffic="true"`.
- **No base-URL configuration.** There is no build field, no config entry, and no
  environment seam for the gateway URL.

### 8. Coverage denominator — decision required

`mobile/config/quality/coverage.toml` does not list `mobile/inference/remote`, so the new
module's production code is currently outside the JVM coverage denominator. I added an
entry, measured, and reverted it, because it cannot pass as declared:

- **LINE 98.47%** — comfortably over the 85% floor.
- **BRANCH 55.93%** — under it. **71 of the 78 missed branches are kotlinx.serialization's
  synthetic `<init>` bitmask branches** in the `@Serializable` wire types (one per optional
  field, reachable only by enumerating field-presence combinations). Only 7 missed branches
  are in real code.

Lowering a declared floor or excluding generated code both contradict AD-33 and the
"no exclusions, no eligibility" rule, so this is an architecture decision, not a client
task. Options: a documented denominator rule for generated serializer code, or replacing
the generated serializers with a hand-written codec. **The module must be added to
`coverage.toml` in the same story that wires it into the app** — production code with no
denominator is exactly what AD-33 forbids.

Note: `coverage.toml` is digest-bound by `config/quality/evidence-bindings.toml`, so any
edit also requires regenerating `manifest_digest`.

### 9. Also missing (smaller)

- `mobile/config/architecture/module-boundaries.toml` declares the `inference` group with
  `source_sets = ["commonMain", "commonTest", "androidMain", "iosMain"]`. `:inference:remote`
  additionally uses `jvmMain`, `jvmTest` and the shared `jvmAndroidMain`; the inventory
  should be updated to match.
- No iOS transport. `HttpGatewayTransport` covers JVM + Android; `iosMain` has no
  implementation of `GatewayTransport`.
- No Maestro flow covers photo → analysis → result; the UJ-1..UJ-4/SM-9 catalog needs the
  new states once they exist.

---

## What I built (all local, in the worktree)

**`mobile/inference/remote/`** — the E4.2 gateway adapter, 40/40 tests green:

- `commonMain/GatewayTransport.kt` — transport port + `GatewayCall` + `GatewayEnvironment`
- `commonMain/GatewayEnvelope.kt` — wire types mirroring `contracts/mobile-api/v1`
- `commonMain/RemoteInferenceEngine.kt` — implements `PhotoInferenceEngine`; maps all 21
  taxonomy codes onto the closed failure set; drops unknown assumption codes rather than
  coercing them; recomputes confidence band from score; never reads a nutrition field
- `jvmAndroidMain/HttpGatewayTransport.kt` — multipart POST, shared by JVM and Android
- `jvmTest/` — 6 suites: engine behaviour, contract shape, envelope codec, outcome
  mapping, transport-over-real-socket, live gateway

`HttpURLConnection` is used rather than Ktor because `ktor-client-okhttp` is absent from
`gradle/verification-metadata.xml` and adding it is a build-policy change owned by another
slice. The swap is a one-file change once the checksums are added.

**`backend/mobile-api/`**:
- `apps/api/src/modules/inference/infrastructure/stub/stub.provider.ts` + tests
- `assertStubFence` in `config/test-mode-fence.ts` — moved out of `main.ts` because
  `main.ts` is excluded from the coverage denominator and an untested fence is not a fence

**`mobile/feature/photo-analysis/.../PhotoAnalysisComposeActivity.kt`** — `RetainedState`
changed `private` → `internal`; `ViewModelProvider` instantiates by reflection and could
not reach a private nested class, crashing the app in `onCreate` with `IllegalAccessException`.

---

## Not verified

- **No device-level HTTP call was made.** Blocked by items 1–3 and 7 above; the emulator
  has no `curl` and no KMP module in this repo has an `androidDeviceTest` source set
  configured, so adding one is itself a build-policy change. Device-level proof should come
  with the wiring story.
- **No iOS verification.** No Apple toolchain run; per AGENTS.md that evidence is
  good-to-have, not a blocker.
- `:inference:remote:assemble` fails on `extractAndroidMainAnnotations` (lint tooling
  artifacts absent from dependency verification). **Pre-existing and unrelated** —
  `:platform:photo-android:assemble` fails identically on untouched code, and `assemble`
  is not part of the quality gate.
