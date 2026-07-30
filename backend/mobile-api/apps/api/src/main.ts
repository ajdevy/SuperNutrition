import { randomBytes } from 'node:crypto';
import { resolve } from 'node:path';
import {
  loadConfig,
  testModeBanner,
  TestModeFenceError,
  ConfigError,
  type AppConfig,
} from './config/index.js';
import { createLogger, type AppLogger } from './logging/index.js';
import { buildServer, type ServerHandle } from './server.js';
import {
  FIXTURE_MARKET_POLICY,
  InMemoryEffectStore,
  InMemoryRateLimiter,
  PRODUCTION_MARKET_POLICY,
  StaticMarketPolicyResolver,
  TestModeAuthenticator,
  generateOpaqueId,
  generateRefreshToken,
  type RateLimitRule,
} from './security/index.js';
import {
  InMemoryPayloadSource,
  OpenRouterInferenceProvider,
  TomlModelProfileSource,
  TwoPassInferenceOrchestrator,
  type InferenceRunRecorder,
} from './modules/inference/index.js';
import { TransferStateTracker } from './routes/gateway/index.js';

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

  const profiles = TomlModelProfileSource.fromFile(
    resolve(process.cwd(), config.inference.modelProfilesPath),
  );

  // Provider credentials are read once, here, and handed to the adapter. They
  // never touch the config object other modules hold (docs/09 §9.8).
  const openRouterKey = process.env['OPENROUTER_API_KEY'] ?? '';
  const providers = openRouterKey
    ? [
        new OpenRouterInferenceProvider(
          openRouterKey,
          { baseUrl: config.inference.openRouterBaseUrl, timeoutMs: 8000 },
          payloads,
        ),
      ]
    : [];

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

  return buildServer({
    config,
    logger,
    gateway: {
      orchestrator,
      limiter,
      effects,
      payloads,
      transfers,
      resolveMarket: (marketId, modality, now) => marketResolver.resolve(marketId, modality, now),
      resolveMarketPolicy: (marketId, modality, now) =>
        marketResolver.resolve(marketId, modality, now),
      policyVersion: marketResolver.policyVersion,
      remoteInferenceEnabled: () => config.inference.remoteEnabled,
      supportedRetentionVersions: ['retention/2'],
      now: () => new Date(),
      requestIdFactory: () => generateOpaqueId('req'),
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
  });
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
