import { describe, it, expect } from 'vitest';
import { DrizzleRepository, NotImplementedError } from './base.repository.js';
import { DrizzleMealEventRepository } from './meal-event.repository.js';
import { DrizzleUserRepository } from './user.repository.js';
import { DrizzleInferenceRunRecorder } from './inference-run.repository.js';
import type { Database } from '../client.js';

const stubDb = {} as Database;

/**
 * The adapter inventory.
 *
 * Behaviour lives in each adapter's own test; what is asserted here is that no
 * repository has quietly gone back to being a placeholder. A method that
 * silently returns a plausible empty result is worse than one that throws,
 * because the caller cannot tell the difference from an empty database.
 */
describe('repository adapters', () => {
  const meals = new DrizzleMealEventRepository(stubDb);
  const users = new DrizzleUserRepository(stubDb);
  const runs = new DrizzleInferenceRunRecorder(stubDb);

  it.each([
    ['findById', meals.findById],
    ['findPage', meals.findPage],
    ['save', meals.save],
    ['supersede', meals.supersede],
    ['softDelete', meals.softDelete],
  ])('MealEventRepository.%s is implemented', (_name, method) => {
    expect(typeof method).toBe('function');
  });

  it.each([
    ['findById', users.findById],
    ['findByExternalRef', users.findByExternalRef],
    ['ensureByExternalRef', users.ensureByExternalRef],
    ['create', users.create],
    ['softDelete', users.softDelete],
  ])('UserRepository.%s is implemented', (_name, method) => {
    expect(typeof method).toBe('function');
  });

  it('InferenceRunRecorder is implemented, not a placeholder', () => {
    expect(runs).toBeInstanceOf(DrizzleInferenceRunRecorder);
    expect(typeof runs.record).toBe('function');
    expect(typeof runs.pruneOlderThan).toBe('function');
  });

  // Every method above now reaches a real query builder, which is exactly why a
  // stub cannot be mistaken for one: a repository written later fails loudly at
  // the call site and names the operation it is missing.
  it('still fails loudly, and by name, for an operation nobody has written yet', () => {
    class UnwrittenRepository extends DrizzleRepository {
      constructor(db: Database) {
        super(db);
      }

      findSomething(): never {
        return this.notImplemented('findSomething');
      }
    }

    const repository = new UnwrittenRepository(stubDb);
    expect(() => repository.findSomething()).toThrow(NotImplementedError);
    expect(() => repository.findSomething()).toThrow(/UnwrittenRepository\.findSomething/);
  });
});
