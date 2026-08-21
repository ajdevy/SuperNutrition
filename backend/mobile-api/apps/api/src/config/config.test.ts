import { describe, it, expect } from 'vitest';
import { loadConfig, ConfigError } from './index.js';
import {
  assertSingleInstanceState,
  assertStubFence,
  TestModeFenceError,
} from './test-mode-fence.js';

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

describe('H-07 — single-instance operation is declared, not assumed', () => {
  // Replay detection, at-most-once effects, rate limits, idempotency and
  // transfer state are all per-process: main.ts builds InMemory* stores and
  // REDIS_URL, though required, is never composed into any of them. A second
  // replica therefore does not share any of it, and a restart forgets all of
  // it — silently, because nothing in the configuration says so.
  //
  // Until the distributed implementation lands, running more than one instance
  // is refused at boot rather than discovered from a duplicated provider call.
  it('accepts the default single-instance deployment', () => {
    expect(loadConfig(base).deployment.instanceCount).toBe(1);
  });

  it('refuses to start with more than one instance while state is per-process', () => {
    // The same fence class that refuses an unsafe test-mode boot: this is the
    // same kind of refusal, at the same moment, for the same reason.
    expect(() => loadConfig({ ...base, APP_INSTANCE_COUNT: '2' })).toThrow(TestModeFenceError);
    expect(() => loadConfig({ ...base, APP_INSTANCE_COUNT: '2' })).toThrow(/per-process/);
  });

  it('refuses a nonsensical instance count rather than guessing', () => {
    expect(() => loadConfig({ ...base, APP_INSTANCE_COUNT: '0' })).toThrow(ConfigError);
    expect(() => loadConfig({ ...base, APP_INSTANCE_COUNT: 'many' })).toThrow(ConfigError);
  });
});

describe('#49 review — the fence lifts only when shared state is COMPLETE', () => {
  /**
   * The first version lifted on `GATEWAY_STATE_BACKEND=redis` alone, while
   * transfer ownership and cancellation were still process-local — the registry
   * existed but was composed nowhere. Declaring `redis` therefore permitted
   * exactly the multi-instance deployment the fence exists to prevent.
   *
   * The appearance of distributed state is worse than none, because it removes
   * the guard while leaving the hazard.
   */
  it('still refuses multi-instance on the memory backend', () => {
    expect(() => assertSingleInstanceState(2, 'memory', true)).toThrow(TestModeFenceError);
  });

  it('refuses multi-instance on redis when the shared state is incomplete', () => {
    expect(() => assertSingleInstanceState(2, 'redis', false)).toThrow(TestModeFenceError);
    expect(() => assertSingleInstanceState(2, 'redis', false)).toThrow(/process-local/);
  });

  it('permits multi-instance on redis only when every store is shared', () => {
    expect(() => assertSingleInstanceState(2, 'redis', true)).not.toThrow();
  });

  it('defers the redis case when the caller cannot yet know', () => {
    // loadConfig runs before anything is composed. Answering `false` there would
    // refuse a legitimate multi-instance boot before the stores it asks about
    // have been built, so it defers to the composition root.
    expect(() => assertSingleInstanceState(2, 'redis', undefined)).not.toThrow();
  });

  it('permits a single instance in every combination', () => {
    for (const backend of ['memory', 'redis'] as const) {
      for (const complete of [true, false, undefined]) {
        expect(() => assertSingleInstanceState(1, backend, complete)).not.toThrow();
      }
    }
  });
});

describe('GW-F1 — the replicated posture must state what it trusts for a client IP', () => {
  const replicated = { ...base, GATEWAY_STATE_BACKEND: 'redis' };

  it('refuses the redis backend with no TRUSTED_PROXY', () => {
    // Not a default, because both defaults are wrong. Trusting nothing makes
    // `request.ip` the balancer for every caller, collapsing the 300/60s IP
    // layer into one global bucket; trusting everything makes the header
    // attacker-chosen.
    expect(() => loadConfig(replicated)).toThrow(ConfigError);
    expect(() => loadConfig(replicated)).toThrow(/TRUSTED_PROXY/);
  });

  it('accepts "none" as a deliberate statement that there is no proxy', () => {
    expect(loadConfig({ ...replicated, TRUSTED_PROXY: 'none' }).server.trustProxy).toBe(false);
  });

  it('reads a hop count as a number, so Fastify counts from the right', () => {
    expect(loadConfig({ ...replicated, TRUSTED_PROXY: '2' }).server.trustProxy).toBe(2);
  });

  it('passes an address or subnet list through unchanged', () => {
    expect(
      loadConfig({ ...replicated, TRUSTED_PROXY: '10.0.0.0/8,192.168.1.1' }).server.trustProxy,
    ).toBe('10.0.0.0/8,192.168.1.1');
  });

  it('never resolves to blanket trust, whatever is configured', () => {
    // `true` would make X-Forwarded-For attacker-chosen: a caller sets any
    // address per request, so the shared bucket becomes a per-forged-header
    // limit. Strictly worse than the problem being fixed, so it is unreachable.
    for (const value of ['true', 'yes', '1', 'all', '*', '0', '-1']) {
      const resolved = loadConfig({ ...replicated, TRUSTED_PROXY: value }).server.trustProxy;
      expect(resolved, `TRUSTED_PROXY=${value} produced blanket trust`).not.toBe(true);
    }
  });

  it('leaves the single-instance backend free to omit it', () => {
    // The memory backend refuses APP_INSTANCE_COUNT > 1 already, so there is no
    // balancer to reason about and no silent wrong answer to prevent.
    expect(loadConfig(base).server.trustProxy).toBe(false);
  });
});
