# Adversarial Seam Review — SuperNutrition Architecture Spine

## Verdict

The spine establishes useful module boundaries, dependency direction, privacy
rules, and single-owner workflow mutation. It does not yet close several
semantic seams between independently delivered epics. In each construction below,
both teams can obey every current AD literally and still produce components that
compile in isolation but disagree at integration time. The recurring problem is
that a named shared contract is not always paired with one owner, one canonical
schema, and one compatibility test.

## Compliant but Incompatible Team Pairs

1. **Mobile Remote Engine ↔ first-party gateway: two wire formats called
   contract v1.** The mobile team can serialize the sealed success/failure
   envelope with Kotlin class discriminators, while the gateway team can expose
   a JSON object with different discriminator names, null handling, and numeric
   encodings. Both can claim compliance with AD-2, AD-13, and the Serialization
   convention because the spine names required envelope fields but does not name
   the canonical wire schema, schema owner, encoding rules, or compatibility
   fixtures. Close the seam by making a language-neutral schema artifact
   authoritative, assigning its owner, and requiring mobile-gateway golden
   request/response fixtures plus backward-compatibility tests.

2. **Local Engine team ↔ Remote Engine team: structurally valid Candidates with
   incompatible identity meaning.** Local can return classifier taxonomy labels
   and remote can return provider-generated natural-language labels; both can
   provide rank, confidence, assumptions, and provenance under AD-2. Candidate
   review and catalog resolution then cannot know whether two labels denote the
   same food, whether alternatives share a namespace, or which locale produced
   the text. Close the seam by defining typed identity evidence: namespace,
   taxonomy/version, normalized display label and locale, stable alternative
   grouping, and the exact boundary between an inference label and a catalog
   identifier.

3. **Orchestrator team ↔ Engine adapter teams: incompatible meanings for
   attempt token, request ID, and session.** The orchestrator can issue one token
   for the whole photo-analysis attempt, while a Remote Engine can generate a new
   request ID for every retry and a Local Engine can reuse the attempt token for
   model download and inference callbacks. AD-2, AD-6, and the IDs convention do
   not distinguish Analysis Session ID, Attempt ID, Engine Invocation ID, and
   wire Request ID. Cancellation, deduplication, telemetry joins, and late-result
   rejection can therefore disagree while every component is compliant. Define
   the identifier hierarchy, creation owner, uniqueness scope, propagation rules,
   and retry semantics.

4. **Camera/platform epic ↔ inference epics: incompatible `PhotoRef` lifetime and
   content.** Android can return a short-lived content URI, iOS can return a
   temporary file URL, and an Engine can assume `PhotoRef` remains readable until
   its asynchronous work ends. AD-4 makes the reference opaque and AD-8 requires
   eventual deletion, but neither defines a lease, read ownership, background
   behavior, normalization version, orientation/color-space contract, maximum
   dimensions, or what “normalized bytes” means. Close the seam with a
   versioned `PhotoLease`/`NormalizedPhoto` contract whose owner controls
   acquisition, concurrent reads, remote-safe derivatives, and deterministic
   release.

5. **Reducer/feature epic ↔ orchestrator/effect-runner epic: one state mutator but
   two side-effect authorities.** The reducer team can emit an `UploadAccepted`
   effect and persist consent state, while the orchestrator team can observe the
   same event and start the Remote Engine directly. Both preserve AD-5's sole
   state mutator and AD-6's one active engine, yet recomposition, effect replay,
   or lifecycle restart can launch duplicate transfers. The spine does not bind
   who consumes effects, how effects are acknowledged, or whether remote start is
   at-most-once. Define a single effect executor, effect identity/acknowledgment,
   and idempotent start protocol keyed by Engine Invocation ID.

6. **Android lifecycle epic ↔ iOS lifecycle epic: incompatible recovery after
   backgrounding or process loss.** Android can retain the state machine and
   `PhotoRef` across configuration change, while iOS can terminate the Analysis
   Session on background. Both satisfy AD-5 and AD-8 because the spine states
   cleanup conditions but not lifecycle suspension, process-death restoration,
   or whether an in-flight consented transfer may finish in background. Close the
   seam with one cross-platform lifecycle policy for active local work, active
   remote work, temporary image leases, restoration, and user-visible recovery.

7. **Candidate-review epic ↔ nutrition-catalog epic: two owners for the confirmed
   food entity.** The feature team can convert a Candidate into its own
   `ConfirmedFood` and store correction state, while the catalog team can expose a
   canonical selection object and expect to own substitutions and serving edits.
   AD-7 says Confirmation maps a Candidate to a canonical item but does not name
   the command, resulting aggregate, or owner of the selected entity. The two
   teams can create parallel models whose IDs and mutation rules diverge. Assign
   ownership of `CatalogSelection` and `ConfirmedMeal`, define commands crossing
   the port, and prohibit feature-local copies of catalog-owned identity.

8. **Country catalog package team A ↔ country catalog package team B: colliding
   “stable string” IDs.** USDA, Open Food Facts, and a regional dataset can all
   expose item `12345`; each team can obey the stable-ID convention and AD-7 while
   producing results that cannot be merged or replayed after a country/package
   change. G-6 explicitly defers merge rules, but catalog and result epics can
   otherwise begin before that gate closes. Require qualified identity
   `(source, dataset version, record ID)`, define canonicalization ownership, and
   block multi-source merge implementation until G-6 is adopted.

9. **Serving editor epic ↔ deterministic nutrition-calculation epic:
   incompatible amount, range, and rounding semantics.** UX can submit “1 bowl,”
   “2 cups,” or a range in the country display unit, while the catalog team can
   require grams and round every nutrient per item before aggregation. Both obey
   AD-7 and AD-12, yet output differs depending on unit conversion, edible
   portion, recipe basis, range propagation, missing nutrient values, precision,
   and rounding order. Define a versioned `ServingBasis` and calculation result
   contract, establish which layer owns conversions, and fix precision,
   rounding, unknown/null, range, and aggregation rules.

10. **Country-experience epic ↔ signed-config epic: different authoritative
    country.** An app root can use the platform region as the bundled safe
    default, while the UX team can let a user switch catalog and the config
    service can apply a remotely allowed country. All comply with AD-12 because
    country remains independent from language, but the spine does not define
    precedence, persistence, user confirmation, or what happens when a previously
    selected country leaves the allowlist. Define one `CountryContext` owner and
    the precedence and transition rules among bundled default, device suggestion,
    user selection, and signed policy.

11. **Country-profile team ↔ model/provider-config teams: individually valid
    configuration that is invalid as a set.** A country profile can activate a
    provider region that a model manifest or gateway deployment has not activated
    yet. Each artifact can be signed, versioned, compatible, and rollbackable
    under AD-9 and AD-12, but there is no atomic policy snapshot or cross-manifest
    compatibility relation. Define an aggregate policy version or signed
    compatibility manifest, require atomic activation, and specify rollback of
    the whole compatible set rather than each file independently.

12. **Gateway provider-selection team ↔ mobile provenance team: conflicting
    ownership of active provider configuration.** AD-9 says Engines report model
    and provider identifiers, while AD-13 keeps provider adapters server-side.
    Mobile can assume its downloaded provider configuration selected the model;
    the gateway can independently route to a fallback provider after request
    acceptance. Both remain compliant, but displayed provenance and evaluation
    attribution can be false. Make the gateway authoritative for remote provider
    selection and response provenance, define permitted fallback disclosure, and
    make mobile policy constrain rather than claim the server choice.

13. **Mobile-release team ↔ gateway-deployment team: environment-compatible in
    diagrams but not in releases.** A production app can receive signed config
    pointing to staging, or a gateway can deploy a contract-v1-compatible
    behavioral change before the corresponding mobile policy is active. AD-13
    names the selected environment and the diagram lists dev/staging/prod, but no
    AD fixes endpoint ownership, certificate/pinning policy, promotion order,
    minimum supported client contract, canary/rollback coupling, or environment
    data isolation. Add a deployment compatibility invariant covering build
    flavor-to-environment binding, promotion gates, version support window,
    rollback, and isolation of credentials, telemetry, and retained operational
    data.

14. **Mobile identity epic ↔ gateway security/rate-limit epic: authenticated rate
    limiting with no shared authentication mechanism.** The gateway team can
    require account JWTs, while the account-free mobile team can provide only an
    anonymous install identifier or platform attestation. Both can claim AD-13:
    the gateway applies authenticated rate limits and the product has no account.
    Define the first-release client authentication/trust mechanism, credential
    lifecycle, replay protection, privacy properties, abuse key, and failure
    behavior before either team implements the boundary.

15. **Mobile telemetry epic ↔ gateway telemetry/evaluation epic: identical
    allowlisted field names with incompatible semantics.** Mobile can bucket
    duration as capture-to-Candidate and terminal outcome as user-visible result;
    gateway can bucket provider latency and outcome as HTTP success. Both obey
    AD-14's allowlist, but SM evidence cannot be joined or interpreted. The spine
    lacks a versioned event taxonomy, producers, units/bucket boundaries,
    required/optional fields, duplicate policy, consent mode, retention, and
    correlation rules. Create one telemetry schema and ownership table, with
    explicit mobile-only, gateway-only, and end-to-end events plus privacy-tested
    retention.

16. **Engine teams ↔ recovery-UX epic: typed failures whose recovery semantics
    disagree.** Local can map model download to `TemporarilyUnavailable`; remote
    can map rate limiting to the same failure, while the UI team maps that type to
    Retry. Each follows the Errors convention and AD-2, but one Retry requires a
    download path and the other requires backoff or manual recovery. Define the
    stable failure taxonomy and mandatory fields for recoverability, retry
    timing, required user action, safe copy key, and whether the current
    `PhotoLease` remains valid.

17. **Local model-package epic ↔ capability/routing epic: incompatible meanings
    of “ready.”** A model manager can report ready when a signed artifact is
    downloaded, while the Local Engine can require compilation, accelerator
    availability, or a memory check before inference. Both obey AD-9, and the
    orchestrator can still make a deterministic AD-6 decision from the snapshot,
    but it deterministically selects an unusable route. Define one capability
    state machine, the owner of each transition, freshness requirements, and the
    exact predicate that permits routing to local inference.

18. **Evaluation-corpus epic ↔ Engine/preprocessing epics: valid comparisons over
    different inputs.** Local can benchmark its native crop and color conversion,
    remote can benchmark a separately compressed image, and the harness can call
    both “the same corpus.” AD-11 demands common contract evidence, but G-2
    defers corpus governance and the spine does not bind preprocessing fixtures,
    meal-level split keys, ground-truth schema, or accepted-result calculation.
    Close G-2 before comparative model stories, version normalized input and
    ground truth, and require leakage-resistant split and scoring fixtures shared
    unchanged by every route.

## Conclusion

AD-1 through AD-15 are directionally coherent, but the next level still has room
to invent incompatible meanings for shared objects and operational policies.
The highest-leverage repair is not more module prose: assign an owner and a
versioned, fixture-tested contract to each cross-team seam—wire envelope,
Candidate evidence, identifier hierarchy, photo lease, confirmed catalog
selection, serving calculation, country/policy snapshot, deployment environment,
failure taxonomy, and telemetry events. Deferred gates G-1, G-2, G-5, and G-6
must also block the stories whose compatibility depends on them, rather than
merely documenting that the decision will happen later.
