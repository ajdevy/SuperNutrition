import { describe, it, expect } from 'vitest';
import { getTableConfig } from 'drizzle-orm/pg-core';
import * as schema from './index.js';
import { inferenceRuns } from './inference-runs.js';
import { nutritionSnapshots } from './nutrition-snapshots.js';
import { userAchievements } from './user-achievements.js';
import { xpTransactions } from './xp-transactions.js';
import { streaks } from './streaks.js';
import { dailyProgress } from './daily-progress.js';
import { mealEvents } from './meal-events.js';
import { catalogItems } from './catalog-items.js';
import { catalogPackages } from './catalog-packages.js';
import { consentReceipts } from './consent-receipts.js';

const columns = (table: Parameters<typeof getTableConfig>[0]): string[] =>
  getTableConfig(table).columns.map((c) => c.name);

const uniqueIndexNames = (table: Parameters<typeof getTableConfig>[0]): string[] =>
  getTableConfig(table)
    .indexes.filter((i) => i.config.unique)
    .map((i) => i.config.name ?? '');

describe('required tables exist (docs/05 §5.3)', () => {
  it.each([
    'users',
    'userProfiles',
    'sessions',
    'consentReceipts',
    'userGoals',
    'mealEvents',
    'mealItems',
    'nutritionSnapshots',
    'inferenceEvidence',
    'inferenceRuns',
    'achievements',
    'userAchievements',
    'streaks',
    'xpTransactions',
    'dailyProgress',
    'coachMessages',
    'catalogPackages',
    'catalogItems',
  ])('exports %s', (name) => {
    expect(schema).toHaveProperty(name);
  });
});

/**
 * The single most important schema assertion in this product.
 *
 * AD-14 permits an opaque attempt ID and forbids telemetry that identifies
 * content or people. A user foreign key here would turn an operational metrics
 * table into a per-person record of what someone photographed and when,
 * joinable to meal_events by anyone with read access (docs/05 §5.4).
 */
describe('inference_runs carries no user identity (AD-14)', () => {
  const cols = columns(inferenceRuns);

  it.each(['user_id', 'email', 'external_ref', 'installation_id', 'session_id'])(
    'has no %s column',
    (forbidden) => {
      expect(cols).not.toContain(forbidden);
    },
  );

  it('stores no payload, prompt, or provider response', () => {
    for (const forbidden of [
      'payload',
      'prompt',
      'prompt_text',
      'response_body',
      'raw_response',
      'candidate_labels',
      'photo_digest',
      'image_digest',
    ]) {
      expect(cols, `leaked ${forbidden}`).not.toContain(forbidden);
    }
  });

  it('keys on an opaque attempt id', () => {
    expect(cols).toContain('attempt_id');
  });

  it('still supports benchmarking', () => {
    for (const required of [
      'model_id',
      'duration_ms',
      'duration_bucket',
      'prompt_tokens',
      'completion_tokens',
      'estimated_cost_micros',
      'success',
      'outcome',
      'created_at',
    ]) {
      expect(cols, `missing ${required}`).toContain(required);
    }
  });
});

describe('idempotency constraints make replay safe (docs/04 §4.8)', () => {
  it('awards an achievement at most once per user', () => {
    expect(uniqueIndexNames(userAchievements)).toContain('user_achievements_uq');
  });

  it('grants XP at most once per (user, reason, ref)', () => {
    expect(uniqueIndexNames(xpTransactions)).toContain('xp_transactions_idempotency_uq');
  });

  it('holds one streak row per user and type', () => {
    expect(uniqueIndexNames(streaks)).toContain('streaks_user_type_uq');
  });

  it('holds one progress row per user and local date', () => {
    expect(uniqueIndexNames(dailyProgress)).toContain('daily_progress_user_date_uq');
  });

  it('holds one snapshot per meal event', () => {
    expect(uniqueIndexNames(nutritionSnapshots)).toContain('nutrition_snapshots_meal_event_uq');
  });

  it('holds one catalog item per package and food id', () => {
    expect(uniqueIndexNames(catalogItems)).toContain('catalog_items_package_food_uq');
  });
});

/**
 * AD-7: every displayed number carries catalog source, dataset version, and
 * serving basis, or it is not admissible.
 */
describe('nutrition snapshots carry provenance (AD-7)', () => {
  const config = getTableConfig(nutritionSnapshots);
  const cols = columns(nutritionSnapshots);

  it.each(['catalog_source', 'dataset_version', 'serving_basis', 'origin'])(
    'requires %s',
    (name) => {
      expect(cols).toContain(name);
      const column = config.columns.find((c) => c.name === name);
      expect(column?.notNull, `${name} must be NOT NULL`).toBe(true);
    },
  );

  it('distinguishes client-grounded from server-grounded permanently', () => {
    const origin = config.columns.find((c) => c.name === 'origin');
    expect(origin?.enumValues).toEqual(['client-grounded', 'server-grounded']);
  });

  it('stores nutrients as numeric, never floating point', () => {
    for (const name of ['calories', 'protein_g', 'fat_g', 'carb_g']) {
      const column = config.columns.find((c) => c.name === name);
      expect(column?.getSQLType(), `${name} must be numeric`).toMatch(/^numeric/);
    }
  });
});

describe('meal events preserve history and local time (docs/05 §5.1)', () => {
  const cols = columns(mealEvents);

  it('stores the local day and timezone explicitly', () => {
    expect(cols).toContain('local_date');
    expect(cols).toContain('timezone');
  });

  it('supports revisions through a supersede chain rather than mutation', () => {
    expect(cols).toContain('revision');
    expect(cols).toContain('supersedes_id');
  });

  it('supports soft delete for the erasure path', () => {
    expect(cols).toContain('deleted_at');
  });

  it('holds no column capable of storing image bytes (AD-8)', () => {
    for (const forbidden of ['photo', 'image', 'image_bytes', 'payload', 'photo_bytes']) {
      expect(cols).not.toContain(forbidden);
    }
  });
});

describe('consent receipts bind a digest, never a payload (AD-8)', () => {
  const cols = columns(consentReceipts);

  it('records the binding fields', () => {
    for (const required of [
      'payload_digest',
      'modality',
      'market_jurisdiction',
      'processing_region',
      'retention_version',
      'configuration_version',
      'consent_copy_version',
    ]) {
      expect(cols, `missing ${required}`).toContain(required);
    }
  });

  it('stores no payload', () => {
    expect(cols).not.toContain('payload');
    expect(cols).not.toContain('image_bytes');
  });
});

describe('catalog packages are rollback-capable (AD-19)', () => {
  const cols = columns(catalogPackages);

  it.each(['checksum', 'license_id', 'status', 'rollback_target_id', 'dataset_version'])(
    'records %s',
    (name) => {
      expect(cols).toContain(name);
    },
  );
});
