import { z } from 'zod';
import {
  AppError,
  ERROR_CODES,
  type FoodRef,
  type Instant,
  type LocalDate,
  type MealEventId,
  type UserId,
} from '@supernutrition/shared';
import type {
  CreateMealEvent,
  CorrectMealEvent,
  DeleteMealEvent,
  MealCommandFailure,
  MealItemInput,
} from '../application/commands.js';
import type {
  GetMealEvent,
  ListMealEvents,
  MealListFailure,
  MealReadFailure,
} from '../application/queries.js';
import type { MealEvent, MealEventFilters, NutritionSnapshot } from '../domain/index.js';
import {
  CorrectMealRequestSchema,
  CreateManualMealRequestSchema,
  CreatePhotoMealRequestSchema,
  CreateVoiceMealRequestSchema,
  ListMealsQuerySchema,
  MealIdParamSchema,
} from './meal.schemas.js';
import type { MealEventResponse, MealPageResponse } from './meal.dto.js';

/**
 * Controller contract. The route module owns transport; this owns the
 * translation between a validated request and a use case, and between a typed
 * failure and a Product API error code.
 */
export interface MealController {
  createPhotoMeal(input: unknown, userId: string): Promise<MealEventResponse>;
  createManualMeal(input: unknown, userId: string): Promise<MealEventResponse>;
  createVoiceMeal(input: unknown, userId: string): Promise<MealEventResponse>;
  correctMeal(id: string, input: unknown, userId: string): Promise<MealEventResponse>;
  getMeal(id: string, userId: string): Promise<MealEventResponse>;
  listMeals(query: unknown, userId: string): Promise<MealPageResponse>;
  deleteMeal(id: string, userId: string): Promise<void>;
}

export interface MealControllerDeps {
  readonly createMeal: CreateMealEvent;
  readonly correctMeal: CorrectMealEvent;
  readonly deleteMeal: DeleteMealEvent;
  readonly getMeal: GetMealEvent;
  readonly listMeals: ListMealEvents;
}

/**
 * Zod at the boundary (docs/06 §6.3). The parsed output is the only thing
 * passed inward — a raw request body never crosses into application code, so a
 * field nobody validated cannot reach a query.
 */
const parse = <T>(schema: z.ZodType<T>, input: unknown): T => {
  const result = schema.safeParse(input);
  if (result.success) return result.data;

  throw AppError.validation(
    'Request failed validation.',
    result.error.issues.map((issue) => ({
      field: issue.path.map(String).join('.') || '(root)',
      issue: issue.code,
    })),
  );
};

const NOT_FOUND = (): AppError =>
  AppError.notFound(ERROR_CODES.MEAL_NOT_FOUND, 'Meal event not found.');

/**
 * Every failure leaves as a Product API code, never a gateway code: the two
 * surfaces have separate taxonomies and a client branches on this one.
 */
const commandError = (failure: MealCommandFailure): AppError => {
  switch (failure) {
    case 'not-found':
      return NOT_FOUND();
    case 'ungrounded-nutrition':
      return AppError.validation('Nutrition must carry a catalog source and dataset version.', [
        { field: 'nutrition', issue: 'ungrounded' },
      ]);
    case 'invalid-items':
      return AppError.validation('A meal needs at least one item.', [
        { field: 'items', issue: 'empty' },
      ]);
    case 'storage-unavailable':
      return AppError.internal('Meal storage is unavailable.');
  }
};

const readError = (failure: MealReadFailure): AppError =>
  failure === 'not-found' ? NOT_FOUND() : AppError.internal('Meal storage is unavailable.');

const listError = (failure: MealListFailure): AppError =>
  failure === 'invalid-cursor'
    ? AppError.validation('Cursor is not a valid page cursor.', [
        { field: 'cursor', issue: 'malformed' },
      ])
    : AppError.internal('Meal storage is unavailable.');

const toItemInputs = (
  items: readonly z.infer<typeof CorrectMealRequestSchema>['items'][number][],
): readonly MealItemInput[] =>
  items.map((item) => ({
    foodRef: item.foodRef,
    displayName: item.displayName,
    servingUnit: item.servingUnit,
    servingAmount: item.servingAmount,
    ...(item.gramWeight === undefined ? {} : { gramWeight: item.gramWeight }),
    ...(item.confidence === undefined ? {} : { confidence: item.confidence }),
    confirmedByUser: item.confirmedByUser,
  }));

/**
 * Normalized where the brand is minted.
 *
 * The schema has proven the value is an ISO instant with an explicit offset;
 * two clients may still spell the same moment differently. Storing the spelling
 * verbatim would make a created meal and the same meal read back disagree on a
 * field the client compares.
 */
const toInstant = (value: string): Instant => new Date(value).toISOString() as Instant;

/**
 * The schema has already proven every food reference is namespace-qualified, so
 * the brand is earned here rather than asserted at a call site that has not
 * seen the validation.
 */
const toNutrition = (
  nutrition: z.infer<typeof CorrectMealRequestSchema>['nutrition'],
): NutritionSnapshot => ({
  ...nutrition,
  perItem: nutrition.perItem.map((entry) => ({ ...entry, foodRef: entry.foodRef as FoodRef })),
});

export const toMealEventResponse = (event: MealEvent): MealEventResponse => ({
  id: event.id,
  userId: event.userId,
  source: event.source.kind,
  status: event.status,
  occurredAt: event.occurredAt,
  localDate: event.localDate,
  timezone: event.timezone,
  mealType: event.mealType,
  revision: event.revision,
  items: event.items.map((item) => ({
    id: item.id,
    position: item.position,
    foodRef: item.foodRef,
    displayName: item.displayName,
    servingUnit: item.servingUnit,
    servingAmount: item.servingAmount,
    gramWeight: item.gramWeight,
    confidence: item.confidence,
    confirmedByUser: item.confirmedByUser,
  })),
  nutrition: event.nutrition
    ? {
        origin: event.nutrition.origin,
        catalogSource: event.nutrition.catalogSource,
        datasetVersion: event.nutrition.datasetVersion,
        servingBasis: {
          unit: event.nutrition.servingBasis.unit,
          amount: event.nutrition.servingBasis.amount,
          gramWeight: event.nutrition.servingBasis.gramWeight,
        },
        calories: event.nutrition.calories,
        proteinG: event.nutrition.proteinG,
        fatG: event.nutrition.fatG,
        carbG: event.nutrition.carbG,
        fiberG: event.nutrition.fiberG,
        sugarG: event.nutrition.sugarG,
        sodiumMg: event.nutrition.sodiumMg,
        perItem: event.nutrition.perItem.map((entry) => ({
          foodRef: entry.foodRef,
          calories: entry.calories,
          proteinG: entry.proteinG,
          fatG: entry.fatG,
          carbG: entry.carbG,
          partial: entry.partial,
        })),
      }
    : null,
  // Best-effort by contract: a failing subscriber must not fail meal creation,
  // and no subscriber is wired yet, so both are honestly null (docs/06 §6.6).
  rewards: null,
  coachMessage: null,
  createdAt: event.createdAt,
});

export class MealControllerImpl implements MealController {
  readonly #deps: MealControllerDeps;

  constructor(deps: MealControllerDeps) {
    this.#deps = deps;
  }

  async createPhotoMeal(input: unknown, userId: string): Promise<MealEventResponse> {
    const request = parse(CreatePhotoMealRequestSchema, input);
    const created = await this.#deps.createMeal.execute({
      source: 'photo',
      userId: userId as UserId,
      occurredAt: toInstant(request.occurredAt),
      localDate: request.localDate as LocalDate,
      timezone: request.timezone,
      mealType: request.mealType,
      items: toItemInputs(request.items),
      nutrition: toNutrition(request.nutrition),
      attemptId: request.attemptId as never,
      configurationVersion: request.configurationVersion,
      // Evidence is accepted so the client contract is stable, and dropped:
      // `inference_evidence` is a separate table and a separate stage. Storing
      // half of it now would leave provenance that cannot be trusted.
      evidence: [],
    });

    if (!created.ok) throw commandError(created.error);
    return toMealEventResponse(created.value);
  }

  async createManualMeal(input: unknown, userId: string): Promise<MealEventResponse> {
    const request = parse(CreateManualMealRequestSchema, input);
    const created = await this.#deps.createMeal.execute({
      source: 'manual',
      userId: userId as UserId,
      occurredAt: toInstant(request.occurredAt),
      localDate: request.localDate as LocalDate,
      timezone: request.timezone,
      mealType: request.mealType,
      items: toItemInputs(request.items),
      nutrition: toNutrition(request.nutrition),
    });

    if (!created.ok) throw commandError(created.error);
    return toMealEventResponse(created.value);
  }

  async createVoiceMeal(input: unknown, userId: string): Promise<MealEventResponse> {
    const request = parse(CreateVoiceMealRequestSchema, input);
    const created = await this.#deps.createMeal.execute({
      source: 'voice',
      userId: userId as UserId,
      occurredAt: toInstant(request.occurredAt),
      localDate: request.localDate as LocalDate,
      timezone: request.timezone,
      mealType: request.mealType,
      items: toItemInputs(request.items),
      nutrition: toNutrition(request.nutrition),
      // User-authored text: stored on the event, never written to logs or
      // telemetry (docs/06 §6.6).
      transcript: request.transcript,
      ...(request.attemptId === undefined ? {} : { attemptId: request.attemptId as never }),
    });

    if (!created.ok) throw commandError(created.error);
    return toMealEventResponse(created.value);
  }

  async correctMeal(id: string, input: unknown, userId: string): Promise<MealEventResponse> {
    const params = parse(MealIdParamSchema, { id });
    const request = parse(CorrectMealRequestSchema, input);

    const corrected = await this.#deps.correctMeal.execute({
      mealEventId: params.id as MealEventId,
      userId: userId as UserId,
      items: toItemInputs(request.items),
      nutrition: toNutrition(request.nutrition),
    });

    if (!corrected.ok) throw commandError(corrected.error);
    return toMealEventResponse(corrected.value);
  }

  async getMeal(id: string, userId: string): Promise<MealEventResponse> {
    const params = parse(MealIdParamSchema, { id });
    const found = await this.#deps.getMeal.execute(params.id as MealEventId, userId as UserId);

    if (!found.ok) throw readError(found.error);
    return toMealEventResponse(found.value);
  }

  async listMeals(query: unknown, userId: string): Promise<MealPageResponse> {
    const request = parse(ListMealsQuerySchema, query);

    const filters: MealEventFilters = {
      ...(request.from === undefined ? {} : { from: request.from as LocalDate }),
      ...(request.to === undefined ? {} : { to: request.to as LocalDate }),
      ...(request.mealType === undefined ? {} : { mealType: request.mealType }),
      ...(request.source === undefined ? {} : { source: request.source }),
    };

    const page = await this.#deps.listMeals.execute(
      userId as UserId,
      {
        limit: request.limit,
        ...(request.cursor === undefined ? {} : { cursor: request.cursor }),
      },
      filters,
    );

    if (!page.ok) throw listError(page.error);
    return {
      data: page.value.data.map(toMealEventResponse),
      page: page.value.page,
    };
  }

  async deleteMeal(id: string, userId: string): Promise<void> {
    const params = parse(MealIdParamSchema, { id });
    const deleted = await this.#deps.deleteMeal.execute(params.id as MealEventId, userId as UserId);
    if (!deleted.ok) throw commandError(deleted.error);
  }
}
