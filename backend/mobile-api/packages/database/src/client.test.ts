import { describe, it, expect } from 'vitest';
import { createDatabase, schema } from './client.js';

describe('database client', () => {
  it('constructs lazily without opening a connection', async () => {
    const { db, close } = createDatabase({
      url: 'postgres://user:pass@127.0.0.1:5432/does_not_exist',
      maxConnections: 1,
      idleTimeoutSeconds: 1,
      connectTimeoutSeconds: 1,
    });
    expect(db).toBeDefined();
    expect(typeof close).toBe('function');
    await close();
  });

  it('exposes the full schema to the query builder', () => {
    expect(schema).toHaveProperty('mealEvents');
    expect(schema).toHaveProperty('inferenceRuns');
  });
});
