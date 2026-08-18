import {
  ok,
  err,
  encodePageCursor,
  decodePageCursor,
  type Result,
  type Page,
  type PageRequest,
  type MealEventId,
  type MealItemId,
  type UserId,
  type LocalDate,
  type Instant,
  type FoodRef,
  type AttemptId,
} from '@supernutrition/shared';
import type {
  DrizzleMealEventRepository,
  MealEventAggregateRow,
  MealEventPageQuery,
} from '@supernutrition/database';
import type {
  MealEvent,
  MealItem,
  MealSource,
  NutritionSnapshot,
  PerItemNutrition,
  ServingBasis,
  MealEventFilters,
  MealEventRepository,
  MealRepositoryFailure,
} from '../domain/index.js';

/**
 * Binds the `MealEventRepository` port to the Drizzle adapter in
 * packages/database.
 *
 * Everything above this file speaks domain types and `Result`; everything below
 * it speaks rows. Translation happens once, here, which is why neither side has
 * to know the other exists (docs/05 §5.7).
 */

type EventRow = MealEventAggregateRow['event'];
type ItemRow = MealEventAggregateRow['items'][number];
type SnapshotRow = NonNullable<MealEventAggregateRow['snapshot']>;

const toSource = (row: EventRow): MealSource => {
  if (row.source === 'photo') {
    return {
      kind: 'photo',
      attemptId: (row.attemptId ?? '') as AttemptId,
      configurationVersion: row.configurationVersion ?? '',
    };
  }
  if (row.source === 'voice') {
    return {
      kind: 'voice',
      transcript: row.transcript ?? '',
      ...(row.attemptId ? { attemptId: row.attemptId as AttemptId } : {}),
    };
  }
  return { kind: 'manual' };
};

const toItem = (row: ItemRow): MealItem => ({
  id: row.id as MealItemId,
  mealEventId: row.mealEventId as MealEventId,
  position: row.position,
  foodRef: row.foodRef as FoodRef,
  displayName: row.displayName,
  servingUnit: row.servingUnit,
  servingAmount: row.servingAmount,
  gramWeight: row.gramWeight,
  confidence: row.confidence,
  confirmedByUser: row.confirmedByUser,
});

const toNutrition = (row: SnapshotRow): NutritionSnapshot => ({
  catalogSource: row.catalogSource as NutritionSnapshot['catalogSource'],
  datasetVersion: row.datasetVersion,
  servingBasis: row.servingBasis as ServingBasis,
  origin: row.origin,
  calories: row.calories,
  proteinG: row.proteinG,
  fatG: row.fatG,
  carbG: row.carbG,
  fiberG: row.fiberG,
  sugarG: row.sugarG,
  sodiumMg: row.sodiumMg,
  perItem: row.perItem as readonly PerItemNutrition[],
});

const instantOf = (value: Date): Instant => value.toISOString() as Instant;

export const toDomainMealEvent = (row: MealEventAggregateRow): MealEvent => ({
  id: row.event.id as MealEventId,
  userId: row.event.userId as UserId,
  source: toSource(row.event),
  status: row.event.status,
  occurredAt: instantOf(row.event.occurredAt),
  localDate: row.event.localDate as LocalDate,
  timezone: row.event.timezone,
  mealType: row.event.mealType,
  revision: row.event.revision,
  supersedesId: row.event.supersedesId as MealEventId | null,
  items: row.items.map(toItem),
  nutrition: row.snapshot ? toNutrition(row.snapshot) : null,
  // Evidence persistence is a separate table and a separate stage; an empty
  // array is honest here, a fabricated one would not be.
  evidence: [],
  createdAt: instantOf(row.event.createdAt),
  confirmedAt: row.event.confirmedAt ? instantOf(row.event.confirmedAt) : null,
});

interface EventWriteRows {
  readonly event: Parameters<DrizzleMealEventRepository['save']>[0];
  readonly items: Parameters<DrizzleMealEventRepository['save']>[1];
  readonly snapshot: Parameters<DrizzleMealEventRepository['save']>[2];
}

export const toWriteRows = (event: MealEvent): EventWriteRows => ({
  event: {
    id: event.id,
    userId: event.userId,
    source: event.source.kind,
    status: event.status,
    occurredAt: new Date(event.occurredAt),
    localDate: event.localDate,
    timezone: event.timezone,
    mealType: event.mealType,
    revision: event.revision,
    supersedesId: event.supersedesId,
    attemptId: event.source.kind === 'manual' ? null : (event.source.attemptId ?? null),
    configurationVersion: event.source.kind === 'photo' ? event.source.configurationVersion : null,
    transcript: event.source.kind === 'voice' ? event.source.transcript : null,
    createdAt: new Date(event.createdAt),
    confirmedAt: event.confirmedAt ? new Date(event.confirmedAt) : null,
  },
  items: event.items.map((item) => ({
    id: item.id,
    mealEventId: event.id,
    position: item.position,
    foodRef: item.foodRef,
    displayName: item.displayName,
    servingUnit: item.servingUnit,
    servingAmount: item.servingAmount,
    gramWeight: item.gramWeight,
    confidence: item.confidence,
    confirmedByUser: item.confirmedByUser,
  })),
  snapshot: event.nutrition
    ? {
        mealEventId: event.id,
        catalogSource: event.nutrition.catalogSource,
        datasetVersion: event.nutrition.datasetVersion,
        servingBasis: event.nutrition.servingBasis,
        origin: event.nutrition.origin,
        calories: event.nutrition.calories,
        proteinG: event.nutrition.proteinG,
        fatG: event.nutrition.fatG,
        carbG: event.nutrition.carbG,
        fiberG: event.nutrition.fiberG,
        sugarG: event.nutrition.sugarG,
        sodiumMg: event.nutrition.sodiumMg,
        perItem: event.nutrition.perItem,
      }
    : null,
});

/**
 * PostgreSQL reports a unique-constraint breach as SQLSTATE 23505.
 *
 * Matched on the code rather than the message: messages are localised and
 * carry table and value detail that must not reach a domain type or a log line.
 */
const isUniqueViolation = (cause: unknown): boolean =>
  typeof cause === 'object' && cause !== null && (cause as { code?: unknown }).code === '23505';

export class DrizzleMealEventRepositoryAdapter implements MealEventRepository {
  readonly #rows: DrizzleMealEventRepository;

  constructor(rows: DrizzleMealEventRepository) {
    this.#rows = rows;
  }

  async findById(
    id: MealEventId,
    userId: UserId,
  ): Promise<Result<MealEvent, MealRepositoryFailure>> {
    try {
      const row = await this.#rows.findById(id, userId);
      return row ? ok(toDomainMealEvent(row)) : err('not-found');
    } catch {
      return err('storage-unavailable');
    }
  }

  async findPage(
    userId: UserId,
    page: PageRequest,
    filters: MealEventFilters,
  ): Promise<Result<Page<MealEvent>, MealRepositoryFailure>> {
    let cursor: MealEventPageQuery['cursor'];
    if (page.cursor !== undefined) {
      const decoded = decodePageCursor(page.cursor);
      if (!decoded.ok) return err('invalid-cursor');
      cursor = decoded.value;
    }

    try {
      const result = await this.#rows.findPage({
        userId,
        limit: page.limit,
        ...(cursor ? { cursor } : {}),
        ...(filters.from ? { from: filters.from } : {}),
        ...(filters.to ? { to: filters.to } : {}),
        ...(filters.mealType ? { mealType: filters.mealType } : {}),
        ...(filters.source ? { source: filters.source } : {}),
      });

      const data = result.rows.map(toDomainMealEvent);
      const last = data[data.length - 1];
      return ok({
        data,
        page: {
          // A cursor is issued only when there is something after it. Handing
          // back one on the last page invites a request that can only be empty.
          nextCursor:
            result.hasMore && last
              ? encodePageCursor({ sortKey: last.occurredAt, id: last.id })
              : null,
          hasMore: result.hasMore,
        },
      });
    } catch {
      return err('storage-unavailable');
    }
  }

  async save(event: MealEvent): Promise<Result<MealEvent, MealRepositoryFailure>> {
    const rows = toWriteRows(event);
    try {
      const saved = await this.#rows.save(rows.event, rows.items, rows.snapshot);
      return ok(toDomainMealEvent(saved));
    } catch {
      return err('storage-unavailable');
    }
  }

  async supersede(
    originalId: MealEventId,
    replacement: MealEvent,
  ): Promise<Result<MealEvent, MealRepositoryFailure>> {
    const rows = toWriteRows(replacement);
    try {
      const saved = await this.#rows.supersede(originalId, rows.event, rows.items, rows.snapshot);
      return saved ? ok(toDomainMealEvent(saved)) : err('not-found');
    } catch (cause) {
      // M-01 — the select-then-insert above is not atomic; the unique index on
      // `supersedes_id` is the real guard. Two concurrent corrections of one
      // meal can both see no successor, and the loser hits that index.
      //
      // Every throw used to become `storage-unavailable`, so the loser of a race
      // the database resolved correctly was told the server was broken — a 500,
      // for a request whose honest answer is the one a stale client already
      // handles. The outcome is identical to the pre-checked case above:
      // somebody else corrected this meal first.
      if (isUniqueViolation(cause)) return err('not-found');
      return err('storage-unavailable');
    }
  }

  async softDelete(id: MealEventId, userId: UserId): Promise<Result<void, MealRepositoryFailure>> {
    try {
      const deleted = await this.#rows.softDelete(id, userId);
      return deleted ? ok(undefined) : err('not-found');
    } catch {
      return err('storage-unavailable');
    }
  }
}
