import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import {
  ConfigurationSnapshotError,
  loadConfigurationSnapshot,
  parseConfigurationSnapshot,
} from './configuration-snapshot.source.js';

/**
 * P-04 — consent was verified against `profiles.current().profileVersion`, the
 * model roster version. The released fixtures carry `cfg/2026-07-26.4` and the
 * roster reports `2026-08-02.1`, so the real composition root refused a
 * contract-valid request. Focused tests injected `['cfg/1']` and never saw it.
 */

describe('the configuration snapshot is its own document', () => {
  it('parses a well-formed declaration', () => {
    const snapshot = parseConfigurationSnapshot(
      'current_version = "cfg/2"\nsupported_versions = ["cfg/1", "cfg/2"]\n',
    );
    expect(snapshot.current).toBe('cfg/2');
    expect(snapshot.supported).toEqual(['cfg/1', 'cfg/2']);
  });

  it.each([
    ['invalid TOML', 'current_version = '],
    ['no current_version', 'supported_versions = ["cfg/1"]'],
    ['an empty current_version', 'current_version = ""\nsupported_versions = ["cfg/1"]'],
    ['a non-string current_version', 'current_version = 3\nsupported_versions = ["cfg/1"]'],
    ['no supported_versions', 'current_version = "cfg/1"'],
    ['an empty supported list', 'current_version = "cfg/1"\nsupported_versions = []'],
    [
      'a non-string in the supported list',
      'current_version = "cfg/1"\nsupported_versions = ["cfg/1", 2]',
    ],
    [
      'a current version missing from the supported list',
      'current_version = "cfg/9"\nsupported_versions = ["cfg/1"]',
    ],
  ])('refuses %s rather than verifying consent against a guess', (_label, raw) => {
    expect(() => parseConfigurationSnapshot(raw)).toThrow(ConfigurationSnapshotError);
  });

  it('refuses an unreadable path', () => {
    expect(() => loadConfigurationSnapshot('/nonexistent/snapshot.toml')).toThrow(
      ConfigurationSnapshotError,
    );
  });
});

describe('the shipped snapshot agrees with the published contract', () => {
  const repoRoot = resolve(process.cwd(), '../..');

  it('accepts the configurationVersion the released v1 fixtures carry', () => {
    // The check that would have caught P-04. The fixture is the contract's own
    // statement of a valid request; a deployment that cannot accept it cannot
    // serve the release it claims to implement.
    const fixture = readFileSync(
      resolve(repoRoot, 'contracts/mobile-api/v1/fixtures/photo-analysis-success.toml'),
      { encoding: 'utf-8' },
    );
    const declared = /configurationVersion = "([^"]+)"/.exec(fixture);
    expect(declared).not.toBeNull();

    const snapshot = loadConfigurationSnapshot(
      resolve(process.cwd(), 'config/configuration-snapshot.toml'),
    );
    expect(snapshot.supported).toContain(declared![1]);
  });

  it('is not the model roster version', () => {
    // The two documents are deliberately separate: rotating a model must not
    // invalidate outstanding consent receipts.
    const roster = readFileSync(resolve(process.cwd(), 'config/model-profiles.toml'), {
      encoding: 'utf-8',
    });
    const rosterVersion = /profile_version = "([^"]+)"/.exec(roster);
    const snapshot = loadConfigurationSnapshot(
      resolve(process.cwd(), 'config/configuration-snapshot.toml'),
    );
    expect(snapshot.supported).not.toContain(rosterVersion![1]);
  });
});
