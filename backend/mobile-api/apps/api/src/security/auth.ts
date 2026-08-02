import { createHash, randomBytes, timingSafeEqual } from 'node:crypto';
import { SignJWT, jwtVerify, importPKCS8, importSPKI, type JWTPayload } from 'jose';
import type { AuthMode, AuthScope } from '@supernutrition/shared';
import { GatewayError, GATEWAY_CODES } from './gateway-errors.js';

/**
 * Gateway authentication (docs/09 §9.2, AD-21).
 *
 * Two modes exist, and the difference between them is enforced structurally
 * rather than by convention:
 *
 * - `attested` — RS256 JWT, 15-minute access token, environment-bound claim.
 * - `test-mode` — sessions issued without attestation, for a client that has no
 *   auth integration yet. Tokens are labelled, and `mayAuthorizeRemoteTransfer`
 *   refuses them against any non-fixture market, in every environment.
 *
 * The boot assertion in `config/test-mode-fence.ts` prevents test mode from
 * starting in production at all. This module handles what happens when it is
 * legitimately on.
 */

export interface AuthContext {
  readonly userId: string;
  readonly installationId: string;
  readonly scope: AuthScope;
  readonly authMode: AuthMode;
  readonly environment: string;
}

export interface TokenPair {
  readonly accessToken: string;
  readonly refreshToken: string;
  readonly accessExpiresAt: string;
  readonly refreshExpiresAt: string;
  readonly authMode: AuthMode;
}

export interface TokenIssuerConfig {
  readonly issuer: string;
  readonly environment: string;
  readonly accessTtlSeconds: number;
  readonly refreshTtlSeconds: number;
}

const ACCESS_ALGORITHM = 'RS256';

export const hashRefreshToken = (token: string): string =>
  createHash('sha256').update(token).digest('hex');

export const generateRefreshToken = (): string => randomBytes(32).toString('base64url');

export const generateOpaqueId = (prefix: string): string =>
  `${prefix}_${randomBytes(16).toString('base64url').replace(/[-_]/g, '').slice(0, 24)}`;

/** RS256 issuer/verifier. Keys are held in the closure, not on a config object. */
export class JwtTokenIssuer {
  readonly #config: TokenIssuerConfig;
  readonly #privateKeyPem: string;
  readonly #publicKeyPem: string;

  constructor(config: TokenIssuerConfig, privateKeyPem: string, publicKeyPem: string) {
    this.#config = config;
    this.#privateKeyPem = privateKeyPem;
    this.#publicKeyPem = publicKeyPem;
  }

  async issue(
    subject: string,
    installationId: string,
    scope: AuthScope,
    authMode: AuthMode,
    now: Date,
  ): Promise<TokenPair> {
    const key = await importPKCS8(this.#privateKeyPem, ACCESS_ALGORITHM);
    const issuedAt = Math.floor(now.getTime() / 1000);
    const accessExp = issuedAt + this.#config.accessTtlSeconds;

    const accessToken = await new SignJWT({
      installationId,
      scope,
      authMode,
      environment: this.#config.environment,
    })
      .setProtectedHeader({ alg: ACCESS_ALGORITHM })
      .setSubject(subject)
      .setIssuer(this.#config.issuer)
      .setIssuedAt(issuedAt)
      .setExpirationTime(accessExp)
      .setJti(generateOpaqueId('jti'))
      .sign(key);

    const refreshToken = generateRefreshToken();
    return {
      accessToken,
      refreshToken,
      accessExpiresAt: new Date(accessExp * 1000).toISOString(),
      refreshExpiresAt: new Date((issuedAt + this.#config.refreshTtlSeconds) * 1000).toISOString(),
      authMode,
    };
  }

  /** `now` is injectable so verification is deterministic under test. */
  async verify(token: string, now?: Date): Promise<AuthContext> {
    const key = await importSPKI(this.#publicKeyPem, ACCESS_ALGORITHM);
    let payload: JWTPayload;
    try {
      const result = await jwtVerify(token, key, {
        issuer: this.#config.issuer,
        algorithms: [ACCESS_ALGORITHM],
        ...(now ? { currentDate: now } : {}),
      });
      payload = result.payload;
    } catch (error) {
      // Match jose's error CODE, not its message. A substring test on the
      // message reports "expired" for anything containing "unexpected", which
      // would tell a client to refresh when the real fault is a bad signature.
      const expired =
        typeof (error as { code?: unknown }).code === 'string' &&
        (error as { code: string }).code === 'ERR_JWT_EXPIRED';
      throw new GatewayError(
        expired ? GATEWAY_CODES.SESSION_EXPIRED : GATEWAY_CODES.AUTHENTICATION_FAILED,
        { cause: error },
      );
    }

    // A staging token presented to production fails on the claim, not on
    // signature luck. AD-21 binds a token to its environment.
    if (payload['environment'] !== this.#config.environment) {
      throw new GatewayError(GATEWAY_CODES.AUTHENTICATION_FAILED, {
        details: [{ field: 'environment', issue: 'claim_mismatch' }],
      });
    }

    const scope = payload['scope'];
    if (scope !== 'gateway' && scope !== 'both') {
      throw new GatewayError(GATEWAY_CODES.INSUFFICIENT_SCOPE);
    }

    const authMode = payload['authMode'];
    if (authMode !== 'attested' && authMode !== 'test-mode') {
      throw new GatewayError(GATEWAY_CODES.AUTHENTICATION_FAILED, {
        details: [{ field: 'authMode', issue: 'unrecognised' }],
      });
    }

    return {
      userId: String(payload.sub ?? ''),
      installationId: String(payload['installationId'] ?? ''),
      scope,
      authMode,
      environment: this.#config.environment,
    };
  }
}

/**
 * Test-mode authenticator.
 *
 * Skips ATTESTATION, which is exactly what a client with no auth integration
 * needs, but still requires a credential. Those are different things, and
 * conflating them is what let an anonymous caller reach the provider: the
 * contract marks `Authorization` non-optional (gateway-request.toml), the
 * taxonomy's `authentication-failed` covers a MISSING token as much as an
 * untrusted one, and the rate limiter keys on `installationId` — so a request
 * with no credential would both spend money and share one throttling bucket
 * with every other anonymous caller.
 *
 * The resulting context is labelled `test-mode`, and that label is what stops
 * it reaching a real market.
 */
export class TestModeAuthenticator {
  readonly #environment: string;

  constructor(environment: string) {
    this.#environment = environment;
  }

  authenticate(authorizationHeader: string | undefined): AuthContext {
    const presented = authorizationHeader?.replace(/^Bearer\s+/i, '').trim() ?? '';
    // A short token is refused rather than folded into a shared fallback
    // principal: a caller who can pick the principal can pick whose rate-limit
    // budget to spend.
    if (presented.length < MIN_TEST_TOKEN_LENGTH) {
      throw new GatewayError(GATEWAY_CODES.AUTHENTICATION_FAILED, {
        details: [{ field: 'authorization', issue: 'missing_or_malformed_bearer_token' }],
      });
    }

    return {
      userId: 'test-user',
      installationId: presented.slice(0, 32),
      scope: 'both',
      authMode: 'test-mode',
      environment: this.#environment,
    };
  }
}

/** Shortest credential accepted in test mode; sessions issue far longer ones. */
export const MIN_TEST_TOKEN_LENGTH = 8;

/** Compares a presented refresh token against a stored hash in constant time. */
export const refreshTokenMatches = (presented: string, storedHash: string): boolean => {
  const a = Buffer.from(hashRefreshToken(presented), 'utf8');
  const b = Buffer.from(storedHash, 'utf8');
  if (a.length !== b.length) return false;
  return timingSafeEqual(a, b);
};
