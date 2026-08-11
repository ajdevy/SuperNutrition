import { randomBytes, randomUUID } from 'node:crypto';
import { resolve } from 'node:path';
import {
  createDatabase,
  DrizzleMealEventRepository,
  DrizzleUserRepository,
} from '@supernutrition/database';
import {
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
  StaticMarketPolicyResolver,
  TestModeAuthenticator,
  TestModeProductAuthenticator,
  generateRefreshToken,
  type RateLimitRule,
} from './security/index.js';
import { StubInferenceProvider } from './modules/inference/infrastructure/stub/stub.provider.js';
import {
  InMemoryPayloadSource,
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
  const payloads = new InMemoryPayloadSource();
  const transfers = new TransferStateTracker();
  const limiter = new InMemoryRateLimiter(RATE_LIMIT_RULES);
  const effects = new InMemoryEffectStore();
  // Separate keyspace from `effects`: a spent request ID and a spent transfer
  // effect are different identifiers with different answers (AD-21 vs AD-18).
  const requestIds = new InMemoryEffectStore();

  const profiles = TomlModelProfileSource.fromFile(
    resolve(process.cwd(), config.inference.modelProfilesPath),
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
      resolveMarket: (marketId, modality, now) => marketResolver.resolve(marketId, modality, now),
      resolveMarketPolicy: (marketId, modality, now) =>
        marketResolver.resolve(marketId, modality, now),
      policyVersion: marketResolver.policyVersion,
      remoteInferenceEnabled: () => config.inference.remoteEnabled,
      supportedRetentionVersions: ['retention/2'],
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
      idempotency: new InMemoryIdempotencyStore(),
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
  return {
    ...server,
    close: async () => {
      await server.close();
      await closeDatabase();
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
