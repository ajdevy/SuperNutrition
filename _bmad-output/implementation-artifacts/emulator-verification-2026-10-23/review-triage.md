# iOS host target-selection review triage

Both Blind Hunter and Edge Case Hunter ran independently, without conversation context, using the required project skills on the complete tracked/untracked diff from779fc327. Their findings are deduplicated below.

| Finding | Severity | Route | Acceptance and outcome |
|---|---|---|---|
| IOS-BUILD-3: sole helper input lets Xcode skip transitive Kotlin changes | high | patch | Both phases alwaysOutOfDate=1; unchanged repeat invokes Gradle and exits0. |
| IOS-BUILD-4: inherited late phase order exposes stale framework/cycle | high | patch, exposed by dependency-input change | Both phases precede Sources/Frameworks. Real incremental red cycle exit65; corrected timestamp probe exits0 with Gradle before Swift/link. |
| IOS-DOC-1: historical host-slice command first fails missing domain:nutrition | medium | patch | Both runbooks distinguish registry, strict checksum and later diagnostic variant stages. |
| IOS-EVIDENCE-1: original device red did not fail before Swift | medium | patch | Findings/spec/report accurately retain its missing-checksum/config-cache error after Swift/link. |
| ANDROID-EVIDENCE-1: initial manual record time precedes later large-font checks | low | patch | Start/consolidation/per-file creation timestamps have explicit scopes. |
| IOS-PACKAGE-1: absent Compose fonts/artwork in the original host integration | high | defer | Existing defect, not caused by target-selection correction; recorded in deferred-work.md and exposed in the user report. |

No intent gap or architecture/policy change was needed. Spec review iteration remains0 because these are bounded patches, with no loopback/re-derivation. The user explicitly accepted Intel-only execution constraints.

Bounded re-review by Edge Case Hunter confirmed both code findings closed and no introduced regression in the corrected slice; log line ordering and unchanged incremental phase execution were inspected. A minor elapsed-time reference was clarified as first-pass45s versus final17.5s.
