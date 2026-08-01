import {
  and,
  desc,
  eq,
  gte,
  inArray,
  isNull,
  lt,
  lte,
  notExists,
  or,
  sql,
  type SQL,
} from 'drizzle-orm';
import { alias, QueryBuilder } from 'drizzle-orm/pg-core';
import type { PageCursor } from '@supernutrition/shared';
import { DrizzleRepository } from './base.repository.js';
import type { Database, TransactionHandle } from '../client.js';
import { mealEvents, type MealEventRow, type NewMealEventRow } from '../schema/meal-events.js';
import { mealItems, type MealItemRow, type NewMealItemRow } from '../schema/meal-items.js';
import {
  nutritionSnapshots,
  type NutritionSnapshotRow,
  type NewNutritionSnapshotRow,
} from '../schema/nutrition-snapshots.js';

/** One aggregate as it exists in storage: root, children, frozen snapshot. */
export interface MealEventAggregateRow {
  readonly event: MealEventRow;
  readonly items: readonly MealItemRow[];
  readonly snapshot: NutritionSnapshotRow | null;
}

export interface MealEventPageQuery {
  readonly userId: string;
  readonly limit: number;
  readonly cursor?: PageCursor;
  readonly from?: string;
  readonly to?: string;
  readonly mealType?: MealEventRow['mealType'];
  readonly source?: MealEventRow['source'];
}

export interface MealEventPageRows {
  readonly rows: readonly MealEventAggregateRow[];
  readonly hasMore: boolean;
}

/**
 * A correction retains the original row and inserts revision N+1 pointing back
 * at it (docs/04 §4.4). History is therefore intact, but a superseded row is no
 * longer current: listing it would show one lunch twice and count its calories
 * twice. The feed asks for rows nothing supersedes.
 *
 * The subquery deliberately does NOT skip a deleted replacement. Having been
 * superseded is a permanent historical fact, and it does not become untrue when
 * the replacement is later deleted — filtering on the replacement's
 * `deleted_at` would make deleting a corrected meal resurrect the revision the
 * user had already replaced.
 */
const notSuperseded = (): SQL => {
  const superseding = alias(mealEvents, 'superseding');
  return notExists(
    new QueryBuilder()
      .select({ one: sql`1` })
      .from(superseding)
      .where(eq(superseding.supersedesId, mealEvents.id)),
  );
};

/**
 * Keyset predicate for `occurred_at DESC, id DESC`. The id is a tiebreaker, not
 * decoration: two meals logged in the same second are ordinary, and a cursor on
 * the timestamp alone would repeat or skip every row sharing it.
 */
const afterCursor = (cursor: PageCursor): SQL | undefined => {
  const at = new Date(cursor.sortKey);
  return or(
    lt(mealEvents.occurredAt, at),
    and(eq(mealEvents.occurredAt, at), lt(mealEvents.id, cursor.id)),
  );
};

/**
 * Exported so the SQL a page actually runs is the SQL under test. A condition
 * builder verified separately from its caller is a condition builder that can
 * drift from it.
 */
export const mealEventPageConditions = (query: MealEventPageQuery): SQL | undefined =>
  and(
    eq(mealEvents.userId, query.userId),
    isNull(mealEvents.deletedAt),
    notSuperseded(),
    query.from ? gte(mealEvents.localDate, query.from) : undefined,
    query.to ? lte(mealEvents.localDate, query.to) : undefined,
    query.mealType ? eq(mealEvents.mealType, query.mealType) : undefined,
    query.source ? eq(mealEvents.source, query.source) : undefined,
    query.cursor ? afterCursor(query.cursor) : undefined,
  );

/** Ownership is part of the identity predicate, never a separate check. */
export const mealEventByIdConditions = (id: string, userId: string): SQL | undefined =>
  and(eq(mealEvents.id, id), eq(mealEvents.userId, userId), isNull(mealEvents.deletedAt));

/**
 * Implements the `MealEventRepository` port from the meals module.
 *
 * Ownership is filtered here, not in a controller: a repository method that can
 * return another user's row does not exist (docs/09 §9.2). Every read also
 * excludes soft-deleted rows, so erasure is honoured by construction rather
 * than by every call site remembering to ask.
 */
export class DrizzleMealEventRepository extends DrizzleRepository {
  constructor(db: Database) {
    super(db);
  }

  async findById(
    id: string,
    userId: string,
    tx?: TransactionHandle,
  ): Promise<MealEventAggregateRow | null> {
    const conn = this.conn(tx);
    const found = await conn
      .select()
      .from(mealEvents)
      .where(mealEventByIdConditions(id, userId))
      .limit(1);

    const event = found[0];
    if (!event) return null;

    const [items, snapshots] = await Promise.all([
      conn.select().from(mealItems).where(eq(mealItems.mealEventId, event.id)),
      conn
        .select()
        .from(nutritionSnapshots)
        .where(eq(nutritionSnapshots.mealEventId, event.id))
        .limit(1),
    ]);

    return {
      event,
      items: [...items].sort((a, b) => a.position - b.position),
      snapshot: snapshots[0] ?? null,
    };
  }

  /**
   * Cursor pagination on `occurred_at DESC`. One extra row is fetched rather
   * than a second COUNT query: `hasMore` is the only fact the client needs, and
   * counting a filtered table to learn a boolean is the expensive way to learn
   * it.
   */
  async findPage(query: MealEventPageQuery, tx?: TransactionHandle): Promise<MealEventPageRows> {
    const conn = this.conn(tx);
    const found = await conn
      .select()
      .from(mealEvents)
      .where(mealEventPageConditions(query))
      .orderBy(desc(mealEvents.occurredAt), desc(mealEvents.id))
      .limit(query.limit + 1);

    const hasMore = found.length > query.limit;
    const events = hasMore ? found.slice(0, query.limit) : found;
    if (events.length === 0) return { rows: [], hasMore: false };

    const ids = events.map((event) => event.id);
    const [items, snapshots] = await Promise.all([
      conn.select().from(mealItems).where(inArray(mealItems.mealEventId, ids)),
      conn.select().from(nutritionSnapshots).where(inArray(nutritionSnapshots.mealEventId, ids)),
    ]);

    return {
      rows: events.map((event) => ({
        event,
        items: items
          .filter((item) => item.mealEventId === event.id)
          .sort((a, b) => a.position - b.position),
        snapshot: snapshots.find((snapshot) => snapshot.mealEventId === event.id) ?? null,
      })),
      hasMore,
    };
  }

  /** One aggregate, one transaction (docs/04 §4.4). */
  async save(
    event: NewMealEventRow,
    items: readonly NewMealItemRow[],
    snapshot: NewNutritionSnapshotRow | null,
    tx?: TransactionHandle,
  ): Promise<MealEventAggregateRow> {
    return tx
      ? this.#insertAggregate(tx, event, items, snapshot)
      : this.db.transaction((handle) => this.#insertAggregate(handle, event, items, snapshot));
  }

  async #insertAggregate(
    conn: Database | TransactionHandle,
    event: NewMealEventRow,
    items: readonly NewMealItemRow[],
    snapshot: NewNutritionSnapshotRow | null,
  ): Promise<MealEventAggregateRow> {
    const inserted = await conn.insert(mealEvents).values(event).returning();
    const root = inserted[0];
    if (!root) throw new Error('meal_events insert returned no row');

    const insertedItems = items.length
      ? await conn
          .insert(mealItems)
          .values(items.map((item) => ({ ...item, mealEventId: root.id })))
          .returning()
      : [];

    const insertedSnapshot = snapshot
      ? await conn
          .insert(nutritionSnapshots)
          .values({ ...snapshot, mealEventId: root.id })
          .returning()
      : [];

    return { event: root, items: insertedItems, snapshot: insertedSnapshot[0] ?? null };
  }

  /**
   * Corrections create revision N+1; the original row is retained.
   *
   * The replacement points at what it supersedes, matching the self-referencing
   * foreign key's direction — the original is never rewritten, because Progress
   * and Gamification have already reacted to it (docs/02 §2.3).
   *
   * Returns null when the original is absent or owned by someone else, so a
   * correction cannot silently create a fresh meal out of a bad id.
   */
  async supersede(
    originalId: string,
    replacement: NewMealEventRow,
    items: readonly NewMealItemRow[],
    snapshot: NewNutritionSnapshotRow | null,
    tx?: TransactionHandle,
  ): Promise<MealEventAggregateRow | null> {
    const write = async (
      conn: Database | TransactionHandle,
    ): Promise<MealEventAggregateRow | null> => {
      const original = await conn
        .select({ id: mealEvents.id })
        .from(mealEvents)
        .where(mealEventByIdConditions(originalId, replacement.userId))
        .limit(1);
      if (!original[0]) return null;

      return this.#insertAggregate(
        conn,
        { ...replacement, supersedesId: originalId },
        items,
        snapshot,
      );
    };

    return tx ? write(tx) : this.db.transaction(write);
  }

  /** Returns false when nothing was deleted, which is a 404 and not a 204. */
  async softDelete(id: string, userId: string, tx?: TransactionHandle): Promise<boolean> {
    const conn = this.conn(tx);
    const deleted = await conn
      .update(mealEvents)
      .set({ deletedAt: sql`now()` })
      .where(mealEventByIdConditions(id, userId))
      .returning({ id: mealEvents.id });

    return deleted.length > 0;
  }
}
