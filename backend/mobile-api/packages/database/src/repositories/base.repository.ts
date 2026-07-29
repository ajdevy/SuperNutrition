import type { Database, TransactionHandle } from '../client.js';

export class NotImplementedError extends Error {
  constructor(operation: string) {
    super(`Not implemented in the Stage 2 skeleton: ${operation}`);
    this.name = 'NotImplementedError';
  }
}

/** Shared base: holds the connection and resolves an optional transaction. */
export abstract class DrizzleRepository {
  protected constructor(protected readonly db: Database) {}

  protected conn(tx?: TransactionHandle): Database | TransactionHandle {
    return tx ?? this.db;
  }

  protected notImplemented(operation: string): never {
    throw new NotImplementedError(`${this.constructor.name}.${operation}`);
  }
}
