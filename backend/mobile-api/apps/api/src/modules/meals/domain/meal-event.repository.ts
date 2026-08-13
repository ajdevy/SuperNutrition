import type {
  Result,
  MealEventId,
  UserId,
  LocalDate,
  Page,
  PageRequest,
  MealTypeKind,
  MealSourceKind,
} from '@supernutrition/shared';
import type { MealEvent } from './meal-event.entity.js';

/**
 * `invalid-cursor` is a repository failure rather than a thrown error: a cursor
 * is client input, and the only component that can judge whether an opaque
 * string is one of its own is the adapter that issued it.
 */
export type MealRepositoryFailure =
  'not-found' | 'conflict' | 'invalid-cursor' | 'storage-unavailable';

export interface MealEventFilters {
  readonly from?: LocalDate;
  readonly to?: LocalDate;
  readonly mealType?: MealTypeKind;
  readonly source?: MealSourceKind;
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
