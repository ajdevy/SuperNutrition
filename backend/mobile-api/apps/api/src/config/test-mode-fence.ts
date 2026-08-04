import type { Env } from './env.schema.js';

/**
 * The `TEST_MODE` fence.
 *
 * The Stage 1 brief asks for sessions handed out without verification because
 * the client has no auth integration yet. AD-21 requires attested
 * installations, short-lived scoped tokens, and replay detection. A flag that
 * disables authentication is the exact failure AD-21 exists to prevent, so it
 * is constrained structurally rather than by convention (docs/01 §1.6,
 * docs/09 §9.2).
 *
 * This is a boot assertion, not a warning. A warning in a log is a control
 * nobody reads at 3am.
 */

export class TestModeFenceError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'TestModeFenceError';
  }
}

export interface FenceInput {
  readonly TEST_MODE: Env['TEST_MODE'];
  readonly NODE_ENV: Env['NODE_ENV'];
  readonly APP_ENVIRONMENT: Env['APP_ENVIRONMENT'];
}

/**
 * Throws when test mode is enabled in a production context. The caller exits
 * non-zero; it does not warn and continue.
 */
export const assertTestModeFence = (env: FenceInput): void => {
  if (!env.TEST_MODE) return;

  if (env.NODE_ENV === 'production') {
    throw new TestModeFenceError(
      'TEST_MODE cannot be enabled when NODE_ENV is production. Refusing to start.',
    );
  }

  if (env.APP_ENVIRONMENT === 'production') {
    throw new TestModeFenceError(
      'TEST_MODE cannot be enabled when APP_ENVIRONMENT is production. Refusing to start.',
    );
  }
};

/**
 * Markets designated for fixture use. A test-mode token may reach the transfer
 * path only for one of these, so test mode can never cause a real user photo to
 * reach a real provider regardless of environment.
 */
export const FIXTURE_MARKET_IDS = ['ZZ', 'XX'] as const;
export type FixtureMarketId = (typeof FIXTURE_MARKET_IDS)[number];

export const isFixtureMarket = (marketId: string): marketId is FixtureMarketId =>
  (FIXTURE_MARKET_IDS as readonly string[]).includes(marketId);

/**
 * Second half of the fence, applied per request at the gateway rather than at
 * boot: an attested token is unrestricted here; a test-mode token is permitted
 * only against a fixture market.
 */
export const mayAuthorizeRemoteTransfer = (
  authMode: 'attested' | 'test-mode',
  marketId: string,
): boolean => (authMode === 'attested' ? true : isFixtureMarket(marketId));

/**
 * The stub-provider fence.
 *
 * `STUB_INFERENCE` makes the gateway answer with fixture candidates so the
 * client transport can be exercised without a provider key. That is a inference
 * surface that returns food identifications nobody's model produced, so it is
 * bound to TEST_MODE by the same boot assertion as the rest of the fence: with
 * attestation on, a stub can never answer a real caller.
 *
 * Lives here rather than in `main.ts` because `main.ts` is excluded from the
 * coverage denominator, and an untested security fence is not a fence.
 */
export const assertStubFence = (stubRequested: boolean, testMode: boolean): boolean => {
  if (!stubRequested) return false;

  if (!testMode) {
    throw new TestModeFenceError(
      'STUB_INFERENCE requires TEST_MODE. Refusing to start with a stub inference provider.',
    );
  }

  return true;
};

/** Startup banner. Deliberately loud and persistent. */
export const testModeBanner = (env: FenceInput): string | null =>
  env.TEST_MODE
    ? [
        '',
        '  ============================================================',
        '   TEST_MODE IS ENABLED',
        '   Sessions are issued without installation attestation.',
        `   environment=${env.APP_ENVIRONMENT} nodeEnv=${env.NODE_ENV}`,
        '   Remote transfer is restricted to fixture markets.',
        '  ============================================================',
        '',
      ].join('\n')
    : null;
