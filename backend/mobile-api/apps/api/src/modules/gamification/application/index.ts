import type { Result, UserId, LocalDate } from '@supernutrition/shared';
import type { GamificationFailure } from '../domain/index.js';

/** Subscriber to MealEventConfirmed. Idempotent and keyed by event ID. */
export interface EvaluateAchievements {
  execute(
    userId: UserId,
    asOf: LocalDate,
    eventId: string,
  ): Promise<Result<readonly string[], GamificationFailure>>;
}
