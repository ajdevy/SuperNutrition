import type { LocalDate, UserId, Instant } from '@supernutrition/shared';

/**
 * The meals module's published boundary interface.
 *
 * Gamification and Progress consume exactly this and never read `meal_events`
 * directly. No module reads another module's tables (docs/02 §2.4).
 */
export interface DailyTotals {
  readonly localDate: LocalDate;
  readonly calories: string;
  readonly proteinG: string | null;
  readonly fatG: string | null;
  readonly carbG: string | null;
  readonly fiberG: string | null;
  readonly mealCount: number;
  /** True when any component nutrient was unknown. Absent is never summed as zero. */
  readonly partial: boolean;
}

export interface RangeTotals {
  readonly from: LocalDate;
  readonly to: LocalDate;
  readonly days: readonly DailyTotals[];
}

export interface MealFactsQuery {
  totalsForLocalDate(userId: UserId, localDate: LocalDate): Promise<DailyTotals>;
  totalsForRange(userId: UserId, from: LocalDate, to: LocalDate): Promise<RangeTotals>;
  confirmedCountSince(userId: UserId, since: Instant): Promise<number>;
  /** Streaks advance on distinct local dates, not on meal counts. */
  distinctLoggedLocalDates(
    userId: UserId,
    from: LocalDate,
    to: LocalDate,
  ): Promise<readonly LocalDate[]>;
}
