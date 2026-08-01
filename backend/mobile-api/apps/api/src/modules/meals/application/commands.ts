import {
  ok,
  err,
  type Result,
  type MealEventId,
  type MealItemId,
  type UserId,
  type LocalDate,
  type Instant,
  type AttemptId,
  type FoodRef,
  type MealTypeKind,
} from '@supernutrition/shared';
import type {
  MealEvent,
  MealItem,
  MealSource,
  NutritionSnapshot,
  MealEvidence,
} from '../domain/meal-event.entity.js';
import type {
  MealEventRepository,
  MealRepositoryFailure,
} from '../domain/meal-event.repository.js';

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
  readonly mealType: MealTypeKind;
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

export interface DeleteMealEvent {
  execute(id: MealEventId, userId: UserId): Promise<Result<void, MealCommandFailure>>;
}

// --- implementations ---------------------------------------------------------

/** Injected so a handler is deterministic under test and has no ambient clock. */
export interface MealCommandDeps {
  readonly repository: MealEventRepository;
  readonly now: () => Date;
  readonly uuid: () => string;
}

/**
 * Only `not-found` survives the crossing. A conflict or an unusable cursor is a
 * storage-side fact the caller of a command cannot act on, and reporting either
 * as `not-found` would tell a client its meal is gone when it is not.
 */
const toCommandFailure = (failure: MealRepositoryFailure): MealCommandFailure =>
  failure === 'not-found' ? 'not-found' : 'storage-unavailable';

/**
 * AD-7: a stored number carries its catalog source, dataset version, and
 * serving basis. Zod rejects a snapshot missing them at the boundary; this
 * guard exists because the use case is also reachable from a future non-HTTP
 * caller, and an ungrounded number must not enter the database by either door.
 */
const groundedNutrition = (nutrition: NutritionSnapshot): boolean =>
  String(nutrition.catalogSource ?? '').trim().length > 0 &&
  String(nutrition.datasetVersion ?? '').trim().length > 0;

const sourceOf = (command: CreateMealCommand): MealSource => {
  if (command.source === 'photo') {
    return {
      kind: 'photo',
      attemptId: command.attemptId,
      configurationVersion: command.configurationVersion,
    };
  }
  if (command.source === 'voice') {
    return {
      kind: 'voice',
      transcript: command.transcript,
      ...(command.attemptId ? { attemptId: command.attemptId } : {}),
    };
  }
  return { kind: 'manual' };
};

const toItems = (
  inputs: readonly MealItemInput[],
  mealEventId: MealEventId,
  uuid: () => string,
): readonly MealItem[] =>
  inputs.map((input, index) => ({
    id: uuid() as MealItemId,
    mealEventId,
    position: index,
    foodRef: input.foodRef as FoodRef,
    displayName: input.displayName,
    servingUnit: input.servingUnit,
    servingAmount: input.servingAmount,
    gramWeight: input.gramWeight ?? null,
    confidence: input.confidence ?? null,
    confirmedByUser: input.confirmedByUser,
  }));

/**
 * The three creation paths converge here (docs/04 §4.4).
 *
 * `rewards` and `coachMessage` are absent rather than computed: Gamification
 * and Coach subscribe to the event, and logging a meal is the primary KPI, so
 * it may not depend on either being implemented (docs/02 §2.5).
 */
export class CreateMealEventHandler implements CreateMealEvent {
  readonly #deps: MealCommandDeps;

  constructor(deps: MealCommandDeps) {
    this.#deps = deps;
  }

  async execute(command: CreateMealCommand): Promise<Result<MealEvent, MealCommandFailure>> {
    if (command.items.length === 0) return err('invalid-items');
    if (!groundedNutrition(command.nutrition)) return err('ungrounded-nutrition');

    const id = this.#deps.uuid() as MealEventId;
    const createdAt = this.#deps.now().toISOString() as Instant;

    const event: MealEvent = {
      id,
      userId: command.userId,
      source: sourceOf(command),
      // A meal reaches the Product API only after the user confirmed it, and
      // `confirmed` is terminal: later changes create a revision (docs/02 §2.3).
      status: 'confirmed',
      occurredAt: command.occurredAt,
      localDate: command.localDate,
      timezone: command.timezone,
      mealType: command.mealType,
      revision: 1,
      supersedesId: null,
      items: toItems(command.items, id, this.#deps.uuid),
      nutrition: command.nutrition,
      evidence: [],
      createdAt,
      confirmedAt: createdAt,
    };

    const saved = await this.#deps.repository.save(event);
    return saved.ok ? ok(saved.value) : err(toCommandFailure(saved.error));
  }
}

/**
 * A correction never mutates history: it writes revision N+1 pointing at the
 * original, because Progress and Gamification have already reacted to the fact
 * the original recorded (docs/02 §2.3).
 */
export class CorrectMealEventHandler implements CorrectMealEvent {
  readonly #deps: MealCommandDeps;

  constructor(deps: MealCommandDeps) {
    this.#deps = deps;
  }

  async execute(command: CorrectMealCommand): Promise<Result<MealEvent, MealCommandFailure>> {
    if (command.items.length === 0) return err('invalid-items');
    if (!groundedNutrition(command.nutrition)) return err('ungrounded-nutrition');

    // Ownership is proven by the load, which filters on user id. A correction
    // aimed at another user's meal is indistinguishable from one aimed at a
    // meal that does not exist, and both answer `not-found` (docs/06 §6.2).
    const existing = await this.#deps.repository.findById(command.mealEventId, command.userId);
    if (!existing.ok) return err(toCommandFailure(existing.error));
    const original = existing.value;

    const id = this.#deps.uuid() as MealEventId;
    const createdAt = this.#deps.now().toISOString() as Instant;

    const replacement: MealEvent = {
      id,
      userId: original.userId,
      source: original.source,
      status: 'confirmed',
      occurredAt: original.occurredAt,
      localDate: original.localDate,
      timezone: original.timezone,
      mealType: original.mealType,
      revision: original.revision + 1,
      supersedesId: original.id,
      items: toItems(command.items, id, this.#deps.uuid),
      nutrition: command.nutrition,
      evidence: [],
      createdAt,
      confirmedAt: createdAt,
    };

    const saved = await this.#deps.repository.supersede(original.id, replacement);
    return saved.ok ? ok(saved.value) : err(toCommandFailure(saved.error));
  }
}

/** Soft delete. The row is retained; a rebuild of that local date is a later stage. */
export class DeleteMealEventHandler implements DeleteMealEvent {
  readonly #repository: MealEventRepository;

  constructor(repository: MealEventRepository) {
    this.#repository = repository;
  }

  async execute(id: MealEventId, userId: UserId): Promise<Result<void, MealCommandFailure>> {
    const deleted = await this.#repository.softDelete(id, userId);
    return deleted.ok ? ok(undefined) : err(toCommandFailure(deleted.error));
  }
}
