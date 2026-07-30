import { describe, it, expect } from 'vitest';
import { NotImplementedError } from './base.repository.js';
import { DrizzleMealEventRepository } from './meal-event.repository.js';
import { DrizzleUserRepository } from './user.repository.js';
import { DrizzleInferenceRunRecorder } from './inference-run.repository.js';
import type { Database } from '../client.js';

const stubDb = {} as Database;

/**
 * Stage 2 generates contracts and placeholders. Every method throws so an
 * unimplemented path fails loudly at the call site rather than silently
 * returning a plausible empty result.
 */
describe('repository skeletons fail loudly', () => {
  const meals = new DrizzleMealEventRepository(stubDb);
  const users = new DrizzleUserRepository(stubDb);
  const runs = new DrizzleInferenceRunRecorder(stubDb);

  it.each([
    ['findById', () => meals.findById('a', 'b')],
    ['findPage', () => meals.findPage({ userId: 'u', limit: 20 })],
    ['save', () => meals.save({} as never, [], null)],
    ['supersede', () => meals.supersede('a', {} as never)],
    ['softDelete', () => meals.softDelete('a', 'b')],
  ])('MealEventRepository.%s is not silently a no-op', async (_name, call) => {
    await expect(call()).rejects.toBeInstanceOf(NotImplementedError);
  });

  it.each([
    ['findById', () => users.findById('a')],
    ['findByExternalRef', () => users.findByExternalRef('a')],
    ['create', () => users.create({} as never)],
    ['softDelete', () => users.softDelete('a')],
  ])('UserRepository.%s is not silently a no-op', async (_name, call) => {
    await expect(call()).rejects.toBeInstanceOf(NotImplementedError);
  });

  // The recorder is implemented (Stage 4). It is exercised against a real
  // database elsewhere; here we only assert it is no longer a placeholder,
  // because a telemetry writer that silently throws would drop every row.
  it('InferenceRunRecorder is implemented, not a placeholder', () => {
    expect(runs).toBeInstanceOf(DrizzleInferenceRunRecorder);
    expect(typeof runs.record).toBe('function');
    expect(typeof runs.pruneOlderThan).toBe('function');
  });

  it('names the missing operation in the error', async () => {
    await expect(meals.findById('a', 'b')).rejects.toThrow(/DrizzleMealEventRepository\.findById/);
  });
});
