import type { Result, UserId } from '@supernutrition/shared';
import type { ErasureReport } from '@supernutrition/contracts';
import type { UserFailure } from '../domain/index.js';

/**
 * Users orchestrates erasure by fanning out over every registered ErasurePort
 * and producing an auditable report. A new domain without a port fails the
 * erasure test — this is what makes AD-24 user-rights evidence a build
 * property (docs/04 §4.2).
 */
export interface EraseUser {
  execute(userId: UserId): Promise<Result<ErasureReport, UserFailure>>;
}

export interface ExportUserData {
  execute(userId: UserId): Promise<Result<{ downloadRef: string }, UserFailure>>;
}
