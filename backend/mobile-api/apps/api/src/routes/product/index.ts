import type { FastifyInstance, FastifyRequest } from 'fastify';
import { toProductError } from '../../middleware/index.js';
import type { MealController } from '../../modules/meals/index.js';

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

/** The meals endpoints this stage implements; the rest of Surface B still 501s. */
export const IMPLEMENTED_PRODUCT_ROUTES = [
  'POST /api/v1/meals/photo',
  'POST /api/v1/meals/manual',
  'POST /api/v1/meals/voice',
  'GET /api/v1/meals',
  'GET /api/v1/meals/:id',
  'PATCH /api/v1/meals/:id',
  'DELETE /api/v1/meals/:id',
] as const;

export interface ProductRouteDeps {
  readonly meals: MealController;
  /** Rejects an anonymous request; resolves the account behind the credential. */
  readonly authenticate: (request: FastifyRequest) => Promise<{ userId: string }>;
}

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
      const { userId } = await deps.authenticate(request);
      return reply.status(201).send(await deps.meals.createPhotoMeal(request.body, userId));
    });

    scope.post('/api/v1/meals/manual', async (request, reply) => {
      const { userId } = await deps.authenticate(request);
      return reply.status(201).send(await deps.meals.createManualMeal(request.body, userId));
    });

    scope.post('/api/v1/meals/voice', async (request, reply) => {
      const { userId } = await deps.authenticate(request);
      return reply.status(201).send(await deps.meals.createVoiceMeal(request.body, userId));
    });

    scope.get('/api/v1/meals', async (request, reply) => {
      const { userId } = await deps.authenticate(request);
      return reply.status(200).send(await deps.meals.listMeals(request.query, userId));
    });

    scope.get<MealIdParams>('/api/v1/meals/:id', async (request, reply) => {
      const { userId } = await deps.authenticate(request);
      return reply.status(200).send(await deps.meals.getMeal(request.params.id, userId));
    });

    // A correction returns revision N+1 rather than the row it replaced.
    scope.patch<MealIdParams>('/api/v1/meals/:id', async (request, reply) => {
      const { userId } = await deps.authenticate(request);
      return reply
        .status(200)
        .send(await deps.meals.correctMeal(request.params.id, request.body, userId));
    });

    scope.delete<MealIdParams>('/api/v1/meals/:id', async (request, reply) => {
      const { userId } = await deps.authenticate(request);
      await deps.meals.deleteMeal(request.params.id, userId);
      return reply.status(204).send();
    });

    // The rest of Surface B says so honestly rather than 404-ing, which would be
    // indistinguishable from a typo in the path. The body keeps the shape the
    // skeleton shipped: it marks an absent route, not a taxonomy failure.
    const implemented = new Set<string>(IMPLEMENTED_PRODUCT_ROUTES);
    for (const route of PRODUCT_ROUTES) {
      if (implemented.has(`${route.method} ${route.path}`)) continue;
      scope.route({
        method: route.method as 'GET',
        url: route.path,
        handler: async (request, reply) =>
          reply.status(501).send({
            error: {
              gatewayCode: 'internal-error',
              userMessageKey: 'error.not_implemented',
              recoverable: false,
              retryAfter: 0,
              correlationId: String(request.id),
              details: [{ field: 'surface', issue: 'product_api_not_implemented' }],
            },
          }),
      });
    }
  });
};
