import { describe, it, expect } from 'vitest';
import { QueryBuilder } from 'drizzle-orm/pg-core';
import {
  DrizzleMealEventRepository,
  mealEventByIdConditions,
  mealEventPageConditions,
} from './meal-event.repository.js';
import { mealEvents } from '../schema/meal-events.js';
import type { Database } from '../client.js';
import type { MealEventRow, NewMealEventRow } from '../schema/meal-events.js';
import type { MealItemRow } from '../schema/meal-items.js';
import type { NutritionSnapshotRow } from '../schema/nutrition-snapshots.js';

/**
 * Two things are worth testing here and they need different instruments.
 *
 * The SQL a page runs is asserted by rendering the real condition builders
 * through drizzle's own query builder — no connection, no fake, the exact SQL
 * that ships. Orchestration (transactions, the extra row that answers
 * `hasMore`, ownership, row grouping) is asserted against a recording double,
 * because that behaviour lives in the repository rather than in the SQL.
 */

const renderedSql = (condition: ReturnType<typeof mealEventPageConditions>): string =>
  new QueryBuilder().select().from(mealEvents).where(condition).toSQL().sql;

const renderedParams = (condition: ReturnType<typeof mealEventPageConditions>): unknown[] =>
  new QueryBuilder().select().from(mealEvents).where(condition).toSQL().params;

interface Call {
  readonly op: string;
  readonly args: readonly unknown[];
}

/** Chainable, thenable recorder shaped like drizzle's builder. */
const recordingDb = (
  results: readonly unknown[][],
): { db: Database; calls: Call[]; transactions: number } => {
  const queue = results.map((rows) => rows);
  const calls: Call[] = [];
  const state = { transactions: 0 };

  const chain = (op: string, args: readonly unknown[]): Record<string, unknown> => {
    calls.push({ op, args });
    const builder: Record<string, unknown> = {};
    for (const method of [
      'from',
      'where',
      'orderBy',
      'limit',
      'values',
      'set',
      'returning',
      'onConflictDoUpdate',
    ]) {
      builder[method] = (...next: unknown[]) => {
        calls.push({ op: method, args: next });
        return builder;
      };
    }
    builder['then'] = (
      resolve: (value: unknown) => unknown,
      reject?: (error: unknown) => unknown,
    ) => Promise.resolve(queue.shift() ?? []).then(resolve, reject);
    return builder;
  };

  const db = {
    select: (...args: unknown[]) => chain('select', args),
    insert: (...args: unknown[]) => chain('insert', args),
    update: (...args: unknown[]) => chain('update', args),
    transaction: (fn: (tx: unknown) => Promise<unknown>) => {
      state.transactions += 1;
      return fn(db);
    },
  } as unknown as Database;

  return {
    db,
    calls,
    get transactions() {
      return state.transactions;
    },
  };
};

const eventRow = (over: Partial<MealEventRow> = {}): MealEventRow =>
  ({
    id: '3f7c1a2e-0000-4000-8000-000000000001',
    userId: '3f7c1a2e-0000-4000-8000-0000000000ff',
    source: 'manual',
    status: 'confirmed',
    occurredAt: new Date('2026-07-29T12:30:00Z'),
    localDate: '2026-07-29',
    timezone: 'Europe/Berlin',
    mealType: 'lunch',
    revision: 1,
    supersedesId: null,
    attemptId: null,
    configurationVersion: null,
    transcript: null,
    createdAt: new Date('2026-07-29T12:30:04Z'),
    confirmedAt: new Date('2026-07-29T12:30:04Z'),
    deletedAt: null,
    ...over,
  }) as MealEventRow;

const itemRow = (over: Partial<MealItemRow> = {}): MealItemRow =>
  ({
    id: '3f7c1a2e-0000-4000-8000-000000000011',
    mealEventId: '3f7c1a2e-0000-4000-8000-000000000001',
    position: 0,
    foodRef: 'usda:fdc:171705',
    displayName: 'Banana, raw',
    servingUnit: 'g',
    servingAmount: '118.000',
    gramWeight: null,
    confidence: null,
    confirmedByUser: true,
    createdAt: new Date('2026-07-29T12:30:04Z'),
    ...over,
  }) as MealItemRow;

const snapshotRow = (mealEventId: string): NutritionSnapshotRow =>
  ({
    id: '3f7c1a2e-0000-4000-8000-000000000021',
    mealEventId,
    catalogSource: 'usda',
    datasetVersion: 'usda-2026.1',
    servingBasis: { unit: 'g', amount: '118', gramWeight: '118' },
    origin: 'client-grounded',
    calories: '105.000',
    proteinG: null,
    fatG: null,
    carbG: null,
    fiberG: null,
    sugarG: null,
    sodiumMg: null,
    perItem: [],
    computedAt: new Date('2026-07-29T12:30:04Z'),
  }) as NutritionSnapshotRow;

const newEvent: NewMealEventRow = {
  id: '3f7c1a2e-0000-4000-8000-000000000002',
  userId: '3f7c1a2e-0000-4000-8000-0000000000ff',
  source: 'manual',
  status: 'confirmed',
  occurredAt: new Date('2026-07-29T12:30:00Z'),
  localDate: '2026-07-29',
  timezone: 'Europe/Berlin',
  mealType: 'lunch',
};

describe('meal event SQL', () => {
  const userId = '3f7c1a2e-0000-4000-8000-0000000000ff';

  it('filters ownership and soft deletion on a single read', () => {
    const sql = renderedSql(mealEventByIdConditions('meal-1', userId));
    expect(sql).toContain('"user_id" =');
    expect(sql).toContain('"deleted_at" is null');
    expect(renderedParams(mealEventByIdConditions('meal-1', userId))).toEqual(['meal-1', userId]);
  });

  it('excludes rows a later revision supersedes', () => {
    const sql = renderedSql(mealEventPageConditions({ userId, limit: 20 }));
    // The correction retains the original row; the feed must not show a
    // corrected lunch twice, nor count its calories twice.
    expect(sql).toContain('not exists');
    expect(sql).toContain('"supersedes_id"');
  });

  // Filtering the replacement's own deleted_at would make deleting a corrected
  // meal resurrect the revision the user had already replaced.
  it('does not un-supersede a row when its replacement is deleted', () => {
    const sql = renderedSql(mealEventPageConditions({ userId, limit: 20 }));
    expect(sql.match(/deleted_at" is null/g)).toHaveLength(1);
    expect(sql).toContain('"meal_events"."deleted_at" is null');
  });

  it('never lists another user or a deleted row', () => {
    const sql = renderedSql(mealEventPageConditions({ userId, limit: 20 }));
    expect(sql).toContain('"user_id" =');
    expect(sql).toContain('"meal_events"."deleted_at" is null');
  });

  it('applies each optional filter only when it is present', () => {
    const bare = renderedSql(mealEventPageConditions({ userId, limit: 20 }));
    expect(bare).not.toContain('"local_date" >=');
    expect(bare).not.toContain('"meal_type" =');
    expect(bare).not.toContain('"source" =');

    const filtered = mealEventPageConditions({
      userId,
      limit: 20,
      from: '2026-07-01',
      to: '2026-07-31',
      mealType: 'lunch',
      source: 'photo',
    });
    const sql = renderedSql(filtered);
    expect(sql).toContain('"local_date" >=');
    expect(sql).toContain('"local_date" <=');
    expect(sql).toContain('"meal_type" =');
    expect(sql).toContain('"source" =');
    expect(renderedParams(filtered)).toEqual([
      userId,
      '2026-07-01',
      '2026-07-31',
      'lunch',
      'photo',
    ]);
  });

  // Two meals logged in the same second are ordinary. A cursor on the timestamp
  // alone repeats or skips every row sharing it.
  it('breaks a cursor tie on the id rather than on the timestamp alone', () => {
    const condition = mealEventPageConditions({
      userId,
      limit: 20,
      cursor: { sortKey: '2026-07-29T12:30:00.000Z', id: 'meal-9' },
    });
    const sql = renderedSql(condition);
    expect(sql).toContain('"occurred_at" <');
    expect(sql).toContain('"occurred_at" =');
    expect(sql).toContain('"id" <');
    expect(renderedParams(condition)).toContain('meal-9');
  });
});

describe('reading one meal', () => {
  it('returns null when the row is absent or owned by someone else', async () => {
    const { db } = recordingDb([[]]);
    const found = await new DrizzleMealEventRepository(db).findById('meal-1', 'user-1');
    expect(found).toBeNull();
  });

  it('joins items and the frozen snapshot, ordered by item position', async () => {
    const event = eventRow();
    const { db } = recordingDb([
      [event],
      [itemRow({ id: 'b', position: 1 }), itemRow({ id: 'a', position: 0 })],
      [snapshotRow(event.id)],
    ]);

    const found = await new DrizzleMealEventRepository(db).findById(event.id, event.userId);

    expect(found?.items.map((item) => item.position)).toEqual([0, 1]);
    expect(found?.snapshot?.calories).toBe('105.000');
  });

  it('reports no snapshot rather than inventing one', async () => {
    const { db } = recordingDb([[eventRow()], [], []]);
    const found = await new DrizzleMealEventRepository(db).findById('meal-1', 'user-1');
    expect(found?.snapshot).toBeNull();
  });
});

describe('listing meals', () => {
  it('asks for one row beyond the page to answer hasMore without a count', async () => {
    const first = eventRow({ id: 'meal-1' });
    const second = eventRow({ id: 'meal-2' });
    const { db, calls } = recordingDb([[first, second], [itemRow()], []]);

    const page = await new DrizzleMealEventRepository(db).findPage({
      userId: 'user-1',
      limit: 1,
    });

    expect(calls.some((call) => call.op === 'limit' && call.args[0] === 2)).toBe(true);
    expect(page.hasMore).toBe(true);
    expect(page.rows).toHaveLength(1);
    expect(page.rows[0]?.event.id).toBe('meal-1');
  });

  it('reports no further page when the extra row is absent', async () => {
    const { db } = recordingDb([[eventRow()], [], []]);
    const page = await new DrizzleMealEventRepository(db).findPage({ userId: 'user-1', limit: 20 });
    expect(page.hasMore).toBe(false);
    expect(page.rows).toHaveLength(1);
  });

  it('is an empty page, not a failure, when the user has logged nothing', async () => {
    const { db, calls } = recordingDb([[]]);
    const page = await new DrizzleMealEventRepository(db).findPage({ userId: 'user-1', limit: 20 });

    expect(page).toEqual({ rows: [], hasMore: false });
    // No child lookup is issued for an empty page.
    expect(calls.filter((call) => call.op === 'select')).toHaveLength(1);
  });

  it('groups each meals items and snapshot with its own event', async () => {
    const first = eventRow({ id: 'meal-1' });
    const second = eventRow({ id: 'meal-2' });
    const { db } = recordingDb([
      [first, second],
      [
        itemRow({ id: 'i2', mealEventId: 'meal-2', position: 0 }),
        itemRow({ id: 'i1', mealEventId: 'meal-1', position: 0 }),
      ],
      [snapshotRow('meal-2')],
    ]);

    const page = await new DrizzleMealEventRepository(db).findPage({
      userId: 'user-1',
      limit: 20,
    });

    expect(page.rows[0]?.items.map((item) => item.id)).toEqual(['i1']);
    expect(page.rows[0]?.snapshot).toBeNull();
    expect(page.rows[1]?.items.map((item) => item.id)).toEqual(['i2']);
    expect(page.rows[1]?.snapshot?.mealEventId).toBe('meal-2');
  });
});

describe('saving an aggregate', () => {
  it('writes root, items, and snapshot in one transaction', async () => {
    const saved = eventRow({ id: 'meal-new' });
    const recorder = recordingDb([[saved], [itemRow({ mealEventId: 'meal-new' })], []]);

    await new DrizzleMealEventRepository(recorder.db).save(newEvent, [itemRow()], null);

    expect(recorder.transactions).toBe(1);
  });

  it('binds every child row to the id the insert actually returned', async () => {
    const saved = eventRow({ id: 'meal-new' });
    const recorder = recordingDb([
      [saved],
      [itemRow({ mealEventId: 'meal-new' })],
      [snapshotRow('meal-new')],
    ]);

    await new DrizzleMealEventRepository(recorder.db).save(
      newEvent,
      [itemRow({ mealEventId: 'stale' })],
      snapshotRow('stale'),
    );

    const written = recorder.calls.filter((call) => call.op === 'values');
    const items = written[1]?.args[0] as { mealEventId: string }[];
    const snapshot = written[2]?.args[0] as { mealEventId: string };
    expect(items[0]?.mealEventId).toBe('meal-new');
    expect(snapshot.mealEventId).toBe('meal-new');
  });

  it('skips the child inserts a meal without items or nutrition does not need', async () => {
    const recorder = recordingDb([[eventRow()]]);
    const result = await new DrizzleMealEventRepository(recorder.db).save(newEvent, [], null);

    expect(recorder.calls.filter((call) => call.op === 'insert')).toHaveLength(1);
    expect(result.items).toEqual([]);
    expect(result.snapshot).toBeNull();
  });

  it('fails loudly when the insert returns nothing', async () => {
    const { db } = recordingDb([[]]);
    await expect(new DrizzleMealEventRepository(db).save(newEvent, [], null)).rejects.toThrow(
      /returned no row/,
    );
  });
});

describe('superseding a meal', () => {
  it('points the replacement at what it supersedes and retains the original', async () => {
    const recorder = recordingDb([
      [{ id: 'meal-1' }],
      [eventRow({ id: 'meal-2', revision: 2 })],
      [],
      [],
    ]);

    const result = await new DrizzleMealEventRepository(recorder.db).supersede(
      'meal-1',
      { ...newEvent, id: 'meal-2', revision: 2 },
      [],
      null,
    );

    const inserted = recorder.calls.find((call) => call.op === 'values')?.args[0] as {
      supersedesId?: string;
      revision?: number;
    };
    expect(inserted.supersedesId).toBe('meal-1');
    expect(inserted.revision).toBe(2);
    expect(result?.event.id).toBe('meal-2');
    // No update touches the original row.
    expect(recorder.calls.some((call) => call.op === 'update')).toBe(false);
  });

  it('refuses to invent a meal when the original is absent or not owned', async () => {
    const recorder = recordingDb([[]]);

    const result = await new DrizzleMealEventRepository(recorder.db).supersede(
      'meal-missing',
      { ...newEvent, id: 'meal-2', revision: 2 },
      [],
      null,
    );

    expect(result).toBeNull();
    expect(recorder.calls.some((call) => call.op === 'insert')).toBe(false);
  });

  it('is one transaction, so a replacement cannot exist without its items', async () => {
    const recorder = recordingDb([
      [{ id: 'meal-1' }],
      [eventRow({ id: 'meal-2' })],
      [itemRow()],
      [],
    ]);

    await new DrizzleMealEventRepository(recorder.db).supersede(
      'meal-1',
      { ...newEvent, id: 'meal-2' },
      [itemRow()],
      null,
    );

    expect(recorder.transactions).toBe(1);
  });
});

describe('soft deleting a meal', () => {
  it('reports whether a row was actually deleted', async () => {
    const present = recordingDb([[{ id: 'meal-1' }]]);
    await expect(
      new DrizzleMealEventRepository(present.db).softDelete('meal-1', 'user-1'),
    ).resolves.toBe(true);

    const absent = recordingDb([[]]);
    await expect(
      new DrizzleMealEventRepository(absent.db).softDelete('meal-1', 'user-1'),
    ).resolves.toBe(false);
  });

  it('sets deleted_at rather than removing history', async () => {
    const recorder = recordingDb([[{ id: 'meal-1' }]]);
    await new DrizzleMealEventRepository(recorder.db).softDelete('meal-1', 'user-1');

    const set = recorder.calls.find((call) => call.op === 'set')?.args[0] as {
      deletedAt?: unknown;
    };
    expect(set.deletedAt).toBeDefined();
    expect(recorder.calls.some((call) => call.op === 'delete')).toBe(false);
  });
});

/**
 * A use case may need several repositories inside one transaction (docs/05
 * §5.7), so every write accepts a handle. Opening a second transaction inside
 * the caller's would silently break that atomicity.
 */
describe('composing into a caller transaction', () => {
  it('reuses the handle it is given on save instead of opening its own', async () => {
    const recorder = recordingDb([[eventRow()]]);
    const outer = recorder.db as unknown as Parameters<DrizzleMealEventRepository['save']>[3];

    await new DrizzleMealEventRepository(recorder.db).save(newEvent, [], null, outer);

    expect(recorder.transactions).toBe(0);
    expect(recorder.calls.some((call) => call.op === 'insert')).toBe(true);
  });

  it('reuses the handle it is given on supersede', async () => {
    const recorder = recordingDb([[{ id: 'meal-1' }], [eventRow({ id: 'meal-2' })]]);
    const outer = recorder.db as unknown as Parameters<DrizzleMealEventRepository['supersede']>[4];

    const result = await new DrizzleMealEventRepository(recorder.db).supersede(
      'meal-1',
      { ...newEvent, id: 'meal-2' },
      [],
      null,
      outer,
    );

    expect(recorder.transactions).toBe(0);
    expect(result?.event.id).toBe('meal-2');
  });
});
