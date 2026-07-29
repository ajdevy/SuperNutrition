import { describe, it, expect } from 'vitest';
import {
  assertTestModeFence,
  TestModeFenceError,
  isFixtureMarket,
  mayAuthorizeRemoteTransfer,
  testModeBanner,
  type FenceInput,
} from './test-mode-fence.js';

const fence = (over: Partial<FenceInput> = {}): FenceInput => ({
  TEST_MODE: false,
  NODE_ENV: 'development',
  APP_ENVIRONMENT: 'development',
  ...over,
});

describe('TEST_MODE fence (AD-21, docs/09 §9.2)', () => {
  it('permits test mode in development', () => {
    expect(() =>
      assertTestModeFence(fence({ TEST_MODE: true, APP_ENVIRONMENT: 'development' })),
    ).not.toThrow();
  });

  it('permits test mode in staging', () => {
    expect(() =>
      assertTestModeFence(fence({ TEST_MODE: true, APP_ENVIRONMENT: 'staging' })),
    ).not.toThrow();
  });

  // The boot assertion: a warning in a log is a control nobody reads at 3am.
  it('refuses to start with TEST_MODE and NODE_ENV=production', () => {
    expect(() => assertTestModeFence(fence({ TEST_MODE: true, NODE_ENV: 'production' }))).toThrow(
      TestModeFenceError,
    );
  });

  it('refuses to start with TEST_MODE and APP_ENVIRONMENT=production', () => {
    expect(() =>
      assertTestModeFence(fence({ TEST_MODE: true, APP_ENVIRONMENT: 'production' })),
    ).toThrow(/Refusing to start/);
  });

  it('is inert when test mode is off, in every environment', () => {
    for (const env of ['development', 'staging', 'production'] as const) {
      expect(() =>
        assertTestModeFence(
          fence({ TEST_MODE: false, APP_ENVIRONMENT: env, NODE_ENV: 'production' }),
        ),
      ).not.toThrow();
    }
  });
});

describe('remote transfer authorization', () => {
  it('lets an attested token reach any market', () => {
    expect(mayAuthorizeRemoteTransfer('attested', 'DE')).toBe(true);
    expect(mayAuthorizeRemoteTransfer('attested', 'ZZ')).toBe(true);
  });

  // The second half of the fence: test mode can never cause a real user photo
  // to reach a real provider, regardless of environment.
  it('blocks a test-mode token against a real market', () => {
    expect(mayAuthorizeRemoteTransfer('test-mode', 'DE')).toBe(false);
    expect(mayAuthorizeRemoteTransfer('test-mode', 'US')).toBe(false);
    expect(mayAuthorizeRemoteTransfer('test-mode', 'GB')).toBe(false);
  });

  it('permits a test-mode token only against a fixture market', () => {
    expect(mayAuthorizeRemoteTransfer('test-mode', 'ZZ')).toBe(true);
    expect(isFixtureMarket('ZZ')).toBe(true);
    expect(isFixtureMarket('DE')).toBe(false);
  });
});

describe('startup banner', () => {
  it('is emitted when test mode is on', () => {
    const banner = testModeBanner(fence({ TEST_MODE: true }));
    expect(banner).toContain('TEST_MODE IS ENABLED');
    expect(banner).toContain('environment=development');
  });

  it('is absent when test mode is off', () => {
    expect(testModeBanner(fence({ TEST_MODE: false }))).toBeNull();
  });
});
