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
import {
  InMemoryIdempotencyStore,
  InMemoryRateLimiter,
  TestModeAuthenticator,
  TestModeProductAuthenticator,
  type RateLimiter,
  type IdempotencyTtl,
} from '../../security/index.js';
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
import { SearchFoodHandler, USDAProvider, type FetchLike } from '../../modules/nutrition/index.js';

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

const DATASET = 'SR Legacy 2021-10';

const usdaFood = (fdcId: number, description: string) => ({
  fdcId,
  description,
  foodNutrients: [
    { nutrientId: 1008, value: 89 },
    { nutrientId: 1003, value: 1.09 },
  ],
  foodPortions: [{ amount: 1, gramWeight: 118, measureUnit: { name: 'medium' } }],
});

/**
 * The catalog transport is a double that records every call, so a test can
 * prove a call did *not* happen — the meal path must never reach USDA.
 */
let usdaCalls: string[];
let usdaReply: () => Response;

const catalogFetch: FetchLike = async (url) => {
  usdaCalls.push(url);
  return usdaReply();
};

const usdaJson = (body: unknown, status = 200): Response =>
  new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });

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
let idempotencyStore: InMemoryIdempotencyStore;
/** Swappable per test, so throttling can be provoked without rebuilding. */
let productLimiter: RateLimiter;

/**
 * The whole composition the tests run against, with one seam.
 *
 * Extracted from `beforeEach` so a test can substitute a single collaborator —
 * an idempotency store whose completion write fails, say — without rebuilding
 * the wiring by hand and drifting from what the suite actually exercises.
 */
const buildTestServer = async (
  overrides: { idempotency?: typeof idempotencyStore; logger?: unknown } = {},
): Promise<ServerHandle> => {
  repository = new InMemoryMealRepository();
  usdaCalls = [];
  usdaReply = () => usdaJson({ foods: [usdaFood(171705, 'Bananas, raw')] });

  idempotencyStore = new InMemoryIdempotencyStore();
  productLimiter = new InMemoryRateLimiter([{ layer: 'user', limit: 120, windowSeconds: 60 }]);
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

  return await buildServer({
    config,
    logger: (overrides.logger ?? createLogger(config)) as never,
    gateway: {
      payloads: { put: () => {}, dispose: () => {} },
      now: () => new Date('2026-07-29T12:30:04Z'),
    } as never,
    product: {
      meals: controller,
      idempotency: overrides.idempotency ?? idempotencyStore,
      limiter: {
        check: (key) => productLimiter.check(key),
        failureMode: (layer) => productLimiter.failureMode(layer),
      },
      // The real provider and use case, with only the transport doubled: the
      // catalog contract a client sees includes the provenance the provider
      // stamps on every fact.
      searchFood: new SearchFoodHandler(
        new USDAProvider(
          'usda-test-key',
          {
            baseUrl: 'https://example.invalid/fdc/v1',
            connectTimeoutMs: 1000,
            totalTimeoutMs: 2000,
            maxRetries: 0,
            circuitBreakerThreshold: 5,
          },
          DATASET,
          catalogFetch,
        ),
      ),
      authenticate: (request) => authenticator.authenticate(request.headers.authorization),
    },
  });
};

beforeEach(async () => {
  server = await buildTestServer();
});

afterEach(async () => {
  await server.close();
});

// `inject()` returns a value that is BOTH a promise and a fluent chain, and
// TypeScript picks the chain — so every `.statusCode` on an awaited result was a
// type error the suite never saw. Awaiting inside pins the response type.
const post = async (url: string, payload: unknown, authorization = TOKEN) =>
  await server.app.inject({
    method: 'POST',
    url,
    payload: payload as never,
    headers: { authorization },
  });

const get = async (url: string, authorization = TOKEN) =>
  await server.app.inject({ method: 'GET', url, headers: { authorization } });

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

describe('idempotency (docs/06 §6.7)', () => {
  const key = 'idem-test-1';

  const postWithKey = async (
    url: string,
    payload: unknown,
    idemKey: string,
    authorization = TOKEN,
  ) =>
    await server.app.inject({
      method: 'POST',
      url,
      payload: payload as never,
      headers: { authorization, 'idempotency-key': idemKey },
    });

  it('replays the stored response for the same key and same body', async () => {
    const first = await postWithKey('/api/v1/meals/manual', meal(), key);
    const second = await postWithKey('/api/v1/meals/manual', meal(), key);

    expect(first.statusCode).toBe(201);
    expect(second.statusCode).toBe(201);
    // Exactly one meal exists: the replay did not create a duplicate.
    expect(repository.events.size).toBe(1);
    expect(second.body).toBe(first.body);
  });

  it('returns 409 CONFLICT for the same key and a different body', async () => {
    await postWithKey('/api/v1/meals/manual', meal(), key);
    const conflicting = await postWithKey(
      '/api/v1/meals/manual',
      meal({ mealType: 'breakfast' }),
      key,
    );

    expect(conflicting.statusCode).toBe(409);
    expect(conflicting.json().error.code).toBe('CONFLICT');
    expect(repository.events.size).toBe(1);
  });

  // M4 — Idempotency-Key is chosen by the client, and short human keys are
  // ordinary. Claimed in one global keyspace, two users picking the same string
  // collided: at best the second got a 409 for a request they never made, at
  // worst the replay branch handed them the stored response to someone else's
  // meal.
  it('releases the key when creation fails, so a later request can use it', async () => {
    // A claim is taken BEFORE create() runs, so a create that throws must
    // release it — otherwise one failed request makes that key permanently
    // unusable, and the failure is reported as a server fault rather than the
    // client error it is.
    const broken = await server.app.inject({
      method: 'POST',
      url: '/api/v1/meals/manual',
      payload: meal({ localDate: '2026-02-30' }) as never,
      headers: { authorization: TOKEN, 'idempotency-key': 'release-me' },
    });
    expect(broken.statusCode, 'a validation failure became a server error').toBe(400);

    const retried = await server.app.inject({
      method: 'POST',
      url: '/api/v1/meals/manual',
      payload: meal() as never,
      headers: { authorization: TOKEN, 'idempotency-key': 'release-me' },
    });
    expect(retried.statusCode, 'the failed claim was never released').toBe(201);
  });

  it('logs the cause of a 5xx without putting it in the body (GW-F2)', async () => {
    // `mapError`/`toProductError` return `cause` and the comment beside them
    // says it is "logged and never sent". Nothing consumed it: the handlers
    // logged a bare `failureCode: 'internal'`, so every 5xx recorded that one
    // had happened and nothing about what it was. Bodies were clean, which is
    // why this never showed up as a leak — it showed up as nothing at all.
    const secret = `DIAGNOSTIC-${Date.now()}`;
    const logged: unknown[] = [];
    const failing = {
      claim: async () => {
        throw new Error(secret);
      },
      complete: async () => {},
      fail: async () => {},
    };
    // Captured at the ROOT logger, because the handlers log through
    // `request.log` — a child. Replacing `app.log.error` after the fact leaves
    // every child still pointing at the original, which is how a first draft of
    // this test reported a failure against a working fix.
    const capturing = {
      level: 'error',
      error: (...args: unknown[]) => void logged.push(args[0]),
      warn: () => {},
      info: () => {},
      debug: () => {},
      trace: () => {},
      fatal: () => {},
      silent: () => {},
      child() {
        return this;
      },
    };
    await server.close();
    server = await buildTestServer({ idempotency: failing as never, logger: capturing });

    const response = await server.app.inject({
      method: 'POST',
      url: '/api/v1/meals/manual',
      payload: meal() as never,
      headers: { authorization: TOKEN, 'idempotency-key': 'diagnostics' },
    });

    expect(response.statusCode).toBe(500);
    expect(response.body, 'the diagnostic reached the client').not.toContain(secret);
    // Inspected as objects, not JSON: `JSON.stringify(new Error('x'))` is `{}`,
    // because a message is not an enumerable own property. Serialising the
    // capture would have reported "nothing logged" about a log line that was
    // there.
    const causes = logged.map((entry) => (entry as { err?: { message?: string } }).err?.message);
    expect(causes, 'the 5xx was answered without recording what caused it').toContain(secret);
  });

  it('does not create a second meal when the completion write fails (DS-F1)', async () => {
    // The claim was released on ANY throw, including a Redis fault raised by
    // `complete()` AFTER the Postgres row had already been written. The client
    // sees a 500, retries the same Idempotency-Key, the claim reads fresh — and
    // a second durable meal exists for one request the user made once. That is
    // the guarantee this store exists to provide, inverted by its own error
    // path.
    //
    // The in-memory store cannot raise from `complete`, which is why nothing
    // caught this: the fault has to be injected.
    let completions = 0;
    const failingCompletion = {
      claim: (key: string, digest: string, ttl: IdempotencyTtl, owner: string) =>
        idempotencyStore.claim(key, digest, ttl, owner),
      complete: async () => {
        completions += 1;
        throw new Error('redis went away after the row was written');
      },
      fail: (key: string, token: string) => idempotencyStore.fail(key, token),
    };
    await server.close();
    server = await buildTestServer({ idempotency: failingCompletion as never });

    const key = 'completion-fails';
    const first = await server.app.inject({
      method: 'POST',
      url: '/api/v1/meals/manual',
      payload: meal() as never,
      headers: { authorization: TOKEN, 'idempotency-key': key },
    });
    expect(completions, 'the completion write was never attempted').toBe(1);
    expect(first.statusCode, 'a completion fault should surface as a server error').toBe(500);

    // Exactly the client behaviour the contract invites: same key, same body.
    const retry = await server.app.inject({
      method: 'POST',
      url: '/api/v1/meals/manual',
      payload: meal() as never,
      headers: { authorization: TOKEN, 'idempotency-key': key },
    });
    expect(
      retry.statusCode,
      'the retry created a second meal: the claim was released even though the row was written',
    ).toBe(409);
    expect(repository.events.size, 'one request produced two durable meals').toBe(1);
  });

  it('keeps one user out of another user’s idempotency keyspace', async () => {
    const mine = await postWithKey('/api/v1/meals/manual', meal(), 'shared-key', TOKEN);
    expect(mine.statusCode).toBe(201);

    const theirs = await postWithKey(
      '/api/v1/meals/manual',
      meal({ mealType: 'breakfast' }),
      'shared-key',
      OTHER_TOKEN,
    );

    // Not a 409: the other user has never used this key.
    expect(theirs.statusCode).toBe(201);
    expect(repository.events.size).toBe(2);
  });

  it('does not replay one user’s stored response to another', async () => {
    const mine = await postWithKey('/api/v1/meals/manual', meal(), 'collide', TOKEN);
    const theirs = await postWithKey('/api/v1/meals/manual', meal(), 'collide', OTHER_TOKEN);

    expect(theirs.statusCode).toBe(201);
    // Same key, same body, different user: two meals, two distinct responses.
    expect(theirs.body).not.toBe(mine.body);
    expect(repository.events.size).toBe(2);
  });

  it('ignores key ordering in the body when deciding the digest', async () => {
    const bodyA = meal();
    const bodyB = JSON.parse(JSON.stringify(bodyA));
    const shuffled: Record<string, unknown> = {};
    for (const k of Object.keys(bodyB).reverse()) shuffled[k] = bodyB[k];

    const first = await postWithKey('/api/v1/meals/manual', bodyA, key);
    const replay = await postWithKey('/api/v1/meals/manual', shuffled, key);

    expect(first.statusCode).toBe(201);
    expect(replay.statusCode).toBe(201);
    expect(repository.events.size).toBe(1);
  });

  it('treats a missing key as a plain non-idempotent create', async () => {
    const first = await post('/api/v1/meals/manual', meal());
    const second = await post('/api/v1/meals/manual', meal());

    expect(first.statusCode).toBe(201);
    expect(second.statusCode).toBe(201);
    expect(repository.events.size).toBe(2);
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
    ['GET', '/api/v1/catalog/search?q=banana&country=US'],
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
  const patch = async (id: string, payload: unknown, authorization = TOKEN) =>
    await server.app.inject({
      method: 'PATCH',
      url: `/api/v1/meals/${id}`,
      payload: payload as never,
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

describe('catalog search', () => {
  it('returns matches carrying source and dataset version', async () => {
    const response = await get('/api/v1/catalog/search?q=banana&country=US');

    expect(response.statusCode).toBe(200);
    expect(response.json()).toEqual({
      query: 'banana',
      country: 'US',
      data: [
        {
          foodRef: 'usda:fdc:171705',
          displayName: 'Bananas, raw',
          factsPer100g: {
            catalogSource: 'usda',
            datasetVersion: DATASET,
            servingBasis: { unit: 'g', amount: '100', gramWeight: '100' },
            calories: '89',
            proteinG: '1.09',
            fatG: null,
            carbG: null,
            fiberG: null,
            sugarG: null,
            sodiumMg: null,
          },
          servingOptions: [{ unit: 'medium', amount: '1', gramWeight: '118' }],
        },
      ],
    });
  });

  // Matches, not verdicts (docs/06 §6.6): the response is a list even when the
  // catalog is confident, because auto-selecting a food identity is a forbidden
  // default under AD-40.
  it('returns every candidate rather than one auto-selected food', async () => {
    usdaReply = () =>
      usdaJson({ foods: [usdaFood(171705, 'Bananas, raw'), usdaFood(173944, 'Banana chips')] });

    const response = await get('/api/v1/catalog/search?q=banana&country=US');

    expect(response.json().data).toHaveLength(2);
  });

  it('honours the requested limit', async () => {
    usdaReply = () =>
      usdaJson({ foods: [usdaFood(171705, 'Bananas, raw'), usdaFood(173944, 'Banana chips')] });

    const response = await get('/api/v1/catalog/search?q=banana&country=US&limit=1');

    expect(response.json().data).toHaveLength(1);
  });

  // Nothing matched is an answer, not a failure — the client can offer manual
  // entry instead of showing an error.
  it('answers 200 with an empty data array when nothing matched', async () => {
    usdaReply = () => usdaJson({ foods: [] });

    const response = await get('/api/v1/catalog/search?q=quux&country=US');

    expect(response.statusCode).toBe(200);
    expect(response.json()).toEqual({ data: [], query: 'quux', country: 'US' });
  });

  it.each([
    ['a missing search term', '/api/v1/catalog/search?country=US', 'q'],
    ['a blank search term', '/api/v1/catalog/search?q=%20%20&country=US', 'q'],
    ['a missing country', '/api/v1/catalog/search?q=banana', 'country'],
    [
      'a country that is not ISO 3166-1 alpha-2',
      '/api/v1/catalog/search?q=banana&country=usa',
      'country',
    ],
  ])('rejects %s', async (_case, url, field) => {
    const response = await get(url);

    expect(response.statusCode).toBe(400);
    const body = response.json();
    expect(body.error.code).toBe('VALIDATION_FAILED');
    expect(body.error.details[0].field).toBe(field);
    expect(body.error).not.toHaveProperty('gatewayCode');
  });

  it('does not call the catalog for a request it has already rejected', async () => {
    await get('/api/v1/catalog/search?country=US');

    expect(usdaCalls).toEqual([]);
  });

  // The catalog costs quota per call. An anonymous caller must not be able to
  // spend it, and must not learn from the error which parameters were wrong.
  it('refuses an anonymous search before it reaches the catalog', async () => {
    const response = await server.app.inject({
      method: 'GET',
      url: '/api/v1/catalog/search?q=banana&country=US',
    });

    expect(response.statusCode).toBe(401);
    expect(response.json().error.code).toBe('UNAUTHENTICATED');
    expect(usdaCalls).toEqual([]);
  });

  it('answers 401 rather than 400 when an anonymous request is also invalid', async () => {
    const response = await server.app.inject({ method: 'GET', url: '/api/v1/catalog/search' });

    expect(response.statusCode).toBe(401);
    expect(usdaCalls).toEqual([]);
  });

  it.each([
    ['is unavailable', 500, 502, 'PROVIDER_UNAVAILABLE'],
    ['is rate limiting us', 429, 429, 'RATE_LIMITED'],
  ])('reports that the catalog %s', async (_case, upstream, expected, code) => {
    usdaReply = () => usdaJson({ error: 'usda' }, upstream);

    const response = await get('/api/v1/catalog/search?q=banana&country=US');

    expect(response.statusCode).toBe(expected);
    expect(response.json().error.code).toBe(code);
  });

  it('reports a catalog timeout as a timeout', async () => {
    usdaReply = () => {
      const error = new Error('The operation was aborted due to timeout');
      error.name = 'TimeoutError';
      throw error;
    };

    const response = await get('/api/v1/catalog/search?q=banana&country=US');

    expect(response.statusCode).toBe(504);
    expect(response.json().error.code).toBe('PROVIDER_TIMEOUT');
  });

  it('never leaks the catalog API key to a client', async () => {
    const response = await get('/api/v1/catalog/search?q=banana&country=US');

    expect(response.body).not.toContain('usda-test-key');
  });
});

/**
 * AD-7 / AD-19 and docs/08 §8.4: the server does not calculate nutrition and
 * does not fill a meal in from the catalog. These assertions fail the moment a
 * calculation path is wired into meal creation.
 */
describe('meal creation is isolated from the catalog', () => {
  it.each([
    ['manual', '/api/v1/meals/manual', meal()],
    [
      'photo',
      '/api/v1/meals/photo',
      meal({ attemptId: 'att_01J8X4Q2K7R9M3P5', configurationVersion: 'cfg/1' }),
    ],
    ['voice', '/api/v1/meals/voice', meal({ transcript: 'a banana' })],
  ])('never calls USDA when a %s meal is created', async (_source, url, payload) => {
    const response = await post(url, payload);

    expect(response.statusCode).toBe(201);
    expect(usdaCalls).toEqual([]);
  });

  // The device owns calculation, so a meal without nutrition is incomplete —
  // the server rejects it rather than looking the food up and filling it in.
  it('still requires nutrition rather than sourcing it from the catalog', async () => {
    const { nutrition: _dropped, ...withoutNutrition } = meal();
    const response = await post('/api/v1/meals/manual', withoutNutrition);

    expect(response.statusCode).toBe(400);
    expect(response.json().error.code).toBe('VALIDATION_FAILED');
    expect(usdaCalls).toEqual([]);
  });

  it('persists the client nutrition verbatim, down to the dataset it came from', async () => {
    const created = await post('/api/v1/meals/photo', {
      ...meal({ attemptId: 'att_01J8X4Q2K7R9M3P5', configurationVersion: 'cfg/1' }),
    });
    const read = await get(`/api/v1/meals/${created.json().id}`);

    expect(read.statusCode).toBe(200);
    expect(read.json().nutrition).toMatchObject(nutrition);
    // Not the dataset version the catalog provider is pinned to — the meal
    // carries the client's provenance, unrewritten.
    expect(read.json().nutrition.datasetVersion).toBe('usda-2026.1');
    expect(usdaCalls).toEqual([]);
  });

  it('leaves the confirmed items exactly as the client sent them', async () => {
    const created = await post('/api/v1/meals/manual', meal());
    const read = await get(`/api/v1/meals/${created.json().id}`);

    expect(read.json().items).toMatchObject(meal().items);
    expect(usdaCalls).toEqual([]);
  });
});

describe('the rest of Surface B', () => {
  it.each([
    ['GET', '/api/v1/goals'],
    ['GET', '/api/v1/journey'],
    ['GET', '/api/v1/progress'],
    ['GET', '/api/v1/achievements'],
    ['GET', '/api/v1/catalog/items/usda:fdc:171705'],
  ])('says %s %s is not implemented rather than 404-ing', async (method, url) => {
    const response = await server.app.inject({ method: method as 'GET', url });

    expect(response.statusCode).toBe(501);
    // Product taxonomy, not the gateway's: a client branching on `code` would
    // read a gatewayCode envelope as an unparseable response (docs/06 §6.2).
    expect(response.json().error.code).toBe('NOT_IMPLEMENTED');
    expect(response.json().error).not.toHaveProperty('gatewayCode');
  });

  it('carries a correlation id on a product response', async () => {
    const response = await get('/api/v1/meals');
    expect(response.headers['x-correlation-id']).toBeDefined();
  });

  // An unknown path answers in the taxonomy of the surface it was aimed at. A
  // product client branches on `code` and has never heard of `gatewayCode`.
  it('answers an unknown product path in the product taxonomy', async () => {
    const response = await get('/api/v1/there-is-no-such-thing');

    expect(response.statusCode).toBe(404);
    expect(response.json().error.code).toBe('VALIDATION_FAILED');
    expect(response.json().error).not.toHaveProperty('gatewayCode');
  });
});

/**
 * The product layer throttles per user and fails OPEN — this surface is unpaid,
 * and a limiter outage should not stop a user seeing yesterday's lunch. The
 * gateway fails closed. One global policy gets one of the two wrong
 * (docs/09 §9.3).
 */
describe('rate limiting (docs/09 §9.3)', () => {
  it('advertises the remaining budget on an allowed request', async () => {
    const response = await get('/api/v1/meals');

    expect(response.statusCode).toBe(200);
    expect(response.headers['x-ratelimit-limit']).toBe('120');
    expect(response.headers['x-ratelimit-remaining']).toBeDefined();
    expect(response.headers['x-ratelimit-reset']).toBeDefined();
  });

  it('answers 429 with Retry-After once the per-user budget is spent', async () => {
    productLimiter = new InMemoryRateLimiter([{ layer: 'user', limit: 1, windowSeconds: 60 }]);

    expect((await get('/api/v1/meals')).statusCode).toBe(200);
    const throttled = await get('/api/v1/meals');

    expect(throttled.statusCode).toBe(429);
    expect(throttled.json().error.code).toBe('RATE_LIMITED');
    expect(throttled.headers['retry-after']).toBe('60');
    expect(throttled.headers['x-ratelimit-remaining']).toBe('0');
  });

  it('serves the request when the limiter itself is unavailable', async () => {
    productLimiter = {
      check: async () => {
        throw new Error('redis is down');
      },
      failureMode: () => 'fail-open',
    };

    expect((await get('/api/v1/meals')).statusCode).toBe(200);
  });

  it('throttles the caller, not the endpoint: an anonymous request never reaches it', async () => {
    productLimiter = new InMemoryRateLimiter([{ layer: 'user', limit: 1, windowSeconds: 60 }]);
    const anonymous = await server.app.inject({ method: 'GET', url: '/api/v1/meals' });

    expect(anonymous.statusCode).toBe(401);
    // The budget is untouched, so a real caller still gets their full quota.
    expect((await get('/api/v1/meals')).statusCode).toBe(200);
  });

  it('never sets a permissive CORS header (docs/09 §9.9)', async () => {
    const response = await get('/api/v1/meals');
    expect(response.headers['access-control-allow-origin']).toBeUndefined();
  });
});

/**
 * Low group — three requests that answered 500 for client mistakes.
 */
describe('client mistakes answer 4xx, not 500', () => {
  it('rejects an impossible calendar date instead of failing at the insert', async () => {
    // 2026-02-30 satisfies ^\d{4}-\d{2}-\d{2}$ and does not exist. It used to
    // reach the database and fail there.
    const response = await post('/api/v1/meals/manual', meal({ localDate: '2026-02-30' }));
    expect(response.statusCode).toBe(400);
  });

  it('rejects a negative quantity', async () => {
    // The decimal pattern began ^-?, so "-250" was a valid amount. The goals
    // module already refused it; the write path did not.
    const response = await post(
      '/api/v1/meals/manual',
      meal({
        items: [
          {
            foodRef: 'usda:fdc:171705',
            displayName: 'Banana, raw',
            servingUnit: 'g',
            servingAmount: '-118',
            confirmedByUser: true,
          },
        ],
      }),
    );
    expect(response.statusCode).toBe(400);
  });

  it('does not crash on a bodyless request that carries an Idempotency-Key', async () => {
    // JSON.stringify(undefined) is undefined, and Hash.update threw on it.
    const response = await server.app.inject({
      method: 'POST',
      url: '/api/v1/meals/manual',
      headers: { authorization: TOKEN, 'idempotency-key': 'no-body' },
    });
    expect(response.statusCode).toBe(400);
    expect(response.statusCode).not.toBe(500);
  });
});
