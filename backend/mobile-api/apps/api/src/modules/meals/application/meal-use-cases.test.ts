import { describe, it, expect, vi } from 'vitest';
import { ok, err } from '@supernutrition/shared';
import {
  CreateMealEventHandler,
  CorrectMealEventHandler,
  DeleteMealEventHandler,
  type CreateMealCommand,
  type MealCommandDeps,
} from './commands.js';
import { GetMealEventHandler, ListMealEventsHandler } from './queries.js';
import type { MealEvent, MealEventRepository, NutritionSnapshot } from '../domain/index.js';

const NOW = new Date('2026-07-29T12:30:04Z');

const nutrition = {
  origin: 'client-grounded',
  catalogSource: 'usda',
  datasetVersion: 'usda-2026.1',
  servingBasis: { unit: 'g', amount: '118', gramWeight: '118' },
  calories: '105.000',
  proteinG: '1.290',
  fatG: null,
  carbG: null,
  fiberG: null,
  sugarG: null,
  sodiumMg: null,
  perItem: [],
} as unknown as NutritionSnapshot;

const items = [
  {
    foodRef: 'usda:fdc:171705',
    displayName: 'Banana, raw',
    servingUnit: 'g',
    servingAmount: '118',
    confirmedByUser: true,
  },
];

const manualCommand = (over: Partial<CreateMealCommand> = {}): CreateMealCommand =>
  ({
    source: 'manual',
    userId: 'user-1',
    occurredAt: '2026-07-29T12:30:00Z',
    localDate: '2026-07-29',
    timezone: 'Europe/Berlin',
    mealType: 'lunch',
    items,
    nutrition,
    ...over,
  }) as CreateMealCommand;

/** Ids are injected so an assertion reads as a fact rather than as a regex. */
const deps = (repository: Partial<MealEventRepository> = {}): MealCommandDeps => {
  let next = 0;
  return {
    repository: {
      findById: vi.fn(async () => err('not-found' as const)),
      findPage: vi.fn(async () => ok({ data: [], page: { nextCursor: null, hasMore: false } })),
      save: vi.fn(async (event: MealEvent) => ok(event)),
      supersede: vi.fn(async (_id: unknown, event: MealEvent) => ok(event)),
      softDelete: vi.fn(async () => ok(undefined)),
      ...repository,
    } as MealEventRepository,
    now: () => NOW,
    uuid: () => `id-${++next}`,
  };
};

const storedEvent = (over: Partial<MealEvent> = {}): MealEvent =>
  ({
    id: 'meal-1',
    userId: 'user-1',
    source: { kind: 'manual' },
    status: 'confirmed',
    occurredAt: '2026-07-29T12:30:00.000Z',
    localDate: '2026-07-29',
    timezone: 'Europe/Berlin',
    mealType: 'lunch',
    revision: 1,
    supersedesId: null,
    items: [],
    nutrition,
    evidence: [],
    createdAt: '2026-07-29T12:30:04.000Z',
    confirmedAt: '2026-07-29T12:30:04.000Z',
    ...over,
  }) as MealEvent;

describe('creating a meal', () => {
  it('confirms the meal, because the user already did', async () => {
    const created = await new CreateMealEventHandler(deps()).execute(manualCommand());

    expect(created.ok).toBe(true);
    if (!created.ok) return;
    expect(created.value.status).toBe('confirmed');
    expect(created.value.revision).toBe(1);
    expect(created.value.supersedesId).toBeNull();
    expect(created.value.confirmedAt).toBe(NOW.toISOString());
  });

  it('keeps the client local date and timezone rather than deriving them from UTC', async () => {
    const created = await new CreateMealEventHandler(deps()).execute(manualCommand());
    expect(created.ok && created.value.localDate).toBe('2026-07-29');
    expect(created.ok && created.value.timezone).toBe('Europe/Berlin');
  });

  it('numbers items by the order sent and gives each one an id', async () => {
    const created = await new CreateMealEventHandler(deps()).execute(
      manualCommand({ items: [items[0]!, { ...items[0]!, displayName: 'Apple' }] }),
    );

    expect(created.ok).toBe(true);
    if (!created.ok) return;
    expect(created.value.items.map((item) => item.position)).toEqual([0, 1]);
    expect(new Set(created.value.items.map((item) => item.id)).size).toBe(2);
    expect(created.value.items[0]?.mealEventId).toBe(created.value.id);
  });

  it('normalizes absent optional item facts to null instead of dropping them', async () => {
    const created = await new CreateMealEventHandler(deps()).execute(manualCommand());
    expect(created.ok && created.value.items[0]).toMatchObject({
      gramWeight: null,
      confidence: null,
    });
  });

  it('carries the photo attempt and configuration version onto the event', async () => {
    const created = await new CreateMealEventHandler(deps()).execute(
      manualCommand({
        source: 'photo',
        attemptId: 'att_0000000000000000',
        configurationVersion: 'cfg/1',
        evidence: [],
      } as unknown as Partial<CreateMealCommand>),
    );
    expect(created.ok && created.value.source).toEqual({
      kind: 'photo',
      attemptId: 'att_0000000000000000',
      configurationVersion: 'cfg/1',
    });
  });

  it('carries the voice transcript onto the event', async () => {
    const created = await new CreateMealEventHandler(deps()).execute(
      manualCommand({
        source: 'voice',
        transcript: 'a banana',
      } as unknown as Partial<CreateMealCommand>),
    );
    expect(created.ok && created.value.source).toEqual({ kind: 'voice', transcript: 'a banana' });
  });

  it('keeps an optional voice attempt id when the client identified the text', async () => {
    const created = await new CreateMealEventHandler(deps()).execute(
      manualCommand({
        source: 'voice',
        transcript: 'a banana',
        attemptId: 'att_0000000000000000',
      } as unknown as Partial<CreateMealCommand>),
    );
    expect(created.ok && created.value.source).toMatchObject({ attemptId: 'att_0000000000000000' });
  });

  // AD-7: an ungrounded number must not enter the database, by any door.
  it('refuses nutrition without a catalog source', async () => {
    const created = await new CreateMealEventHandler(deps()).execute(
      manualCommand({ nutrition: { ...nutrition, catalogSource: undefined } as never }),
    );
    expect(created).toEqual({ ok: false, error: 'ungrounded-nutrition' });
  });

  it('refuses nutrition without a dataset version', async () => {
    const created = await new CreateMealEventHandler(deps()).execute(
      manualCommand({ nutrition: { ...nutrition, datasetVersion: '   ' } as never }),
    );
    expect(created).toEqual({ ok: false, error: 'ungrounded-nutrition' });
  });

  it('refuses a meal with no items', async () => {
    const created = await new CreateMealEventHandler(deps()).execute(manualCommand({ items: [] }));
    expect(created).toEqual({ ok: false, error: 'invalid-items' });
  });

  it('never writes when validation failed', async () => {
    const handlerDeps = deps();
    await new CreateMealEventHandler(handlerDeps).execute(manualCommand({ items: [] }));
    expect(handlerDeps.repository.save).not.toHaveBeenCalled();
  });

  it('reports a storage outage rather than pretending the meal was logged', async () => {
    const created = await new CreateMealEventHandler(
      deps({ save: vi.fn(async () => err('storage-unavailable' as const)) }),
    ).execute(manualCommand());
    expect(created).toEqual({ ok: false, error: 'storage-unavailable' });
  });

  // Logging a meal is the primary KPI and may not depend on a subscriber.
  it('does not wait on rewards or coaching to exist', async () => {
    const created = await new CreateMealEventHandler(deps()).execute(manualCommand());
    expect(created.ok && created.value.evidence).toEqual([]);
  });
});

describe('correcting a meal', () => {
  /** A fresh double per test: a shared spy accumulates calls across them. */
  const loaded = () => ({ findById: vi.fn(async () => ok(storedEvent())) });

  it('creates revision N+1 pointing at the original', async () => {
    const corrected = await new CorrectMealEventHandler(deps(loaded())).execute({
      mealEventId: 'meal-1' as never,
      userId: 'user-1' as never,
      items,
      nutrition,
    });

    expect(corrected.ok).toBe(true);
    if (!corrected.ok) return;
    expect(corrected.value.revision).toBe(2);
    expect(corrected.value.supersedesId).toBe('meal-1');
    expect(corrected.value.id).not.toBe('meal-1');
  });

  it('inherits what a correction does not change', async () => {
    const corrected = await new CorrectMealEventHandler(
      deps({ findById: vi.fn(async () => ok(storedEvent({ mealType: 'dinner' }))) }),
    ).execute({
      mealEventId: 'meal-1' as never,
      userId: 'user-1' as never,
      items,
      nutrition,
    });

    expect(corrected.ok && corrected.value).toMatchObject({
      mealType: 'dinner',
      occurredAt: '2026-07-29T12:30:00.000Z',
      localDate: '2026-07-29',
      timezone: 'Europe/Berlin',
    });
  });

  it('supersedes rather than overwriting, so history survives', async () => {
    const handlerDeps = deps(loaded());
    await new CorrectMealEventHandler(handlerDeps).execute({
      mealEventId: 'meal-1' as never,
      userId: 'user-1' as never,
      items,
      nutrition,
    });

    expect(handlerDeps.repository.supersede).toHaveBeenCalled();
    expect(handlerDeps.repository.save).not.toHaveBeenCalled();
  });

  it('reports a meal it cannot load as not-found', async () => {
    const corrected = await new CorrectMealEventHandler(deps()).execute({
      mealEventId: 'meal-gone' as never,
      userId: 'user-1' as never,
      items,
      nutrition,
    });
    expect(corrected).toEqual({ ok: false, error: 'not-found' });
  });

  // Another user's meal and a nonexistent meal answer the same thing: a 403
  // would confirm the resource exists (docs/06 §6.2).
  it('cannot correct a meal the caller does not own', async () => {
    const handlerDeps = deps(loaded());
    await new CorrectMealEventHandler(handlerDeps).execute({
      mealEventId: 'meal-1' as never,
      userId: 'user-2' as never,
      items,
      nutrition,
    });
    expect(handlerDeps.repository.findById).toHaveBeenCalledWith('meal-1', 'user-2');
  });

  it('refuses an ungrounded correction before it touches storage', async () => {
    const handlerDeps = deps(loaded());
    const corrected = await new CorrectMealEventHandler(handlerDeps).execute({
      mealEventId: 'meal-1' as never,
      userId: 'user-1' as never,
      items,
      nutrition: { ...nutrition, datasetVersion: '' } as never,
    });

    expect(corrected).toEqual({ ok: false, error: 'ungrounded-nutrition' });
    expect(handlerDeps.repository.findById).not.toHaveBeenCalled();
  });

  it('refuses a correction that would leave no items', async () => {
    const corrected = await new CorrectMealEventHandler(deps(loaded())).execute({
      mealEventId: 'meal-1' as never,
      userId: 'user-1' as never,
      items: [],
      nutrition,
    });
    expect(corrected).toEqual({ ok: false, error: 'invalid-items' });
  });

  it('reports a storage outage on the read as an outage, not as not-found', async () => {
    const corrected = await new CorrectMealEventHandler(
      deps({ findById: vi.fn(async () => err('storage-unavailable' as const)) }),
    ).execute({
      mealEventId: 'meal-1' as never,
      userId: 'user-1' as never,
      items,
      nutrition,
    });
    expect(corrected).toEqual({ ok: false, error: 'storage-unavailable' });
  });

  it('reports a lost race on the write as not-found', async () => {
    const corrected = await new CorrectMealEventHandler(
      deps({ ...loaded(), supersede: vi.fn(async () => err('not-found' as const)) }),
    ).execute({
      mealEventId: 'meal-1' as never,
      userId: 'user-1' as never,
      items,
      nutrition,
    });
    expect(corrected).toEqual({ ok: false, error: 'not-found' });
  });
});

describe('deleting a meal', () => {
  it('succeeds without a value', async () => {
    const repository = deps().repository;
    expect(
      await new DeleteMealEventHandler(repository).execute('meal-1' as never, 'user-1' as never),
    ).toEqual({ ok: true, value: undefined });
  });

  it('reports a meal that was not there as not-found', async () => {
    const repository = deps({
      softDelete: vi.fn(async () => err('not-found' as const)),
    }).repository;
    expect(
      await new DeleteMealEventHandler(repository).execute('meal-1' as never, 'user-1' as never),
    ).toEqual({ ok: false, error: 'not-found' });
  });

  it('reports a storage outage as an outage', async () => {
    const repository = deps({
      softDelete: vi.fn(async () => err('storage-unavailable' as const)),
    }).repository;
    expect(
      await new DeleteMealEventHandler(repository).execute('meal-1' as never, 'user-1' as never),
    ).toEqual({ ok: false, error: 'storage-unavailable' });
  });
});

describe('reading meals', () => {
  it('returns the aggregate the repository holds', async () => {
    const repository = deps({ findById: vi.fn(async () => ok(storedEvent())) }).repository;
    const found = await new GetMealEventHandler(repository).execute(
      'meal-1' as never,
      'user-1' as never,
    );
    expect(found.ok && found.value.id).toBe('meal-1');
  });

  it('answers not-found for an unknown or unowned meal', async () => {
    const found = await new GetMealEventHandler(deps().repository).execute(
      'meal-1' as never,
      'user-1' as never,
    );
    expect(found).toEqual({ ok: false, error: 'not-found' });
  });

  // Answering "no such meal" during an outage tells a user their history is gone.
  it('never reports a storage outage as a missing meal', async () => {
    const repository = deps({
      findById: vi.fn(async () => err('storage-unavailable' as const)),
    }).repository;
    const found = await new GetMealEventHandler(repository).execute(
      'meal-1' as never,
      'user-1' as never,
    );
    expect(found).toEqual({ ok: false, error: 'storage-unavailable' });
  });

  it('passes the page request and filters through unchanged', async () => {
    const findPage = vi.fn(async () =>
      ok({ data: [], page: { nextCursor: null, hasMore: false } }),
    );
    const repository = deps({ findPage }).repository;

    await new ListMealEventsHandler(repository).execute(
      'user-1' as never,
      { limit: 20, cursor: 'c' },
      { mealType: 'lunch' },
    );

    expect(findPage).toHaveBeenCalledWith(
      'user-1',
      { limit: 20, cursor: 'c' },
      { mealType: 'lunch' },
    );
  });

  it('treats an empty list as a successful answer', async () => {
    const listed = await new ListMealEventsHandler(deps().repository).execute(
      'user-1' as never,
      { limit: 20 },
      {},
    );
    expect(listed.ok && listed.value.data).toEqual([]);
  });

  it('surfaces a rejected cursor so the boundary can answer VALIDATION_FAILED', async () => {
    const repository = deps({
      findPage: vi.fn(async () => err('invalid-cursor' as const)),
    }).repository;
    const listed = await new ListMealEventsHandler(repository).execute(
      'user-1' as never,
      { limit: 20, cursor: 'nope' },
      {},
    );
    expect(listed).toEqual({ ok: false, error: 'invalid-cursor' });
  });

  it('reports any other repository failure as a storage outage', async () => {
    const repository = deps({ findPage: vi.fn(async () => err('conflict' as const)) }).repository;
    const listed = await new ListMealEventsHandler(repository).execute(
      'user-1' as never,
      { limit: 20 },
      {},
    );
    expect(listed).toEqual({ ok: false, error: 'storage-unavailable' });
  });
});

describe('the guard holds for a caller that is not the HTTP boundary', () => {
  // Zod rejects these at the boundary; the use case is also reachable from a
  // future non-HTTP caller, and an ungrounded number must not enter by either
  // door (AD-7).
  it.each([
    ['an absent catalog source', { catalogSource: undefined }],
    ['an absent dataset version', { datasetVersion: undefined }],
  ])('refuses %s', async (_name, over) => {
    const created = await new CreateMealEventHandler(deps()).execute(
      manualCommand({ nutrition: { ...nutrition, ...over } as never }),
    );
    expect(created).toEqual({ ok: false, error: 'ungrounded-nutrition' });
  });
});
