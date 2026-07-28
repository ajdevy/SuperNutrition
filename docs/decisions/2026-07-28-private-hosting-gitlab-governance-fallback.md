# Private hosting governance fallback

Status: proposed
Decision date: 2026-07-28
Review/expiry date: 2026-08-28

## Context

The canonical GitHub repository is private under a plan that cannot expose
private-repository rulesets or an organization-enforced required workflow. The
checked-in GitHub Actions workflow therefore provides bootstrap evidence only;
it cannot prove tamper-resistant merge enforcement. GitHub Team is not an
available project constraint.

## Decision

Use a private GitLab mirror as the merge authority, with a separate private
governance project owning the trusted audit and merge bot:

1. `flexsentlabs/SuperNutrition` is the private canonical source project.
   GitHub remains a read-only issue/PR mirror until the migration is verified.
2. `flexsentlabs/supernutrition-governance` is a separate private project whose
   protected default branch contains the pinned trusted-policy-audit pipeline.
   The source project must not be able to modify that branch or its protected
   variables.
3. The source project's `main` branch allows neither developer direct pushes
   nor developer merges. Only the governance merge bot may push a verified
   merge result. The bot accepts an exact merge-request head SHA, checks out
   the source at that SHA, and validates the six-job fingerprint set, root and
   contract policy, lifecycle/inventory digests, native evidence, and the
   protected ownership configuration before merging.
4. Source-project CI remains useful for ordinary test execution but is not the
   trusted policy source. The governance pipeline runs the policy verifier from
   its own protected commit and rejects a source change that edits, removes, or
   bypasses the policy contract.
5. Provisioning is complete only after a direct-push and direct-merge attempt
   returns `403`, a candidate with a modified policy workflow is rejected, a
   clean candidate is merged by the bot, and the resulting merge SHA has all
   six same-SHA fingerprints plus the governance attestation.

The mobile lifecycle and root ownership manifest remain pending until those
live checks are recorded. This decision does not authorize an active-state or
release claim by itself.

## Alternatives considered

- GitHub Team/Enterprise or public hosting: rejected because the project
  constraint excludes Team and public exposure is not an acceptable privacy
  substitute.
- GitLab protected branch with only the project `.gitlab-ci.yml`: rejected;
  the candidate can edit that CI file, so it is not an immutable trusted audit.
- Manual checklist or local pre-push hook: rejected; neither can enforce the
  merge boundary against a bypass.

## Consequences

The fallback keeps source private and avoids GitHub Team, but it requires two
private GitLab projects, a distinct governance owner, a protected bot secret,
and a continuously available governance runner. GitHub cannot remain the
authoritative merge host while its private branch has no enforceable required
check.

## Owner

Repository-policy owner, with independent source and governance principals.

## Validation evidence

- The authenticated `flexsentlabs` GitLab group is private and permits project
  creation for the current owner; no SuperNutrition mirror or governance
  project exists yet.
- GitLab's current documentation classifies compliance-pipeline enforcement
  and external status checks as higher-tier controls; the design therefore
  uses a protected governance project and merge bot instead of claiming those
  features are available on the current tier.

## Provisioning exit checklist

- [ ] Create both private GitLab projects and record their immutable IDs.
- [ ] Protect both default branches; remove developer push/merge permissions
  from the source `main` branch.
- [ ] Add the governance bot as the only source merge principal and store its
  token only as a protected governance-project variable.
- [ ] Pin the governance audit source and runner image by digest.
- [ ] Verify blocked bypass, modified-policy rejection, clean merge, and
  same-SHA attestation on a disposable test merge request.
- [ ] Attach the live URLs, IDs, logs, and SHA to this decision, change
  `Status` to `accepted`, and then revisit AD-38/lifecycle atomically.
