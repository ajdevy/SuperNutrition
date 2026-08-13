# Prompt templates

Stage 2 deliberately contains no prompt text. `backend/02-skeleton.md` excludes
AI prompt implementation, and the constraints those templates must satisfy are
specified in `docs/07-openrouter-integration.md` §7.6:

- Request food identity only — labels, ranks, confidence, assumptions. Never
  calories, macros, or micronutrients: nutrition comes from the catalog (AD-7),
  and asking a model for a number invites it to supply one.
- Never concatenate user-controlled text into instructions. A voice transcript
  is a delimited data field, and the system prompt states it is untrusted input
  to be classified rather than instructions to follow.
- Parse responses against a strict schema. A non-matching response is
  `malformed-response`, not a best-effort salvage.
- Prompt text is never logged, stored, or written to `inference_runs` (AD-14).

Templates are versioned by `prompt_version` and recorded in provenance.
