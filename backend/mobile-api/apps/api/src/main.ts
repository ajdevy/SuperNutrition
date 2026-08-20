import { randomBytes, randomUUID } from 'node:crypto';
import { resolve } from 'node:path';
import {
  createDatabase,
  DrizzleMealEventRepository,
  DrizzleUserRepository,
} from '@supernutrition/database';
import {
  assertSingleInstanceState,
  loadConfig,
  testModeBanner,
  TestModeFenceError,
  assertStubFence,
  assertPaidRosterInProduction,
  ConfigError,
  type AppConfig,
} from './config/index.js';
import { createLogger, type AppLogger } from './logging/index.js';
import { buildServer, type ServerHandle } from './server.js';
import {
  FIXTURE_MARKET_POLICY,
  InMemoryEffectStore,
  InMemoryIdempotencyStore,
  InMemoryRateLimiter,
  PRODUCTION_MARKET_POLICY,
  RedisAttemptRegistry,
  RedisEffectStore,
  RedisIdempotencyStore,
  RedisRateLimiter,
  createRedisClient,
  StaticMarketPolicyResolver,
  TestModeAuthenticator,
  TestModeProductAuthenticator,
  generateRefreshToken,
  type RateLimitRule,
} from './security/index.js';
import { StubInferenceProvider } from './modules/inference/infrastructure/stub/stub.provider.js';
import {
  InMemoryPayloadSource,
  loadConfigurationSnapshot,
  OpenRouterInferenceProvider,
  TomlModelProfileSource,
  TwoPassInferenceOrchestrator,
  type InferenceRunRecorder,
} from './modules/inference/index.js';
import {
  CorrectMealEventHandler,
  CreateMealEventHandler,
  DeleteMealEventHandler,
  DrizzleMealEventRepositoryAdapter,
  GetMealEventHandler,
  ListMealEventsHandler,
  MealControllerImpl,
  type MealCommandDeps,
} from './modules/meals/index.js';
import { USDAProvider, SearchFoodHandler, LookupFoodHandler } from './modules/nutrition/index.js';
import { DrizzleUserRepositoryAdapter } from './modules/users/infrastructure/index.js';
import { TransferStateTracker } from './routes/gateway/index.js';

const USDA_CONFIG = {
  baseUrl: 'https://api.nal.usda.gov/fdc/v1',
  connectTimeoutMs: 3000,
  totalTimeoutMs: 5000,
  maxRetries: 2,
  circuitBreakerThreshold: 5,
} as const;

/**
 * Composition root — the only place concrete adapters are chosen (AD-1, AD-10).
 *
 * Startup order matters. Configuration is validated and the TEST_MODE fence is
 * asserted BEFORE anything else is constructed, so a misconfigured process
 * exits non-zero rather than starting and serving (docs/09 §9.2).
 */

/**
 * How long the shared registry remembers an attempt.
 *
 * Matched to TERMINAL_RETENTION_SECONDS in transfer-state.ts: for exactly as
 * long as the gateway can describe an attempt locally, every instance can also
 * say who holds it and whether it was cancelled.
 *
 * ⚠ Fixed, NOT derived from market policy. Story 4.4 criterion 4 asks for the
 * latter; doing so against a `retentionDays = 0` market would expire the
 * at-most-once guard immediately. See the note on EFFECT_TTL_SECONDS in
 * routes/gateway/inference.handler.ts — this is an open owner decision, not an
 * oversight.
 */
const TRANSFER_REGISTRY_TTL_SECONDS = 86_400;

const RATE_LIMIT_RULES: readonly RateLimitRule[] = [
  { layer: 'installation', limit: 20, windowSeconds: 3600 },
  { layer: 'ip', limit: 300, windowSeconds: 60 },
  { layer: 'user', limit: 120, windowSeconds: 60 },
  { layer: 'auth-endpoint', limit: 10, windowSeconds: 60 },
];

/**
 * Telemetry sink used until a database connection is wired.
 *
 * A no-op rather than a throw: telemetry must never fail an attempt. The
 * Drizzle recorder is bound by the worker that owns a database handle, not by
 * the gateway — the gateway route group has no database edge at all (AD-8).
 */
const nullRecorder: InferenceRunRecorder = { record: async () => {} };

export const buildApplication = async (
  config: AppConfig,
  logger: AppLogger,
): Promise<ServerHandle> => {
  // H-07/#49 — which of these stores is shared depends on the state backend.
  //
  // `memory` (the default) keeps replay, at-most-once effects, rate limits and
  // idempotency per-process, and `assertSingleInstanceState` refuses to boot
  // more than one instance for exactly that reason. `redis` composes the shared
  // stores and lifts the refusal, because it removes the reason for it.
  // Payload bytes are ALWAYS process-local and ephemeral, in every backend.
  // There is deliberately no Redis counterpart: an instance that does not hold
  // the bytes fails closed rather than having them shipped to it (AD-8).
  const payloads = new InMemoryPayloadSource();
  // The state machine and its abort controller are inherently local — the
  // controller aborts a request THIS process is making. What crosses instances
  // is ownership and cancellation, which the registry below carries.
  const transfers = new TransferStateTracker();

  const shared = config.deployment.gatewayStateBackend === 'redis';
  const redis = shared ? await createRedisClient(config.redis.url) : null;

  // #49 — replay, at-most-once effects and rate limits are shared when the Redis
  // backend is composed, and per-process otherwise. `memory` is the default and
  // is refused above for more than one instance; `redis` lifts that refusal
  // because it removes the reason for it.
  //
  // An unreachable Redis throws RedisUnavailableError from every operation. It
  // does NOT fall back to the in-memory stores: that fallback would restore
  // at-most-once-per-process silently, at the moment the system is least
  // healthy, which is the hazard this backend exists to remove.
  const limiter =
    redis === null
      ? new InMemoryRateLimiter(RATE_LIMIT_RULES)
      : new RedisRateLimiter(redis, RATE_LIMIT_RULES);
  const effects =
    redis === null ? new InMemoryEffectStore() : new RedisEffectStore(redis, 'effect');
  // Separate keyspace from `effects`: a spent request ID and a spent transfer
  // effect are different identifiers with different answers (AD-21 vs AD-18).
  const requestIds =
    redis === null ? new InMemoryEffectStore() : new RedisEffectStore(redis, 'request-id');

  // #49 — cross-instance attempt ownership and cancellation.
  //
  // Null with the memory backend: one instance, so the local tracker already is
  // the whole truth. With redis this is what makes transfer ownership and
  // cancellation actually shared — without it the Redis backend would coordinate
  // replay, effects and rate limits while transfer state stayed per-process,
  // which is the appearance of distributed state rather than the fact of it.
  const attempts =
    redis === null ? null : new RedisAttemptRegistry(redis, TRANSFER_REGISTRY_TTL_SECONDS);
  // Identifies this process to the shared registry. Not a secret and not a
  // network address: it only has to differ between instances.
  const instanceId = process.env['APP_INSTANCE_ID'] ?? `${process.pid}-${randomUUID().slice(0, 8)}`;

  // Multi-instance Product idempotency (story 4.4 criterion 3).
  //
  // Only the status and the meal's id go to Redis. The body is a diary entry —
  // userId, dish names, times, nutrition — and Redis is a coordination store
  // whose retention rules were written for identifiers, not Product API user
  // content. Owner decision: keep the body in Postgres and re-read it on replay.
  const idempotency =
    redis === null
      ? new InMemoryIdempotencyStore()
      : new RedisIdempotencyStore(redis, {
          describe: (response: unknown) => {
            const envelope = response as { status?: unknown; body?: { id?: unknown } };
            const { status } = envelope;
            const id = envelope.body?.id;
            if (typeof status !== 'number' || typeof id !== 'string') return null;
            return { status, ref: id };
          },
          // The scoped key is `<userId>:<clientKey>` (routes/product), and a
          // userId is a uuid, so the first colon separates them. Re-reading
          // through the controller means the owner check runs again on the
          // replay path: a reference alone is not authority.
          rehydrate: async (ref: string, scopedKey: string) => {
            const separator = scopedKey.indexOf(':');
            if (separator <= 0) return null;
            const ownerId = scopedKey.slice(0, separator);
            try {
              const body = await mealController.getMeal(ref, ownerId);
              return { status: 201, body };
            } catch {
              // Deleted, or not this user's. Nothing to replay, and the store
              // turns that into a conflict rather than inventing a response.
              return null;
            }
          },
        });

  const idempotencyShared = redis !== null;

  // #49 — the definitive instance fence, asserted HERE because this is the first
  // point at which the answer is knowable: `loadConfig` runs before anything is
  // composed and defers the redis case to this call.
  //
  // Complete means every store the fence protects is shared. Derived from the
  // bindings themselves rather than from a flag, so it cannot drift from what
  // was actually built: adding a new per-process store that the fence protects
  // and forgetting it here is the mistake this guards, and the only way to make
  // this true is to bind the thing.
  //
  // H-04 — the fence lifts only when EVERY protected store is shared.
  //
  // Product idempotency was the last one that was not: `buildServer` received a
  // process-local store unconditionally, so two replicas could each accept the
  // same Idempotency-Key and each create a durable meal event. The gateway's own
  // state being shared was never enough — the fence protects the deployment, not
  // one surface of it.
  //
  // It is now bound to Redis alongside the rest, so the reason for the refusal is
  // gone. Derived from the bindings rather than declared: `idempotencyShared` is
  // true because a shared store was actually constructed, and the only way to
  // make it true is to construct one.
  assertSingleInstanceState(
    config.deployment.instanceCount,
    config.deployment.gatewayStateBackend,
    redis !== null && attempts !== null && idempotencyShared,
  );

  const profiles = TomlModelProfileSource.fromFile(
    resolve(process.cwd(), config.inference.modelProfilesPath),
  );
  // Fails closed: a deployment that cannot say which policy set it is running
  // must not verify consent against a guess.
  const configurationSnapshot = loadConfigurationSnapshot(
    resolve(process.cwd(), config.inference.configurationSnapshotPath),
  );

  // M9 — the roster in config/model-profiles.toml is currently OpenRouter
  // `:free` endpoints, marked TEMPORARY because the account has no credits.
  // Every consent receipt this gateway accepts asserts retentionDays = 0, and a
  // free tier is not contracted to honour that. The comment said TEMPORARY; this
  // makes it true.
  assertPaidRosterInProduction(
    profiles.current().profiles.map((profile) => profile.model),
    { NODE_ENV: config.nodeEnv, APP_ENVIRONMENT: config.env },
  );

  // Provider credentials are read once, here, and handed to the adapter. They
  // never touch the config object other modules hold (docs/09 §9.8).
  const openRouterKey = process.env['OPENROUTER_API_KEY'] ?? '';

  // STUB_INFERENCE exists so the client<->gateway path is exercisable without a
  // provider key. It is fenced to TEST_MODE: with attestation on, a stub can
  // never answer a real caller. Every other gate on the request stays real.
  const stubAllowed = assertStubFence(config.stubInference, config.auth.testMode);

  const providers = stubAllowed
    ? [new StubInferenceProvider()]
    : openRouterKey
      ? [
          new OpenRouterInferenceProvider(
            openRouterKey,
            { baseUrl: config.inference.openRouterBaseUrl, timeoutMs: 30_000 },
            payloads,
          ),
        ]
      : [];

  if (stubAllowed) {
    logger.warn(
      { telemetry: { environment: config.env, authMode: 'test-mode' } },
      'STUB inference provider active: candidates are fixtures, not model output',
    );
  }

  if (providers.length === 0) {
    logger.warn(
      { telemetry: { environment: config.env } },
      'no inference provider configured; remote attempts fail closed',
    );
  }

  const orchestrator = new TwoPassInferenceOrchestrator({
    providers,
    profiles,
    recorder: nullRecorder,
    environment: config.env,
  });

  // Test mode gets fixture markets only. Production ships an EMPTY policy set,
  // so every market is `unresolved` and fails closed until a named legal review
  // supplies a posture (AD-41 MP-1, gated by G-1 and G-4).
  const marketResolver = new StaticMarketPolicyResolver(
    config.auth.testMode ? FIXTURE_MARKET_POLICY : PRODUCTION_MARKET_POLICY,
    config.auth.testMode ? 'fixture-policy/1' : 'production-policy/pending-g1',
  );

  const testAuthenticator = new TestModeAuthenticator(config.env);

  // Surface B. The connection is constructed lazily and is not opened here, so
  // a database outage delays the first product request rather than preventing
  // the gateway from serving at all (AD-8: the gateway has no database edge).
  const { db, close: closeDatabase } = createDatabase(config.database);

  const meals = new DrizzleMealEventRepositoryAdapter(new DrizzleMealEventRepository(db));
  const users = new DrizzleUserRepositoryAdapter(new DrizzleUserRepository(db));

  const commandDeps: MealCommandDeps = {
    repository: meals,
    now: () => new Date(),
    uuid: () => randomUUID(),
  };

  const mealController = new MealControllerImpl({
    createMeal: new CreateMealEventHandler(commandDeps),
    correctMeal: new CorrectMealEventHandler(commandDeps),
    deleteMeal: new DeleteMealEventHandler(meals),
    getMeal: new GetMealEventHandler(meals),
    listMeals: new ListMealEventsHandler(meals),
  });

  // USDA catalog provider — credentials isolated, same pattern as OpenRouter.
  const usdaKey = process.env['USDA_API_KEY'] ?? '';
  const usdaProvider = new USDAProvider(usdaKey, USDA_CONFIG, 'SR Legacy 2021-10');
  const searchFood = new SearchFoodHandler(usdaProvider);
  // LookupFoodHandler is wired but not routed yet (catalog/items/:foodId is
  // still 501 until a future stage adds the item lookup endpoint).
  void LookupFoodHandler; // suppress unused-import lint if tree-shaken

  const productAuthenticator = new TestModeProductAuthenticator(testAuthenticator, {
    resolve: async (externalRef) => {
      const user = await users.ensureByExternalRef(externalRef);
      return user.ok ? user.value.id : null;
    },
  });

  const server = await buildServer({
    config,
    logger,
    gateway: {
      orchestrator,
      limiter,
      effects,
      requestIds,
      payloads,
      transfers,
      attempts,
      instanceId,
      resolveMarket: (marketId, modality, now) => marketResolver.resolve(marketId, modality, now),
      resolveMarketPolicy: (marketId, modality, now) =>
        marketResolver.resolve(marketId, modality, now),
      policyVersion: marketResolver.policyVersion,
      remoteInferenceEnabled: () => config.inference.remoteEnabled,
      supportedRetentionVersions: ['retention/2'],
      // H-06 — the versions this deployment actually runs, never the request's.
      // A client that supplied this list could declare its own stale
      // configuration supported, which is the check defeating itself.
      //
      // P-04 — this used to read `profiles.current().profileVersion`, the model
      // ROSTER version. A roster says which models to call; a
      // ConfigurationSnapshot is the policy set that produced the disclosure the
      // user was shown (AD-20, consent-receipt.toml). Binding one to the other
      // meant rotating a model invalidated every outstanding receipt, and it
      // made the real composition root refuse the released fixture's
      // `cfg/2026-07-26.4` while unit tests injected `['cfg/1']` and passed.
      supportedConfigurationVersions: configurationSnapshot.supported,
      now: () => new Date(),
      authenticate: async (request) => {
        if (config.auth.testMode) {
          return testAuthenticator.authenticate(request.headers.authorization);
        }
        // Production verification is wired when the attestation service exists.
        // Until then production REFUSES rather than silently accepting.
        throw new TestModeFenceError(
          'production authentication requires an attestation verifier (G-4)',
        );
      },
      issueTestSession: async () => {
        if (!config.auth.testMode) {
          throw new TestModeFenceError('session issuance requires attestation outside test mode');
        }
        const now = Date.now();
        return {
          accessToken: `test.${randomBytes(24).toString('base64url')}`,
          refreshToken: generateRefreshToken(),
          accessExpiresAt: new Date(now + config.auth.accessTtlSeconds * 1000).toISOString(),
          refreshExpiresAt: new Date(now + config.auth.refreshTtlSeconds * 1000).toISOString(),
          authMode: 'test-mode',
        };
      },
    },
    product: {
      meals: mealController,
      searchFood,
      idempotency,
      limiter,
      authenticate: async (request) => {
        if (config.auth.testMode) {
          return productAuthenticator.authenticate(request.headers.authorization);
        }
        throw new TestModeFenceError(
          'production authentication requires an attestation verifier (G-4)',
        );
      },
    },
  });

  // The database handle is owned here, so it is released here. A close that
  // only shuts the HTTP server leaves the pool draining after the process
  // believes it stopped.
  //
  // The Redis client is owned here too, and was not being released — the same
  // defect the comment above exists to prevent, one connection later. A held
  // socket keeps the event loop alive, so a process that had "stopped" would
  // not exit.
  return {
    ...server,
    close: async () => {
      await server.close();
      await closeDatabase();
      if (redis !== null) {
        await (redis as unknown as { quit: () => Promise<unknown> }).quit();
      }
    },
  };
};

const main = async (): Promise<void> => {
  let config: AppConfig;
  try {
    config = loadConfig(process.env);
  } catch (error) {
    if (error instanceof TestModeFenceError || error instanceof ConfigError) {
      process.stderr.write(`${error.name}: ${error.message}\n`);
      process.exit(1);
    }
    throw error;
  }

  const banner = testModeBanner({
    TEST_MODE: config.auth.testMode,
    NODE_ENV: config.nodeEnv,
    APP_ENVIRONMENT: config.env,
  });
  if (banner) process.stderr.write(`${banner}\n`);

  const logger = createLogger(config);

  // Kill-switch state is logged at startup so an operator can see it without
  // reading configuration (AD-24).
  logger.info(
    {
      telemetry: {
        environment: config.env,
        authMode: config.auth.testMode ? 'test-mode' : 'attested',
        outcome: config.inference.remoteEnabled ? 'remote-enabled' : 'remote-disabled',
      },
    },
    'mobile-api starting',
  );

  const server = await buildApplication(config, logger);
  const address = await server.listen();
  logger.info({ telemetry: { route: address } }, 'mobile-api listening');

  for (const signal of ['SIGINT', 'SIGTERM'] as const) {
    process.once(signal, () => {
      void server.close().then(() => process.exit(0));
    });
  }
};

if (process.env['NODE_ENV'] !== 'test') {
  void main();
}
