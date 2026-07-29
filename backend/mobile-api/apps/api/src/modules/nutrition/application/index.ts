import type { Result } from '@supernutrition/shared';
import type { FoodLookupRequest, FoodLookupResult, NutritionFailure } from '../domain/index.js';

export interface LookupFood {
  execute(request: FoodLookupRequest): Promise<Result<FoodLookupResult, NutritionFailure>>;
}
