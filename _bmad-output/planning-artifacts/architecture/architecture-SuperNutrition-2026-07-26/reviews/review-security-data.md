# Security, Privacy, and Data-Integrity Review — NutriArch

## Verdict

The updated spine has a strong security baseline: local-first routing, explicit
consent, leased photos, signed configuration, an attested gateway session,
allowlisted telemetry, deterministic nutrition, and an owned production service.
It is not yet safe to treat those controls as end-to-end guarantees. Five
release-significant gaps remain at the boundaries between mobile, gateway,
provider, configuration, catalog, telemetry, and incident operations.

## Findings

1. **Consent is a UI/state fact, not yet an end-to-end authorization bound to the
   transfer.** AD-6 and AD-8 require per-attempt consent before transfer, and
   AD-18 prevents duplicate transfer-start effects, but no canonical
   `ConsentGrant` binds the disclosure version, purpose, photo lease, country/data
   region, provider policy, configuration snapshot, grant time, and attempt ID.
   A queued effect can therefore send after the reducer records revocation, or
   the gateway can accept a request under a different provider/region policy than
   the user saw. Cancellation only invalidates the local attempt token; it does
   not require a gateway cancellation/idempotency protocol or provider-side
   processing stop. This falls short of PRD FR-10's “revoked before transfer” and
   “stop downstream processing where possible.” Require an immutable,
   single-action consent assertion validated immediately before byte transfer and
   again at the gateway, plus gateway idempotency and cancellation semantics that
   explicitly distinguish queued, sending, accepted, provider-processing, and
   irrecoverable states.

2. **The raw-photo lifecycle ends at vague gateway language rather than the last
   subprocessor copy.** AD-8 closes the mobile lease, while AD-13 says the gateway
   does not “durably store” photos and AD-24 asks for retention/deletion evidence.
   None defines whether request buffers, temporary files, reverse proxies, WAFs,
   tracing, crash dumps, queues, provider caches, backups, or failed requests are
   inside “request processing,” nor sets maximum lifetimes and deletion evidence
   for each copy. Provider fallback can also change the actual retention/training
   terms after consent. On device, deleting the lease does not by itself exclude
   OS backups, image-library caches, thumbnails, or diagnostic snapshots.
   Establish one end-to-end data inventory and lifecycle contract covering
   device derivatives, gateway infrastructure, every provider/subprocessor,
   failure paths, backups, maximum TTLs, encryption/key disposal, training
   prohibition, deletion verification, and DPA enforcement. G-4 must prove this
   contract before remote enablement, not merely record a provider promise.

3. **“Attested installation session” is not a complete account-free trust and
   abuse model.** AD-21 does not define attestation freshness, nonce/replay
   protection, token audience and environment binding, device-key binding,
   token storage, issuer/key rotation, clock skew, reinstall/reset abuse,
   unsupported-device behavior, or compromise response. A stolen bearer token
   can potentially replay costly uploads, while an attacker can farm fresh
   installations to bypass per-install rate limits. AD-13's “authenticated rate
   limits” and AD-24's dashboards do not close that gap. Define the trust model
   and failure posture per platform, bind short-lived tokens to build flavor,
   device-held key, gateway audience, configuration version, and replay nonce,
   and combine install, network, request-id, concurrency, and cost controls.
   Include issuer/signing-key compromise and token revocation in the rollback
   drill.

4. **Supply-chain integrity is uneven: configuration and model packages are
   stronger than nutrition catalogs, and none has a complete key-compromise
   story.** AD-20 signs a monotonic `ConfigurationSnapshot`, and AD-22 verifies
   app-managed model checksums, but the spine does not fix trust-root
   provisioning, signature algorithm/key hierarchy, rotation, revocation,
   anti-freeze/max-age behavior, two-person approval, or recovery when a signing
   key is compromised. More seriously, AD-7/AD-19 make the catalog the sole
   nutrition authority without requiring signed catalog packages, allowlisted
   origins, atomic inactive-slot activation, rollback, reproducible ingestion,
   source-file hashes, schema/unit validation, or anomaly bounds. A malicious or
   mistaken catalog update can therefore produce perfectly deterministic but
   dangerously wrong nutrition. Extend the signed artifact policy to country
   profiles, model manifests/packages, provider policy, and catalog
   packages/build provenance; require compatibility-bound signatures, anti-
   rollback/freeze controls, staged activation, independent approval, audit log,
   and tested recovery from both bad content and key compromise.

5. **Tier-1 regional policy, telemetry privacy, and incident evidence are not
   separated cleanly enough to enforce legal handling.** AD-12 uses one country
   profile for catalog, units, consent, and provider region, but catalog country
   is not necessarily legal residence, current location, store jurisdiction, or
   permitted processing region; a traveler can validly choose one food catalog
   while requiring another privacy route. AD-14's allowlisted events can still
   become sensitive or linkable through attempt IDs plus route, capability,
   correction, catalog, model, and configuration versions, yet no invariant sets
   purpose, sampling, access control, region, retention, aggregation,
   consent-withdrawal, or deletion behavior. The product is account-free, so the
   mechanism for deletion/access requests is also undefined. AD-24 requires
   alerts, a kill switch, and a runbook but does not require immutable operator
   audit events, immediate gateway-side shutdown independent of configuration
   propagation, signing/secret breach playbooks, regional notification duties,
   or proof that in-flight requests stop. Separate `CatalogCountry`,
   `Market/Jurisdiction`, and `ProcessingRegion`; define a privacy-governed
   telemetry/security-audit schema and retention policy; and make incident
   controls include gateway-enforced deny, provider credential revocation,
   immutable admin/config audit, regional response obligations, and evidence from
   periodic breach and deletion drills.
