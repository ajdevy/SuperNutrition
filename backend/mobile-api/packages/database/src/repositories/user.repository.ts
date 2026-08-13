import { and, eq, isNull, sql } from 'drizzle-orm';
import { DrizzleRepository } from './base.repository.js';
import type { Database, TransactionHandle } from '../client.js';
import { users, type UserRow, type NewUserRow } from '../schema/users.js';

export class DrizzleUserRepository extends DrizzleRepository {
  constructor(db: Database) {
    super(db);
  }

  async findById(id: string, tx?: TransactionHandle): Promise<UserRow | null> {
    const found = await this.conn(tx)
      .select()
      .from(users)
      .where(and(eq(users.id, id), isNull(users.deletedAt)))
      .limit(1);
    return found[0] ?? null;
  }

  /**
   * The external ref is the installation identifier from the auth context. A
   * soft-deleted row keeps it, so this read deliberately does NOT filter
   * `deletedAt`: an erased account must be recognisable as erased rather than
   * look absent and be recreated under a new id.
   */
  async findByExternalRef(ref: string, tx?: TransactionHandle): Promise<UserRow | null> {
    const found = await this.conn(tx)
      .select()
      .from(users)
      .where(eq(users.externalRef, ref))
      .limit(1);
    return found[0] ?? null;
  }

  async create(user: NewUserRow, tx?: TransactionHandle): Promise<UserRow> {
    const inserted = await this.conn(tx).insert(users).values(user).returning();
    const created = inserted[0];
    if (!created) throw new Error('users insert returned no row');
    return created;
  }

  /**
   * First-access provisioning for a client that has no account creation flow.
   *
   * Written as an upsert rather than a read-then-insert because two concurrent
   * first requests from the same installation are the normal case on a mobile
   * client, and the check-then-act version loses that race against its own
   * unique index.
   */
  async ensureByExternalRef(ref: string, tx?: TransactionHandle): Promise<UserRow> {
    const upserted = await this.conn(tx)
      .insert(users)
      .values({ externalRef: ref })
      .onConflictDoUpdate({
        target: users.externalRef,
        set: { updatedAt: sql`now()` },
      })
      .returning();

    const user = upserted[0];
    if (!user) throw new Error('users upsert returned no row');
    return user;
  }

  /** Two-phase erasure: soft delete, then the fan-out over every ErasurePort. */
  async softDelete(id: string, tx?: TransactionHandle): Promise<boolean> {
    const deleted = await this.conn(tx)
      .update(users)
      .set({ status: 'deleted', deletedAt: sql`now()`, updatedAt: sql`now()` })
      .where(and(eq(users.id, id), isNull(users.deletedAt)))
      .returning({ id: users.id });

    return deleted.length > 0;
  }
}
