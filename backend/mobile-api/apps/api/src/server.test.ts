import { describe, it, expect } from 'vitest';
import { describeServer } from './server.js';
import { MIDDLEWARE_ORDER } from './middleware/index.js';

describe('server assembly', () => {
  it('declares middleware in the documented order', () => {
    const { middleware } = describeServer();
    expect(middleware).toEqual(MIDDLEWARE_ORDER);
  });

  // docs/06 §6.3: rejecting an oversized body before parsing it is the
  // difference between a cheap 413 and an expensive one.
  it('guards content length before authentication and validation', () => {
    const { middleware } = describeServer();
    expect(middleware.indexOf('content-length-guard')).toBeLessThan(
      middleware.indexOf('authentication'),
    );
    expect(middleware.indexOf('authentication')).toBeLessThan(
      middleware.indexOf('schema-validation'),
    );
  });

  it('assigns a correlation id first so every log line and error carries one', () => {
    expect(describeServer().middleware[0]).toBe('correlation-id');
  });

  it('registers both surfaces', () => {
    const { routes } = describeServer();
    expect(routes.some((r) => r.path.startsWith('/gw/v1'))).toBe(true);
    expect(routes.some((r) => r.path.startsWith('/api/v1'))).toBe(true);
  });
});
