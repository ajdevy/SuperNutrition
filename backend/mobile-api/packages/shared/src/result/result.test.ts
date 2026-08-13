import { describe, it, expect } from 'vitest';
import { ok, err, isOk, isErr, map, mapErr, flatMap, unwrapOr, unwrap, all } from './result.js';

describe('Result', () => {
  it('narrows a success', () => {
    const r = ok(42);
    expect(isOk(r)).toBe(true);
    expect(isErr(r)).toBe(false);
    if (isOk(r)) expect(r.value).toBe(42);
  });

  it('narrows a failure', () => {
    const r = err('boom');
    expect(isErr(r)).toBe(true);
    expect(isOk(r)).toBe(false);
    if (isErr(r)) expect(r.error).toBe('boom');
  });

  it('maps a success and leaves a failure untouched', () => {
    expect(map(ok(2), (n) => n * 3)).toEqual(ok(6));
    expect(map(err<string>('e'), (n: number) => n * 3)).toEqual(err('e'));
  });

  it('mapErr transforms a failure and leaves a success untouched', () => {
    expect(mapErr(err('e'), (e) => `${e}!`)).toEqual(err('e!'));
    expect(mapErr(ok(1), (e: string) => `${e}!`)).toEqual(ok(1));
  });

  it('flatMap chains fallible operations and short-circuits', () => {
    const half = (n: number) => (n % 2 === 0 ? ok(n / 2) : err('odd'));
    expect(flatMap(ok(8), half)).toEqual(ok(4));
    expect(flatMap(ok(7), half)).toEqual(err('odd'));
    expect(flatMap(err<string>('prior'), half)).toEqual(err('prior'));
  });

  it('unwrapOr substitutes a fallback only for a failure', () => {
    expect(unwrapOr(ok(1), 9)).toBe(1);
    expect(unwrapOr(err<string>('e'), 9)).toBe(9);
  });

  it('unwrap throws on a failure', () => {
    expect(unwrap(ok('v'))).toBe('v');
    expect(() => unwrap(err({ code: 'nope' }))).toThrow(/Called unwrap on a failed Result/);
  });

  it('all collects successes and short-circuits on the first failure', () => {
    expect(all([ok(1), ok(2), ok(3)])).toEqual(ok([1, 2, 3]));
    expect(all([ok(1), err('bad'), ok(3)])).toEqual(err('bad'));
    expect(all<number, string>([])).toEqual(ok([]));
  });
});
