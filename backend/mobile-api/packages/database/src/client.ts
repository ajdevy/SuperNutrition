import { drizzle, type PostgresJsDatabase } from 'drizzle-orm/postgres-js';
import postgres from 'postgres';
import * as schema from './schema/index.js';

export type Database = PostgresJsDatabase<typeof schema>;
export type TransactionHandle = Parameters<Parameters<Database['transaction']>[0]>[0];

export interface DatabaseConfig {
  readonly url: string;
  readonly maxConnections: number;
  readonly idleTimeoutSeconds: number;
  readonly connectTimeoutSeconds: number;
}

export const createDatabase = (
  config: DatabaseConfig,
): { db: Database; close: () => Promise<void> } => {
  const sql = postgres(config.url, {
    max: config.maxConnections,
    idle_timeout: config.idleTimeoutSeconds,
    connect_timeout: config.connectTimeoutSeconds,
  });
  const db = drizzle(sql, { schema });
  return { db, close: () => sql.end({ timeout: 5 }) };
};

/**
 * Repository methods accept an optional handle so a use case can compose
 * several repositories into one transaction (docs/05 §5.7). Domain events are
 * collected during the transaction and dispatched after commit.
 */
export const withTransaction = async <T>(
  db: Database,
  fn: (tx: TransactionHandle) => Promise<T>,
): Promise<T> => db.transaction(fn);

export { schema };
