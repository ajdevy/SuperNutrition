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

  MODEL_PROFILES_PATH: z.string().default('./config/model-profiles.toml'),

  /** Operator-controlled provider kill switch (AD-24). Effective without deploy. */
  REMOTE_INFERENCE_ENABLED: booleanFromEnv.default(true),

  LOG_LEVEL: z.enum(['fatal', 'error', 'warn', 'info', 'debug', 'trace']).default('info'),
});

export type Env = z.infer<typeof EnvSchema>;
