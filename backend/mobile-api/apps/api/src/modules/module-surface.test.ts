import { describe, it, expect } from 'vitest';

import * as auth from './auth/index.js';
import * as users from './users/index.js';
import * as goals from './goals/index.js';
import * as meals from './meals/index.js';
import * as inference from './inference/index.js';
import * as nutrition from './nutrition/index.js';
import * as catalog from './catalog/index.js';
import * as coach from './coach/index.js';
import * as gamification from './gamification/index.js';
import * as progress from './progress/index.js';

import * as authInfra from './auth/infrastructure/index.js';
import * as usersInfra from './users/infrastructure/index.js';
import * as goalsInfra from './goals/infrastructure/index.js';
import * as mealsInfra from './meals/infrastructure/index.js';
import * as inferenceInfra from './inference/infrastructure/index.js';
import * as nutritionInfra from './nutrition/infrastructure/index.js';
import * as catalogInfra from './catalog/infrastructure/index.js';
import * as coachInfra from './coach/infrastructure/index.js';
import * as gamificationInfra from './gamification/infrastructure/index.js';
import * as progressInfra from './progress/infrastructure/index.js';

/**
 * Every module exposes exactly one public surface (docs/03 §3.2). Importing
 * each barrel proves the ten boundaries resolve, that the layer directories
 * exist, and that a module carrying runtime behaviour actually exports it.
 */
const MODULES = {
  auth,
  users,
  goals,
  meals,
  inference,
  nutrition,
  catalog,
  coach,
  gamification,
  progress,
} as const;

const INFRASTRUCTURE = {
  auth: authInfra,
  users: usersInfra,
  goals: goalsInfra,
  meals: mealsInfra,
  inference: inferenceInfra,
  nutrition: nutritionInfra,
  catalog: catalogInfra,
  coach: coachInfra,
  gamification: gamificationInfra,
  progress: progressInfra,
} as const;

describe('module surfaces', () => {
  it('declares the ten modules from the architecture', () => {
    expect(Object.keys(MODULES)).toEqual([
      'auth',
      'users',
      'goals',
      'meals',
      'inference',
      'nutrition',
      'catalog',
      'coach',
      'gamification',
      'progress',
    ]);
  });

  it.each(Object.entries(MODULES))('%s resolves its public surface', (_name, surface) => {
    expect(surface).toBeDefined();
  });

  it.each(Object.entries(INFRASTRUCTURE))(
    '%s resolves its infrastructure layer',
    (_name, surface) => {
      expect(surface).toBeDefined();
    },
  );

  it('exposes the USDA adapter through the nutrition module', () => {
    expect(nutrition).toHaveProperty('USDAProvider');
    expect(nutritionInfra).toHaveProperty('USDAProvider');
  });

  it('exposes meal request validators through the meals module', () => {
    expect(meals).toHaveProperty('CreateManualMealRequestSchema');
    expect(meals).toHaveProperty('CreatePhotoMealRequestSchema');
    expect(meals).toHaveProperty('CreateVoiceMealRequestSchema');
    expect(meals).toHaveProperty('NutritionSnapshotSchema');
  });

  it('exposes gateway validators through the inference module', () => {
    expect(inference).toHaveProperty('ConsentReceiptSchema');
    expect(inference).toHaveProperty('PhotoInferenceRequestSchema');
    expect(inference).toHaveProperty('TextInferenceRequestSchema');
  });

  it('exposes auth and goal validators', () => {
    expect(auth).toHaveProperty('CreateSessionRequestSchema');
    expect(auth).toHaveProperty('RefreshRequestSchema');
    expect(goals).toHaveProperty('UpdateGoalRequestSchema');
    expect(users).toHaveProperty('UpdateProfileRequestSchema');
    expect(catalog).toHaveProperty('CatalogSearchQuerySchema');
    expect(progress).toHaveProperty('ProgressQuerySchema');
  });

  // Coach is a projection: it reads facts and writes only its own messages.
  // Its surface must expose no other domain's repository port (docs/04 §4.8).
  it("keeps coach free of other domains' repository ports", () => {
    const keys = [...Object.keys(coach), ...Object.keys(coachInfra)];
    for (const key of keys) {
      expect(key).not.toMatch(/MealEventRepository|UserGoalRepository|XpLedger|DailyProgress/);
    }
  });

  it('keeps gamification free of a direct meal repository port', () => {
    const keys = [...Object.keys(gamification), ...Object.keys(gamificationInfra)];
    for (const key of keys) {
      expect(key).not.toMatch(/MealEventRepository/);
    }
  });
});
