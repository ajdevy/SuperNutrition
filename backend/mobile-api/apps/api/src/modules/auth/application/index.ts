import type { Result } from '@supernutrition/shared';
import type { AuthFailure, InstallationAttestation, TokenPair } from '../domain/index.js';

export interface CreateSession {
  execute(attestation: InstallationAttestation): Promise<Result<TokenPair, AuthFailure>>;
}

export interface RefreshSession {
  execute(refreshToken: string): Promise<Result<TokenPair, AuthFailure>>;
}

export interface RevokeSession {
  execute(sessionId: string, reason: string): Promise<Result<void, AuthFailure>>;
}
