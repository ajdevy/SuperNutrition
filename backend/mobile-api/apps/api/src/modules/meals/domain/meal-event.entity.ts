import type {
  MealEventId,
  MealItemId,
  UserId,
  LocalDate,
  Instant,
  FoodRef,
  AttemptId,
  MealStatusKind,
  MealTypeKind,
  NutritionOrigin,
  CatalogSourceKind,
} from '@supernutrition/shared';

/** The three creation paths converge here. Nothing downstream branches on `source`. */
export type MealSource = PhotoSource | ManualSource | VoiceSource;

export interface PhotoSource {
  readonly kind: 'photo';
  readonly attemptId: AttemptId;
  readonly configurationVersion: string;
}

export interface ManualSource {
  readonly kind: 'manual';
}

export interface VoiceSource {
  readonly kind: 'voice';
  readonly transcript: string;
  readonly attemptId?: AttemptId;
}

export type MealStatus = MealStatusKind;

export interface MealItem {
  readonly id: MealItemId;
  readonly mealEventId: MealEventId;
  readonly position: number;
  readonly foodRef: FoodRef;
  readonly displayName: string;
  readonly servingUnit: string;
  readonly servingAmount: string;
  readonly gramWeight: string | null;
  readonly confidence: string | null;
  /** Principle 2: user confirmation outranks catalog data, which outranks inference. */
  readonly confirmedByUser: boolean;
}

export interface MealEvidence {
  readonly id: string;
  readonly mealEventId: MealEventId;
  readonly mealItemId: MealItemId | null;
  readonly attemptId: AttemptId;
  readonly type: 'candidate' | 'assumption' | 'abstention' | 'correction';
  readonly payload: Readonly<Record<string, unknown>>;
  readonly provenance: EvidenceProvenance;
}

export interface EvidenceProvenance {
  readonly route: 'local' | 'remote' | 'manual';
  readonly engineId: string;
  readonly modelVersion: string;
  readonly calibrationVersion: string;
  readonly contractVersion: string;
}

/**
 * AD-7: every displayed number carries catalog source, dataset version, and
 * serving basis. For a client-created meal this is persisted verbatim and is
 * never recomputed server-side (docs/01 §1.5).
 */
export interface NutritionSnapshot {
  readonly catalogSource: CatalogSourceKind;
  readonly datasetVersion: string;
  readonly servingBasis: ServingBasis;
  readonly origin: NutritionOrigin;
  readonly calories: string;
  readonly proteinG: string | null;
  readonly fatG: string | null;
  readonly carbG: string | null;
  readonly fiberG: string | null;
  readonly sugarG: string | null;
  readonly sodiumMg: string | null;
  readonly perItem: readonly PerItemNutrition[];
}

export interface ServingBasis {
  readonly unit: string;
  readonly amount: string;
  readonly gramWeight: string | null;
}

export interface PerItemNutrition {
  readonly foodRef: FoodRef;
  readonly calories: string;
  readonly proteinG: string | null;
  readonly fatG: string | null;
  readonly carbG: string | null;
  /** Absent is not zero. A partial item flags the aggregate (docs/08 §8.7). */
  readonly partial: boolean;
}

/**
 * The aggregate root. `confirmed` is terminal: a correction creates revision
 * N+1 through `supersedesId` rather than mutating history (docs/02 §2.3).
 */
export interface MealEvent {
  readonly id: MealEventId;
  readonly userId: UserId;
  readonly source: MealSource;
  readonly status: MealStatus;
  readonly occurredAt: Instant;
  /** Written at creation from the client snapshot, never derived later from UTC. */
  readonly localDate: LocalDate;
  readonly timezone: string;
  readonly mealType: MealTypeKind;
  readonly revision: number;
  readonly supersedesId: MealEventId | null;
  readonly items: readonly MealItem[];
  readonly nutrition: NutritionSnapshot | null;
  readonly evidence: readonly MealEvidence[];
  readonly createdAt: Instant;
  readonly confirmedAt: Instant | null;
}
