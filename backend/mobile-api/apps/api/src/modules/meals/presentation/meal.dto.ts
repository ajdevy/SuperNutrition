/** Response DTOs. Wire shapes only — no domain types leak outward. */
export interface MealItemResponse {
  readonly id: string;
  readonly position: number;
  readonly foodRef: string;
  readonly displayName: string;
  readonly servingUnit: string;
  readonly servingAmount: string;
  readonly gramWeight: string | null;
  readonly confidence: string | null;
  readonly confirmedByUser: boolean;
}

export interface NutritionResponse {
  readonly origin: string;
  readonly catalogSource: string;
  readonly datasetVersion: string;
  readonly servingBasis: { unit: string; amount: string; gramWeight: string | null };
  readonly calories: string;
  readonly proteinG: string | null;
  readonly fatG: string | null;
  readonly carbG: string | null;
  readonly fiberG: string | null;
  readonly sugarG: string | null;
  readonly sodiumMg: string | null;
  readonly perItem: readonly PerItemNutritionResponse[];
}

/** Best-effort. Null when a subscriber failed; the meal is still created. */
export interface RewardsResponse {
  readonly xpAwarded: number;
  readonly achievementsUnlocked: readonly string[];
  readonly streak: { type: string; currentCount: number } | null;
}

export interface CoachMessageResponse {
  readonly type: string;
  readonly messageKey: string;
  readonly params: Readonly<Record<string, unknown>>;
}

export interface PerItemNutritionResponse {
  readonly foodRef: string;
  readonly calories: string;
  readonly proteinG: string | null;
  readonly fatG: string | null;
  readonly carbG: string | null;
  /** Absent is not zero. A partial item flags the aggregate (docs/08 §8.7). */
  readonly partial: boolean;
}

export interface MealEventResponse {
  readonly id: string;
  readonly userId: string;
  readonly source: string;
  readonly status: string;
  readonly occurredAt: string;
  readonly localDate: string;
  readonly timezone: string;
  readonly mealType: string;
  readonly revision: number;
  readonly items: readonly MealItemResponse[];
  readonly nutrition: NutritionResponse | null;
  readonly rewards: RewardsResponse | null;
  readonly coachMessage: CoachMessageResponse | null;
  readonly createdAt: string;
}

/** Cursor page (docs/06 §6.4). The cursor is opaque and validated on receipt. */
export interface MealPageResponse {
  readonly data: readonly MealEventResponse[];
  readonly page: { readonly nextCursor: string | null; readonly hasMore: boolean };
}
