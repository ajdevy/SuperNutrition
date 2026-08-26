import { describe, it, expect } from 'vitest';
import { parseLocalDate, parseInstant, isValidTimezone, daysBetween } from './local-date.js';
import { isOk } from '../result/result.js';
import type { LocalDate } from './local-date.js';

describe('LocalDate', () => {
  it('accepts a real calendar day', () => {
    expect(isOk(parseLocalDate('2026-07-29'))).toBe(true);
  });

  it('rejects malformed input', () => {
    expect(parseLocalDate('29-07-2026')).toEqual({ ok: false, error: 'malformed' });
    expect(parseLocalDate('2026-7-9')).toEqual({ ok: false, error: 'malformed' });
  });

  // A regex alone accepts 2026-02-30; the round-trip check is what rejects it.
  it('rejects dates that pass the pattern but do not exist', () => {
    expect(parseLocalDate('2026-02-30')).toEqual({ ok: false, error: 'not-a-real-date' });
    expect(parseLocalDate('2026-13-01')).toEqual({ ok: false, error: 'not-a-real-date' });
  });

  it('accepts a leap day in a leap year and rejects it otherwise', () => {
    expect(isOk(parseLocalDate('2028-02-29'))).toBe(true);
    expect(parseLocalDate('2026-02-29')).toEqual({ ok: false, error: 'not-a-real-date' });
  });

  it('normalizes instants and rejects unparsable ones', () => {
    const parsed = parseInstant('2026-07-29T12:30:00+02:00');
    expect(isOk(parsed)).toBe(true);
    if (isOk(parsed)) expect(parsed.value).toBe('2026-07-29T10:30:00.000Z');
    expect(parseInstant('nonsense')).toEqual({ ok: false, error: 'malformed' });
  });

  it('validates IANA timezones against the runtime database', () => {
    expect(isValidTimezone('Europe/Berlin')).toBe(true);
    expect(isValidTimezone('UTC')).toBe(true);
    expect(isValidTimezone('Mars/Olympus_Mons')).toBe(false);
  });

  it('counts days across a month boundary', () => {
    expect(daysBetween('2026-07-29' as LocalDate, '2026-08-02' as LocalDate)).toBe(4);
    expect(daysBetween('2026-07-29' as LocalDate, '2026-07-29' as LocalDate)).toBe(0);
  });
});
