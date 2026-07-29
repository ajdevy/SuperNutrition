import { DrizzleRepository } from './base.repository.js';
import type { Database, TransactionHandle } from '../client.js';
import type { MealEventRow, NewMealEventRow } from '../schema/meal-events.js';
import type { NewMealItemRow } from '../schema/meal-items.js';
import type { NewNutritionSnapshotRow } from '../schema/nutrition-snapshots.js';

export interface MealEventPageQuery {
  readonly userId: string;
  readonly limit: number;
  readonly cursor?: string;
  readonly from?: string;
  readonly to?: string;
}

/**
 * Implements the `MealEventRepository` port from the meals module.
 *
 * Ownership is filtered here, not in a controller: a repository method that can
 * return another user's row does not exist (docs/09 §9.2).
 */
export class DrizzleMealEventRepository extends DrizzleRepository {
  constructor(db: Database) {
    super(db);
  }

  async findById(
    _id: string,
    _userId: string,
    _tx?: TransactionHandle,
  ): Promise<MealEventRow | null> {
    return this.notImplemented('findById');
  }

  async findPage(_query: MealEventPageQuery, _tx?: TransactionHandle): Promise<MealEventRow[]> {
    return this.notImplemented('findPage');
  }

  async save(
    _event: NewMealEventRow,
    _items: readonly NewMealItemRow[],
    _snapshot: NewNutritionSnapshotRow | null,
    _tx?: TransactionHandle,
  ): Promise<MealEventRow> {
    return this.notImplemented('save');
  }

  /** Corrections create revision N+1; the original row is retained. */
  async supersede(
    _originalId: string,
    _replacement: NewMealEventRow,
    _tx?: TransactionHandle,
  ): Promise<MealEventRow> {
    return this.notImplemented('supersede');
  }

  async softDelete(_id: string, _userId: string, _tx?: TransactionHandle): Promise<void> {
    return this.notImplemented('softDelete');
  }
}
