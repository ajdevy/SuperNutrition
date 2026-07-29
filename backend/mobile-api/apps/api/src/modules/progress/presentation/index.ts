import { z } from 'zod';

const localDate = z.string().regex(/^\d{4}-\d{2}-\d{2}$/);

export const ProgressQuerySchema = z.object({ date: localDate });
export const WeeklyProgressQuerySchema = z.object({ weekStart: localDate });
export const TrendsQuerySchema = z.object({
  from: localDate,
  to: localDate,
  metric: z.enum(['calories', 'protein', 'fat', 'carb', 'fiber']),
});

export interface DailyProgressResponse {
  readonly localDate: string;
  readonly totals: Readonly<Record<string, string | null | boolean>>;
  readonly goal: Readonly<Record<string, unknown>> | null;
  readonly adherence: Readonly<Record<string, unknown>> | null;
  readonly mealCount: number;
  readonly computedAt: string;
}
