import type {
  Result,
  MealEventId,
  UserId,
  LocalDate,
  Instant,
  AttemptId,
} from '@supernutrition/shared';
import type { MealEvent, NutritionSnapshot, MealEvidence } from '../domain/meal-event.entity.js';

export interface MealItemInput {
  readonly foodRef: string;
  readonly displayName: string;
  readonly servingUnit: string;
  readonly servingAmount: string;
  readonly gramWeight?: string;
  readonly confidence?: string;
  readonly confirmedByUser: boolean;
}

interface CreateMealBase {
  readonly userId: UserId;
  readonly occurredAt: Instant;
  readonly localDate: LocalDate;
  readonly timezone: string;
  readonly mealType: string;
  readonly items: readonly MealItemInput[];
  /** Required for a confirmed meal. Persisted verbatim (docs/01 §1.5). */
  readonly nutrition: NutritionSnapshot;
}

export interface CreatePhotoMealCommand extends CreateMealBase {
  readonly source: 'photo';
  readonly attemptId: AttemptId;
  readonly configurationVersion: string;
  readonly evidence: readonly Omit<MealEvidence, 'id' | 'mealEventId'>[];
}

export interface CreateManualMealCommand extends CreateMealBase {
  readonly source: 'manual';
}

export interface CreateVoiceMealCommand extends CreateMealBase {
  readonly source: 'voice';
  readonly transcript: string;
  readonly attemptId?: AttemptId;
}

export type CreateMealCommand =
  CreatePhotoMealCommand | CreateManualMealCommand | CreateVoiceMealCommand;

export interface CorrectMealCommand {
  readonly mealEventId: MealEventId;
  readonly userId: UserId;
  readonly items: readonly MealItemInput[];
  readonly nutrition: NutritionSnapshot;
}

export type MealCommandFailure =
  'not-found' | 'ungrounded-nutrition' | 'invalid-items' | 'storage-unavailable';

/**
 * Use case contracts. Stage 2 declares them; Stage 3 implements.
 *
 * `rewards` and `coachMessage` are best-effort: a failing subscriber must not
 * fail meal creation, because logging a meal is the primary KPI (docs/02 §2.5).
 */
export interface CreateMealEvent {
  execute(command: CreateMealCommand): Promise<Result<MealEvent, MealCommandFailure>>;
}

export interface CorrectMealEvent {
  execute(command: CorrectMealCommand): Promise<Result<MealEvent, MealCommandFailure>>;
}
