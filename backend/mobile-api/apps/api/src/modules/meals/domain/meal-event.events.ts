import type { DomainEvent, MealEventId, UserId, LocalDate } from '@supernutrition/shared';

export interface MealEventConfirmedPayload {
  readonly mealEventId: MealEventId;
  readonly userId: UserId;
  readonly localDate: LocalDate;
  readonly mealType: string;
  readonly revision: number;
}

export type MealEventConfirmed = DomainEvent<'meal.event.confirmed', MealEventConfirmedPayload>;

export interface MealEventSupersededPayload {
  readonly originalId: MealEventId;
  readonly replacementId: MealEventId;
  readonly userId: UserId;
  readonly localDate: LocalDate;
}

export type MealEventSuperseded = DomainEvent<'meal.event.superseded', MealEventSupersededPayload>;
