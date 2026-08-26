import { z } from 'zod';

export const UpdateProfileRequestSchema = z.object({
  displayName: z.string().trim().min(1).max(120).nullable().optional(),
  locale: z.string().min(2).max(35).optional(),
  measurementSystem: z.enum(['metric', 'imperial']).optional(),
  timezone: z
    .string()
    .refine(
      (v) => {
        try {
          new Intl.DateTimeFormat('en-US', { timeZone: v });
          return true;
        } catch {
          return false;
        }
      },
      { message: 'must be a valid IANA timezone' },
    )
    .optional(),
});

export interface UserProfileResponse {
  readonly id: string;
  readonly displayName: string | null;
  readonly locale: string;
  readonly measurementSystem: string;
  readonly timezone: string;
}
