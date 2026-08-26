import { describe, it, expect } from 'vitest';
import { generateCorrelationId, resolveCorrelationId } from './correlation-id.js';

describe('correlation ids', () => {
  it('generates unique hex ids', () => {
    const a = generateCorrelationId();
    expect(a).toMatch(/^[0-9a-f]{32}$/);
    expect(a).not.toBe(generateCorrelationId());
  });

  it('accepts a well-formed client-supplied id', () => {
    expect(resolveCorrelationId('01J8X4Q2K7R9M3P5')).toBe('01J8X4Q2K7R9M3P5');
  });

  it('generates one when none is supplied', () => {
    expect(resolveCorrelationId(undefined)).toMatch(/^[0-9a-f]{32}$/);
  });

  // An arbitrary header value ends up in structured logs, where a crafted
  // string could forge log entries.
  it.each([
    ['too short', 'abc'],
    ['too long', 'x'.repeat(200)],
    ['newline injection', 'ok\nlevel=fatal'],
    ['spaces', 'has spaces'],
    ['quotes', 'a"b'],
  ])('replaces a malformed id (%s)', (_name, supplied) => {
    expect(resolveCorrelationId(supplied)).toMatch(/^[0-9a-f]{32}$/);
  });
});
