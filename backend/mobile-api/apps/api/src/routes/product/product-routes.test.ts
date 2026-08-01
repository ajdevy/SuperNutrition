import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { randomUUID } from 'node:crypto';
import {
  ok,
  err,
  decodePageCursor,
  encodePageCursor,
  type Result,
  type Page,
  type PageRequest,
  type MealEventId,
  type UserId,
} from '@supernutrition/shared';
import { buildServer, type ServerHandle } from '../../server.js';
import { createLogger } from '../../logging/index.js';
import type { AppConfig } from '../../config/index.js';
import { TestModeAuthenticator, TestModeProductAuthenticator } from '../../security/index.js';
import {
  CreateMealEventHandler,
  CorrectMealEventHandler,
  DeleteMealEventHandler,
  GetMealEventHandler,
  ListMealEventsHandler,
  MealControllerImpl,
  type MealEvent,
  type MealEventFilters,
  type MealEventRepository,
  type MealRepositoryFailure,
} from '../../modules/meals/index.js';

/**
 * Surface B end to end against a real Fastify instance.
 *
 * Everything below the HTTP boundary is the real implementation — controller,
 * use cases, and the port — with storage held in memory. What is asserted is
 * the contract a client sees: status codes, the Product API error shape, cursor
 * pagination, ownership, and that a correction reads back as one meal.
 */

/** The port, implemented in memory with the same rules the SQL enforces. */
class InMemoryMealRepository implements MealEventRepository {
  readonly events = new Map<string, MealEvent>();
  readonly deleted = new Set<string>();
  failure: MealRepositoryFailure | null = null;

  #visible(userId: string): MealEvent[] {
    // Having been superseded is permanent: a deleted replacement does not make
    // the revision it replaced current again.
    const superseded = new Set(
      [...this.events.values()]
        .filter((event) => event.supersedesId)
        .map((event) => event.supersedesId as string),
    );
    return [...this.events.values()]
      .filter(
        (event) =>
          event.userId === userId && !this.deleted.has(event.id) && !superseded.has(event.id),
      )
      .sort((a, b) =>
        a.occurredAt === b.occurredAt
          ? b.id.localeCompare(a.id)
          : b.occurredAt.localeCompare(a.occurredAt),
      );
  }

  async findById(
    id: MealEventId,
    userId: UserId,
  ): Promise<Result<MealEvent, MealRepositoryFailure>> {
    if (this.failure) return err(this.failure);
    const event = this.events.get(id);
    return event && event.userId === userId && !this.deleted.has(id) ? ok(event) : err('not-found');
  }

  async findPage(
    userId: UserId,
    page: PageRequest,
    filters: MealEventFilters,
  ): Promise<Result<Page<MealEvent>, MealRepositoryFailure>> {
    if (this.failure) return err(this.failure);

    let rows = this.#visible(userId);
    if (filters.from) rows = rows.filter((event) => event.localDate >= filters.from!);
    if (filters.to) rows = rows.filter((event) => event.localDate <= filters.to!);
    if (filters.mealType) rows = rows.filter((event) => event.mealType === filters.mealType);
    if (filters.source) rows = rows.filter((event) => event.source.kind === filters.source);

    if (page.cursor !== undefined) {
      const decoded = decodePageCursor(page.cursor);
      if (!decoded.ok) return err('invalid-cursor');
      const { sortKey, id } = decoded.value;
      rows = rows.filter(
        (event) => event.occurredAt < sortKey || (event.occurredAt === sortKey && event.id < id),
      );
    }

    const data = rows.slice(0, page.limit);
    const hasMore = rows.length > page.limit;
    const last = data[data.length - 1];
    return ok({
      data,
      page: {
        nextCursor:
          hasMore && last ? encodePageCursor({ sortKey: last.occurredAt, id: last.id }) : null,
        hasMore,
      },
    });
  }

  async save(event: MealEvent): Promise<Result<MealEvent, MealRepositoryFailure>> {
    if (this.failure) return err(this.failure);
    this.events.set(event.id, event);
    return ok(event);
  }

  async supersede(
    originalId: MealEventId,
    replacement: MealEvent,
  ): Promise<Result<MealEvent, MealRepositoryFailure>> {
    if (this.failure) return err(this.failure);
    const original = this.events.get(originalId);
    if (!original || original.userId !== replacement.userId || this.deleted.has(originalId)) {
      return err('not-found');
    }
    this.events.set(replacement.id, replacement);
    return ok(replacement);
  }

  async softDelete(id: MealEventId, userId: UserId): Promise<Result<void, MealRepositoryFailure>> {
    if (this.failure) return err(this.failure);
    const event = this.events.get(id);
    if (!event || event.userId !== userId || this.deleted.has(id)) return err('not-found');
    this.deleted.add(id);
    return ok(undefined);
  }
}

const config = {
  env: 'development',
  nodeEnv: 'development',
  server: { host: '127.0.0.1', port: 0 },
  database: {
    url: 'postgres://x',
    maxConnections: 1,
    idleTimeoutSeconds: 1,
    connectTimeoutSeconds: 1,
  },
  redis: { url: 'redis://x' },
  auth: { issuer: 'i', accessTtlSeconds: 900, refreshTtlSeconds: 1000, testMode: true },
  inference: { remoteEnabled: true, modelProfilesPath: 'p', openRouterBaseUrl: 'https://x' },
  logging: { level: 'silent', allowlistPath: 'a' },
} as unknown as AppConfig;

const TOKEN = 'Bearer test.installation-one';
const OTHER_TOKEN = 'Bearer test.installation-two';

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

const meal = (over: Record<string, unknown> = {}) => ({
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
  ...over,
});

let server: ServerHandle;
let repository: InMemoryMealRepository;

beforeEach(async () => {
  repository = new InMemoryMealRepository();

  const commandDeps = { repository, now: () => new Date('2026-07-29T12:30:04Z'), uuid: randomUUID };
  const controller = new MealControllerImpl({
    createMeal: new CreateMealEventHandler(commandDeps),
    correctMeal: new CorrectMealEventHandler(commandDeps),
    deleteMeal: new DeleteMealEventHandler(repository),
    getMeal: new GetMealEventHandler(repository),
    listMeals: new ListMealEventsHandler(repository),
  });

  // The installation identifies the account, exactly as the composition root
  // wires it; only the store behind it is in memory.
  const accounts = new Map<string, string>();
  const authenticator = new TestModeProductAuthenticator(new TestModeAuthenticator('development'), {
    resolve: async (externalRef) => {
      const existing = accounts.get(externalRef) ?? randomUUID();
      accounts.set(externalRef, existing);
      return existing;
    },
  });

  server = await buildServer({
    config,
    logger: createLogger(config),
    gateway: {
      payloads: { put: () => {}, dispose: () => {} },
      now: () => new Date('2026-07-29T12:30:04Z'),
    } as never,
    product: {
      meals: controller,
      authenticate: (request) => authenticator.authenticate(request.headers.authorization),
    },
  });
});

afterEach(async () => {
  await server.close();
});

const post = (url: string, payload: unknown, authorization = TOKEN) =>
  server.app.inject({ method: 'POST', url, payload, headers: { authorization } });

const get = (url: string, authorization = TOKEN) =>
  server.app.inject({ method: 'GET', url, headers: { authorization } });

describe('creating meals', () => {
  it.each([
    ['manual', '/api/v1/meals/manual', meal()],
    [
      'photo',
      '/api/v1/meals/photo',
      meal({ attemptId: 'att_01J8X4Q2K7R9M3P5', configurationVersion: 'cfg/1' }),
    ],
    ['voice', '/api/v1/meals/voice', meal({ transcript: 'a banana' })],
  ])('creates a %s meal and returns the aggregate', async (source, url, payload) => {
    const response = await post(url, payload);

    expect(response.statusCode).toBe(201);
    expect(response.json()).toMatchObject({
      source,
      status: 'confirmed',
      revision: 1,
      mealType: 'lunch',
      localDate: '2026-07-29',
      rewards: null,
      coachMessage: null,
    });
    expect(response.json().nutrition.datasetVersion).toBe('usda-2026.1');
  });

  it('never carries image bytes on the product surface', async () => {
    const response = await post('/api/v1/meals/photo', {
      ...meal({ attemptId: 'att_01J8X4Q2K7R9M3P5', configurationVersion: 'cfg/1' }),
    });
    expect(response.body).not.toContain('payload');
    expect(response.statusCode).toBe(201);
  });

  it('rejects nutrition without provenance with a Product API code', async () => {
    const { catalogSource: _dropped, ...ungrounded } = nutrition;
    const response = await post('/api/v1/meals/manual', meal({ nutrition: ungrounded }));

    expect(response.statusCode).toBe(400);
    const body = response.json();
    expect(body.error.code).toBe('VALIDATION_FAILED');
    expect(body.error).not.toHaveProperty('gatewayCode');
    expect(body.error.correlationId).toBeDefined();
  });

  it('rejects an unparseable body with the product taxonomy, not the gateway one', async () => {
    const response = await server.app.inject({
      method: 'POST',
      url: '/api/v1/meals/manual',
      headers: { authorization: TOKEN, 'content-type': 'application/json' },
      payload: '{"broken":',
    });

    expect(response.statusCode).toBe(400);
    expect(response.json().error.code).toBe('VALIDATION_FAILED');
  });

  it('reports a storage outage as INTERNAL_ERROR without leaking why', async () => {
    repository.failure = 'storage-unavailable';
    const response = await post('/api/v1/meals/manual', meal());

    expect(response.statusCode).toBe(500);
    expect(response.json().error.code).toBe('INTERNAL_ERROR');
    expect(response.body).not.toMatch(/at\s+\w+\s+\(/);
  });
});

describe('authentication', () => {
  it('refuses an unauthenticated request', async () => {
    const response = await server.app.inject({
      method: 'GET',
      url: '/api/v1/meals',
    });

    expect(response.statusCode).toBe(401);
    expect(response.json().error.code).toBe('UNAUTHENTICATED');
  });

  it.each([
    ['POST', '/api/v1/meals/manual'],
    ['GET', '/api/v1/meals'],
    ['GET', '/api/v1/meals/3f7c1a2e-0000-4000-8000-000000000001'],
    ['PATCH', '/api/v1/meals/3f7c1a2e-0000-4000-8000-000000000001'],
    ['DELETE', '/api/v1/meals/3f7c1a2e-0000-4000-8000-000000000001'],
  ])('refuses %s %s without a credential', async (method, url) => {
    const response = await server.app.inject({
      method: method as 'GET',
      url,
      ...(method === 'GET' || method === 'DELETE' ? {} : { payload: meal() }),
    });
    expect(response.statusCode).toBe(401);
  });

  it('keeps one installation session working across requests', async () => {
    await post('/api/v1/meals/manual', meal());
    const listed = await get('/api/v1/meals');
    expect(listed.json().data).toHaveLength(1);
  });
});

describe('reading meals', () => {
  it('returns a meal by id', async () => {
    const created = await post('/api/v1/meals/manual', meal());
    const response = await get(`/api/v1/meals/${created.json().id}`);

    expect(response.statusCode).toBe(200);
    expect(response.json().id).toBe(created.json().id);
  });

  it('answers 404 MEAL_NOT_FOUND for an unknown meal', async () => {
    const response = await get('/api/v1/meals/3f7c1a2e-0000-4000-8000-00000000dead');

    expect(response.statusCode).toBe(404);
    expect(response.json().error.code).toBe('MEAL_NOT_FOUND');
  });

  it('rejects an id that is not a uuid', async () => {
    const response = await get('/api/v1/meals/not-a-uuid');
    expect(response.statusCode).toBe(400);
    expect(response.json().error.code).toBe('VALIDATION_FAILED');
  });

  // A 403 would confirm the resource exists (docs/06 §6.2).
  it("answers 404, not 403, for another user's meal", async () => {
    const created = await post('/api/v1/meals/manual', meal());
    const response = await get(`/api/v1/meals/${created.json().id}`, OTHER_TOKEN);

    expect(response.statusCode).toBe(404);
    expect(response.json().error.code).toBe('MEAL_NOT_FOUND');
  });

  it('lists nothing for a user who has logged nothing', async () => {
    const response = await get('/api/v1/meals');

    expect(response.statusCode).toBe(200);
    expect(response.json()).toEqual({ data: [], page: { nextCursor: null, hasMore: false } });
  });

  it('lists newest first', async () => {
    await post('/api/v1/meals/manual', meal({ occurredAt: '2026-07-29T08:00:00Z' }));
    await post('/api/v1/meals/manual', meal({ occurredAt: '2026-07-29T20:00:00Z' }));

    const listed = await get('/api/v1/meals');
    expect(listed.json().data.map((event: { occurredAt: string }) => event.occurredAt)).toEqual([
      '2026-07-29T20:00:00.000Z',
      '2026-07-29T08:00:00.000Z',
    ]);
  });

  it('pages with an opaque cursor rather than an offset', async () => {
    await post('/api/v1/meals/manual', meal({ occurredAt: '2026-07-29T08:00:00Z' }));
    await post('/api/v1/meals/manual', meal({ occurredAt: '2026-07-29T20:00:00Z' }));

    const first = await get('/api/v1/meals?limit=1');
    expect(first.json().page.hasMore).toBe(true);
    expect(first.json().data).toHaveLength(1);

    const second = await get(
      `/api/v1/meals?limit=1&cursor=${encodeURIComponent(first.json().page.nextCursor)}`,
    );
    expect(second.json().data[0].occurredAt).toBe('2026-07-29T08:00:00.000Z');
    expect(second.json().page).toEqual({ nextCursor: null, hasMore: false });
  });

  it('rejects a cursor it did not issue', async () => {
    const response = await get('/api/v1/meals?cursor=nonsense');

    expect(response.statusCode).toBe(400);
    expect(response.json().error.code).toBe('VALIDATION_FAILED');
  });

  it('filters by date range, meal type, and source', async () => {
    await post('/api/v1/meals/manual', meal({ localDate: '2026-07-01', mealType: 'breakfast' }));
    await post('/api/v1/meals/voice', meal({ transcript: 'a banana', mealType: 'dinner' }));

    expect((await get('/api/v1/meals?mealType=dinner')).json().data).toHaveLength(1);
    expect((await get('/api/v1/meals?source=voice')).json().data).toHaveLength(1);
    expect((await get('/api/v1/meals?from=2026-07-29')).json().data).toHaveLength(1);
    expect((await get('/api/v1/meals?to=2026-07-01')).json().data).toHaveLength(1);
  });

  it('rejects a page size beyond the documented bound', async () => {
    expect((await get('/api/v1/meals?limit=101')).statusCode).toBe(400);
  });
});

describe('correcting a meal', () => {
  const patch = (id: string, payload: unknown, authorization = TOKEN) =>
    server.app.inject({
      method: 'PATCH',
      url: `/api/v1/meals/${id}`,
      payload,
      headers: { authorization },
    });

  it('returns revision N+1 rather than the row it replaced', async () => {
    const created = await post('/api/v1/meals/manual', meal());
    const corrected = await patch(created.json().id, {
      items: [{ ...meal().items[0], displayName: 'Banana, large', servingAmount: '136' }],
      nutrition: { ...nutrition, calories: '121.000' },
    });

    expect(corrected.statusCode).toBe(200);
    expect(corrected.json().revision).toBe(2);
    expect(corrected.json().id).not.toBe(created.json().id);
    expect(corrected.json().nutrition.calories).toBe('121.000');
  });

  // History is retained, but a corrected lunch is still one lunch.
  it('leaves one meal in the feed, at the new revision', async () => {
    const created = await post('/api/v1/meals/manual', meal());
    await patch(created.json().id, { items: meal().items, nutrition });

    const listed = await get('/api/v1/meals');
    expect(listed.json().data).toHaveLength(1);
    expect(listed.json().data[0].revision).toBe(2);
  });

  it('answers 404 for a meal that is not there', async () => {
    const response = await patch('3f7c1a2e-0000-4000-8000-00000000dead', {
      items: meal().items,
      nutrition,
    });
    expect(response.statusCode).toBe(404);
    expect(response.json().error.code).toBe('MEAL_NOT_FOUND');
  });

  it("cannot correct another user's meal", async () => {
    const created = await post('/api/v1/meals/manual', meal());
    const response = await patch(
      created.json().id,
      { items: meal().items, nutrition },
      OTHER_TOKEN,
    );
    expect(response.statusCode).toBe(404);
  });

  it('rejects a correction that would leave the meal ungrounded', async () => {
    const created = await post('/api/v1/meals/manual', meal());
    const { datasetVersion: _dropped, ...ungrounded } = nutrition;
    const response = await patch(created.json().id, {
      items: meal().items,
      nutrition: ungrounded,
    });

    expect(response.statusCode).toBe(400);
    expect(response.json().error.code).toBe('VALIDATION_FAILED');
  });
});

describe('deleting a meal', () => {
  const remove = (id: string, authorization = TOKEN) =>
    server.app.inject({ method: 'DELETE', url: `/api/v1/meals/${id}`, headers: { authorization } });

  it('returns 204 with no body', async () => {
    const created = await post('/api/v1/meals/manual', meal());
    const response = await remove(created.json().id);

    expect(response.statusCode).toBe(204);
    expect(response.body).toBe('');
  });

  it('makes the meal unreadable and unlisted afterwards', async () => {
    const created = await post('/api/v1/meals/manual', meal());
    await remove(created.json().id);

    expect((await get(`/api/v1/meals/${created.json().id}`)).statusCode).toBe(404);
    expect((await get('/api/v1/meals')).json().data).toEqual([]);
  });

  it('answers 404 for a meal that is already gone', async () => {
    const created = await post('/api/v1/meals/manual', meal());
    await remove(created.json().id);

    const again = await remove(created.json().id);
    expect(again.statusCode).toBe(404);
    expect(again.json().error.code).toBe('MEAL_NOT_FOUND');
  });

  it("cannot delete another user's meal", async () => {
    const created = await post('/api/v1/meals/manual', meal());
    expect((await remove(created.json().id, OTHER_TOKEN)).statusCode).toBe(404);
  });

  // Deleting a corrected meal must not bring back the revision the user had
  // already replaced. Being superseded is permanent, and it does not become
  // untrue when the replacement is deleted.
  it('removes a corrected meal entirely rather than resurrecting revision 1', async () => {
    const created = await post('/api/v1/meals/manual', meal());
    const corrected = await server.app.inject({
      method: 'PATCH',
      url: `/api/v1/meals/${created.json().id}`,
      payload: { items: meal().items, nutrition },
      headers: { authorization: TOKEN },
    });

    await remove(corrected.json().id);

    expect((await get('/api/v1/meals')).json().data).toEqual([]);
  });
});

describe('the rest of Surface B', () => {
  it.each([
    ['GET', '/api/v1/goals'],
    ['GET', '/api/v1/journey'],
    ['GET', '/api/v1/progress'],
    ['GET', '/api/v1/achievements'],
    ['GET', '/api/v1/catalog/search'],
  ])('says %s %s is not implemented rather than 404-ing', async (method, url) => {
    const response = await server.app.inject({ method: method as 'GET', url });

    expect(response.statusCode).toBe(501);
    expect(response.json().error.userMessageKey).toBe('error.not_implemented');
  });

  it('carries a correlation id on a product response', async () => {
    const response = await get('/api/v1/meals');
    expect(response.headers['x-correlation-id']).toBeDefined();
  });

  it('never sets a permissive CORS header (docs/09 §9.9)', async () => {
    const response = await get('/api/v1/meals');
    expect(response.headers['access-control-allow-origin']).toBeUndefined();
  });
});
