import type { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';
import { createHash } from 'node:crypto';
import { AppError, ERROR_CODES } from '@supernutrition/shared';
import { rateLimitHeaders, toProductError } from '../../middleware/index.js';
import type { IdempotencyStore, RateLimitDecision, RateLimiter } from '../../security/index.js';
import type { MealController } from '../../modules/meals/index.js';
import type { SearchFood } from '../../modules/nutrition/index.js';

/** Surface B — the product REST API (docs/06 §6.6). */
export const PRODUCT_ROUTES = [
  { method: 'POST', path: '/api/v1/auth/session' },
  { method: 'POST', path: '/api/v1/auth/refresh' },
  { method: 'POST', path: '/api/v1/auth/logout' },
  { method: 'DELETE', path: '/api/v1/auth/sessions' },

  { method: 'POST', path: '/api/v1/meals/photo' },
  { method: 'POST', path: '/api/v1/meals/manual' },
  { method: 'POST', path: '/api/v1/meals/voice' },
  { method: 'GET', path: '/api/v1/meals' },
  { method: 'GET', path: '/api/v1/meals/:id' },
  { method: 'PATCH', path: '/api/v1/meals/:id' },
  { method: 'DELETE', path: '/api/v1/meals/:id' },

  { method: 'GET', path: '/api/v1/goals' },
  { method: 'PATCH', path: '/api/v1/goals' },

  { method: 'GET', path: '/api/v1/journey' },
  { method: 'GET', path: '/api/v1/progress' },
  { method: 'GET', path: '/api/v1/progress/weekly' },
  { method: 'GET', path: '/api/v1/progress/trends' },
  { method: 'GET', path: '/api/v1/achievements' },
  { method: 'GET', path: '/api/v1/coach/messages' },

  { method: 'GET', path: '/api/v1/users/me' },
  { method: 'PATCH', path: '/api/v1/users/me' },
  { method: 'GET', path: '/api/v1/users/me/consents' },
  { method: 'POST', path: '/api/v1/users/me/export' },
  { method: 'DELETE', path: '/api/v1/users/me' },

  { method: 'GET', path: '/api/v1/catalog/search' },
  { method: 'GET', path: '/api/v1/catalog/items/:foodId' },
  { method: 'GET', path: '/api/v1/catalog/packages' },
] as const;

/** Endpoints this and previous stages implement; the rest of Surface B still 501s. */
export const IMPLEMENTED_PRODUCT_ROUTES = [
  'POST /api/v1/meals/photo',
  'POST /api/v1/meals/manual',
  'POST /api/v1/meals/voice',
  'GET /api/v1/meals',
  'GET /api/v1/meals/:id',
  'PATCH /api/v1/meals/:id',
  'DELETE /api/v1/meals/:id',
  'GET /api/v1/catalog/search',
] as const;

export interface ProductRouteDeps {
  readonly meals: MealController;
  readonly searchFood: SearchFood;
  /** Rejects an anonymous request; resolves the account behind the credential. */
  readonly authenticate: (request: FastifyRequest) => Promise<{ userId: string }>;
  /** At-most-once semantics for creating POSTs (docs/06 §6.7). */
  readonly idempotency: IdempotencyStore;
  /** Per-user product layer, 120/min, fail-OPEN (docs/09 §9.3). */
  readonly limiter: RateLimiter;
}

/**
 * Authenticate, then throttle, then hand back the caller.
 *
 * The product layer fails OPEN where the gateway fails closed: this surface is
 * unpaid, and a limiter outage should not stop a user seeing yesterday's lunch.
 * One global policy gets one of the two wrong (docs/09 §9.3).
 *
 * Headers are set on the reply even when the request is allowed, so a client
 * can back off BEFORE it is rejected rather than only learning the limit by
 * hitting it (docs/06 §6.8).
 */
const authenticateAndThrottle = async (
  deps: ProductRouteDeps,
  request: FastifyRequest,
  reply: FastifyReply,
): Promise<{ userId: string }> => {
  const principal = await deps.authenticate(request);

  let decision: RateLimitDecision;
  try {
    decision = await deps.limiter.check({ layer: 'user', principal: principal.userId });
  } catch {
    return principal; // fail open
  }

  reply.headers(rateLimitHeaders(decision));
  if (!decision.allowed) {
    if (decision.retryAfterSeconds !== null) {
      reply.header('Retry-After', String(decision.retryAfterSeconds));
    }
    throw new AppError(ERROR_CODES.RATE_LIMITED, 'Too many requests.');
  }

  return principal;
};

/** 24h retention, matching docs/06 §6.7. */
const IDEMPOTENCY_TTL_SECONDS = 24 * 60 * 60;

/** Canonical body digest: key order must not decide idempotency collisions. */
const canonicalDigest = (body: unknown): string => {
  const normalized = (value: unknown): unknown => {
    if (Array.isArray(value)) return value.map(normalized);
    if (value !== null && typeof value === 'object') {
      const entries = Object.entries(value as Record<string, unknown>)
        .map(([key, val]) => [key, normalized(val)] as const)
        .sort(([a], [b]) => a.localeCompare(b));
      return Object.fromEntries(entries);
    }
    return value;
  };
  return createHash('sha256')
    .update(JSON.stringify(normalized(body)))
    .digest('hex');
};

/** What a completed idempotent request stores, so a replay can reproduce it. */
interface StoredOutcome {
  readonly status: number;
  readonly body: unknown;
}

/**
 * Wraps a creating POST in Idempotency-Key semantics: a replay returns the
 * stored response, a same-key/different-body request conflicts (409), and a
 * fresh key executes once and caches the result.
 *
 * Without a key the request is a plain create. That is deliberate: the header
 * is what a client uses to promise "this is the same request", and inventing a
 * digest-based key for callers who did not ask would silently collapse two
 * genuinely separate identical meals into one.
 */
const withIdempotency = async <T extends StoredOutcome>(
  deps: ProductRouteDeps,
  request: FastifyRequest,
  create: () => Promise<T>,
): Promise<StoredOutcome> => {
  const key = request.headers['idempotency-key'];
  if (typeof key !== 'string' || key.length === 0) return create();

  const claim = await deps.idempotency.claim(
    key,
    canonicalDigest(request.body),
    IDEMPOTENCY_TTL_SECONDS,
  );
  if (claim.state === 'conflict') {
    throw AppError.conflict('Idempotency-Key was already used with a different request body.');
  }
  // The stored status is replayed too, not assumed: a replay must reproduce the
  // original response, and hardcoding 201 would invent one.
  if (claim.state === 'replay') return claim.response as StoredOutcome;

  try {
    const result = await create();
    await deps.idempotency.complete(key, { status: result.status, body: result.body });
    return result;
  } catch (error) {
    // Release the claim so a retry with the same key may run. A claim that is
    // still held (never completed, never failed) keeps conflicting, which is
    // what stops two concurrent retries from both creating.
    await deps.idempotency.fail(key);
    throw error;
  }
};

type MealIdParams = { Params: { id: string } };

/**
 * Fastify wiring for `/api/v1`.
 *
 * Registered inside its own encapsulated scope so the Product API error
 * taxonomy applies to everything raised under it — including the errors
 * Fastify raises before a handler runs, such as an unparseable body. Without
 * the scope those would fall through to the gateway's error handler and reach a
 * product client wearing a gateway code it cannot branch on.
 */
export const registerProductRoutes = async (
  app: FastifyInstance,
  deps: ProductRouteDeps,
): Promise<void> => {
  await app.register(async (scope) => {
    scope.setErrorHandler((error, request, reply) => {
      const mapped = toProductError(error);
      if (mapped.status >= 500) {
        request.log.error({ telemetry: { failureCode: 'internal' } }, 'product request failed');
      }
      return reply.status(mapped.status).send(mapped.toResponse(String(request.id)));
    });

    scope.post('/api/v1/meals/photo', async (request, reply) => {
      const { userId } = await authenticateAndThrottle(deps, request, reply);
      const result = await withIdempotency(deps, request, async () => ({
        status: 201,
        body: await deps.meals.createPhotoMeal(request.body, userId),
      }));
      return reply.status(result.status).send(result.body);
    });

    scope.post('/api/v1/meals/manual', async (request, reply) => {
      const { userId } = await authenticateAndThrottle(deps, request, reply);
      const result = await withIdempotency(deps, request, async () => ({
        status: 201,
        body: await deps.meals.createManualMeal(request.body, userId),
      }));
      return reply.status(result.status).send(result.body);
    });

    scope.post('/api/v1/meals/voice', async (request, reply) => {
      const { userId } = await authenticateAndThrottle(deps, request, reply);
      const result = await withIdempotency(deps, request, async () => ({
        status: 201,
        body: await deps.meals.createVoiceMeal(request.body, userId),
      }));
      return reply.status(result.status).send(result.body);
    });

    scope.get('/api/v1/meals', async (request, reply) => {
      const { userId } = await authenticateAndThrottle(deps, request, reply);
      return reply.status(200).send(await deps.meals.listMeals(request.query, userId));
    });

    scope.get<MealIdParams>('/api/v1/meals/:id', async (request, reply) => {
      const { userId } = await authenticateAndThrottle(deps, request, reply);
      return reply.status(200).send(await deps.meals.getMeal(request.params.id, userId));
    });

    // A correction returns revision N+1 rather than the row it replaced.
    scope.patch<MealIdParams>('/api/v1/meals/:id', async (request, reply) => {
      const { userId } = await authenticateAndThrottle(deps, request, reply);
      return reply
        .status(200)
        .send(await deps.meals.correctMeal(request.params.id, request.body, userId));
    });

    scope.delete<MealIdParams>('/api/v1/meals/:id', async (request, reply) => {
      const { userId } = await authenticateAndThrottle(deps, request, reply);
      await deps.meals.deleteMeal(request.params.id, userId);
      return reply.status(204).send();
    });

    // ── Catalog search (Stage 6) ───────────────────────────────────────
    // Returns matches, not verdicts — never a single auto-selected food
    // (docs/06 §6.6; auto-selecting food identity is a forbidden default
    // under AD-40).
    scope.get('/api/v1/catalog/search', async (request, reply) => {
      // Authenticated and throttled like the rest of Surface B, and before the
      // query is even parsed: an anonymous caller must not be able to spend the
      // catalog provider's quota, nor learn which parameters a request is
      // missing.
      await authenticateAndThrottle(deps, request, reply);

      const qs = request.query as Record<string, unknown>;
      const q = typeof qs['q'] === 'string' ? qs['q'].trim() : '';
      const country = typeof qs['country'] === 'string' ? qs['country'].trim() : '';
      const rawLimit = typeof qs['limit'] === 'string' ? Number(qs['limit']) : 5;
      const limit =
        Number.isFinite(rawLimit) && rawLimit >= 1 && rawLimit <= 25 ? Math.floor(rawLimit) : 5;

      if (!q) {
        throw AppError.validation('Missing required query parameter.', [
          { field: 'q', issue: 'required' },
        ]);
      }
      if (!/^[A-Z]{2}$/.test(country)) {
        throw AppError.validation('Invalid or missing country code.', [
          { field: 'country', issue: 'invalid_iso_3166' },
        ]);
      }

      const result = await deps.searchFood.execute({ query: q, country, limit });
      if (!result.ok) {
        const failureMap: Record<string, () => AppError> = {
          'not-found': () => AppError.validation('No catalog source matched.', []),
          'rate-limited': () => new AppError('RATE_LIMITED', 'Catalog rate limit reached.'),
          timeout: () => new AppError('PROVIDER_TIMEOUT', 'Catalog lookup timed out.'),
          'provider-unavailable': () =>
            new AppError('PROVIDER_UNAVAILABLE', 'Catalog provider unavailable.'),
          'unsupported-country': () =>
            AppError.validation('Unsupported catalog country.', [
              { field: 'country', issue: 'unsupported' },
            ]),
        };
        throw (failureMap[result.error] ?? (() => AppError.internal()))();
      }

      const data = result.value.slice(0, limit);
      return reply.status(200).send({ data, query: q, country });
    });

    // A declared-but-unshipped route says so honestly rather than 404-ing,
    // which would be indistinguishable from a typo in the path. It answers in
    // the PRODUCT taxonomy: a `gatewayCode` here would be a code the product
    // client has never heard of, which is the same reason this scope has its
    // own error handler at all.
    const implemented = new Set<string>(IMPLEMENTED_PRODUCT_ROUTES);
    for (const route of PRODUCT_ROUTES) {
      if (implemented.has(`${route.method} ${route.path}`)) continue;
      scope.route({
        method: route.method as 'GET',
        url: route.path,
        handler: async (request, reply) => {
          const error = new AppError(
            ERROR_CODES.NOT_IMPLEMENTED,
            'This endpoint is declared but not implemented yet.',
            { details: [{ field: 'surface', issue: 'product_api_not_implemented' }] },
          );
          return reply.status(error.status).send(error.toResponse(String(request.id)));
        },
      });
    }
  });
};
