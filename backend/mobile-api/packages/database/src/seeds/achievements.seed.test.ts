import { describe, it, expect } from 'vitest';
import { ACHIEVEMENT_SEEDS } from './achievements.seed.js';

describe('MVP achievement seeds (docs/04 §4.8)', () => {
  it('ships the five MVP achievements', () => {
    expect(ACHIEVEMENT_SEEDS.map((a) => a.key)).toEqual([
      'first_meal',
      'streak_3_day',
      'streak_7_day',
      'healthy_week',
      'protein_hero',
    ]);
  });

  it('uses unique keys and sort orders', () => {
    const keys = ACHIEVEMENT_SEEDS.map((a) => a.key);
    expect(new Set(keys).size).toBe(keys.length);
    const orders = ACHIEVEMENT_SEEDS.map((a) => a.sortOrder);
    expect(new Set(orders).size).toBe(orders.length);
  });

  // "Scalable achievement engine" means the sixth achievement is a seed row,
  // not a deploy of new branching code.
  it('expresses every criterion declaratively', () => {
    for (const achievement of ACHIEVEMENT_SEEDS) {
      const criteria = achievement.criteria as Record<string, unknown>;
      expect(criteria, `${achievement.key} has no criteria`).toBeTypeOf('object');
      expect(criteria).toHaveProperty('type');
      expect(criteria).toHaveProperty('operator');
      expect(criteria).toHaveProperty('value');
    }
  });

  it('uses only known criterion types', () => {
    const known = ['meal_count', 'streak', 'goal_adherence', 'goal_adherence_days'];
    for (const achievement of ACHIEVEMENT_SEEDS) {
      const { type } = achievement.criteria as { type: string };
      expect(known, `${achievement.key} uses unknown type ${type}`).toContain(type);
    }
  });

  it('awards positive XP', () => {
    for (const achievement of ACHIEVEMENT_SEEDS) {
      expect(achievement.xpReward).toBeGreaterThan(0);
    }
  });

  it('carries user-facing name and description for localization upstream', () => {
    for (const achievement of ACHIEVEMENT_SEEDS) {
      expect(achievement.name.length).toBeGreaterThan(0);
      expect(achievement.description.length).toBeGreaterThan(0);
    }
  });
});
