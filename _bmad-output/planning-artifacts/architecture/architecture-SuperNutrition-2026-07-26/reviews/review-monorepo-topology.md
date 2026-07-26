# Monorepo Topology Review — SuperNutrition Architecture Spine

Reviewed: 2026-07-26
Target: `../ARCHITECTURE-SPINE.md` and its implementation companions
Lens: mobile KMP ownership, product-root isolation, contract-only integration,
and implementation-agent scope

## Verdict

**FAIL — changes required before implementation handoff.**

The mobile KMP module, build, and test ownership model is coherent, and
`web/landing/` plus `backend/mobile-api/` have independent manifests, locks,
caches, tests, quality entrypoints, delivery configuration, and owners.
However, the documents still permit one undeclared landing-to-backend runtime
edge and contain invocation templates that contradict the one-module ownership
rule.

The deterministic spine lint passed with zero findings; the failures below are
semantic boundary failures.

| Gate | Result | Basis |
| --- | --- | --- |
| Mobile KMP module/build/test ownership | **PASS** | AD-25 through AD-30 place all KMP modules, build logic, dependency state, test support, quality manifests, and host/device evidence under `mobile/`, with test-code leakage and undeclared graph edges rejected. |
| Web and backend product isolation | **PASS** | AD-27, AD-29, AD-30, AD-35, and AD-36 give each product an independent build/test/quality/owner boundary and prohibit cross-root source, workspace, generated-source, and task dependencies. |
| `contracts/` as the only legal integration boundary | **FAIL** | AD-36 does not forbid an uncontracted public landing-to-backend runtime call. |
| Unambiguous implementation-agent scopes | **FAIL** | The scaffold and contract templates assign multiple ownership domains to one `Owned slice`, contrary to AD-15 and the standard invocation envelope. |

## Critical findings

None.

## High findings

### 1. AD-36 leaves a legal-looking uncontracted landing-to-backend runtime edge

AD-36 says that the landing site cannot call *private provider* or
*mobile-backend administration* APIs. That restriction implies that a public
endpoint on `backend/mobile-api/` may be called, but the only published contract
named by the rule is `contracts/mobile-api/` for the mobile consumer. Neither the
spine nor the structural seed defines a landing/backend contract, pinned
consumer artifact, compatibility test, or explicit declaration that the landing
has no backend runtime dependency.

This weakens NFR-22 and the PRD addendum, both of which say that **all**
cross-product integration uses versioned language-neutral artifacts under
`contracts/`. The import/workspace checks in AD-36 would not catch an HTTP,
event, or configuration dependency, so a landing implementation could add a
public backend call while every listed boundary check still passes.

**Required correction:** Make the runtime rule universal: no product may call,
subscribe to, or consume runtime configuration from another product unless the
interaction has a versioned contract under `contracts/<interaction>/`, a pinned
consumer artifact, compatibility tests, an integration story, and approval from
all affected owners. For the present topology, state explicitly that
`web/landing/` has no runtime dependency on `backend/mobile-api/`; a future
interaction must first add a separately owned contract such as
`contracts/landing-api/`. Extend root boundary tests to inventory outbound
endpoints/events/configuration, not only source and build edges.

Evidence: `ARCHITECTURE-SPINE.md` AD-36 lines 286–292 and structural seed lines
371–384; `epics.md` NFR-22 line 117 and AR-43 line 163; PRD `addendum.md` lines
12–15.

### 2. Implementation templates violate the one-module invocation boundary

AD-15 and the standard envelope require one invocation to own one exact module or
platform slice. Template 7.0 instead defines one `Owned slice` spanning root
product directories, boundary tooling, CODEOWNERS, CI, mobile Gradle settings,
conventions, the catalog, empty app roots, test infrastructure, manifests, and
all six product jobs. Its opening sentence names two agents but never gives them
separate allowed-file lists, invocations, handoff criteria, or ownership
boundaries. Template 7.1 likewise assigns both `mobile/domain/inference` and
`mobile/testing/inference` to one owned slice. The PRD addendum further allows a
mobile agent to edit cross-product contracts whenever an integration story
grants the paths, without saying that the contract owner must remain a separate
invocation.

An implementation agent therefore cannot tell whether the one-module rule or the
broader story template is authoritative. Following the template can combine
root policy, contract ownership, mobile build ownership, and multiple product
quality surfaces in one change, defeating the collision-prevention purpose of
AD-15 and AD-36.

**Required correction:** Split template 7.0 into explicit invocations with
disjoint allowed paths: a root-boundary/CI invocation, a mobile scaffold
invocation, and later separately reviewed web/backend toolchain invocations.
Split template 7.1 into a contract-module invocation and a
`mobile/testing/inference` support invocation, or formally redefine the unit of
ownership and update AD-15 plus every template consistently. State that an
integration story coordinates separate owner-scoped invocations; it does not
authorize one mobile agent to own `contracts/` or another product root. If a
single integration PR is allowed, require all sub-slices, owners, and
independently executable evidence to be listed explicitly.

Evidence: `ARCHITECTURE-SPINE.md` AD-15 lines 142–146 and AD-36 lines 286–292;
`implementation-agent-guide.md` standard envelope lines 108–130, template 7.0
lines 148–164, and template 7.1 lines 167–176; PRD `addendum.md` lines 71–76.
