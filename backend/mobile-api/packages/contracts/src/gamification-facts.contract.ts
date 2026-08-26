import type { LocalDate, UserId } from '@supernutrition/shared';

export interface StreakFacts {
  readonly type: 'daily_logging' | 'goal_adherence';
  readonly currentCount: number;
  readonly longestCount: number;
  readonly lastLocalDate: LocalDate | null;
}

export interface GamificationFactsQuery {
  streak(userId: UserId, type: StreakFacts['type']): Promise<StreakFacts | null>;
  xpBalance(userId: UserId): Promise<number>;
  unlockedAchievementKeys(userId: UserId): Promise<readonly string[]>;
}
