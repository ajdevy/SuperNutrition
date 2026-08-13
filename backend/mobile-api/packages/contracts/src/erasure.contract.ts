import type { UserId } from '@supernutrition/shared';

/**
 * Every domain holding user-scoped data registers one of these. Users
 * orchestrates the fan-out and produces an auditable report; a new domain
 * without a port fails the erasure test (docs/04 §4.2).
 */
export interface ErasurePort {
  readonly domain: string;
  erase(userId: UserId): Promise<ErasureOutcome>;
}

export interface ErasureOutcome {
  readonly domain: string;
  readonly rowsAffected: number;
  readonly completedAt: string;
}

export interface ErasureReport {
  readonly userId: UserId;
  readonly outcomes: readonly ErasureOutcome[];
  readonly completedAt: string;
}
