import type { Result, UserId } from '@supernutrition/shared';
import type { UserGoal, GoalFailure } from '../domain/index.js';

export interface UpdateGoal {
  execute(userId: UserId, patch: Partial<UserGoal>): Promise<Result<UserGoal, GoalFailure>>;
}
