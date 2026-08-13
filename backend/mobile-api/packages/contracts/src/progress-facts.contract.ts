import type { LocalDate, UserId } from '@supernutrition/shared';
import type { DailyTotals } from './meal-facts.contract.js';

export interface GoalAdherence {
  readonly calories: number;
  readonly protein: number;
  readonly fat: number;
  readonly carb: number;
  readonly status: 'under' | 'on_track' | 'over';
}

export interface DailyProgressFacts {
  readonly localDate: LocalDate;
  readonly totals: DailyTotals;
  readonly adherence: GoalAdherence | null;
}

export interface ProgressFactsQuery {
  daily(userId: UserId, localDate: LocalDate): Promise<DailyProgressFacts | null>;
  weekly(userId: UserId, weekStart: LocalDate): Promise<readonly DailyProgressFacts[]>;
}
