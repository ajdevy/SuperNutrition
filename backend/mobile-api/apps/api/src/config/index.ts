import { EnvSchema, type Env, type Environment } from './env.schema.js';
import {
  assertSingleInstanceState,
  assertTestModeFence,
  testModeBanner,
} from './test-mode-fence.js';

export interface AppConfig {
  readonly env: Environment;
  readonly nodeEnv: Env['NODE_ENV'];
  readonly server: {
    readonly host: string;
    readonly port: number;
    /**
     * What Fastify is told to trust for `request.ip` (GW-F1).
     *
     * `false` = trust nothing, the socket peer is the client. A number is a hop
     * count from the right of `X-Forwarded-For`; a string is an explicit
     * address or subnet list. Never `true`.
     */
    readonly trustProxy: false | number | string;
  };
  readonly database: {
    readonly url: string;
    readonly maxConnections: number;
    readonly idleTimeoutSeconds: number;
    readonly connectTimeoutSeconds: number;
  };
  readonly redis: { readonly url: string };
  /** H-07 — declared instance count; >1 is refused while state is per-process. */
  readonly deployment: {
    readonly instanceCount: number;
    readonly gatewayStateBackend: 'memory' | 'redis';
  };
  readonly auth: {
    readonly issuer: string;
    readonly accessTtlSeconds: number;
    readonly refreshTtlSeconds: number;
    readonly testMode: boolean;
  };
  readonly inference: {
    readonly remoteEnabled: boolean;
    readonly modelProfilesPath: string;
    readonly configurationSnapshotPath: string;
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
/**
 * `TRUSTED_PROXY` as Fastify's `trustProxy`.
 *
 * `true` is deliberately unreachable: it trusts any `X-Forwarded-For` a caller
 * sends, so the IP rate limit becomes a per-forged-header limit — worse than
 * the single shared bucket it was meant to fix.
 */
const resolveTrustProxy = (raw: string | undefined): false | number | string => {
  if (raw === undefined || raw === 'none') return false;
  const hops = Number(raw);
  if (Number.isInteger(hops) && hops > 0) return hops;
  return raw;
};

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
  assertSingleInstanceState(env.APP_INSTANCE_COUNT, env.GATEWAY_STATE_BACKEND);

  // GW-F1 — the replicated posture must state what it trusts for a client IP.
  //
  // `redis` is the backend a multi-instance deployment uses, and a multi-instance
  // deployment sits behind a balancer. Left unset there, `request.ip` is the
  // balancer for every caller: the IP rate limit becomes one global bucket, so a
  // single noisy source produces fleet-wide 429s and the unauthenticated session
  // endpoint is throttled globally rather than per client.
  //
  // Refused rather than defaulted, because both defaults are wrong: trusting
  // nothing gives the shared bucket, and trusting everything makes the header
  // attacker-chosen. `TRUSTED_PROXY=none` says "no balancer" deliberately.
  if (env.GATEWAY_STATE_BACKEND === 'redis' && env.TRUSTED_PROXY === undefined) {
    throw new ConfigError(
      'TRUSTED_PROXY must be set when GATEWAY_STATE_BACKEND=redis. ' +
        'Behind a load balancer, request.ip is the balancer for every caller and the ' +
        'IP rate limit collapses into one shared bucket. Set a hop count, an explicit ' +
        'address or subnet list, or "none" if this deployment has no proxy in front of it.',
    );
  }

  return {
    env: env.APP_ENVIRONMENT,
    nodeEnv: env.NODE_ENV,
    server: {
      host: env.HOST,
      port: env.PORT,
      trustProxy: resolveTrustProxy(env.TRUSTED_PROXY),
    },
    database: {
      url: env.DATABASE_URL,
      maxConnections: env.DATABASE_MAX_CONNECTIONS,
      idleTimeoutSeconds: env.DATABASE_IDLE_TIMEOUT_SECONDS,
      connectTimeoutSeconds: env.DATABASE_CONNECT_TIMEOUT_SECONDS,
    },
    redis: { url: env.REDIS_URL },
    deployment: {
      instanceCount: env.APP_INSTANCE_COUNT,
      gatewayStateBackend: env.GATEWAY_STATE_BACKEND,
    },
    auth: {
      issuer: env.JWT_ISSUER,
      accessTtlSeconds: env.JWT_ACCESS_TTL_SECONDS,
      refreshTtlSeconds: env.JWT_REFRESH_TTL_SECONDS,
      testMode: env.TEST_MODE,
    },
    inference: {
      remoteEnabled: env.REMOTE_INFERENCE_ENABLED,
      modelProfilesPath: env.MODEL_PROFILES_PATH,
      configurationSnapshotPath: env.CONFIGURATION_SNAPSHOT_PATH,
      openRouterBaseUrl: env.OPENROUTER_BASE_URL,
    },
    logging: { level: env.LOG_LEVEL },
    stubInference: env.STUB_INFERENCE,
  };
};

export { assertSingleInstanceState } from './test-mode-fence.js';
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
