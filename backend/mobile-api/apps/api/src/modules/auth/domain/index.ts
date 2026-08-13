import type {
  Result,
  SessionId,
  UserId,
  InstallationId,
  Instant,
  AuthMode,
  AuthScope,
} from '@supernutrition/shared';

export interface Session {
  readonly id: SessionId;
  readonly userId: UserId;
  readonly installationId: InstallationId;
  readonly environment: string;
  readonly authMode: AuthMode;
  readonly scope: AuthScope;
  readonly refreshFamilyId: string;
  readonly issuedAt: Instant;
  readonly expiresAt: Instant;
  readonly revokedAt: Instant | null;
}

export interface TokenPair {
  readonly accessToken: string;
  readonly refreshToken: string;
  readonly accessExpiresAt: Instant;
  readonly refreshExpiresAt: Instant;
  readonly authMode: AuthMode;
}

/** Modules needing identity receive this. They never see a JWT (docs/04 §4.1). */
export interface AuthContext {
  readonly userId: UserId;
  readonly installationId: InstallationId;
  readonly scope: AuthScope;
  readonly authMode: AuthMode;
  readonly environment: string;
}

export interface InstallationAttestation {
  readonly platform: 'android' | 'ios';
  readonly attestationToken: string;
  readonly nonce: string;
}

export type AuthFailure =
  | 'invalid-attestation'
  | 'nonce-replayed'
  | 'token-expired'
  | 'token-invalid'
  | 'environment-mismatch'
  | 'session-revoked'
  | 'refresh-reused';

/**
 * Two adapters: a real platform verifier and a fixture verifier. The fixture
 * adapter is bound only after the TEST_MODE boot assertion has passed.
 */
export interface AttestationVerifier {
  verify(attestation: InstallationAttestation): Promise<Result<InstallationId, AuthFailure>>;
}

/** One-time claim. A replayed nonce or request ID is 401 TOKEN_REPLAYED. */
export interface NonceStore {
  claimOnce(nonce: string, ttlSeconds: number): Promise<boolean>;
}

export interface TokenIssuer {
  issue(session: Session): Promise<TokenPair>;
  verifyAccess(token: string, environment: string): Promise<Result<AuthContext, AuthFailure>>;
}

export interface SessionRepository {
  findByRefreshHash(hash: string): Promise<Result<Session, AuthFailure>>;
  create(session: Session, refreshHash: string): Promise<Result<Session, AuthFailure>>;
  /** Presenting an already-rotated token revokes the whole lineage. */
  revokeFamily(familyId: string, reason: string): Promise<Result<void, AuthFailure>>;
  revoke(id: SessionId, reason: string): Promise<Result<void, AuthFailure>>;
}
