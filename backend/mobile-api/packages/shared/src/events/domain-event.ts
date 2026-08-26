import type { Instant } from '../value-objects/local-date.js';

/**
 * Domain events carry identifiers and scalar context, never whole aggregates
 * (docs/02 §2.4). A handler needing more re-reads through the owner's contract.
 */
export interface DomainEvent<TName extends string = string, TPayload = unknown> {
  readonly eventId: string;
  readonly name: TName;
  readonly occurredAt: Instant;
  readonly correlationId: string;
  readonly payload: TPayload;
}

export type DomainEventHandler<E extends DomainEvent = DomainEvent> = (event: E) => Promise<void>;

/**
 * Dispatch happens after commit, so a subscriber never observes state that
 * later rolls back. At-least-once is the only honest assumption once this is
 * backed by a queue, so handlers must be idempotent and keyed by `eventId`.
 */
export interface EventBus {
  publish(event: DomainEvent): Promise<void>;
  publishAll(events: readonly DomainEvent[]): Promise<void>;
  subscribe<E extends DomainEvent>(name: E['name'], handler: DomainEventHandler<E>): void;
}

/** Collects events raised during a transaction for post-commit dispatch. */
export interface EventCollector {
  collect(event: DomainEvent): void;
  drain(): readonly DomainEvent[];
}

export const DOMAIN_EVENT_NAMES = {
  MEAL_EVENT_CREATED: 'meal.event.created',
  MEAL_EVENT_IDENTIFIED: 'meal.event.identified',
  MEAL_EVENT_CONFIRMED: 'meal.event.confirmed',
  MEAL_EVENT_FAILED: 'meal.event.failed',
  MEAL_EVENT_SUPERSEDED: 'meal.event.superseded',
  ACHIEVEMENT_UNLOCKED: 'gamification.achievement.unlocked',
  STREAK_ADVANCED: 'gamification.streak.advanced',
  XP_AWARDED: 'gamification.xp.awarded',
  DAILY_PROGRESS_RECOMPUTED: 'progress.daily.recomputed',
  COACH_MESSAGE_CREATED: 'coach.message.created',
} as const;

export type DomainEventName = (typeof DOMAIN_EVENT_NAMES)[keyof typeof DOMAIN_EVENT_NAMES];
