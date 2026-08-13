import type { Result, UserId, LocalDate, Instant } from '@supernutrition/shared';

/**
 * Versioned, not mutated. Changing a target closes the current period and opens
 * a new one, so historical adherence is computed against the goal that was
 * actually in force (docs/04 §4.3).
 */
export interface UserGoal {
  readonly id: string;
  readonly userId: UserId;
  readonly goalType: 'lose' | 'maintain' | 'gain' | 'custom';
  readonly targetCalories: number;
  readonly targetProteinG: string;
  readonly targetFatG: string;
  readonly targetCarbG: string;
  readonly activityLevel: 'sedentary' | 'light' | 'moderate' | 'active' | 'very_active';
  readonly effectiveFrom: LocalDate;
  readonly effectiveTo: LocalDate | null;
  readonly createdAt: Instant;
}

export type GoalFailure = 'not-found' | 'overlapping-period' | 'storage-unavailable';

export interface UserGoalRepository {
  findActiveAt(userId: UserId, localDate: LocalDate): Promise<Result<UserGoal, GoalFailure>>;
  findCurrent(userId: UserId): Promise<Result<UserGoal, GoalFailure>>;
  /** Closes the current version and opens the next. Never an in-place update. */
  openNewVersion(goal: UserGoal): Promise<Result<UserGoal, GoalFailure>>;
}
