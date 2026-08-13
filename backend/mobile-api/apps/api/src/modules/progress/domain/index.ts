import type { Result, UserId, LocalDate, Instant } from '@supernutrition/shared';

export interface DailyProgress {
  readonly userId: UserId;
  readonly localDate: LocalDate;
  readonly totals: ProgressTotals;
  readonly goalId: string | null;
  readonly goalAdherence: GoalAdherence | null;
  readonly mealCount: number;
  readonly computedAt: Instant;
}

export interface ProgressTotals {
  readonly calories: string;
  readonly proteinG: string | null;
  readonly fatG: string | null;
  readonly carbG: string | null;
  readonly fiberG: string | null;
  readonly partial: boolean;
}

export interface GoalAdherence {
  readonly calories: number;
  readonly protein: number;
  readonly fat: number;
  readonly carb: number;
  readonly status: 'under' | 'on_track' | 'over';
}

export interface WeeklyProgress {
  readonly weekStartLocalDate: LocalDate;
  readonly totals: ProgressTotals;
  readonly days: readonly DailyProgress[];
  readonly trend: Trend;
}

export interface Trend {
  readonly metric: string;
  readonly direction: 'up' | 'down' | 'flat';
  readonly changeRatio: number;
}

export type ProgressFailure = 'not-found' | 'storage-unavailable';

/**
 * `daily_progress` is a derived projection, not a source of truth. `rebuild`
 * exists so any date range can be regenerated from meal_events — a projection
 * that cannot be rebuilt has quietly become a source of truth (docs/04 §4.8).
 */
export interface ProgressProjector {
  recompute(userId: UserId, localDate: LocalDate): Promise<Result<DailyProgress, ProgressFailure>>;
  rebuild(userId: UserId, from: LocalDate, to: LocalDate): Promise<Result<number, ProgressFailure>>;
}

export interface TrendCalculator {
  compute(days: readonly DailyProgress[], metric: string): Trend;
}

export interface DailyProgressRepository {
  find(userId: UserId, localDate: LocalDate): Promise<Result<DailyProgress, ProgressFailure>>;
  findRange(
    userId: UserId,
    from: LocalDate,
    to: LocalDate,
  ): Promise<Result<readonly DailyProgress[], ProgressFailure>>;
}
