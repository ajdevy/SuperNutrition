import type { Result, Modality, ResidencyPosture } from '@supernutrition/shared';

/**
 * Security contracts (docs/09) and their implementations.
 *
 * The contracts were declared in Stage 2; Stage 4 supplies the gateway
 * implementations re-exported at the bottom of this file.
 */

export interface RateLimitKey {
  readonly layer: 'ip' | 'user' | 'installation' | 'environment' | 'auth-endpoint';
  readonly principal: string;
}

export interface RateLimitDecision {
  readonly allowed: boolean;
  readonly limit: number;
  readonly remaining: number;
  readonly resetAtEpochSeconds: number;
  readonly retryAfterSeconds: number | null;
}

/**
 * Redis unavailability fails CLOSED on the gateway (paid, abusable) and OPEN on
 * product reads (unpaid; a cache outage should not stop a user seeing
 * yesterday's lunch). One global policy gets one of the two wrong (docs/09 §9.3).
 */
export interface RateLimiter {
  check(key: RateLimitKey): Promise<RateLimitDecision>;
  failureMode(layer: RateLimitKey['layer']): 'fail-open' | 'fail-closed';
}

/** Key + request digest, 24h. Same key + different digest is 409 (docs/06 §6.7). */
/**
 * How long a claim holds its key, in each of the two states it can be in.
 *
 * One number used to serve both, and it was the 24-hour replay window. So a
 * process that died between claiming and completing — or whose SET applied but
 * whose reply was lost — locked that key fleet-wide for 24 hours with nothing
 * created (DS-F2). An honest retry got a conflict about a meal that does not
 * exist, and no operator action short of reaching into Redis could clear it.
 *
 * The two states answer different questions, so they get different bounds:
 * `inFlightSeconds` is "how long may one request be in progress", which is a
 * request-timeout question, and `completedSeconds` is "how long is a replay
 * honoured", which is the contract's replay window.
 */
export interface IdempotencyTtl {
  readonly inFlightSeconds: number;
  readonly completedSeconds: number;
}

export interface IdempotencyStore {
  /**
   * `key` is expected to be OPAQUE — a digest, not a readable composite.
   *
   * `owner` is passed so a replay can be re-authorised against the record it
   * re-reads, and is deliberately NOT stored: it exists only for the duration of
   * the call. The key used to be `<userId>:<client key>`, which put a raw
   * account identifier and a client-chosen free-text string into the keyspace of
   * a store anyone with the connection can list.
   */
  claim(
    key: string,
    requestDigest: string,
    ttl: IdempotencyTtl,
    owner: string,
  ): Promise<IdempotencyClaim>;
  /**
   * Records the response and extends the key to the replay window.
   *
   * Extends rather than preserves: the claim was written with the short
   * in-flight bound, and only a completed record earns the full window.
   */
  complete(key: string, response: unknown, ttl: IdempotencyTtl): Promise<void>;
  /**
   * Releases a claim whose request failed, so a retry with the same key may run
   * again. Distinct from `complete` because "failed" and "returned this
   * response" are different outcomes: sniffing a sentinel out of the response
   * value would make any body containing that shape unstorable.
   */
  fail(key: string): Promise<void>;
}

export type IdempotencyClaim =
  | { readonly state: 'fresh' }
  | { readonly state: 'replay'; readonly response: unknown }
  | { readonly state: 'conflict' };

/** At-most-once execution for transfer-start effects (AD-18). */
export interface EffectStore {
  claimOnce(attemptId: string, effectId: string, ttlSeconds: number): Promise<boolean>;
}

/**
 * Cross-instance attempt ownership and cancellation (#49).
 *
 * Deliberately narrow. `TransferStateTracker` keeps the per-attempt state
 * machine and its AbortController, both of which are inherently local — the
 * controller aborts a request THIS process is making, and no other instance can
 * abort it. What has to be shared is smaller: which instance holds an attempt,
 * and whether it has been cancelled.
 *
 * Image bytes are never written here (AD-8). The value stored against an
 * attempt is an instance identifier.
 */
export interface AttemptRegistry {
  /**
   * Binds the attempt to the holding instance AND the owning installation.
   * False when another instance already holds it.
   */
  claimAttempt(attemptId: string, instanceId: string, owner: string): Promise<boolean>;
  /**
   * Which instance holds it, or null when it is unknown OR owned by someone
   * else. H-01 — those two cases MUST be indistinguishable: answering "held
   * elsewhere" to a non-owner confirms that another installation's attempt is
   * live, which is an enumeration oracle.
   */
  holderOf(attemptId: string, owner: string): Promise<string | null>;
  /**
   * Publishes a cancellation to every instance, only for the owner. False when
   * unknown or foreign — the same answer for both, for the same reason.
   * Idempotent for the owner.
   */
  markCancelled(attemptId: string, owner: string): Promise<boolean>;
  isCancelled(attemptId: string): Promise<boolean>;
}

export interface MarketPolicyEntry {
  readonly marketId: string;
  readonly marketJurisdiction: string;
  readonly residencyPosture: ResidencyPosture;
  readonly permittedProcessingRegions: readonly string[];
  readonly remoteInference: Readonly<Record<Modality, boolean>>;
  readonly consentCopyVersion: Readonly<Record<Modality, string | null>>;
  readonly acceptedInputLanguages: readonly string[];
  readonly legalReviewRef: string;
  readonly approvedBy: string;
  readonly approvedAt: string;
  readonly expiresAt: string;
}

export interface MarketResolution {
  readonly marketJurisdiction: string;
  readonly processingRegion: string | null;
  readonly residencyPosture: ResidencyPosture;
  readonly remotePermitted: boolean;
  readonly consentCopyVersion: string | null;
  readonly acceptedInputLanguages: readonly string[];
}

export type MarketPolicyFailure =
  | 'market-unresolved'
  | 'market-prohibited'
  | 'posture-expired'
  | 'modality-not-permitted'
  | 'region-not-permitted';

/**
 * AD-41 / MP-1..MP-8. Every market defaults to `unresolved` and permits no
 * remote inference of any modality. Absence, `unresolved`, and expiry are
 * equivalent AT RESOLUTION TIME, not at build time (MP-3). Photo and text are
 * decided independently (MP-4).
 */
export interface MarketPolicyResolver {
  resolve(
    marketId: string,
    modality: Modality,
    now: Date,
  ): Result<MarketResolution, MarketPolicyFailure>;
}

/** All six conditions must hold; any one absent fails closed (docs/09 §9.5). */
export interface ConsentVerification {
  readonly digestMatches: boolean;
  readonly modalityMatches: boolean;
  readonly copyVersionKnown: boolean;
  readonly posturePermits: boolean;
  readonly regionPermitted: boolean;
  readonly versionsSupported: boolean;
}

export const consentSatisfied = (v: ConsentVerification): boolean =>
  v.digestMatches &&
  v.modalityMatches &&
  v.copyVersionKnown &&
  v.posturePermits &&
  v.regionPermitted &&
  v.versionsSupported;

export interface ConsentVerifier {
  verify(
    receiptDigest: string,
    payloadDigest: string,
    modality: Modality,
    marketId: string,
  ): Promise<ConsentVerification>;
}

/*
 * `UploadValidator` used to be declared here and was never implemented or
 * consumed. It was removed rather than left: an interface with no implementor
 * reads as a missing guard, and an audit of #50 concluded from it that the
 * released `content-length` and `streamed-size-guard` steps had no
 * implementation at all.
 *
 * They do. Both are enforced by the HTTP layer: `bodyLimit` on the Fastify
 * instance (server.ts) refuses an over-large declared Content-Length before the
 * body is read and caps the stream while reading, and @fastify/multipart's
 * `limits` bound file size, file count and field count.
 */
/**
 * AD-8 disposal. Runs on success, error, timeout, and cancellation alike —
 * the same code path, not four (docs/09 §9.6).
 */
export interface PayloadDisposal {
  dispose(attemptId: string): Promise<void>;
}

// --- implementations ---------------------------------------------------------

export { GATEWAY_CODES, GATEWAY_ERRORS, GatewayError, isGatewayError } from './gateway-errors.js';
export type { GatewayCode, GatewayErrorSpec, GatewayErrorDetail } from './gateway-errors.js';

export { verifyConsent, computePayloadDigest, MAX_CONSENT_VALIDITY_SECONDS } from './consent.js';
export type { ConsentReceiptInput, ConsentVerificationInput } from './consent.js';

export {
  StaticMarketPolicyResolver,
  deniedResolution,
  FIXTURE_MARKET_POLICY,
  PRODUCTION_MARKET_POLICY,
} from './market-policy.resolver.js';

export {
  JwtTokenIssuer,
  TestModeAuthenticator,
  MIN_TEST_TOKEN_LENGTH,
  hashRefreshToken,
  generateRefreshToken,
  generateOpaqueId,
  refreshTokenMatches,
} from './auth.js';
export type { AuthContext, TokenPair, TokenIssuerConfig } from './auth.js';

export { TestModeProductAuthenticator } from './product-auth.js';
export type { ProductPrincipal, InstallationUserResolver } from './product-auth.js';

export {
  RedisRateLimiter,
  InMemoryRateLimiter,
  RedisEffectStore,
  InMemoryEffectStore,
  InMemoryIdempotencyStore,
  RedisAttemptRegistry,
  RedisIdempotencyStore,
  RedisUnavailableError,
  createRedisClient,
} from './stores.js';
export type { RedisLike, RateLimitRule, IdempotencyReference } from './stores.js';
