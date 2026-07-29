import type { CreateMealEvent, CorrectMealEvent } from '../application/commands.js';
import type { GetMealEvent, ListMealEvents } from '../application/queries.js';
import type { MealEventResponse } from './meal.dto.js';

/**
 * Controller contract. Handlers are declared here and implemented in Stage 3;
 * this stage fixes the surface so route registration and validation wiring can
 * be reviewed without business logic.
 */
export interface MealController {
  createPhotoMeal(input: unknown, userId: string): Promise<MealEventResponse>;
  createManualMeal(input: unknown, userId: string): Promise<MealEventResponse>;
  createVoiceMeal(input: unknown, userId: string): Promise<MealEventResponse>;
  correctMeal(id: string, input: unknown, userId: string): Promise<MealEventResponse>;
  getMeal(id: string, userId: string): Promise<MealEventResponse>;
  listMeals(query: unknown, userId: string): Promise<unknown>;
  deleteMeal(id: string, userId: string): Promise<void>;
}

export interface MealControllerDeps {
  readonly createMeal: CreateMealEvent;
  readonly correctMeal: CorrectMealEvent;
  readonly getMeal: GetMealEvent;
  readonly listMeals: ListMealEvents;
}
