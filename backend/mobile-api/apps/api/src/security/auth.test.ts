import { describe, it, expect, beforeAll } from 'vitest';
import { generateKeyPairSync } from 'node:crypto';
import {
  JwtTokenIssuer,
  TestModeAuthenticator,
  hashRefreshToken,
  generateRefreshToken,
  generateOpaqueId,
  refreshTokenMatches,
} from './auth.js';
import { GatewayError, GATEWAY_CODES } from './gateway-errors.js';
import { SignJWT, importPKCS8 } from 'jose';

let privateKey: string;
let publicKey: string;
let otherPublicKey: string;

beforeAll(() => {
  const pair = generateKeyPairSync('rsa', {
    modulusLength: 2048,
    publicKeyEncoding: { type: 'spki', format: 'pem' },
    privateKeyEncoding: { type: 'pkcs8', format: 'pem' },
  });
  privateKey = pair.privateKey;
  publicKey = pair.publicKey;
  otherPublicKey = generateKeyPairSync('rsa', {
    modulusLength: 2048,
    publicKeyEncoding: { type: 'spki', format: 'pem' },
    privateKeyEncoding: { type: 'pkcs8', format: 'pem' },
  }).publicKey;
});

const config = {
  issuer: 'supernutrition-mobile-api',
  environment: 'staging',
  accessTtlSeconds: 900,
  refreshTtlSeconds: 2_592_000,
};

const issuerFor = (pub = publicKey, env = config.environment) =>
  new JwtTokenIssuer({ ...config, environment: env }, privateKey, pub);

const NOW = new Date('2026-07-29T10:00:00Z');

describe('token issuance', () => {
  it('issues a pair with the expected lifetimes and mode', async () => {
    const pair = await issuerFor().issue('user-1', 'install-1', 'gateway', 'attested', NOW);

    expect(pair.authMode).toBe('attested');
    expect(pair.accessExpiresAt).toBe('2026-07-29T10:15:00.000Z');
    expect(pair.refreshExpiresAt).toBe('2026-08-28T10:00:00.000Z');
    expect(pair.refreshToken).toHaveLength(43);
  });

  it('round-trips claims through verification', async () => {
    const issuer = issuerFor();
    const pair = await issuer.issue('user-1', 'install-1', 'gateway', 'attested', NOW);
    const context = await issuer.verify(pair.accessToken, NOW);

    expect(context).toEqual({
      userId: 'user-1',
      installationId: 'install-1',
      scope: 'gateway',
      authMode: 'attested',
      environment: 'staging',
    });
  });
});

describe('token verification rejects', () => {
  it('a token signed by another key', async () => {
    const pair = await issuerFor().issue('u', 'i', 'gateway', 'attested', NOW);
    await expect(issuerFor(otherPublicKey).verify(pair.accessToken)).rejects.toBeInstanceOf(
      GatewayError,
    );
  });

  it('garbage', async () => {
    await expect(issuerFor().verify('not.a.token')).rejects.toMatchObject({
      gatewayCode: 'authentication-failed',
    });
  });

  // AD-21 binds a token to its environment, so a staging token presented to
  // production fails on the claim rather than on signature luck.
  it('a token minted for another environment', async () => {
    const staging = issuerFor(publicKey, 'staging');
    const pair = await staging.issue('u', 'i', 'gateway', 'attested', NOW);
    await expect(
      issuerFor(publicKey, 'production').verify(pair.accessToken, NOW),
    ).rejects.toMatchObject({ gatewayCode: 'authentication-failed' });
  });

  it('a token without gateway scope', async () => {
    const issuer = issuerFor();
    const pair = await issuer.issue('u', 'i', 'product', 'attested', NOW);
    await expect(issuer.verify(pair.accessToken, NOW)).rejects.toMatchObject({
      gatewayCode: 'insufficient-scope',
    });
  });

  it('accepts the both scope', async () => {
    const issuer = issuerFor();
    const pair = await issuer.issue('u', 'i', 'both', 'attested', NOW);
    await expect(issuer.verify(pair.accessToken, NOW)).resolves.toMatchObject({ scope: 'both' });
  });

  it('an expired token', async () => {
    const issuer = new JwtTokenIssuer({ ...config, accessTtlSeconds: -10 }, privateKey, publicKey);
    const pair = await issuer.issue('u', 'i', 'gateway', 'attested', NOW);
    await expect(issuer.verify(pair.accessToken, NOW)).rejects.toMatchObject({
      gatewayCode: 'session-expired',
    });
  });

  it('reports a bad signature as authentication-failed, not session-expired', async () => {
    const pair = await issuerFor().issue('u', 'i', 'gateway', 'attested', NOW);
    await expect(issuerFor(otherPublicKey).verify(pair.accessToken, NOW)).rejects.toMatchObject({
      gatewayCode: 'authentication-failed',
    });
  });
});

describe('refresh token handling', () => {
  it('hashes at rest and never stores the raw value', () => {
    const token = generateRefreshToken();
    const hash = hashRefreshToken(token);
    expect(hash).toHaveLength(64);
    expect(hash).not.toContain(token);
  });

  it('matches a presented token against its stored hash', () => {
    const token = generateRefreshToken();
    expect(refreshTokenMatches(token, hashRefreshToken(token))).toBe(true);
    expect(refreshTokenMatches(generateRefreshToken(), hashRefreshToken(token))).toBe(false);
  });

  it('rejects a hash of the wrong length without throwing', () => {
    expect(refreshTokenMatches('anything', 'short')).toBe(false);
  });
});

describe('opaque identifiers', () => {
  it('carry the requested prefix and are unique', () => {
    const a = generateOpaqueId('req');
    const b = generateOpaqueId('req');
    expect(a.startsWith('req_')).toBe(true);
    expect(a).not.toBe(b);
  });
});

describe('test-mode authenticator', () => {
  const authenticator = new TestModeAuthenticator('development');

  it('labels a credentialed context as test-mode', () => {
    expect(authenticator.authenticate('Bearer abcdefghijkl').authMode).toBe('test-mode');
  });

  it('derives an installation id from a presented token', () => {
    expect(authenticator.authenticate('Bearer installation-abc-123').installationId).toContain(
      'installation-abc',
    );
  });

  // Test mode skips attestation, not authentication. An anonymous caller here
  // would reach a paid provider and would share one rate-limit principal with
  // every other anonymous caller.
  it.each([
    ['absent', undefined],
    ['empty', ''],
    ['bearer with nothing after it', 'Bearer '],
    ['too short to be a credential', 'Bearer tiny'],
  ])('refuses a request whose credential is %s', (_label, header) => {
    expect(() => authenticator.authenticate(header)).toThrowError(
      expect.objectContaining({ gatewayCode: GATEWAY_CODES.AUTHENTICATION_FAILED }),
    );
  });
});

/**
 * Low group — a credential that names nobody.
 *
 * userId came from `String(payload.sub ?? '')`, so a token without `sub`
 * authenticated as the empty string. Every such caller then shared one
 * rate-limit bucket keyed on "", meaning one of them could exhaust the limit for
 * all of them, and any per-user decision downstream was being made about nobody
 * in particular.
 */
describe('a token must name who it speaks for', () => {
  // Signed with the real key so verification reaches the claim checks. This is
  // the shape a broken — or hostile — issuer emits, which is exactly the case
  // the claim check exists for.
  const signWithout = async (field: 'sub' | 'installationId'): Promise<string> => {
    const claims: Record<string, unknown> = {
      sub: 'u',
      installationId: 'i',
      scope: 'gateway',
      authMode: 'attested',
      environment: config.environment,
    };
    delete claims[field];
    const key = await importPKCS8(privateKey, 'RS256');
    return new SignJWT(claims)
      .setProtectedHeader({ alg: 'RS256' })
      .setIssuer(config.issuer)
      .setIssuedAt(Math.floor(NOW.getTime() / 1000))
      .setExpirationTime(Math.floor(NOW.getTime() / 1000) + 900)
      .sign(key);
  };

  it.each(['sub', 'installationId'] as const)('refuses a token with no %s', async (field) => {
    // userId came from String(payload.sub ?? ''), so this used to authenticate
    // as the empty string — and every such caller shared one rate-limit bucket
    // keyed on "", so one of them could exhaust the limit for all of them.
    await expect(issuerFor().verify(await signWithout(field), NOW)).rejects.toMatchObject({
      gatewayCode: 'authentication-failed',
    });
  });
});
