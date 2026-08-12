import { readFileSync } from 'node:fs';
import { parse } from 'smol-toml';
import type {
  ModelProfile,
  ModelProfileSnapshot,
  ModelProfileSource,
  RoutingPolicy,
} from '../domain/model-profile.js';

/**
 * Reads `config/model-profiles.toml`.
 *
 * AD-9: model selection is configuration, never code. This adapter is the only
 * thing that reads that file, and the file is the only place a model identifier
 * appears — a CI grep asserts the second half of that claim.
 *
 * The snapshot is loaded once and held. One attempt must not observe two
 * different rosters mid-flight, and re-reading per request would allow exactly
 * that.
 */

export class ConfigurationError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'ConfigurationError';
  }
}

interface RawProfile {
  id?: unknown;
  pass?: unknown;
  provider?: unknown;
  model?: unknown;
  max_output_tokens?: unknown;
  temperature?: unknown;
  cost_per_1k_input_micros?: unknown;
  cost_per_1k_output_micros?: unknown;
  enabled?: unknown;
}

const requireString = (value: unknown, field: string): string => {
  if (typeof value !== 'string' || value.length === 0) {
    throw new ConfigurationError(`model-profiles: ${field} must be a non-empty string`);
  }
  return value;
};

const requireNumber = (value: unknown, field: string): number => {
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    throw new ConfigurationError(`model-profiles: ${field} must be a finite number`);
  }
  return value;
};

export const parseModelProfiles = (source: string): ModelProfileSnapshot => {
  const raw = parse(source) as Record<string, unknown>;

  const profileVersion = requireString(raw['profile_version'], 'profile_version');

  // Read, rather than left as prose. Every other key here is parsed, so an
  // unvalidated one reads as machine-consumed when it is not: an operator
  // following the roll-back comment would find out it is malformed, or that it
  // names the roster already running, at the moment they needed it to work.
  const rollbackRaw = raw['rollback_target'];
  let rollbackTarget: string | null = null;
  if (rollbackRaw !== undefined) {
    rollbackTarget = requireString(rollbackRaw, 'rollback_target');
    if (rollbackTarget === profileVersion) {
      throw new ConfigurationError(
        'model-profiles: rollback_target must differ from profile_version; ' +
          'rolling back to the version in force changes nothing',
      );
    }
  }
  const routingRaw = (raw['routing'] ?? {}) as Record<string, unknown>;

  const maxPasses = requireNumber(routingRaw['max_passes'], 'routing.max_passes');
  if (maxPasses !== 1 && maxPasses !== 2) {
    throw new ConfigurationError('model-profiles: routing.max_passes must be 1 or 2');
  }

  const threshold = requireNumber(
    routingRaw['confidence_threshold'],
    'routing.confidence_threshold',
  );
  if (threshold < 0 || threshold > 1) {
    throw new ConfigurationError('model-profiles: routing.confidence_threshold must be in [0,1]');
  }

  const acceptBand = requireString(routingRaw['accept_band'], 'routing.accept_band');
  if (acceptBand !== 'low' && acceptBand !== 'medium' && acceptBand !== 'high') {
    throw new ConfigurationError('model-profiles: routing.accept_band must be low|medium|high');
  }

  const routing: RoutingPolicy = {
    confidenceThreshold: threshold,
    acceptBand,
    maxPasses,
    passDeadlineMs: requireNumber(routingRaw['pass_deadline_ms'], 'routing.pass_deadline_ms'),
    totalDeadlineMs: requireNumber(routingRaw['total_deadline_ms'], 'routing.total_deadline_ms'),
  };

  // A deadline of zero or less is not a deadline: AbortSignal.timeout(0) aborts
  // before the request leaves, so the roster would parse cleanly and then fail
  // every attempt with a timeout nobody could explain from the config.
  for (const [name, value] of [
    ['pass_deadline_ms', routing.passDeadlineMs],
    ['total_deadline_ms', routing.totalDeadlineMs],
  ] as const) {
    if (!Number.isFinite(value) || value <= 0) {
      throw new ConfigurationError(`model-profiles: routing.${name} must be a positive number`);
    }
  }

  if (routing.passDeadlineMs > routing.totalDeadlineMs) {
    throw new ConfigurationError(
      'model-profiles: pass_deadline_ms cannot exceed total_deadline_ms',
    );
  }

  const rawProfiles = raw['profile'];
  if (!Array.isArray(rawProfiles) || rawProfiles.length === 0) {
    throw new ConfigurationError('model-profiles: at least one [[profile]] is required');
  }

  const profiles: ModelProfile[] = rawProfiles.map((entry) => {
    const p = entry as RawProfile;
    const pass = requireNumber(p.pass, 'profile.pass');
    if (pass !== 1 && pass !== 2) {
      throw new ConfigurationError('model-profiles: profile.pass must be 1 or 2');
    }
    return {
      id: requireString(p.id, 'profile.id'),
      pass,
      provider: requireString(p.provider, 'profile.provider'),
      model: requireString(p.model, 'profile.model'),
      maxOutputTokens: requireNumber(p.max_output_tokens, 'profile.max_output_tokens'),
      temperature: requireNumber(p.temperature, 'profile.temperature'),
      costPer1kInputMicros: requireNumber(
        p.cost_per_1k_input_micros,
        'profile.cost_per_1k_input_micros',
      ),
      costPer1kOutputMicros: requireNumber(
        p.cost_per_1k_output_micros,
        'profile.cost_per_1k_output_micros',
      ),
      enabled: p.enabled === true,
    };
  });

  const ids = profiles.map((p) => p.id);
  if (new Set(ids).size !== ids.length) {
    throw new ConfigurationError('model-profiles: profile ids must be unique');
  }
  if (!profiles.some((p) => p.pass === 1 && p.enabled)) {
    throw new ConfigurationError('model-profiles: at least one enabled pass-1 profile is required');
  }

  return { profileVersion, rollbackTarget, routing, profiles };
};

export class TomlModelProfileSource implements ModelProfileSource {
  readonly #snapshot: ModelProfileSnapshot;

  constructor(snapshot: ModelProfileSnapshot) {
    this.#snapshot = snapshot;
  }

  static fromFile(path: string): TomlModelProfileSource {
    return new TomlModelProfileSource(parseModelProfiles(readFileSync(path, 'utf8')));
  }

  current(): ModelProfileSnapshot {
    return this.#snapshot;
  }

  profilesForPass(pass: 1 | 2): readonly ModelProfile[] {
    return this.#snapshot.profiles.filter((profile) => profile.pass === pass);
  }
}
