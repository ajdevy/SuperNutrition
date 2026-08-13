import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { TELEMETRY_ALLOWLIST, FORBIDDEN_TELEMETRY_FIELDS } from '@supernutrition/shared';
import { parse } from 'smol-toml';
import { describe, it, expect } from 'vitest';

const here = dirname(fileURLToPath(import.meta.url));
// apps/api/src/logging -> backend/mobile-api is four levels up.
const allowlistFile = resolve(here, '..', '..', '..', '..', 'config', 'telemetry-allowlist.toml');

/**
 * `config/telemetry-allowlist.toml` describes the AD-14 allowlist, and
 * `TELEMETRY_ALLOWLIST` enforces it. Nothing loads the file — the serializer is
 * built from the constant — so the two could disagree indefinitely with the
 * file looking authoritative to anyone auditing what the service emits.
 *
 * `TELEMETRY_ALLOWLIST_PATH` made that worse by naming the file in the
 * environment, which reads as "the process loads this". It never did, and it
 * must not: a telemetry allowlist an operator can repoint at another file is a
 * way to widen telemetry at deploy time without a code review, which is the
 * opposite of what AD-14 asks for. The knob is gone; the file stays as the
 * human-readable record and this test keeps it truthful.
 */
describe('the documented telemetry allowlist matches the enforced one', () => {
  const doc = parse(readFileSync(allowlistFile, 'utf8')) as {
    allowed: string[];
    forbidden: string[];
  };

  it('documents exactly the fields the serializer permits', () => {
    expect([...doc.allowed].sort()).toEqual([...TELEMETRY_ALLOWLIST].sort());
  });

  it('documents exactly the fields the negative test denies', () => {
    expect([...doc.forbidden].sort()).toEqual([...FORBIDDEN_TELEMETRY_FIELDS].sort());
  });

  it('never lists a field as both allowed and forbidden', () => {
    const allowed = new Set(doc.allowed);
    expect(doc.forbidden.filter((field) => allowed.has(field))).toEqual([]);
  });
});
