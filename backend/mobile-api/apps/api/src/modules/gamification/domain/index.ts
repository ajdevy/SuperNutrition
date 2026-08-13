import type { Result, UserId, LocalDate, AchievementId, Instant } from '@supernutrition/shared';

/**
 * `criteria` is a declarative expression evaluated by the rule engine. No
 * achievement key appears in a conditional in application code (docs/04 §4.8).
 */
export interface Achievement {
  readonly id: AchievementId;
  readonly key: string;
  readonly name: string;
  readonly description: string;
  readonly criteria: AchievementCriteria;
  readonly xpReward: number;
  readonly active: boolean;
}

export type AchievementCriteria =
  | {
      readonly type: 'meal_count';
      readonly operator: '>=';
      readonly value: number;
      readonly window: 'all_time';
    }
  | {
      readonly type: 'streak';
      readonly streakType: StreakType;
      readonly operator: '>=';
      readonly value: number;
    }
  | {
      readonly type: 'goal_adherence';
      readonly nutrient: string;
      readonly operator: '>=';
      readonly value: number;
      readonly window: { days: number };
    }
  | {
      readonly type: 'goal_adherence_days';
      readonly nutrient: string;
      readonly operator: '>=';
      readonly value: number;
      readonly window: { days: number };
    };

export type StreakType = 'daily_logging' | 'goal_adherence';

export interface UserAchievement {
  readonly userId: UserId;
  readonly achievementId: AchievementId;
  readonly unlockedAt: Instant;
}

/** Recomputed from distinct local dates, never blindly incremented. */
export interface Streak {
  readonly userId: UserId;
  readonly type: StreakType;
  readonly currentCount: number;
  readonly longestCount: number;
  readonly lastLocalDate: LocalDate | null;
}

/** A ledger. Balance is the sum; (userId, reason, refId) is unique. */
export interface XpTransaction {
  readonly userId: UserId;
  readonly amount: number;
  readonly reason: string;
  readonly refId: string;
}

export type GamificationFailure = 'already-awarded' | 'unknown-achievement' | 'storage-unavailable';

export interface AchievementRuleEngine {
  evaluate(
    criteria: AchievementCriteria,
    userId: UserId,
    asOf: LocalDate,
  ): Promise<Result<boolean, GamificationFailure>>;
}

export interface XpLedger {
  award(transaction: XpTransaction): Promise<Result<void, GamificationFailure>>;
  balance(userId: UserId): Promise<number>;
}

export interface StreakRepository {
  recompute(
    userId: UserId,
    type: StreakType,
    asOf: LocalDate,
  ): Promise<Result<Streak, GamificationFailure>>;
}
