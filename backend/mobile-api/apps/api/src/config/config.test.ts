import { describe, it, expect } from 'vitest';
import { loadConfig, ConfigError } from './index.js';
import { assertStubFence, TestModeFenceError } from './test-mode-fence.js';

const base: NodeJS.ProcessEnv = {
  NODE_ENV: 'development',
  APP_ENVIRONMENT: 'development',
  DATABASE_URL: 'postgres://user:pass@localhost:5432/supernutrition',
  REDIS_URL: 'redis://localhost:6379',
  JWT_PRIVATE_KEY: 'a'.repeat(64),
  JWT_PUBLIC_KEY: 'b'.repeat(64),
  JWT_ISSUER: 'supernutrition-mobile-api',
};

describe('loadConfig', () => {
  it('parses a valid environment into typed accessors', () => {
    const config = loadConfig(base);
    expect(config.env).toBe('development');
    expect(config.server.port).toBe(3000);
    expect(config.auth.testMode).toBe(false);
    expect(config.database.maxConnections).toBe(10);
    expect(config.inference.remoteEnabled).toBe(true);
  });

  it('rejects a missing required variable', () => {
    const { DATABASE_URL: _omitted, ...without } = base;
    expect(() => loadConfig(without)).toThrow(ConfigError);
  });

  it('rejects a malformed URL', () => {
    expect(() => loadConfig({ ...base, DATABASE_URL: 'not-a-url' })).toThrow(ConfigError);
  });

  // Failing at boot beats failing at the first user request (docs/09 §9.8).
  it('rejects a placeholder secret', () => {
    expect(() => loadConfig({ ...base, JWT_PRIVATE_KEY: 'changeme' })).toThrow(ConfigError);
    expect(() => loadConfig({ ...base, JWT_PUBLIC_KEY: 'placeholder' })).toThrow(ConfigError);
  });

  it('rejects an out-of-range port', () => {
    expect(() => loadConfig({ ...base, PORT: '70000' })).toThrow(ConfigError);
  });

  it('coerces boolean-ish environment values', () => {
    expect(loadConfig({ ...base, TEST_MODE: 'true' }).auth.testMode).toBe(true);
    expect(loadConfig({ ...base, TEST_MODE: '1' }).auth.testMode).toBe(true);
    expect(loadConfig({ ...base, TEST_MODE: 'false' }).auth.testMode).toBe(false);
    expect(loadConfig({ ...base, TEST_MODE: '0' }).auth.testMode).toBe(false);
  });

  it('applies the kill switch from configuration', () => {
    expect(loadConfig({ ...base, REMOTE_INFERENCE_ENABLED: 'false' }).inference.remoteEnabled).toBe(
      false,
    );
  });

  // The fence runs inside loadConfig, so no caller can construct a config that
  // bypasses it.
  it('enforces the TEST_MODE fence during load', () => {
    expect(() => loadConfig({ ...base, TEST_MODE: 'true', APP_ENVIRONMENT: 'production' })).toThrow(
      TestModeFenceError,
    );
  });

  it('reports every invalid field in one error', () => {
    try {
      loadConfig({ ...base, PORT: '0', DATABASE_URL: 'bad' });
      throw new Error('expected a ConfigError');
    } catch (error) {
      expect(error).toBeInstanceOf(ConfigError);
      expect((error as ConfigError).message).toContain('PORT');
      expect((error as ConfigError).message).toContain('DATABASE_URL');
    }
  });

  it('omits provider credentials from the config object', () => {
    const config = loadConfig({ ...base, OPENROUTER_API_KEY: 'sk-or-secret-value-123456' });
    expect(JSON.stringify(config)).not.toContain('sk-or-secret-value-123456');
  });
});

describe('STUB_INFERENCE is parsed like every other flag', () => {
  // main.ts read `process.env['STUB_INFERENCE'] === 'true'` directly, bypassing
  // this schema entirely. TEST_MODE accepts true/1/yes; STUB_INFERENCE accepted
  // the single spelling `true`, so `STUB_INFERENCE=1` — the spelling that works
  // for the flag beside it — silently meant "off" and the operator got real
  // providers while believing they had stubs. A typo in the name behaved the
  // same way: indistinguishable from deliberately disabling it.
  it('accepts the same spellings as the flags beside it', () => {
    for (const on of ['true', '1', 'yes']) {
      expect(loadConfig({ ...base, TEST_MODE: 'true', STUB_INFERENCE: on }).stubInference).toBe(
        true,
      );
    }
    for (const off of ['false', '0', 'no']) {
      expect(loadConfig({ ...base, TEST_MODE: 'true', STUB_INFERENCE: off }).stubInference).toBe(
        false,
      );
    }
  });

  it('defaults to off when unset', () => {
    expect(loadConfig(base).stubInference).toBe(false);
  });

  // Unparseable is not "off". A value nobody meant is a mistake to report, not
  // one to resolve silently in either direction.
  it('refuses a value it cannot read rather than guessing', () => {
    expect(() => loadConfig({ ...base, TEST_MODE: 'true', STUB_INFERENCE: 'TRUE' })).toThrow(
      ConfigError,
    );
    expect(() => loadConfig({ ...base, TEST_MODE: 'true', STUB_INFERENCE: 'on' })).toThrow(
      ConfigError,
    );
  });

  // The fence is unchanged and stays where it is tested: parsing decides what
  // the value IS, `assertStubFence` decides whether it is allowed.
  //
  // This is the seam the drift broke. `STUB_INFERENCE=1` outside TEST_MODE used
  // to reach the fence as `false`, so the fence saw no stub requested, raised
  // nothing, and the service came up on real providers while the operator
  // believed they were stubbed. The fence was never wrong — it was handed a
  // misparsed value. Now it gets what the operator wrote and refuses to boot.
  it('hands the fence what the operator actually wrote', () => {
    const parsed = loadConfig({ ...base, TEST_MODE: 'false', STUB_INFERENCE: '1' });

    expect(parsed.stubInference).toBe(true);
    expect(() => assertStubFence(parsed.stubInference, parsed.auth.testMode)).toThrow(
      TestModeFenceError,
    );
  });

  it('still permits a stub inside TEST_MODE', () => {
    const parsed = loadConfig({ ...base, TEST_MODE: 'true', STUB_INFERENCE: 'yes' });

    expect(assertStubFence(parsed.stubInference, parsed.auth.testMode)).toBe(true);
  });
});
