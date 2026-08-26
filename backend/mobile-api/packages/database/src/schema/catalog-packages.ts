import {
  pgTable,
  uuid,
  text,
  bigint,
  timestamp,
  uniqueIndex,
  type AnyPgColumn,
} from 'drizzle-orm/pg-core';
import { catalogSource, catalogPackageStatus } from './enums.js';

/**
 * AD-19: packages are signed, checksummed, license-identified, staged,
 * atomically activated, and rollback-capable. `licenseId` is required because
 * USDA public domain and Open Food Facts ODbL are not interchangeable and the
 * obligation attaches per package (docs/08 §8.5).
 */
export const catalogPackages = pgTable(
  'catalog_packages',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    packageId: text('package_id').notNull(),
    catalogCountry: text('catalog_country').notNull(),
    datasetVersion: text('dataset_version').notNull(),
    source: catalogSource('source').notNull(),
    checksum: text('checksum').notNull(),
    licenseId: text('license_id').notNull(),
    sizeBytes: bigint('size_bytes', { mode: 'number' }),
    status: catalogPackageStatus('status').notNull().default('staged'),
    activatedAt: timestamp('activated_at', { withTimezone: true }),
    rollbackTargetId: uuid('rollback_target_id').references((): AnyPgColumn => catalogPackages.id),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (t) => [uniqueIndex('catalog_packages_package_id_uq').on(t.packageId)],
);

export type CatalogPackageRow = typeof catalogPackages.$inferSelect;
export type NewCatalogPackageRow = typeof catalogPackages.$inferInsert;
