import { DrizzleRepository } from './base.repository.js';
import type { Database } from '../client.js';
import type { NewInferenceRunRow } from '../schema/inference-runs.js';

/**
 * Implements the `InferenceRunRecorder` port.
 *
 * The row type carries no user foreign key by construction (docs/05 §5.4), so
 * this adapter cannot record one even if a caller supplied it.
 */
export class DrizzleInferenceRunRecorder extends DrizzleRepository {
  constructor(db: Database) {
    super(db);
  }

  async record(_run: NewInferenceRunRow): Promise<void> {
    return this.notImplemented('record');
  }

  /** Retention sweep: 90 days granular, then rollups. */
  async pruneOlderThan(_cutoff: Date): Promise<number> {
    return this.notImplemented('pruneOlderThan');
  }
}
