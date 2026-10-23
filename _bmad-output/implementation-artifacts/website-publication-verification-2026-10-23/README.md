# Repository-based website publication verification

Status: published and verified on2026-10-23.

Source repository: [flexsent-labs/supernutrition.app](https://github.com/flexsent-labs/supernutrition.app). Production `main` commit: [`e4d6d73a8503ecc768d1db07bfb0ef4b421c13e9`](https://github.com/flexsent-labs/supernutrition.app/commit/e4d6d73a8503ecc768d1db07bfb0ef4b421c13e9). The activation commit adds only operational documentation; the prepared legal content was already present in its parent533b956.

## Publication and reproducibility

The existing Cloudflare `supernutrition-app` Worker was connected to this repository and `main`. The first repository push triggered [Workers Builds8975782e](https://dash.cloudflare.com/dad0c564a42378278ff866d3eb2c0a35/workers/services/view/supernutrition-app/production/builds/8975782e-538d-46a7-87fb-c156dabfab45), which completed successfully at09:28:44 UTC. Active Worker version: `73453bed-2db5-47d6-b173-fa150b792bfb`. [GitHub verification39932046546](https://github.com/flexsent-labs/supernutrition.app/actions/runs/39932046546) also passed for the same commit.

Both hosts passed10 waitlist tests with0 failures, regenerated both clean build outputs, and verified79 files with identical manifest SHA-256 `1d5920c43c1e2fbef2551d6a4d5cfe65cdc4ef38322b7f9a9568e13301c8ef3c`. Cloudflare checked the saved receipt again before deployment. See `build-verification.log` and `publication-success.png`. This confirms the observed Linux build outputs and pinned toolchain; it is not a claim about arbitrary operating systems or unpinned native image libraries.

The saved production configuration uses `npm run cloudflare:build`, `npm run cloudflare:deploy`, root `/`, `SKIP_DEPENDENCY_INSTALL=1`, disabled preview builds and disabled Cloudflare build cache. The [source activation record](https://github.com/flexsent-labs/supernutrition.app/blob/e4d6d73a8503ecc768d1db07bfb0ef4b421c13e9/docs/cloudflare-builds-activation-2026-10-23.md) describes setup and reduced credential permissions. Cloudflare created its managed token even with builds disabled; its default permissions were reduced before enabling publication. GitHub access is limited to the website repository.

## Live verification

[Live provenance](https://supernutrition.app/version.json) returns the exact source commit and Node22.23.3, npm10.9.9 and Wrangler4.103.0. All six localized document routes returned HTTP200 and contain the confirmed operator `Flexsent labs SIA`:

- [Privacy Policy](https://supernutrition.app/privacy-policy/), [Russian](https://supernutrition.app/ru/privacy-policy/), [Spanish](https://supernutrition.app/es/privacy-policy/).
- [Terms & Conditions](https://supernutrition.app/terms/), [Russian](https://supernutrition.app/ru/terms/), [Spanish](https://supernutrition.app/es/terms/).

`live-routes.json` contains exact response hashes, sizes, titles and check time. English Privacy and Terms were also opened in the browser; their cross-link works and the rendered pages were inspected (`privacy-policy.png`, `terms.png`). Android Profile already opens these exact English URLs via `ProfileLegalLinks` and its native URI handler. Website publication does not require an additional app source change.

## Remaining app verification

The app source remains commit3d54a716 on `codex/home-profile` in `/Users/anastasia/.codex/worktrees/f2ff/SuperNutrition`. The final shared UI JVM run passed230/230; affected Core UI, Presenter and Feature suites passed4/4,19/19 and25/25. Production-debug assembly and explicit iOS arm64 compilation passed.

Final native acceptance is unresolved: the new unchanged full-suite invocation was interrupted after36 completed POC runner records (29 passed,7 Activity-launch failures); Production and UI Acceptance were not reached. The bounded renderer recovery displayed a Launcher Quickstep ANR before app launch. The earlier150/150 native result predates the last signature/SAM cleanup. Physical Android evidence is unavailable under the user's emulator-only constraint; the policy gate still rejects expired physical evidence. Typed Detekt also retains its documented frontend-analysis limitation. See the [app verification report](../existing-screens-verification-2026-10-23/README.md) for exact commands, host, XML provenance and diagnostics. App native/visual/hash acceptance remains in-review.
