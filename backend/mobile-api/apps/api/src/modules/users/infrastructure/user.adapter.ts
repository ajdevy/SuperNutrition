import { ok, err, type Result, type UserId, type Instant } from '@supernutrition/shared';
import type { DrizzleUserRepository } from '@supernutrition/database';
import type { User, UserFailure, UserRepository } from '../domain/index.js';

/** Binds the `UserRepository` port to the Drizzle adapter in packages/database. */

type UserRow = NonNullable<Awaited<ReturnType<DrizzleUserRepository['findById']>>>;

export const toDomainUser = (row: UserRow): User => ({
  id: row.id as UserId,
  externalRef: row.externalRef,
  status: row.status,
  createdAt: row.createdAt.toISOString() as Instant,
  deletedAt: row.deletedAt ? (row.deletedAt.toISOString() as Instant) : null,
});

export class DrizzleUserRepositoryAdapter implements UserRepository {
  readonly #rows: DrizzleUserRepository;

  constructor(rows: DrizzleUserRepository) {
    this.#rows = rows;
  }

  async findById(id: UserId): Promise<Result<User, UserFailure>> {
    try {
      const row = await this.#rows.findById(id);
      return row ? ok(toDomainUser(row)) : err('not-found');
    } catch {
      return err('storage-unavailable');
    }
  }

  async findByExternalRef(externalRef: string): Promise<Result<User, UserFailure>> {
    try {
      const row = await this.#rows.findByExternalRef(externalRef);
      return row ? ok(toDomainUser(row)) : err('not-found');
    } catch {
      return err('storage-unavailable');
    }
  }

  async ensureByExternalRef(externalRef: string): Promise<Result<User, UserFailure>> {
    try {
      const row = await this.#rows.ensureByExternalRef(externalRef);
      // The unique external ref survives erasure, so the upsert can hand back a
      // deleted account. Reporting it absent is the point: an erased user does
      // not come back by presenting the same installation id.
      return row.deletedAt ? err('not-found') : ok(toDomainUser(row));
    } catch {
      return err('storage-unavailable');
    }
  }

  async create(user: User): Promise<Result<User, UserFailure>> {
    try {
      const row = await this.#rows.create({
        id: user.id,
        externalRef: user.externalRef,
        status: user.status,
      });
      return ok(toDomainUser(row));
    } catch {
      return err('storage-unavailable');
    }
  }

  async softDelete(id: UserId): Promise<Result<void, UserFailure>> {
    try {
      const deleted = await this.#rows.softDelete(id);
      return deleted ? ok(undefined) : err('not-found');
    } catch {
      return err('storage-unavailable');
    }
  }
}
