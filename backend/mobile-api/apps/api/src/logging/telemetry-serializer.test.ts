import { describe, it, expect } from 'vitest';
import { FORBIDDEN_TELEMETRY_FIELDS } from '@supernutrition/shared';
import {
  telemetrySerializer,
  requestSerializer,
  responseSerializer,
} from './telemetry-serializer.js';

describe('telemetry serializer (AD-14)', () => {
  it('passes allowlisted fields through', () => {
    expect(
      telemetrySerializer({ attemptId: 'att_x', route: '/gw/v1', outcome: 'candidates' }),
    ).toEqual({ attemptId: 'att_x', route: '/gw/v1', outcome: 'candidates' });
  });

  it('drops every forbidden field before emission', () => {
    const hostile = Object.fromEntries(FORBIDDEN_TELEMETRY_FIELDS.map((f) => [f, 'leak']));
    const emitted = telemetrySerializer({ ...hostile, route: '/gw/v1' });
    expect(emitted).toEqual({ route: '/gw/v1' });
    expect(JSON.stringify(emitted)).not.toContain('leak');
  });

  it('never serializes a request body or headers', () => {
    const emitted = requestSerializer({
      method: 'POST',
      url: '/gw/v1/inference/photo',
      id: 'req-1',
    } as never);
    expect(emitted).toEqual({
      method: 'POST',
      route: '/gw/v1/inference/photo',
      requestId: 'req-1',
    });
    expect(emitted).not.toHaveProperty('body');
    expect(emitted).not.toHaveProperty('headers');
  });

  it('serializes only the response status', () => {
    expect(responseSerializer({ statusCode: 403 })).toEqual({ statusCode: 403 });
  });

  it('omits a non-string request id rather than coercing it', () => {
    expect(requestSerializer({ method: 'GET', url: '/x', id: 42 })).toEqual({
      method: 'GET',
      route: '/x',
      requestId: undefined,
    });
  });
});
