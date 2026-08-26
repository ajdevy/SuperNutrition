import { readFileSync } from 'node:fs';
import { parse } from 'smol-toml';

/**
 * The signed ConfigurationSnapshot versions this deployment honours (AD-20).
 *
 * P-04 — consent verification was previously bound to
 * `profiles.current().profileVersion`, which is the *model roster* version. A
 * roster is a list of which models to call; a ConfigurationSnapshot is the
 * policy set that produced the disclosure the user was shown. They are different
 * documents with different lifecycles, and treating one as the other had two
 * consequences:
 *
 *  - Rotating a model — an operational change with no user-facing disclosure —
 *    invalidated every outstanding consent receipt.
 *  - The released fixture's `cfg/2026-07-26.4` did not match the roster's
 *    `2026-08-02.1`, so a contract-valid request was refused by the real
 *    composition root while focused unit tests injected `['cfg/1']` and passed.
 *
 * `consent-receipt.toml` is explicit: the version ties the decision "to the
 * exact policy set that produced the disclosure". This is that source, and it is
 * deliberately its own file rather than a field on the roster, so a model change
 * cannot silently move it.
 */

export interface ConfigurationSnapshot {
  /** The snapshot in force. Receipts and requests must name a supported one. */
  readonly current: string;
  /**
   * Versions still accepted. A receipt issued under the previous snapshot stays
   * valid through its own expiry rather than being invalidated by a rollout —
   * the user was shown that disclosure and it was true when they agreed to it.
   */
  readonly supported: readonly string[];
}

export class ConfigurationSnapshotError extends Error {}

const isStringArray = (value: unknown): value is string[] =>
  Array.isArray(value) && value.every((entry) => typeof entry === 'string' && entry.length > 0);

/**
 * Parses and validates the snapshot declaration. Every failure throws: a
 * deployment that cannot say which policy set it is running must not verify
 * consent against a guess.
 */
export const parseConfigurationSnapshot = (raw: string): ConfigurationSnapshot => {
  let parsed: unknown;
  try {
    parsed = parse(raw);
  } catch (cause) {
    throw new ConfigurationSnapshotError(`configuration snapshot is not valid TOML: ${cause}`);
  }
  if (typeof parsed !== 'object' || parsed === null) {
    throw new ConfigurationSnapshotError('configuration snapshot is not a table');
  }
  const table = parsed as Record<string, unknown>;
  const current = table['current_version'];
  if (typeof current !== 'string' || current.length === 0) {
    throw new ConfigurationSnapshotError('configuration snapshot has no current_version');
  }
  const supported = table['supported_versions'];
  if (!isStringArray(supported) || supported.length === 0) {
    throw new ConfigurationSnapshotError('configuration snapshot has no supported_versions');
  }
  // A current version absent from the supported list would accept receipts for
  // every snapshot except the one being issued.
  if (!supported.includes(current)) {
    throw new ConfigurationSnapshotError(`current_version ${current} is not in supported_versions`);
  }
  return { current, supported };
};

export const loadConfigurationSnapshot = (path: string): ConfigurationSnapshot => {
  let raw: string;
  try {
    // Explicit encoding: the default is locale-dependent on some platforms and
    // a version string must not depend on where the process runs.
    raw = readFileSync(path, { encoding: 'utf-8' });
  } catch (cause) {
    throw new ConfigurationSnapshotError(`configuration snapshot unreadable at ${path}: ${cause}`);
  }
  return parseConfigurationSnapshot(raw);
};
