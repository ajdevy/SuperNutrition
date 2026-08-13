import { describe, it, expect, vi } from 'vitest';
import { ok, err, isAppError, type AppError } from '@supernutrition/shared';
import { MealControllerImpl, type MealControllerDeps } from './meal.controller.js';
import type { MealEvent, NutritionSnapshot } from '../domain/index.js';

/**
 * The translation between a validated request and a use case, and between a
 * typed failure and a Product API error code. A gateway code reaching this
 * surface is a client that cannot branch on what it was told (docs/06 §6.2).
 */

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
  perItem: [
    {
      foodRef: 'usda:fdc:171705',
      calories: '105.000',
      proteinG: null,
      fatG: null,
      carbG: null,
      partial: false,
    },
  ],
};

const body = {
  occurredAt: '2026-07-29T12:30:00Z',
  localDate: '2026-07-29',
  timezone: 'Europe/Berlin',
  mealType: 'lunch',
  items: [
    {
      foodRef: 'usda:fdc:171705',
      displayName: 'Banana, raw',
      servingUnit: 'g',
      servingAmount: '118',
      confirmedByUser: true,
    },
  ],
  nutrition,
};

const MEAL_ID = '3f7c1a2e-0000-4000-8000-000000000001';

const event = (over: Partial<MealEvent> = {}): MealEvent =>
  ({
    id: MEAL_ID,
    userId: 'user-1',
    source: { kind: 'manual' },
    status: 'confirmed',
    occurredAt: '2026-07-29T12:30:00.000Z',
    localDate: '2026-07-29',
    timezone: 'Europe/Berlin',
    mealType: 'lunch',
    revision: 1,
    supersedesId: null,
    items: [
      {
        id: 'item-1',
        mealEventId: MEAL_ID,
        position: 0,
        foodRef: 'usda:fdc:171705',
        displayName: 'Banana, raw',
        servingUnit: 'g',
        servingAmount: '118.000',
        gramWeight: null,
        confidence: null,
        confirmedByUser: true,
      },
    ],
    nutrition: nutrition as unknown as NutritionSnapshot,
    evidence: [],
    createdAt: '2026-07-29T12:30:04.000Z',
    confirmedAt: '2026-07-29T12:30:04.000Z',
    ...over,
  }) as MealEvent;

const controller = (over: Partial<MealControllerDeps> = {}) => {
  const deps = {
    createMeal: { execute: vi.fn(async () => ok(event())) },
    correctMeal: { execute: vi.fn(async () => ok(event({ revision: 2 }))) },
    deleteMeal: { execute: vi.fn(async () => ok(undefined)) },
    getMeal: { execute: vi.fn(async () => ok(event())) },
    listMeals: {
      execute: vi.fn(async () =>
        ok({ data: [event()], page: { nextCursor: 'next', hasMore: true } }),
      ),
    },
    ...over,
  } as unknown as MealControllerDeps;

  return { deps, controller: new MealControllerImpl(deps) };
};

const rejection = async (call: Promise<unknown>): Promise<AppError> => {
  try {
    await call;
  } catch (error) {
    if (isAppError(error)) return error;
    throw error;
  }
  throw new Error('expected the call to reject');
};

describe('creating a meal over HTTP', () => {
  it('returns the aggregate with rewards and coaching honestly null', async () => {
    const { controller: meals } = controller();
    const response = await meals.createManualMeal(body, 'user-1');

    expect(response).toMatchObject({
      id: MEAL_ID,
      source: 'manual',
      status: 'confirmed',
      revision: 1,
      rewards: null,
      coachMessage: null,
    });
    expect(response.nutrition?.datasetVersion).toBe('usda-2026.1');
    expect(response.items[0]?.displayName).toBe('Banana, raw');
  });

  it('passes only validated data inward', async () => {
    const { deps, controller: meals } = controller();
    await meals.createManualMeal({ ...body, futureField: 'ignored' }, 'user-1');

    const command = vi.mocked(deps.createMeal.execute).mock.calls[0]?.[0];
    expect(command).not.toHaveProperty('futureField');
    expect(command).toMatchObject({ source: 'manual', userId: 'user-1', mealType: 'lunch' });
  });

  it('builds the photo command from the photo schema', async () => {
    const { deps, controller: meals } = controller();
    await meals.createPhotoMeal(
      { ...body, attemptId: 'att_01J8X4Q2K7R9M3P5', configurationVersion: 'cfg/1' },
      'user-1',
    );

    expect(vi.mocked(deps.createMeal.execute).mock.calls[0]?.[0]).toMatchObject({
      source: 'photo',
      attemptId: 'att_01J8X4Q2K7R9M3P5',
      configurationVersion: 'cfg/1',
    });
  });

  // Evidence is accepted so the client contract is stable, and dropped: the
  // evidence table is a separate stage, and half-stored provenance is worse
  // than none.
  it('accepts evidence and stores none of it', async () => {
    const { deps, controller: meals } = controller();
    await meals.createPhotoMeal(
      {
        ...body,
        attemptId: 'att_01J8X4Q2K7R9M3P5',
        configurationVersion: 'cfg/1',
        evidence: [
          {
            mealItemId: null,
            attemptId: 'att_01J8X4Q2K7R9M3P5',
            type: 'candidate',
            payload: { label: 'Banana' },
            provenance: {
              route: 'remote',
              engineId: 'e',
              modelVersion: 'm',
              calibrationVersion: 'c',
              contractVersion: 'v',
            },
          },
        ],
      },
      'user-1',
    );

    const command = vi.mocked(deps.createMeal.execute).mock.calls[0]?.[0] as {
      evidence?: unknown[];
    };
    expect(command.evidence).toEqual([]);
  });

  it('builds the voice command with its transcript', async () => {
    const { deps, controller: meals } = controller();
    await meals.createVoiceMeal({ ...body, transcript: 'a banana' }, 'user-1');

    expect(vi.mocked(deps.createMeal.execute).mock.calls[0]?.[0]).toMatchObject({
      source: 'voice',
      transcript: 'a banana',
    });
  });

  it('rejects a malformed body with VALIDATION_FAILED and names the field', async () => {
    const { controller: meals } = controller();
    const error = await rejection(meals.createManualMeal({ ...body, items: [] }, 'user-1'));

    expect(error.code).toBe('VALIDATION_FAILED');
    expect(error.status).toBe(400);
    expect(error.details.map((detail) => detail.field)).toContain('items');
  });

  it('rejects nutrition without provenance at the boundary (AD-7)', async () => {
    const { catalogSource: _dropped, ...ungrounded } = nutrition;
    const { controller: meals } = controller();
    const error = await rejection(
      meals.createManualMeal({ ...body, nutrition: ungrounded }, 'user-1'),
    );

    expect(error.code).toBe('VALIDATION_FAILED');
    expect(error.details.some((detail) => detail.field.startsWith('nutrition'))).toBe(true);
  });
});

describe('failures leave as Product API codes', () => {
  it('maps ungrounded nutrition to VALIDATION_FAILED', async () => {
    const { controller: meals } = controller({
      createMeal: { execute: vi.fn(async () => err('ungrounded-nutrition' as const)) },
    });
    const error = await rejection(meals.createManualMeal(body, 'user-1'));
    expect(error.code).toBe('VALIDATION_FAILED');
    expect(error.status).toBe(400);
  });

  it('maps invalid items to VALIDATION_FAILED', async () => {
    const { controller: meals } = controller({
      createMeal: { execute: vi.fn(async () => err('invalid-items' as const)) },
    });
    expect((await rejection(meals.createManualMeal(body, 'user-1'))).code).toBe(
      'VALIDATION_FAILED',
    );
  });

  it('maps a missing meal to MEAL_NOT_FOUND', async () => {
    const { controller: meals } = controller({
      getMeal: { execute: vi.fn(async () => err('not-found' as const)) },
    });
    const error = await rejection(meals.getMeal(MEAL_ID, 'user-1'));
    expect(error.code).toBe('MEAL_NOT_FOUND');
    expect(error.status).toBe(404);
  });

  it('maps a storage outage to INTERNAL_ERROR and leaks nothing', async () => {
    const { controller: meals } = controller({
      getMeal: { execute: vi.fn(async () => err('storage-unavailable' as const)) },
    });
    const error = await rejection(meals.getMeal(MEAL_ID, 'user-1'));

    expect(error.code).toBe('INTERNAL_ERROR');
    expect(error.status).toBe(500);
    expect(JSON.stringify(error.toResponse('c1'))).not.toMatch(/postgres|drizzle|stack/i);
  });

  it('maps a rejected cursor to VALIDATION_FAILED rather than to a 500', async () => {
    const { controller: meals } = controller({
      listMeals: { execute: vi.fn(async () => err('invalid-cursor' as const)) },
    });
    const error = await rejection(meals.listMeals({ cursor: 'nonsense' }, 'user-1'));

    expect(error.code).toBe('VALIDATION_FAILED');
    expect(error.details[0]).toEqual({ field: 'cursor', issue: 'malformed' });
  });

  it('maps a correction against a missing meal to MEAL_NOT_FOUND', async () => {
    const { controller: meals } = controller({
      correctMeal: { execute: vi.fn(async () => err('not-found' as const)) },
    });
    expect((await rejection(meals.correctMeal(MEAL_ID, body, 'user-1'))).code).toBe(
      'MEAL_NOT_FOUND',
    );
  });

  it('maps a delete against a missing meal to MEAL_NOT_FOUND', async () => {
    const { controller: meals } = controller({
      deleteMeal: { execute: vi.fn(async () => err('not-found' as const)) },
    });
    expect((await rejection(meals.deleteMeal(MEAL_ID, 'user-1'))).code).toBe('MEAL_NOT_FOUND');
  });

  it('rejects an id that is not a uuid before reaching a query', async () => {
    const { deps, controller: meals } = controller();
    const error = await rejection(meals.getMeal('not-a-uuid', 'user-1'));

    expect(error.code).toBe('VALIDATION_FAILED');
    expect(deps.getMeal.execute).not.toHaveBeenCalled();
  });
});

describe('correcting and listing', () => {
  it('returns revision N+1 from a correction', async () => {
    const { deps, controller: meals } = controller();
    const response = await meals.correctMeal(MEAL_ID, body, 'user-1');

    expect(response.revision).toBe(2);
    expect(vi.mocked(deps.correctMeal.execute).mock.calls[0]?.[0]).toMatchObject({
      mealEventId: MEAL_ID,
      userId: 'user-1',
    });
  });

  it('returns a cursor page shaped as the contract says', async () => {
    const { controller: meals } = controller();
    const page = await meals.listMeals({ limit: '1' }, 'user-1');

    expect(page.page).toEqual({ nextCursor: 'next', hasMore: true });
    expect(page.data[0]?.id).toBe(MEAL_ID);
  });

  it('applies the documented default page size', async () => {
    const { deps, controller: meals } = controller();
    await meals.listMeals({}, 'user-1');

    expect(vi.mocked(deps.listMeals.execute).mock.calls[0]?.[1]).toEqual({ limit: 20 });
  });

  it('passes each filter through as a filter, not as a page field', async () => {
    const { deps, controller: meals } = controller();
    await meals.listMeals(
      { from: '2026-07-01', to: '2026-07-31', mealType: 'lunch', source: 'photo', cursor: 'c' },
      'user-1',
    );

    const call = vi.mocked(deps.listMeals.execute).mock.calls[0];
    expect(call?.[1]).toEqual({ limit: 20, cursor: 'c' });
    expect(call?.[2]).toEqual({
      from: '2026-07-01',
      to: '2026-07-31',
      mealType: 'lunch',
      source: 'photo',
    });
  });

  it('rejects a page size beyond the documented bound', async () => {
    const { controller: meals } = controller();
    expect((await rejection(meals.listMeals({ limit: '101' }, 'user-1'))).code).toBe(
      'VALIDATION_FAILED',
    );
  });

  it('returns nothing at all from a delete', async () => {
    const { controller: meals } = controller();
    await expect(meals.deleteMeal(MEAL_ID, 'user-1')).resolves.toBeUndefined();
  });

  it('reports a meal with no nutrition as having none', async () => {
    const { controller: meals } = controller({
      getMeal: { execute: vi.fn(async () => ok(event({ nutrition: null }))) },
    });
    expect((await meals.getMeal(MEAL_ID, 'user-1')).nutrition).toBeNull();
  });
});

describe('optional fields and remaining failure paths', () => {
  it('passes optional item facts through when the client sent them', async () => {
    const { deps, controller: meals } = controller();
    await meals.createManualMeal(
      {
        ...body,
        items: [{ ...body.items[0], gramWeight: '118', confidence: '0.93' }],
      },
      'user-1',
    );

    const command = vi.mocked(deps.createMeal.execute).mock.calls[0]?.[0] as {
      items: { gramWeight?: string; confidence?: string }[];
    };
    expect(command.items[0]).toMatchObject({ gramWeight: '118', confidence: '0.93' });
  });

  it('carries an optional voice attempt id when the client identified the text', async () => {
    const { deps, controller: meals } = controller();
    await meals.createVoiceMeal(
      { ...body, transcript: 'a banana', attemptId: 'att_01J8X4Q2K7R9M3P5' },
      'user-1',
    );

    expect(vi.mocked(deps.createMeal.execute).mock.calls[0]?.[0]).toMatchObject({
      attemptId: 'att_01J8X4Q2K7R9M3P5',
    });
  });

  it('reports a voice creation failure with a Product API code', async () => {
    const { controller: meals } = controller({
      createMeal: { execute: vi.fn(async () => err('storage-unavailable' as const)) },
    });
    const error = await rejection(
      meals.createVoiceMeal({ ...body, transcript: 'a banana' }, 'user-1'),
    );
    expect(error.code).toBe('INTERNAL_ERROR');
  });

  it('reports a photo creation failure with a Product API code', async () => {
    const { controller: meals } = controller({
      createMeal: { execute: vi.fn(async () => err('storage-unavailable' as const)) },
    });
    const error = await rejection(
      meals.createPhotoMeal(
        { ...body, attemptId: 'att_01J8X4Q2K7R9M3P5', configurationVersion: 'cfg/1' },
        'user-1',
      ),
    );
    expect(error.code).toBe('INTERNAL_ERROR');
  });

  it('reports a storage outage on a list as INTERNAL_ERROR, not as a bad cursor', async () => {
    const { controller: meals } = controller({
      listMeals: { execute: vi.fn(async () => err('storage-unavailable' as const)) },
    });
    const error = await rejection(meals.listMeals({}, 'user-1'));
    expect(error.code).toBe('INTERNAL_ERROR');
  });

  it('names the root when the whole body is the wrong shape', async () => {
    const { controller: meals } = controller();
    const error = await rejection(meals.createManualMeal('not an object', 'user-1'));

    expect(error.code).toBe('VALIDATION_FAILED');
    expect(error.details.map((detail) => detail.field)).toContain('(root)');
  });
});
