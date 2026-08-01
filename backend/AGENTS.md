# Backend workflow conventions

## Git push policy

GitHub Actions CI on this repo bills minutes (private repo, paid billing enabled).

- Commit freely and often — commits never trigger CI.
- Do NOT push unless the user agrees: only push a finished branch for review/PR, or when explicitly asked.
- One push per feature branch (extra pushes only if review requests changes).
- Run `npm test` and the full local quality entrypoint before any push; local gates are authoritative, CI is only an optional adapter.
