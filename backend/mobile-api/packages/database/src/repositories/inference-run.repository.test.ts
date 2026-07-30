import { describe, it, expect } from 'vitest';
import { DrizzleInferenceRunRecorder } from './inference-run.repository.js';
import type { Database } from '../client.js';
import type { NewInferenceRunRow } from '../schema/inference-runs.js';

/**
 * The telemetry writer, exercised against a fake query builder.
 *
 * What matters is the SHAPE of what it persists, not that Drizzle works: the
 * insert is built field by field from an allowlist so a future caller cannot
 * attach a user identifier and have it silently written (AD-14, docs/05 §5.4).
 */

const fakeDb = () => {
  const inserted: Record<string, unknown>[] = [];
  const deletedWhere: unknown[] = [];
  const db = {
    insert: () => ({
      values: async (row: Record<string, unknown>) => {
        inserted.push(row);
      },
    }),
    delete: () => ({
      where: (condition: unknown) => {
        deletedWhere.push(condition);
        return { returning: async () => [{ id: 'a' }, { id: 'b' }] };
      },
    }),
  } as unknown as Database;
  return { db, inserted, deletedWhere };
};

const row: NewInferenceRunRow = {
  attemptId: 'att_0000000000000000',
  passNumber: 1,
  provider: 'openrouter',
  modelId: 'vendor/model',
  modelProfileVersion: '2026-07-29.1',
  outcome: 'candidates',
  success: true,
  confidence: '0.930',
  durationMs: 1234,
  durationBucket: '1s-3s',
  promptTokens: 1000,
  completionTokens: 500,
  estimatedCostMicros: 200n,
  failureCode: null,
  marketJurisdiction: 'ZZ',
  processingRegion: 'fixture-region',
  environment: 'test',
};

describe('recording a run', () => {
  it('persists the benchmarking fields', async () => {
    const { db, inserted } = fakeDb();
    await new DrizzleInferenceRunRecorder(db).record(row);

    expect(inserted).toHaveLength(1);
    expect(inserted[0]).toMatchObject({
      attemptId: 'att_0000000000000000',
      passNumber: 1,
      modelId: 'vendor/model',
      durationBucket: '1s-3s',
      estimatedCostMicros: 200n,
      environment: 'test',
    });
  });

  // The reason the insert is an allowlist rather than a spread.
  it('drops any field a caller attaches that is not on the allowlist', async () => {
    const { db, inserted } = fakeDb();
    await new DrizzleInferenceRunRecorder(db).record({
      ...row,
      userId: 'user-1',
      prompt: 'identify this food',
      responseBody: '{...}',
      label: 'Banana',
    } as unknown as NewInferenceRunRow);

    const persisted = inserted[0] as Record<string, unknown>;
    for (const forbidden of ['userId', 'prompt', 'responseBody', 'label']) {
      expect(persisted, `persisted ${forbidden}`).not.toHaveProperty(forbidden);
    }
  });

  it('normalizes absent optional fields to null', async () => {
    const { db, inserted } = fakeDb();
    await new DrizzleInferenceRunRecorder(db).record({
      attemptId: 'att_1',
      passNumber: 2,
      provider: 'openrouter',
      modelId: 'm',
      modelProfileVersion: 'v',
      outcome: 'failure',
      success: false,
      durationMs: 10,
      durationBucket: 'under-1s',
      environment: 'test',
    } as NewInferenceRunRow);

    expect(inserted[0]).toMatchObject({
      confidence: null,
      promptTokens: null,
      completionTokens: null,
      estimatedCostMicros: null,
      failureCode: null,
      marketJurisdiction: null,
      processingRegion: null,
    });
  });
});

describe('retention sweep', () => {
  it('returns the number of rows pruned', async () => {
    const { db, deletedWhere } = fakeDb();
    const pruned = await new DrizzleInferenceRunRecorder(db).pruneOlderThan(
      new Date('2026-04-30T00:00:00Z'),
    );
    expect(pruned).toBe(2);
    expect(deletedWhere).toHaveLength(1);
  });
});
