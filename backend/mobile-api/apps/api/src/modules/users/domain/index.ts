import type { Result, UserId, Instant, Modality } from '@supernutrition/shared';

export interface User {
  readonly id: UserId;
  readonly externalRef: string;
  readonly status: 'active' | 'suspended' | 'deleted';
  readonly createdAt: Instant;
  readonly deletedAt: Instant | null;
}

export interface UserProfile {
  readonly userId: UserId;
  readonly displayName: string | null;
  readonly locale: string;
  readonly measurementSystem: 'metric' | 'imperial';
  readonly timezone: string;
}

/**
 * AD-8: binds the digest of the scrubbed normalized payload. The payload itself
 * is never stored. This is the durable proof the transfer precondition was met.
 */
export interface ConsentReceipt {
  readonly userId: UserId;
  readonly attemptId: string;
  readonly purpose: string;
  readonly modality: Modality;
  readonly payloadDigest: string;
  readonly marketJurisdiction: string;
  readonly processingRegion: string;
  readonly retentionVersion: string;
  readonly configurationVersion: string;
  readonly consentCopyVersion: string;
  readonly decision: 'granted' | 'declined';
  readonly decidedAt: Instant;
}

export type UserFailure = 'not-found' | 'already-exists' | 'storage-unavailable';

export interface UserRepository {
  findById(id: UserId): Promise<Result<User, UserFailure>>;
  /** The external ref is the installation identifier from the auth context. */
  findByExternalRef(externalRef: string): Promise<Result<User, UserFailure>>;
  /**
   * First-access provisioning for a client with no account-creation flow: the
   * user exists after the first authenticated request, not before it. An erased
   * account is reported `not-found` rather than resurrected.
   */
  ensureByExternalRef(externalRef: string): Promise<Result<User, UserFailure>>;
  create(user: User): Promise<Result<User, UserFailure>>;
  softDelete(id: UserId): Promise<Result<void, UserFailure>>;
}

export interface ConsentReceiptRepository {
  record(receipt: ConsentReceipt): Promise<Result<ConsentReceipt, UserFailure>>;
  findByDigest(digest: string): Promise<Result<ConsentReceipt, UserFailure>>;
}
