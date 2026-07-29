import type {
  Result,
  MealEventId,
  UserId,
  LocalDate,
  Page,
  PageRequest,
} from '@supernutrition/shared';
import type { MealEvent } from './meal-event.entity.js';

export type MealRepositoryFailure = 'not-found' | 'conflict' | 'storage-unavailable';

export interface MealEventFilters {
  readonly from?: LocalDate;
  readonly to?: LocalDate;
  readonly mealType?: string;
  readonly source?: string;
}

/** Port. The Drizzle adapter implementing it lives in packages/database. */
export interface MealEventRepository {
  findById(id: MealEventId, userId: UserId): Promise<Result<MealEvent, MealRepositoryFailure>>;
  findPage(
    userId: UserId,
    page: PageRequest,
    filters: MealEventFilters,
  ): Promise<Result<Page<MealEvent>, MealRepositoryFailure>>;
  save(event: MealEvent): Promise<Result<MealEvent, MealRepositoryFailure>>;
  supersede(
    originalId: MealEventId,
    replacement: MealEvent,
  ): Promise<Result<MealEvent, MealRepositoryFailure>>;
  softDelete(id: MealEventId, userId: UserId): Promise<Result<void, MealRepositoryFailure>>;
}
