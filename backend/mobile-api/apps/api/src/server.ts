import Fastify, { type FastifyBaseLogger, type FastifyInstance } from 'fastify';
import multipart from '@fastify/multipart';
import { AppError, ERROR_CODES } from '@supernutrition/shared';
import type { AppConfig } from './config/index.js';
import type { AppLogger } from './logging/index.js';
import { MIDDLEWARE_ORDER, mapError, resolveCorrelationId } from './middleware/index.js';
import { GATEWAY_CODES, GatewayError } from './security/index.js';
import { GATEWAY_ROUTES, PRODUCT_ROUTES } from './routes/index.js';
import { registerGatewayRoutes, type GatewayRouteDeps } from './routes/gateway/routes.js';
import { registerProductRoutes, type ProductRouteDeps } from './routes/product/index.js';
import { MAX_PAYLOAD_BYTES } from './routes/gateway/inference.handler.js';

/**
 * HTTP server assembly.
 *
 * CORS is deliberately NOT registered. The consumer is a native client, and
 * docs/09 §9.9 disables CORS until a first-party web client exists as its own
 * product root. Registering it "just in case" widens the browser-reachable
 * surface of an API no browser is meant to call.
 */

export interface ServerDeps {
  readonly config: AppConfig;
  readonly logger: AppLogger;
  readonly gateway: GatewayRouteDeps;
  readonly product: ProductRouteDeps;
}

export interface ServerHandle {
  readonly app: FastifyInstance;
  listen(): Promise<string>;
  close(): Promise<void>;
}

export const describeServer = (): {
  middleware: readonly string[];
  routes: readonly { method: string; path: string }[];
} => ({
  middleware: MIDDLEWARE_ORDER,
  routes: [...GATEWAY_ROUTES, ...PRODUCT_ROUTES],
});

export const buildServer = async (deps: ServerDeps): Promise<ServerHandle> => {
  const app = Fastify({
    // Widened to Fastify's own logger interface so the instance keeps the
    // default generic; a pino-typed instance makes every route registration
    // structurally incompatible with a plain FastifyInstance parameter.
    loggerInstance: deps.logger as unknown as FastifyBaseLogger,
    bodyLimit: MAX_PAYLOAD_BYTES,
    // GW-F1 — how far `X-Forwarded-For` is believed when deriving `request.ip`,
    // which is what the IP rate-limit layer and the session endpoint key on.
    // Configuration decides; there is no default that is right for both a
    // single container and a replicated deployment behind a balancer.
    trustProxy: deps.config.server.trustProxy,
    genReqId: (request) =>
      resolveCorrelationId(request.headers['x-correlation-id'] as string | undefined),
  });

  await app.register(multipart, {
    limits: { fileSize: MAX_PAYLOAD_BYTES, files: 1, fields: 8 },
  });

  // Correlation ID first, before anything can fail, so every response carries
  // one (docs/06 §6.2).
  app.addHook('onRequest', async (request, reply) => {
    reply.header('X-Correlation-Id', String(request.id));
    reply.header('Cache-Control', 'no-store');
    reply.header('X-Content-Type-Options', 'nosniff');
    reply.header('Referrer-Policy', 'no-referrer');
  });

  // Every thrown error maps to a B-5 taxonomy entry. Nothing reaches a client
  // unmapped, and no internal detail or provider message is serialized.
  app.setErrorHandler((error, request, reply) => {
    const mapped = mapError(error, String(request.id));
    if (mapped.status >= 500) {
      // GW-F2 — the cause is LOGGED, which is what the response body's comment
      // has always claimed. `mapError` returns it and nothing consumed it, so
      // every 5xx recorded that one had happened and nothing about what it was:
      // a pipeline fault left no stack, no message, no error name anywhere. Not
      // a leak — bodies stayed clean — but forensically silent, which is the
      // opposite of the intent the code documented.
      //
      // Through the allowlisted logger, so it reaches operators and not
      // clients.
      request.log.error(
        { err: mapped.cause, telemetry: { failureCode: 'internal' } },
        'gateway request failed',
      );
    }
    return reply.status(mapped.status).headers(mapped.headers).send(mapped.body);
  });

  // An unknown path answers in the taxonomy of the surface it was aimed at. A
  // product client branches on `code`, has never heard of `gatewayCode`, and
  // would read a gateway envelope as an unparseable response rather than a 404
  // (docs/06 §6.2).
  app.setNotFoundHandler((request, reply) => {
    if (request.url.startsWith('/api/')) {
      const error = new AppError(ERROR_CODES.VALIDATION_FAILED, 'No such endpoint.', {
        details: [{ field: 'path', issue: 'not_found' }],
      });
      return reply.status(404).send(error.toResponse(String(request.id)));
    }

    const mapped = mapError(
      new GatewayError(GATEWAY_CODES.VALIDATION_ERROR, {
        details: [{ field: 'path', issue: 'not_found' }],
      }),
      String(request.id),
    );
    return reply.status(404).headers(mapped.headers).send(mapped.body);
  });

  app.get('/health/live', async () => ({ status: 'ok' }));

  // Readiness reports authMode so no environment can be in test mode
  // unnoticed (docs/09 §9.2).
  app.get('/health/ready', async () => ({
    status: 'ok',
    environment: deps.config.env,
    authMode: deps.config.auth.testMode ? 'test-mode' : 'attested',
    remoteInferenceEnabled: deps.config.inference.remoteEnabled,
    contractVersion: 'mobile-api/v1',
  }));

  await registerGatewayRoutes(app, deps.gateway);

  // Surface B registers itself, including which of its routes are still
  // unimplemented. The server does not stub product routes in a loop any more:
  // a route list here and a handler list there is how the two drift apart.
  await registerProductRoutes(app, deps.product);

  return {
    app,
    listen: () => app.listen({ host: deps.config.server.host, port: deps.config.server.port }),
    close: () => app.close(),
  };
};
