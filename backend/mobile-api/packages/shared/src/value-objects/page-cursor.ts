import { type Result, ok, err } from '../result/result.js';

/**
 * Opaque cursor for keyset pagination (docs/06 §6.4).
 *
 * It carries the sort key AND a tiebreaker id, because two meals logged in the
 * same second are ordinary: a cursor on the sort key alone either repeats or
 * skips the rows sharing that key. It is deliberately not a base64 offset —
 * an offset drifts when a row is inserted mid-scroll, which is exactly what a
 * feed the user is reading from does.
 */
export interface PageCursor {
  /** ISO-8601 instant of the sorted column. */
  readonly sortKey: string;
  readonly id: string;
}

export type CursorError = 'malformed';

export const encodePageCursor = (cursor: PageCursor): string =>
  Buffer.from(JSON.stringify({ t: cursor.sortKey, i: cursor.id }), 'utf8').toString('base64url');

/**
 * Validated on receipt. A cursor is client-supplied input like any other, so a
 * malformed one is a typed failure the boundary turns into `VALIDATION_FAILED`,
 * never an unhandled throw or a silently ignored filter.
 */
/** Canonical 8-4-4-4-12 hex form, the only shape a `uuid` column accepts. */
const UUID_PATTERN =
  /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;

export const decodePageCursor = (value: string): Result<PageCursor, CursorError> => {
  let parsed: unknown;
  try {
    parsed = JSON.parse(Buffer.from(value, 'base64url').toString('utf8'));
  } catch {
    return err('malformed');
  }

  if (parsed === null || typeof parsed !== 'object') return err('malformed');
  const { t, i } = parsed as { t?: unknown; i?: unknown };
  if (typeof t !== 'string' || typeof i !== 'string' || i.length === 0) return err('malformed');
  if (Number.isNaN(Date.parse(t))) return err('malformed');
  // M-03 — the tie-breaker is compared against a PostgreSQL `uuid` column. A
  // non-empty string that is not a UUID passed this check, reached the database,
  // and came back as a driver error the adapter mapped to `storage-unavailable`
  // — a 500 blaming the server for a cursor the client made up. Shape is a
  // client-input question and belongs here, where the answer is
  // `VALIDATION_FAILED`.
  if (!UUID_PATTERN.test(i)) return err('malformed');

  return ok({ sortKey: t, id: i });
};
