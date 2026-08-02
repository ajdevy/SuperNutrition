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
export interface IdempotencyStore {
  claim(key: string, requestDigest: string, ttlSeconds: number): Promise<IdempotencyClaim>;
  complete(key: string, response: unknown): Promise<void>;
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

/**
 * Upload validation, cheapest rejection first: content-length, content-type,
 * streamed size guard, magic bytes, dimensions (docs/09 §9.4).
 */
export type UploadRejection =
  | 'too-large'
  | 'unsupported-media-type'
  | 'declared-length-mismatch'
  | 'magic-byte-mismatch'
  | 'dimensions-exceeded';

export interface UploadValidator {
  validate(
    contentLength: number,
    contentType: string,
    head: Uint8Array,
  ): Result<void, UploadRejection>;
}

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
} from './stores.js';
export type { RedisLike, RateLimitRule } from './stores.js';
