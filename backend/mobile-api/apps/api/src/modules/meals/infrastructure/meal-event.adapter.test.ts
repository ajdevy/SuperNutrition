import { describe, it, expect, vi } from 'vitest';
import { encodePageCursor, decodePageCursor } from '@supernutrition/shared';
import type { DrizzleMealEventRepository, MealEventAggregateRow } from '@supernutrition/database';
import {
  DrizzleMealEventRepositoryAdapter,
  toDomainMealEvent,
  toWriteRows,
} from './meal-event.adapter.js';
import type { MealEvent } from '../domain/index.js';

/**
 * The translation layer between rows and the domain.
 *
 * Both directions are asserted, because a mapper that is only tested one way
 * passes happily while losing a field on the way back.
 */

const aggregate = (over: Partial<MealEventAggregateRow['event']> = {}): MealEventAggregateRow =>
  ({
    event: {
      // A real row's id is a uuid column, and the page cursor is compared
      // against it — so the fixture has to be one (M-03).
      id: '11111111-1111-4111-8111-111111111111',
      userId: 'user-1',
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
    },
    items: [
      {
        id: 'item-1',
        mealEventId: '11111111-1111-4111-8111-111111111111',
        position: 0,
        foodRef: 'usda:fdc:171705',
        displayName: 'Banana, raw',
        servingUnit: 'g',
        servingAmount: '118.000',
        gramWeight: null,
        confidence: null,
        confirmedByUser: true,
        createdAt: new Date('2026-07-29T12:30:04Z'),
      },
    ],
    snapshot: {
      id: 'snap-1',
      mealEventId: '11111111-1111-4111-8111-111111111111',
      catalogSource: 'usda',
      datasetVersion: 'usda-2026.1',
      servingBasis: { unit: 'g', amount: '118', gramWeight: '118' },
      origin: 'client-grounded',
      calories: '105.000',
      proteinG: '1.290',
      fatG: null,
      carbG: null,
      fiberG: null,
      sugarG: null,
      sodiumMg: null,
      perItem: [{ foodRef: 'usda:fdc:171705', calories: '105.000', partial: false }],
      computedAt: new Date('2026-07-29T12:30:04Z'),
    },
  }) as unknown as MealEventAggregateRow;

const rows = (over: Partial<DrizzleMealEventRepository> = {}): DrizzleMealEventRepository =>
  ({
    findById: vi.fn(async () => null),
    findPage: vi.fn(async () => ({ rows: [], hasMore: false })),
    save: vi.fn(async () => aggregate()),
    supersede: vi.fn(async () => aggregate()),
    softDelete: vi.fn(async () => true),
    ...over,
  }) as unknown as DrizzleMealEventRepository;

describe('rows to domain', () => {
  it('carries timestamps out as instants, not Date objects', () => {
    const event = toDomainMealEvent(aggregate());
    expect(event.occurredAt).toBe('2026-07-29T12:30:00.000Z');
    expect(event.createdAt).toBe('2026-07-29T12:30:04.000Z');
    expect(event.confirmedAt).toBe('2026-07-29T12:30:04.000Z');
  });

  it('reconstructs the photo source with its attempt and configuration version', () => {
    const event = toDomainMealEvent(
      aggregate({
        source: 'photo',
        attemptId: 'att_0000000000000000',
        configurationVersion: 'cfg/1',
      }),
    );
    expect(event.source).toEqual({
      kind: 'photo',
      attemptId: 'att_0000000000000000',
      configurationVersion: 'cfg/1',
    });
  });

  it('reconstructs the voice source with its transcript', () => {
    const event = toDomainMealEvent(aggregate({ source: 'voice', transcript: 'a banana' }));
    expect(event.source).toEqual({ kind: 'voice', transcript: 'a banana' });
  });

  it('omits an absent attempt id on the voice path rather than inventing one', () => {
    const event = toDomainMealEvent(aggregate({ source: 'voice', transcript: 'a banana' }));
    expect(event.source).not.toHaveProperty('attemptId');
  });

  it('reports evidence as empty, because this stage does not persist it', () => {
    expect(toDomainMealEvent(aggregate()).evidence).toEqual([]);
  });

  it('keeps nutrition provenance attached to the numbers', () => {
    const event = toDomainMealEvent(aggregate());
    expect(event.nutrition).toMatchObject({
      catalogSource: 'usda',
      datasetVersion: 'usda-2026.1',
      calories: '105.000',
      proteinG: '1.290',
    });
  });

  it('reports a meal with no snapshot as having no nutrition', () => {
    const event = toDomainMealEvent({ ...aggregate(), snapshot: null });
    expect(event.nutrition).toBeNull();
  });

  it('reports an unconfirmed meal as unconfirmed rather than as confirmed now', () => {
    const event = toDomainMealEvent(aggregate({ confirmedAt: null }));
    expect(event.confirmedAt).toBeNull();
  });
});

describe('domain to rows', () => {
  const event = toDomainMealEvent(aggregate());

  it('writes the discriminated source into its columns', () => {
    const photo = toWriteRows({
      ...event,
      source: { kind: 'photo', attemptId: 'att_1' as never, configurationVersion: 'cfg/1' },
    });
    expect(photo.event).toMatchObject({
      source: 'photo',
      attemptId: 'att_1',
      configurationVersion: 'cfg/1',
      transcript: null,
    });

    const voice = toWriteRows({
      ...event,
      source: { kind: 'voice', transcript: 'a banana' },
    });
    expect(voice.event).toMatchObject({
      source: 'voice',
      transcript: 'a banana',
      attemptId: null,
      configurationVersion: null,
    });

    const manual = toWriteRows(event);
    expect(manual.event).toMatchObject({
      source: 'manual',
      attemptId: null,
      configurationVersion: null,
      transcript: null,
    });
  });

  it('numbers item positions from the order they were sent', () => {
    const written = toWriteRows(event);
    expect(written.items[0]).toMatchObject({ position: 0, mealEventId: event.id });
  });

  it('persists the snapshot verbatim, provenance included (AD-7)', () => {
    const written = toWriteRows(event);
    expect(written.snapshot).toMatchObject({
      catalogSource: 'usda',
      datasetVersion: 'usda-2026.1',
      origin: 'client-grounded',
      calories: '105.000',
    });
  });

  it('writes no snapshot row for a meal that carries no nutrition', () => {
    expect(toWriteRows({ ...event, nutrition: null }).snapshot).toBeNull();
  });
});

describe('reading through the port', () => {
  it('reports absence as not-found rather than as an empty meal', async () => {
    const adapter = new DrizzleMealEventRepositoryAdapter(rows());
    const found = await adapter.findById(
      '11111111-1111-4111-8111-111111111111' as never,
      'user-1' as never,
    );
    expect(found).toEqual({ ok: false, error: 'not-found' });
  });

  it('never lets a storage fault surface as a missing meal', async () => {
    const adapter = new DrizzleMealEventRepositoryAdapter(
      rows({
        findById: vi.fn(async () => {
          throw new Error('connection refused');
        }) as never,
      }),
    );
    const found = await adapter.findById(
      '11111111-1111-4111-8111-111111111111' as never,
      'user-1' as never,
    );
    expect(found).toEqual({ ok: false, error: 'storage-unavailable' });
  });
});

describe('paging through the port', () => {
  it('issues a cursor only when there is a further page', async () => {
    const withMore = new DrizzleMealEventRepositoryAdapter(
      rows({ findPage: vi.fn(async () => ({ rows: [aggregate()], hasMore: true })) as never }),
    );
    const page = await withMore.findPage('user-1' as never, { limit: 1 }, {});
    expect(page.ok).toBe(true);
    if (!page.ok) return;

    expect(page.value.page.hasMore).toBe(true);
    const cursor = decodePageCursor(page.value.page.nextCursor ?? '');
    expect(cursor.ok).toBe(true);
    if (cursor.ok) {
      expect(cursor.value).toEqual({
        sortKey: '2026-07-29T12:30:00.000Z',
        id: '11111111-1111-4111-8111-111111111111',
      });
    }
  });

  it('hands back no cursor on the last page', async () => {
    const adapter = new DrizzleMealEventRepositoryAdapter(
      rows({ findPage: vi.fn(async () => ({ rows: [aggregate()], hasMore: false })) as never }),
    );
    const page = await adapter.findPage('user-1' as never, { limit: 20 }, {});
    expect(page.ok && page.value.page.nextCursor).toBeNull();
  });

  it('is an empty page, not a failure, when nothing matches', async () => {
    const adapter = new DrizzleMealEventRepositoryAdapter(rows());
    const page = await adapter.findPage('user-1' as never, { limit: 20 }, {});
    expect(page).toEqual({
      ok: true,
      value: { data: [], page: { nextCursor: null, hasMore: false } },
    });
  });

  it('rejects a cursor it did not issue instead of ignoring it', async () => {
    const findPage = vi.fn(async () => ({ rows: [], hasMore: false }));
    const adapter = new DrizzleMealEventRepositoryAdapter(rows({ findPage: findPage as never }));

    const page = await adapter.findPage('user-1' as never, { limit: 20, cursor: 'nonsense' }, {});

    expect(page).toEqual({ ok: false, error: 'invalid-cursor' });
    // Silently dropping the cursor would quietly restart the feed at page one.
    expect(findPage).not.toHaveBeenCalled();
  });

  it('passes a decoded cursor and every filter down to the query', async () => {
    const findPage = vi.fn(async () => ({ rows: [], hasMore: false }));
    const adapter = new DrizzleMealEventRepositoryAdapter(rows({ findPage: findPage as never }));

    await adapter.findPage(
      'user-1' as never,
      {
        limit: 20,
        cursor: encodePageCursor({
          sortKey: '2026-07-29T12:30:00.000Z',
          id: '99999999-9999-4999-8999-999999999999',
        }),
      },
      {
        from: '2026-07-01' as never,
        to: '2026-07-31' as never,
        mealType: 'lunch',
        source: 'photo',
      },
    );

    expect(findPage).toHaveBeenCalledWith({
      userId: 'user-1',
      limit: 20,
      cursor: { sortKey: '2026-07-29T12:30:00.000Z', id: '99999999-9999-4999-8999-999999999999' },
      from: '2026-07-01',
      to: '2026-07-31',
      mealType: 'lunch',
      source: 'photo',
    });
  });

  it('reports a storage fault as such', async () => {
    const adapter = new DrizzleMealEventRepositoryAdapter(
      rows({
        findPage: vi.fn(async () => {
          throw new Error('connection refused');
        }) as never,
      }),
    );
    const page = await adapter.findPage('user-1' as never, { limit: 20 }, {});
    expect(page).toEqual({ ok: false, error: 'storage-unavailable' });
  });
});

describe('writing through the port', () => {
  const event: MealEvent = toDomainMealEvent(aggregate());

  it('returns the aggregate as stored', async () => {
    const adapter = new DrizzleMealEventRepositoryAdapter(rows());
    const saved = await adapter.save(event);
    expect(saved.ok && saved.value.id).toBe('11111111-1111-4111-8111-111111111111');
  });

  it('reports a failed write as storage-unavailable, never as success', async () => {
    const adapter = new DrizzleMealEventRepositoryAdapter(
      rows({
        save: vi.fn(async () => {
          throw new Error('deadlock detected');
        }) as never,
      }),
    );
    expect(await adapter.save(event)).toEqual({ ok: false, error: 'storage-unavailable' });
  });

  it('reports a correction against a missing original as not-found', async () => {
    const adapter = new DrizzleMealEventRepositoryAdapter(
      rows({ supersede: vi.fn(async () => null) as never }),
    );
    expect(await adapter.supersede('meal-gone' as never, event)).toEqual({
      ok: false,
      error: 'not-found',
    });
  });

  it('returns the new revision after a correction', async () => {
    const adapter = new DrizzleMealEventRepositoryAdapter(
      rows({ supersede: vi.fn(async () => aggregate({ id: 'meal-2', revision: 2 })) as never }),
    );
    const corrected = await adapter.supersede(
      '11111111-1111-4111-8111-111111111111' as never,
      event,
    );
    expect(corrected.ok && corrected.value.revision).toBe(2);
  });

  it('reports a delete that matched nothing as not-found', async () => {
    const adapter = new DrizzleMealEventRepositoryAdapter(
      rows({ softDelete: vi.fn(async () => false) as never }),
    );
    expect(
      await adapter.softDelete('11111111-1111-4111-8111-111111111111' as never, 'user-1' as never),
    ).toEqual({
      ok: false,
      error: 'not-found',
    });
  });

  it('reports a successful delete without a value', async () => {
    const adapter = new DrizzleMealEventRepositoryAdapter(rows());
    expect(
      await adapter.softDelete('11111111-1111-4111-8111-111111111111' as never, 'user-1' as never),
    ).toEqual({
      ok: true,
      value: undefined,
    });
  });

  it('reports a delete that threw as storage-unavailable', async () => {
    const adapter = new DrizzleMealEventRepositoryAdapter(
      rows({
        softDelete: vi.fn(async () => {
          throw new Error('connection refused');
        }) as never,
      }),
    );
    expect(
      await adapter.softDelete('11111111-1111-4111-8111-111111111111' as never, 'user-1' as never),
    ).toEqual({
      ok: false,
      error: 'storage-unavailable',
    });
  });

  it('reports a correction that threw as storage-unavailable', async () => {
    const adapter = new DrizzleMealEventRepositoryAdapter(
      rows({
        supersede: vi.fn(async () => {
          throw new Error('connection refused');
        }) as never,
      }),
    );
    expect(await adapter.supersede('11111111-1111-4111-8111-111111111111' as never, event)).toEqual(
      {
        ok: false,
        error: 'storage-unavailable',
      },
    );
  });
});

describe('defensive mapping of partly written rows', () => {
  it('returns the mapped aggregate when the row exists', async () => {
    const adapter = new DrizzleMealEventRepositoryAdapter(
      rows({ findById: vi.fn(async () => aggregate()) as never }),
    );
    const found = await adapter.findById(
      '11111111-1111-4111-8111-111111111111' as never,
      'user-1' as never,
    );
    expect(found.ok && found.value.id).toBe('11111111-1111-4111-8111-111111111111');
    expect(found.ok && found.value.items).toHaveLength(1);
  });

  // A photo row without its attempt columns should not produce `undefined`
  // inside a discriminated union the rest of the code destructures.
  it('substitutes empty provenance rather than undefined on a photo row', () => {
    const event = toDomainMealEvent(aggregate({ source: 'photo' }));
    expect(event.source).toEqual({ kind: 'photo', attemptId: '', configurationVersion: '' });
  });

  it('substitutes an empty transcript rather than undefined on a voice row', () => {
    const event = toDomainMealEvent(aggregate({ source: 'voice' }));
    expect(event.source).toEqual({ kind: 'voice', transcript: '' });
  });

  it('keeps the attempt id when a voice row carries one', () => {
    const event = toDomainMealEvent(
      aggregate({ source: 'voice', transcript: 'a banana', attemptId: 'att_0000000000000000' }),
    );
    expect(event.source).toMatchObject({ attemptId: 'att_0000000000000000' });
  });

  it('writes no confirmation timestamp for a meal that has none', () => {
    const event = toDomainMealEvent(aggregate({ confirmedAt: null }));
    expect(toWriteRows(event).event.confirmedAt).toBeNull();
  });
});

describe('M-01 — a lost correction race is stale, not a server outage', () => {
  /**
   * `supersede` selects then inserts; the unique index on `supersedes_id` is the
   * real concurrency guard. Two corrections of one meal can both observe no
   * successor, and the loser hits the index.
   *
   * Every throw used to become `storage-unavailable` — a 500 blaming the server
   * for a race the database resolved correctly. The honest answer is the same
   * one the pre-check already gives: somebody corrected this meal first.
   *
   * These assert the MAPPING. The race itself needs two real concurrent
   * PostgreSQL transactions; an in-memory repository cannot produce a
   * unique-index violation, so it cannot demonstrate the race.
   */
  const replacement = toDomainMealEvent(aggregate());
  const throwing = (cause: unknown) =>
    new DrizzleMealEventRepositoryAdapter(
      rows({
        supersede: vi.fn(async () => {
          throw cause;
        }) as never,
      }),
    );

  it('reports a unique violation as not-found', async () => {
    const violation = Object.assign(new Error('duplicate key value'), { code: '23505' });
    expect(
      await throwing(violation).supersede(
        '11111111-1111-4111-8111-111111111111' as never,
        replacement,
      ),
    ).toEqual({
      ok: false,
      error: 'not-found',
    });
  });

  it('reports a WRAPPED unique violation as not-found', async () => {
    // The shape a real database produces. Drizzle wraps the driver error, so the
    // thrown value is a plain Error whose `code` is undefined and whose `cause`
    // carries 23505. Checking only the top level passed the test above and would
    // have failed in production; verified against PostgreSQL 16.
    const wrapped = new Error('Failed query: insert into meal_events', {
      cause: Object.assign(new Error('duplicate key value violates unique constraint'), {
        code: '23505',
        name: 'PostgresError',
      }),
    });
    expect(
      await throwing(wrapped).supersede(
        '11111111-1111-4111-8111-111111111111' as never,
        replacement,
      ),
    ).toEqual({
      ok: false,
      error: 'not-found',
    });
  });

  it('does not loop forever on a self-referential cause chain', async () => {
    const loop: { cause?: unknown } = {};
    loop.cause = loop;
    expect(
      await throwing(loop).supersede('11111111-1111-4111-8111-111111111111' as never, replacement),
    ).toEqual({
      ok: false,
      error: 'storage-unavailable',
    });
  });

  it('still reports a genuine outage as storage-unavailable', async () => {
    // The mapping must not swallow real failures: a connection reset is not a
    // stale client.
    const reset = Object.assign(new Error('connection terminated'), { code: '08006' });
    expect(
      await throwing(reset).supersede('11111111-1111-4111-8111-111111111111' as never, replacement),
    ).toEqual({
      ok: false,
      error: 'storage-unavailable',
    });
  });

  it('reports an error with no code as storage-unavailable', async () => {
    expect(
      await throwing(new Error('something else')).supersede(
        '11111111-1111-4111-8111-111111111111' as never,
        replacement,
      ),
    ).toEqual({
      ok: false,
      error: 'storage-unavailable',
    });
  });
});
