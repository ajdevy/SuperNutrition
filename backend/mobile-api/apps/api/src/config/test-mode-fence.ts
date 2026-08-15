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

/**
 * The zero-cost-roster fence.
 *
 * config/model-profiles.toml currently names OpenRouter `:free` models, marked
 * TEMPORARY because the account has no credits. Free-tier endpoints are a
 * different bargain from paid ones — providers commonly reserve the right to
 * train on or retain what is sent to them — and every consent receipt this
 * gateway accepts asserts `retentionDays = 0`. Shipping that roster to
 * production would make the receipt a statement the gateway cannot support,
 * which is the same class of problem as the cancellation record that claimed
 * nothing had been sent.
 *
 * A comment saying TEMPORARY is not a control. This is, and like the rest of the
 * fence it is a boot assertion rather than a warning.
 */
export const assertPaidRosterInProduction = (
  modelIds: readonly string[],
  environment: { NODE_ENV: Env['NODE_ENV']; APP_ENVIRONMENT: Env['APP_ENVIRONMENT'] },
): void => {
  const isProduction =
    environment.NODE_ENV === 'production' || environment.APP_ENVIRONMENT === 'production';
  if (!isProduction) return;

  const free = modelIds.filter((id) => id.endsWith(':free'));
  if (free.length === 0) return;

  throw new TestModeFenceError(
    `Zero-cost model endpoints cannot be used in production: ${[...new Set(free)].sort().join(', ')}. ` +
      'Every accepted consent receipt asserts retentionDays = 0, which a free-tier ' +
      'endpoint is not contracted to honour. Refusing to start.',
  );
};

/**
 * H-07 — refuse to boot a second instance while state is per-process.
 *
 * Replay detection, at-most-once effects, rate limits, idempotency and transfer
 * state all live in memory: `main.ts` composes the InMemory* stores, and
 * REDIS_URL is required by the schema but wired into none of them. A second
 * replica shares none of it, so a request id spent on instance A is unspent on
 * instance B, an effect claimed on one can be claimed again on the other, and a
 * rate limit is per-process rather than per-installation.
 *
 * The failure that produces is a duplicated provider call: a user's photo sent
 * twice, billed twice, for one attempt they made once. It is silent, it depends
 * on which instance a request lands on, and AD-18 exists to prevent exactly it.
 *
 * P-01 — be exact about what this is. It is a SELF-REPORT, not a deployment
 * safety boundary. Each process reads its own environment and refuses only a
 * value that says "greater than one". Two containers can each declare `1`, or
 * omit the variable entirely, and both boot happily and both call the provider
 * for the same request. A restart still forgets every claim.
 *
 * So this does not enforce single-instance operation and must not be described
 * as refusing an unsafe multi-instance deployment. What it does is narrower and
 * still worth having: it makes the constraint impossible to hold accidentally
 * while believing otherwise, and it fails an operator who deliberately configures
 * a replica count here instead of letting them discover the problem from a
 * duplicated provider bill.
 *
 * Enforcement belongs in the deployment control plane — one replica, with
 * auditable evidence — before any ingress or remote activation. Issue #49 is the
 * real fix; this is the honest boundary until it lands.
 */
export const assertSingleInstanceState = (instanceCount: number): void => {
  if (instanceCount > 1) {
    throw new TestModeFenceError(
      `APP_INSTANCE_COUNT=${instanceCount}: replay, at-most-once, rate-limit, idempotency and ` +
        'transfer state are per-process, so a second instance shares none of them and one attempt ' +
        'can reach a provider twice. Refusing to start. Run a single instance until the ' +
        'Redis-backed stores are wired.',
    );
  }
};
