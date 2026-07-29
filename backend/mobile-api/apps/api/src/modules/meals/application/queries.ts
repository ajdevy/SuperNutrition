import type { Result, MealEventId, UserId, Page, PageRequest } from '@supernutrition/shared';
import type { MealEvent, MealEventFilters } from '../domain/index.js';

export interface GetMealEvent {
  execute(id: MealEventId, userId: UserId): Promise<Result<MealEvent, 'not-found'>>;
}

export interface ListMealEvents {
  execute(
    userId: UserId,
    page: PageRequest,
    filters: MealEventFilters,
  ): Promise<Result<Page<MealEvent>, 'storage-unavailable'>>;
}
