import { EnvSchema, type Env, type Environment } from './env.schema.js';
import {
  assertSingleInstanceState,
  assertTestModeFence,
  testModeBanner,
} from './test-mode-fence.js';

export interface AppConfig {
  readonly env: Environment;
  readonly nodeEnv: Env['NODE_ENV'];
  readonly server: { readonly host: string; readonly port: number };
  readonly database: {
    readonly url: string;
    readonly maxConnections: number;
    readonly idleTimeoutSeconds: number;
    readonly connectTimeoutSeconds: number;
  };
  readonly redis: { readonly url: string };
  /** H-07 — declared instance count; >1 is refused while state is per-process. */
  readonly deployment: { readonly instanceCount: number };
  readonly auth: {
    readonly issuer: string;
    readonly accessTtlSeconds: number;
    readonly refreshTtlSeconds: number;
    readonly testMode: boolean;
  };
  readonly inference: {
    readonly remoteEnabled: boolean;
    readonly modelProfilesPath: string;
    readonly openRouterBaseUrl: string;
  };
  readonly logging: { readonly level: Env['LOG_LEVEL'] };
  /** Parsed by EnvSchema; still fenced to TEST_MODE at boot. */
  readonly stubInference: boolean;
}

export class ConfigError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'ConfigError';
  }
}

/**
 * Typed accessors over a validated environment. Nothing downstream reads
 * `process.env` directly, so every setting has one parse and one shape.
 *
 * Provider credentials are deliberately absent from this object: the
 * OpenRouter key is read once into the adapter's closure and is not reachable
 * from a config object other modules hold (docs/09 §9.8).
 */
export const loadConfig = (source: NodeJS.ProcessEnv): AppConfig => {
  const parsed = EnvSchema.safeParse(source);
  if (!parsed.success) {
    const issues = parsed.error.issues
      .map((i) => `${i.path.join('.') || '(root)'}: ${i.message}`)
      .join('; ');
    throw new ConfigError(`Invalid environment: ${issues}`);
  }

  const env = parsed.data;
  assertTestModeFence(env);
  // H-07 — a multi-instance deployment is refused while state is per-process.
  assertSingleInstanceState(env.APP_INSTANCE_COUNT);

  return {
    env: env.APP_ENVIRONMENT,
    nodeEnv: env.NODE_ENV,
    server: { host: env.HOST, port: env.PORT },
    database: {
      url: env.DATABASE_URL,
      maxConnections: env.DATABASE_MAX_CONNECTIONS,
      idleTimeoutSeconds: env.DATABASE_IDLE_TIMEOUT_SECONDS,
      connectTimeoutSeconds: env.DATABASE_CONNECT_TIMEOUT_SECONDS,
    },
    redis: { url: env.REDIS_URL },
    deployment: { instanceCount: env.APP_INSTANCE_COUNT },
    auth: {
      issuer: env.JWT_ISSUER,
      accessTtlSeconds: env.JWT_ACCESS_TTL_SECONDS,
      refreshTtlSeconds: env.JWT_REFRESH_TTL_SECONDS,
      testMode: env.TEST_MODE,
    },
    inference: {
      remoteEnabled: env.REMOTE_INFERENCE_ENABLED,
      modelProfilesPath: env.MODEL_PROFILES_PATH,
      openRouterBaseUrl: env.OPENROUTER_BASE_URL,
    },
    logging: { level: env.LOG_LEVEL },
    stubInference: env.STUB_INFERENCE,
  };
};

export { EnvSchema, assertTestModeFence, testModeBanner };
export type { Env, Environment };
export {
  TestModeFenceError,
  assertStubFence,
  assertPaidRosterInProduction,
  isFixtureMarket,
  mayAuthorizeRemoteTransfer,
  FIXTURE_MARKET_IDS,
} from './test-mode-fence.js';
