import { z } from 'zod';

const decimalString = z.string().regex(/^\d{1,7}(\.\d{1,3})?$/);
const localDate = z.string().regex(/^\d{4}-\d{2}-\d{2}$/);

export const UpdateGoalRequestSchema = z
  .object({
    goalType: z.enum(['lose', 'maintain', 'gain', 'custom']).optional(),
    targetCalories: z.number().int().min(500).max(10_000).optional(),
    targetProteinG: decimalString.optional(),
    targetFatG: decimalString.optional(),
    targetCarbG: decimalString.optional(),
    activityLevel: z.enum(['sedentary', 'light', 'moderate', 'active', 'very_active']).optional(),
    effectiveFrom: localDate.optional(),
  })
  .refine((v) => Object.keys(v).length > 0, { message: 'at least one field is required' });

export const GetGoalQuerySchema = z.object({ at: localDate.optional() });

export interface GoalResponse {
  readonly id: string;
  readonly goalType: string;
  readonly targetCalories: number;
  readonly targetProteinG: string;
  readonly targetFatG: string;
  readonly targetCarbG: string;
  readonly activityLevel: string;
  readonly effectiveFrom: string;
  readonly effectiveTo: string | null;
}
