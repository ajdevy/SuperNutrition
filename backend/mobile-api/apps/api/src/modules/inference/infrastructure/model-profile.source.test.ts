import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import {
  parseModelProfiles,
  TomlModelProfileSource,
  ConfigurationError,
} from './model-profile.source.js';

const VALID = `
schema = "supernutrition.backend.model-profiles.v1"
profile_version = "2026-07-29.1"

[routing]
confidence_threshold = 0.75
accept_band = "high"
max_passes = 2
pass_deadline_ms = 8000
total_deadline_ms = 20000

[[profile]]
id = "fast"
pass = 1
provider = "openrouter"
model = "vendor/fast"
max_output_tokens = 1024
temperature = 0.0
cost_per_1k_input_micros = 75
cost_per_1k_output_micros = 300
enabled = true

[[profile]]
id = "deep"
pass = 2
provider = "openrouter"
model = "vendor/deep"
max_output_tokens = 2048
temperature = 0.0
cost_per_1k_input_micros = 2500
cost_per_1k_output_micros = 10000
enabled = false
`;

const without = (block: string) => VALID.replace(block, '');

describe('parsing a valid roster', () => {
  const snapshot = parseModelProfiles(VALID);

  it('reads the profile version reported in provenance', () => {
    expect(snapshot.profileVersion).toBe('2026-07-29.1');
  });

  it('reads routing policy', () => {
    expect(snapshot.routing).toMatchObject({
      confidenceThreshold: 0.75,
      acceptBand: 'high',
      maxPasses: 2,
      passDeadlineMs: 8000,
      totalDeadlineMs: 20000,
    });
  });

  it('maps snake_case configuration to camelCase profiles', () => {
    expect(snapshot.profiles[0]).toMatchObject({
      id: 'fast',
      pass: 1,
      model: 'vendor/fast',
      costPer1kInputMicros: 75,
      enabled: true,
    });
  });

  it('treats a missing enabled flag as disabled', () => {
    expect(snapshot.profiles[1]?.enabled).toBe(false);
  });
});

describe('invalid configuration fails loudly', () => {
  it.each([
    ['missing profile_version', 'profile_version = "2026-07-29.1"'],
    ['missing threshold', 'confidence_threshold = 0.75'],
    ['missing max_passes', 'max_passes = 2'],
    ['missing pass deadline', 'pass_deadline_ms = 8000'],
  ])('rejects %s', (_name, line) => {
    expect(() => parseModelProfiles(without(line))).toThrow(ConfigurationError);
  });

  it('rejects a threshold outside [0,1]', () => {
    expect(() => parseModelProfiles(VALID.replace('0.75', '1.5'))).toThrow(/\[0,1\]/);
  });

  it('rejects an unknown accept band', () => {
    expect(() => parseModelProfiles(VALID.replace('"high"', '"perfect"'))).toThrow(/accept_band/);
  });

  it('rejects max_passes outside 1..2', () => {
    expect(() => parseModelProfiles(VALID.replace('max_passes = 2', 'max_passes = 3'))).toThrow(
      /max_passes/,
    );
  });

  it('rejects a pass deadline longer than the total budget', () => {
    expect(() =>
      parseModelProfiles(VALID.replace('total_deadline_ms = 20000', 'total_deadline_ms = 1000')),
    ).toThrow(/cannot exceed/);
  });

  it('rejects a profile pass outside 1..2', () => {
    expect(() => parseModelProfiles(VALID.replace('pass = 1', 'pass = 3'))).toThrow(/profile.pass/);
  });

  it('rejects duplicate profile ids', () => {
    expect(() => parseModelProfiles(VALID.replace('id = "deep"', 'id = "fast"'))).toThrow(/unique/);
  });

  it('requires at least one profile', () => {
    expect(() =>
      parseModelProfiles(
        'profile_version = "x"\n[routing]\nconfidence_threshold = 0.5\naccept_band = "high"\nmax_passes = 1\npass_deadline_ms = 1\ntotal_deadline_ms = 2\n',
      ),
    ).toThrow(/at least one/);
  });

  // A roster with no enabled pass-1 profile cannot serve any attempt, so it is
  // a configuration error rather than a runtime surprise.
  it('requires an enabled pass-1 profile', () => {
    expect(() => parseModelProfiles(VALID.replace('enabled = true', 'enabled = false'))).toThrow(
      /pass-1/,
    );
  });
});

describe('source behaviour', () => {
  const source = new TomlModelProfileSource(parseModelProfiles(VALID));

  it('filters profiles by pass', () => {
    expect(source.profilesForPass(1).map((p) => p.id)).toEqual(['fast']);
    expect(source.profilesForPass(2).map((p) => p.id)).toEqual(['deep']);
  });

  // One attempt must not observe two rosters mid-flight.
  it('returns a stable snapshot', () => {
    expect(source.current()).toBe(source.current());
  });
});

describe('the shipped configuration is valid', () => {
  it('parses config/model-profiles.toml', () => {
    // apps/api/src/modules/inference/infrastructure -> product root is six up.
    const path = resolve(
      import.meta.dirname,
      '..',
      '..',
      '..',
      '..',
      '..',
      '..',
      'config',
      'model-profiles.toml',
    );
    const snapshot = parseModelProfiles(readFileSync(path, 'utf8'));
    expect(snapshot.profiles.some((p) => p.pass === 1 && p.enabled)).toBe(true);
    expect(snapshot.routing.maxPasses).toBe(2);
  });
});

/**
 * Low group — a deadline of zero parsed cleanly.
 *
 * `AbortSignal.timeout(0)` aborts before the request leaves, so a roster with
 * pass_deadline_ms = 0 loaded without complaint and then failed every attempt
 * with a timeout nobody could trace back to the config. Negative values were
 * accepted the same way.
 */
describe('a deadline has to be a deadline', () => {
  it.each([
    ['zero pass deadline', 'pass_deadline_ms = 8000', 'pass_deadline_ms = 0'],
    ['negative pass deadline', 'pass_deadline_ms = 8000', 'pass_deadline_ms = -1'],
    ['zero total deadline', 'total_deadline_ms = 20000', 'total_deadline_ms = 0'],
  ])('rejects %s', (_name, from, to) => {
    expect(() => parseModelProfiles(VALID.replace(from, to))).toThrow(ConfigurationError);
  });

  it('still accepts a positive deadline', () => {
    expect(() => parseModelProfiles(VALID)).not.toThrow();
  });
});
