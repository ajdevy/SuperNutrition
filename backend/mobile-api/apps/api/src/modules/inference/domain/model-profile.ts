/**
 * Model selection is configuration, never code (AD-9). `config/model-profiles.toml`
 * is the only file in the product containing a model identifier, and a CI grep
 * asserts that (docs/07 §7.4).
 */
export interface ModelProfile {
  readonly id: string;
  readonly pass: 1 | 2;
  readonly provider: string;
  readonly model: string;
  readonly maxOutputTokens: number;
  readonly temperature: number;
  readonly costPer1kInputMicros: number;
  readonly costPer1kOutputMicros: number;
  readonly enabled: boolean;
}

export interface RoutingPolicy {
  /** Gate G-5 owns the validated value. The checked-in number is a dev placeholder. */
  readonly confidenceThreshold: number;
  readonly acceptBand: 'low' | 'medium' | 'high';
  readonly maxPasses: 1 | 2;
  readonly passDeadlineMs: number;
  readonly totalDeadlineMs: number;
}

export interface ModelProfileSnapshot {
  readonly profileVersion: string;
  /**
   * The roster to return to if this one misbehaves, or null for the first
   * published roster, which has nothing behind it.
   */
  readonly rollbackTarget: string | null;
  readonly routing: RoutingPolicy;
  readonly profiles: readonly ModelProfile[];
}

/** One validated snapshot is captured per attempt, not re-read mid-flight. */
export interface ModelProfileSource {
  current(): ModelProfileSnapshot;
  profilesForPass(pass: 1 | 2): readonly ModelProfile[];
}
