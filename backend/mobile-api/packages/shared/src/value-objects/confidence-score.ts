import { type Result, ok, err } from '../result/result.js';

export type ConfidenceBand = 'low' | 'medium' | 'high';

/**
 * A calibrated confidence in [0, 1].
 *
 * Spine convention: confidence is calibrated within one engine/model version
 * and is never compared across engines unless Gate G-5 supplies a validated
 * calibration transform. `engineId` and `calibrationVersion` travel with the
 * score so a cross-engine comparison is at least visible when attempted.
 */
export interface ConfidenceScore {
  readonly score: number;
  readonly band: ConfidenceBand;
  readonly engineId: string;
  readonly calibrationVersion: string;
}

export const CONFIDENCE_BAND_BOUNDS = {
  /** Below this is `low`. */
  medium: 0.5,
  /** At or above this is `high`. */
  high: 0.75,
} as const;

export const bandFor = (score: number): ConfidenceBand => {
  if (score >= CONFIDENCE_BAND_BOUNDS.high) return 'high';
  if (score >= CONFIDENCE_BAND_BOUNDS.medium) return 'medium';
  return 'low';
};

export type ConfidenceError = 'out-of-range' | 'not-finite';

export const createConfidenceScore = (
  score: number,
  engineId: string,
  calibrationVersion: string,
): Result<ConfidenceScore, ConfidenceError> => {
  if (!Number.isFinite(score)) return err('not-finite');
  if (score < 0 || score > 1) return err('out-of-range');
  return ok({ score, band: bandFor(score), engineId, calibrationVersion });
};

/**
 * Comparison is permitted only within one engine and calibration version.
 * Returns a failure rather than a silent result when that does not hold.
 */
export const compareWithinEngine = (
  a: ConfidenceScore,
  b: ConfidenceScore,
): Result<number, 'incomparable'> => {
  if (a.engineId !== b.engineId || a.calibrationVersion !== b.calibrationVersion) {
    return err('incomparable');
  }
  return ok(a.score - b.score);
};
