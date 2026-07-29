import { z } from 'zod';

export const CreateSessionRequestSchema = z.object({
  platform: z.enum(['android', 'ios']),
  attestationToken: z.string().min(1).max(8192),
  nonce: z.string().min(16).max(128),
});

export const RefreshRequestSchema = z.object({
  refreshToken: z.string().min(32).max(512),
});

export interface TokenPairResponse {
  readonly accessToken: string;
  readonly refreshToken: string;
  readonly accessExpiresAt: string;
  readonly refreshExpiresAt: string;
  /** Surfaced so no environment can be in test mode unnoticed (docs/09 §9.2). */
  readonly authMode: 'attested' | 'test-mode';
}
