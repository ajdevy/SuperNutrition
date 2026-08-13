-- M8 — one correction per original meal event.
--
-- supersede() verified only that the original existed, so two corrections of the
-- same meal (a retry, or two devices) both inserted and both became live heads.
-- The feed counted the meal twice, and nothing in the schema said that was wrong.
--
-- A unique index over a nullable column permits any number of NULLs in Postgres,
-- so ordinary rows — the ones that supersede nothing — are untouched.
CREATE UNIQUE INDEX IF NOT EXISTS "meal_events_supersedes_unique_idx"
  ON "meal_events" ("supersedes_id");
