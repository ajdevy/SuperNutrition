import type { NewAchievementRow } from '../schema/achievements.js';

/**
 * The five MVP achievements as declarative criteria. No achievement key appears
 * in a conditional anywhere in application code; the rule engine evaluates
 * these expressions against the meal-facts projection (docs/04 §4.8).
 *
 * Seeds are idempotent and re-runnable: upsert on `key`.
 */
export const ACHIEVEMENT_SEEDS: readonly NewAchievementRow[] = [
  {
    key: 'first_meal',
    name: 'First Meal',
    description: 'Log your first meal.',
    criteria: { type: 'meal_count', operator: '>=', value: 1, window: 'all_time' },
    xpReward: 50,
    sortOrder: 1,
  },
  {
    key: 'streak_3_day',
    name: '3 Day Streak',
    description: 'Log a meal on three consecutive days.',
    criteria: { type: 'streak', streakType: 'daily_logging', operator: '>=', value: 3 },
    xpReward: 100,
    sortOrder: 2,
  },
  {
    key: 'streak_7_day',
    name: '7 Day Streak',
    description: 'Log a meal on seven consecutive days.',
    criteria: { type: 'streak', streakType: 'daily_logging', operator: '>=', value: 7 },
    xpReward: 250,
    sortOrder: 3,
  },
  {
    key: 'healthy_week',
    name: 'Healthy Week',
    description: 'Meet your calorie goal on five days in one week.',
    criteria: {
      type: 'goal_adherence_days',
      nutrient: 'calories',
      operator: '>=',
      value: 5,
      window: { days: 7 },
    },
    xpReward: 300,
    sortOrder: 4,
  },
  {
    key: 'protein_hero',
    name: 'Protein Hero',
    description: 'Reach your protein goal in a single day.',
    criteria: {
      type: 'goal_adherence',
      nutrient: 'protein',
      operator: '>=',
      value: 1.0,
      window: { days: 1 },
    },
    xpReward: 150,
    sortOrder: 5,
  },
];
