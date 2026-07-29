import type { Result, UserId, MealEventId, Instant } from '@supernutrition/shared';

/**
 * Stores a message key plus params, not rendered prose: a stored English
 * sentence cannot be re-rendered in another locale (docs/05 §5.3).
 */
export interface CoachMessage {
  readonly userId: UserId;
  readonly mealEventId: MealEventId | null;
  readonly type: 'motivation' | 'recommendation' | 'habit';
  readonly messageKey: string;
  readonly params: Readonly<Record<string, unknown>>;
  readonly createdAt: Instant;
}

export interface CoachRecommendation {
  readonly actionKey: string;
  readonly params: Readonly<Record<string, unknown>>;
}

/** Read-only snapshot. Coach interprets facts; it never modifies them. */
export interface CoachContext {
  readonly userId: UserId;
  readonly recentTotals: Readonly<Record<string, string | null>>;
  readonly adherenceStatus: string | null;
  readonly streakCount: number;
  readonly goalType: string | null;
}

export type CoachFailure = 'no-context' | 'storage-unavailable';

/**
 * Coach has NO write access to any other domain. Its module holds no other
 * repository port, and a graph test asserts it (docs/04 §4.9).
 *
 * Medical and allergen conclusions are prohibited (AD-40). The message catalog
 * is scanned for prohibited claim patterns.
 */
export interface CoachContextAssembler {
  assemble(userId: UserId): Promise<Result<CoachContext, CoachFailure>>;
}

export interface MessageSelector {
  select(context: CoachContext): Result<CoachMessage, CoachFailure>;
}

export interface CoachMessageRepository {
  save(message: CoachMessage): Promise<Result<CoachMessage, CoachFailure>>;
}
