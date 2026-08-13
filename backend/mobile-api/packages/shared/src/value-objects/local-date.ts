import { type Result, ok, err } from '../result/result.js';
import type { Brand } from '../types/branded.js';

/**
 * A calendar day in the user's own timezone, as `YYYY-MM-DD`.
 *
 * Daily aggregation uses the local day and stores the timezone alongside it
 * (docs/05 §5.1). Deriving the day from a UTC instant later loses the
 * information needed to get a traveller's or a DST boundary's day right.
 */
export type LocalDate = Brand<string, 'LocalDate'>;

/** A UTC instant, ISO-8601 with explicit offset. */
export type Instant = Brand<string, 'Instant'>;

const LOCAL_DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

export type DateError = 'malformed' | 'not-a-real-date';

export const parseLocalDate = (value: string): Result<LocalDate, DateError> => {
  if (!LOCAL_DATE_PATTERN.test(value)) return err('malformed');
  const [y, m, d] = value.split('-').map(Number) as [number, number, number];
  const probe = new Date(Date.UTC(y, m - 1, d));
  if (probe.getUTCFullYear() !== y || probe.getUTCMonth() !== m - 1 || probe.getUTCDate() !== d) {
    return err('not-a-real-date');
  }
  return ok(value as LocalDate);
};

export const parseInstant = (value: string): Result<Instant, DateError> => {
  const parsed = Date.parse(value);
  if (Number.isNaN(parsed)) return err('malformed');
  return ok(new Date(parsed).toISOString() as Instant);
};

/** IANA timezone identifier, validated against the runtime's own database. */
export const isValidTimezone = (value: string): boolean => {
  try {
    new Intl.DateTimeFormat('en-US', { timeZone: value });
    return true;
  } catch {
    return false;
  }
};

/** Inclusive day count, used for streak and trend windows. */
export const daysBetween = (from: LocalDate, to: LocalDate): number => {
  const a = Date.parse(`${from}T00:00:00Z`);
  const b = Date.parse(`${to}T00:00:00Z`);
  return Math.round((b - a) / 86_400_000);
};
