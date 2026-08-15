import { z } from 'zod';

/**
 * Typed environment schema. Validated once at startup so a missing or
 * malformed secret fails at boot rather than at the first user request
 * (docs/09 §9.8).
 */
export const EnvironmentSchema = z.enum(['development', 'staging', 'production']);
export type Environment = z.infer<typeof EnvironmentSchema>;

const booleanFromEnv = z
  .union([z.boolean(), z.enum(['true', 'false', '1', '0'])])
  .transform((v) => v === true || v === 'true' || v === '1');

const port = z.coerce.number().int().min(1).max(65_535);
const positiveInt = z.coerce.number().int().positive();

const PLACEHOLDER_SECRETS = new Set([
  '',
  'changeme',
  'change-me',
  'placeholder',
  'your-key-here',
  'todo',
]);

const secret = (minLength: number) =>
  z
    .string()
    .min(minLength)
    .refine((v) => !PLACEHOLDER_SECRETS.has(v.toLowerCase()), {
      message: 'secret is a placeholder value',
    });

export const EnvSchema = z.object({
  NODE_ENV: z.enum(['development', 'test', 'production']).default('development'),
  APP_ENVIRONMENT: EnvironmentSchema,
  PORT: port.default(3000),
  HOST: z.string().default('0.0.0.0'),

  DATABASE_URL: z.string().url(),
  DATABASE_MAX_CONNECTIONS: positiveInt.default(10),
  DATABASE_IDLE_TIMEOUT_SECONDS: positiveInt.default(30),
  DATABASE_CONNECT_TIMEOUT_SECONDS: positiveInt.default(10),

  REDIS_URL: z.string().url(),
  /**
   * H-07 — how many instances of this service are being run.
   *
   * Replay detection, at-most-once effects, rate limits, idempotency and
   * transfer state are all per-process today: main.ts composes InMemory* stores
   * and REDIS_URL, though required, is wired into none of them. A second replica
   * shares none of that state, so a request id spent on instance A is unspent on
   * instance B and the provider can be called twice for one effect.
   *
   * That is a safe deployment of one instance and an unsafe deployment of two,
   * and nothing said which one you had. Declaring it makes the boundary explicit
   * and lets the boot refuse the unsafe case instead of leaving it to be
   * discovered from a duplicated provider call.
   */
  APP_INSTANCE_COUNT: z.coerce.number().int().positive().default(1),
  /**
   * #49 — where the gateway's shared state lives.
   *
   * `memory` (default) is the single-instance composition: every store is
   * per-process, and `APP_INSTANCE_COUNT > 1` is refused. `redis` composes the
   * Redis-backed stores so replay, at-most-once effects, rate limits, attempt
   * ownership and cancellation are shared, and lifts that refusal.
   *
   * Explicit rather than inferred from REDIS_URL being set: a URL is present in
   * every environment, and switching the correctness model of the service on
   * whether a variable happens to be populated is exactly the kind of silent
   * decision this work exists to remove.
   */
  GATEWAY_STATE_BACKEND: z.enum(['memory', 'redis']).default('memory'),
  /**
   * Read directly from process.env in main.ts, so it never went through this
   * schema: `STUB_INFERENCE=TRUE`, `=1` and `=yes` all silently meant false,
   * and a typo in the variable name was indistinguishable from disabling it.
   * Declared here so the parse is one place and the accepted spellings are
   * visible. Still fenced to TEST_MODE by assertStubFence — this only decides
   * what the value IS, never whether it is allowed.
   */
  STUB_INFERENCE: z
    .enum(['true', 'false', '1', '0', 'yes', 'no'])
    .default('false')
    .transform((value) => value === 'true' || value === '1' || value === 'yes'),

  JWT_PRIVATE_KEY: secret(32),
  JWT_PUBLIC_KEY: secret(32),
  JWT_ISSUER: z.string().min(1),
  JWT_ACCESS_TTL_SECONDS: positiveInt.default(900),
  JWT_REFRESH_TTL_SECONDS: positiveInt.default(2_592_000),

  /**
   * Hands out sessions without attestation. Fenced by `assertTestModeFence`:
   * the process refuses to start with this enabled in production, and
   * test-mode tokens cannot authorize a real remote transfer (docs/09 §9.2).
   */
  TEST_MODE: booleanFromEnv.default(false),

  OPENROUTER_API_KEY: secret(16).optional(),
  OPENROUTER_BASE_URL: z.string().url().default('https://openrouter.ai/api/v1'),
  USDA_API_KEY: secret(16).optional(),

  /** Signed ConfigurationSnapshot declaration (AD-20), separate from the model roster. */
  CONFIGURATION_SNAPSHOT_PATH: z.string().min(1).default('./config/configuration-snapshot.toml'),
  MODEL_PROFILES_PATH: z.string().default('./config/model-profiles.toml'),

  /** Operator-controlled provider kill switch (AD-24). Effective without deploy. */
  REMOTE_INFERENCE_ENABLED: booleanFromEnv.default(true),

  LOG_LEVEL: z.enum(['fatal', 'error', 'warn', 'info', 'debug', 'trace']).default('info'),
});

export type Env = z.infer<typeof EnvSchema>;
