import {
  ok,
  err,
  type Result,
  type MealEventId,
  type UserId,
  type Page,
  type PageRequest,
} from '@supernutrition/shared';
import type { MealEvent, MealEventFilters, MealEventRepository } from '../domain/index.js';

/**
 * A read failure is not a 404.
 *
 * The port reports storage separately from absence because answering
 * "no such meal" during an outage tells a user their history is gone.
 */
export type MealReadFailure = 'not-found' | 'storage-unavailable';
export type MealListFailure = 'invalid-cursor' | 'storage-unavailable';

export interface GetMealEvent {
  execute(id: MealEventId, userId: UserId): Promise<Result<MealEvent, MealReadFailure>>;
}

export interface ListMealEvents {
  execute(
    userId: UserId,
    page: PageRequest,
    filters: MealEventFilters,
  ): Promise<Result<Page<MealEvent>, MealListFailure>>;
}

// --- implementations ---------------------------------------------------------

export class GetMealEventHandler implements GetMealEvent {
  readonly #repository: MealEventRepository;

  constructor(repository: MealEventRepository) {
    this.#repository = repository;
  }

  async execute(id: MealEventId, userId: UserId): Promise<Result<MealEvent, MealReadFailure>> {
    const found = await this.#repository.findById(id, userId);
    if (found.ok) return ok(found.value);
    return err(found.error === 'not-found' ? 'not-found' : 'storage-unavailable');
  }
}

export class ListMealEventsHandler implements ListMealEvents {
  readonly #repository: MealEventRepository;

  constructor(repository: MealEventRepository) {
    this.#repository = repository;
  }

  /** An empty page is a successful answer, not a failure. */
  async execute(
    userId: UserId,
    page: PageRequest,
    filters: MealEventFilters,
  ): Promise<Result<Page<MealEvent>, MealListFailure>> {
    const found = await this.#repository.findPage(userId, page, filters);
    if (found.ok) return ok(found.value);
    return err(found.error === 'invalid-cursor' ? 'invalid-cursor' : 'storage-unavailable');
  }
}
