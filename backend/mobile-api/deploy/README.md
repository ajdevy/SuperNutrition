# Deploy and rollback skeleton

AD-37 requires a deploy/rollback skeleton at activation. This is that skeleton,
not a production deployment: G-4 owns the runtime, region, and provider
decision, and AD-24 §9.11 owns the production enablement checklist.

## Artifact

One container image built from `Dockerfile`, tagged with the commit SHA. The
image is the only deployable; there is no second entrypoint.

## Deploy

1. Build and push `mobile-api:<sha>`.
2. Run migrations (`packages/database/src/migrations`, forward-only) against the
   target database. Migrations are expand/migrate/contract and never rewrite a
   large table during a deploy (docs/05 §5.6).
3. Roll out the new image. Readiness gates on `/health/ready`, which reports
   `authMode` so a test-mode process cannot go live unnoticed.

## Rollback

Roll back the application to the previous image tag. Migrations are NOT rolled
back: a corrective forward migration is written instead, because a down
migration is written once, tested never, and run in an incident.

AD-24 requires a **recorded rollback drill** before production enablement. It has
not been performed; see `docs/11-technical-backlog.md` B-34.

## Not yet decided

Runtime platform, region mapping, secret manager binding, and scaling policy are
Gate G-4. This file describes the shape those decisions must fill, not the
decisions themselves.
