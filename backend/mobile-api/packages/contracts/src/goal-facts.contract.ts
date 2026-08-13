import type { LocalDate, UserId } from '@supernutrition/shared';

export interface GoalSnapshot {
  readonly goalId: string;
  readonly targetCalories: number;
  readonly targetProteinG: string;
  readonly targetFatG: string;
  readonly targetCarbG: string;
  readonly effectiveFrom: LocalDate;
  readonly effectiveTo: LocalDate | null;
}

/** Adherence always resolves the goal in force on that local date. */
export interface GoalFactsQuery {
  findActiveAt(userId: UserId, localDate: LocalDate): Promise<GoalSnapshot | null>;
  findCurrent(userId: UserId): Promise<GoalSnapshot | null>;
}
