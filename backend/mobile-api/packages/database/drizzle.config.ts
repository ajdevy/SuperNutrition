import type { Config } from 'drizzle-kit';

export default {
  schema: './packages/database/src/schema/index.ts',
  out: './packages/database/src/migrations',
  dialect: 'postgresql',
  // Forward-only migrations (docs/05 §5.6). drizzle-kit generates; a bad
  // migration is corrected by a new one, never by a down migration.
  strict: true,
  verbose: true,
} satisfies Config;
