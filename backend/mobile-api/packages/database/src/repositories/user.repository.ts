import { DrizzleRepository } from './base.repository.js';
import type { Database, TransactionHandle } from '../client.js';
import type { UserRow, NewUserRow } from '../schema/users.js';

export class DrizzleUserRepository extends DrizzleRepository {
  constructor(db: Database) {
    super(db);
  }

  async findById(_id: string, _tx?: TransactionHandle): Promise<UserRow | null> {
    return this.notImplemented('findById');
  }

  async findByExternalRef(_ref: string, _tx?: TransactionHandle): Promise<UserRow | null> {
    return this.notImplemented('findByExternalRef');
  }

  async create(_user: NewUserRow, _tx?: TransactionHandle): Promise<UserRow> {
    return this.notImplemented('create');
  }

  /** Two-phase erasure: soft delete, then the fan-out over every ErasurePort. */
  async softDelete(_id: string, _tx?: TransactionHandle): Promise<void> {
    return this.notImplemented('softDelete');
  }
}
