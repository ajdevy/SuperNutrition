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

  return ok({ sortKey: t, id: i });
};
