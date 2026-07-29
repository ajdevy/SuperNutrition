import type { Result, UserId, LocalDate } from '@supernutrition/shared';
import type { DailyProgress, WeeklyProgress, ProgressFailure } from '../domain/index.js';

export interface GetDailyProgress {
  execute(userId: UserId, localDate: LocalDate): Promise<Result<DailyProgress, ProgressFailure>>;
}

export interface GetWeeklyProgress {
  execute(userId: UserId, weekStart: LocalDate): Promise<Result<WeeklyProgress, ProgressFailure>>;
}
