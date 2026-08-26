import type { Result, UserId } from '@supernutrition/shared';
import type { CoachMessage, CoachFailure } from '../domain/index.js';

export interface GenerateCoachMessage {
  execute(userId: UserId, eventId: string): Promise<Result<CoachMessage, CoachFailure>>;
}
