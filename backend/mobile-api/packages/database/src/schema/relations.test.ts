import { describe, it, expect } from 'vitest';
import { getTableConfig } from 'drizzle-orm/pg-core';
import { users, userProfiles } from './users.js';
import { sessions } from './sessions.js';
import { consentReceipts } from './consent-receipts.js';
import { userGoals } from './user-goals.js';
import { mealEvents } from './meal-events.js';
import { mealItems } from './meal-items.js';
import { nutritionSnapshots } from './nutrition-snapshots.js';
import { inferenceEvidence } from './inference-evidence.js';
import { achievements } from './achievements.js';
import { userAchievements } from './user-achievements.js';
import { streaks } from './streaks.js';
import { xpTransactions } from './xp-transactions.js';
import { dailyProgress } from './daily-progress.js';
import { coachMessages } from './coach-messages.js';
import { catalogPackages } from './catalog-packages.js';
import { catalogItems } from './catalog-items.js';

type Table = Parameters<typeof getTableConfig>[0];

/** Resolves the FK closures, which is also what proves the graph is wired. */
const foreignKeys = (table: Table) =>
  getTableConfig(table).foreignKeys.map((fk) => {
    const ref = fk.reference();
    return {
      columns: ref.columns.map((c) => c.name),
      foreignTable: getTableConfig(ref.foreignTable).name,
      foreignColumns: ref.foreignColumns.map((c) => c.name),
      onDelete: fk.onDelete,
    };
  });

describe('user-owned tables cascade for the erasure path (docs/04 §4.2)', () => {
  it.each([
    ['user_profiles', userProfiles],
    ['sessions', sessions],
    ['consent_receipts', consentReceipts],
    ['user_goals', userGoals],
    ['meal_events', mealEvents],
    ['user_achievements', userAchievements],
    ['streaks', streaks],
    ['xp_transactions', xpTransactions],
    ['daily_progress', dailyProgress],
    ['coach_messages', coachMessages],
  ] as const)('%s references users on delete cascade', (_name, table) => {
    const toUsers = foreignKeys(table as Table).filter((fk) => fk.foreignTable === 'users');
    expect(toUsers.length).toBeGreaterThan(0);
    expect(toUsers[0]?.foreignColumns).toEqual(['id']);
    expect(toUsers[0]?.onDelete).toBe('cascade');
  });
});

describe('aggregate children cascade with their root', () => {
  it('meal_items belong to a meal event', () => {
    const fks = foreignKeys(mealItems);
    expect(fks[0]?.foreignTable).toBe('meal_events');
    expect(fks[0]?.onDelete).toBe('cascade');
  });

  it('nutrition_snapshots belong to a meal event', () => {
    const fks = foreignKeys(nutritionSnapshots);
    expect(fks[0]?.foreignTable).toBe('meal_events');
    expect(fks[0]?.onDelete).toBe('cascade');
  });

  it('inference_evidence belongs to a meal event and optionally an item', () => {
    const tables = foreignKeys(inferenceEvidence).map((fk) => fk.foreignTable);
    expect(tables).toContain('meal_events');
    expect(tables).toContain('meal_items');
  });

  it('catalog_items belong to a package', () => {
    const fks = foreignKeys(catalogItems);
    expect(fks[0]?.foreignTable).toBe('catalog_packages');
    expect(fks[0]?.onDelete).toBe('cascade');
  });
});

describe('history-preserving references', () => {
  // Deleting a meal must not delete the coaching message about it; the message
  // is a fact that already reached the user.
  it('coach_messages detach from a deleted meal rather than vanishing', () => {
    const toMeals = foreignKeys(coachMessages).filter((fk) => fk.foreignTable === 'meal_events');
    expect(toMeals[0]?.onDelete).toBe('set null');
  });

  it('meal_events supersede chain points at itself', () => {
    const selfRefs = foreignKeys(mealEvents).filter((fk) => fk.foreignTable === 'meal_events');
    expect(selfRefs[0]?.columns).toEqual(['supersedes_id']);
  });

  it('catalog_packages rollback target points at itself', () => {
    const selfRefs = foreignKeys(catalogPackages).filter(
      (fk) => fk.foreignTable === 'catalog_packages',
    );
    expect(selfRefs[0]?.columns).toEqual(['rollback_target_id']);
  });

  it('user_achievements reference the achievement catalog', () => {
    const tables = foreignKeys(userAchievements).map((fk) => fk.foreignTable);
    expect(tables).toContain('achievements');
  });

  it('daily_progress records the goal version it was computed against', () => {
    const tables = foreignKeys(dailyProgress).map((fk) => fk.foreignTable);
    expect(tables).toContain('user_goals');
  });
});

describe('inference_runs stands alone (AD-14)', () => {
  it('has no foreign key to any user-owned table', async () => {
    const { inferenceRuns } = await import('./inference-runs.js');
    expect(foreignKeys(inferenceRuns)).toEqual([]);
  });
});

describe('reference tables are not user-scoped', () => {
  it.each([
    ['achievements', achievements],
    ['catalog_packages', catalogPackages],
  ] as const)('%s has no users foreign key', (_name, table) => {
    expect(foreignKeys(table as Table).some((fk) => fk.foreignTable === 'users')).toBe(false);
  });

  it('users is the root of the ownership graph', () => {
    expect(foreignKeys(users)).toEqual([]);
  });
});
