import { describe, it, expect } from 'vitest';
import { readFileSync, readdirSync } from 'node:fs';
import { join, dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parse } from 'smol-toml';
import { GATEWAY_ERRORS, GATEWAY_CODES } from '../../security/gateway-errors.js';

/**
 * Conformance against the RELEASED contract, not against a copy of it.
 *
 * This reads `contracts/mobile-api/v1` as data at test time. If the contract
 * owner changes a status code, a recoverability flag, or a message key, this
 * fails — which is the only way the implementation and the release cannot
 * drift apart silently (AD-36).
 *
 * Reading the release as DATA is not a runtime dependency on `contracts/`:
 * nothing here is imported by production code, and the product boundary scan
 * checks production sources only.
 */

const here = dirname(fileURLToPath(import.meta.url));
// apps/api/src/routes/gateway -> repo root is seven levels up.
const contractRoot = resolve(
  here,
  '..',
  '..',
  '..',
  '..',
  '..',
  '..',
  '..',
  'contracts',
  'mobile-api',
  'v1',
);

const loadToml = (relative: string): Record<string, unknown> =>
  parse(readFileSync(join(contractRoot, relative), 'utf8')) as Record<string, unknown>;

interface TaxonomyEntry {
  gatewayCode: string;
  httpStatus: number;
  userMessageKey: string;
  recoverable: boolean;
  retryAfter: number;
}

const taxonomy = loadToml('error-taxonomy.toml')['error'] as TaxonomyEntry[];

describe('contract release is reachable', () => {
  it('finds the released taxonomy', () => {
    expect(taxonomy.length).toBeGreaterThan(15);
  });
});

describe('error taxonomy matches the release exactly', () => {
  it.each(taxonomy.map((entry) => [entry.gatewayCode, entry] as const))(
    '%s agrees on status, message key, recoverability, and retry',
    (code, entry) => {
      const implemented = GATEWAY_ERRORS[code as keyof typeof GATEWAY_ERRORS];
      expect(implemented, `${code} is missing from the implementation`).toBeDefined();
      expect(implemented.httpStatus).toBe(entry.httpStatus);
      expect(implemented.userMessageKey).toBe(entry.userMessageKey);
      expect(implemented.recoverable).toBe(entry.recoverable);
      expect(implemented.retryAfter).toBe(entry.retryAfter);
    },
  );

  it('implements every released code and no extra ones', () => {
    const released = new Set(taxonomy.map((entry) => entry.gatewayCode));
    const implemented = new Set(Object.values(GATEWAY_CODES));
    expect([...implemented].filter((code) => !released.has(code))).toEqual([]);
    expect([...released].filter((code) => !implemented.has(code as never))).toEqual([]);
  });
});

describe('gateway responses carry no nutrition (AD-7)', () => {
  const forbidden = (
    (loadToml('schema.toml')['nutrition'] as Record<string, unknown>)[
      'forbidden_response_keys'
    ] as string[]
  ).map((key) => key.toLowerCase());

  const walk = (dir: string): string[] =>
    readdirSync(dir, { withFileTypes: true }).flatMap((entry) =>
      entry.isDirectory()
        ? walk(join(dir, entry.name))
        : entry.name.endsWith('.ts') && !entry.name.endsWith('.test.ts')
          ? [join(dir, entry.name)]
          : [],
    );

  const gatewaySources = walk(here);

  it('declares no forbidden nutrition field in any gateway response type', () => {
    const offenders: string[] = [];
    for (const file of gatewaySources) {
      const source = readFileSync(file, 'utf8');
      for (const key of forbidden) {
        // A property declaration, not a word inside a comment.
        if (new RegExp(`(readonly\\s+)?${key}\\s*[?]?\\s*:`, 'i').test(source)) {
          offenders.push(`${file}:${key}`);
        }
      }
    }
    expect(offenders).toEqual([]);
  });
});

describe('transfer state contract', () => {
  const transfer = loadToml('transfer-states.toml');

  it('never claims recall in any released state', () => {
    const invariants = transfer['invariants'] as Record<string, unknown>;
    expect(invariants['recall_never_claimed']).toBe(true);

    const field = (transfer['response_field'] as { name: string; enum?: unknown[] }[]).find(
      (entry) => entry.name === 'recallClaimed',
    );
    expect(field?.enum).toEqual([false]);
  });

  it('declares the three states in order', () => {
    const states = (transfer['state'] as { id: string; order: number }[])
      .sort((a, b) => a.order - b.order)
      .map((entry) => entry.id);
    expect(states).toEqual(['pre-transfer', 'transferring', 'transferred']);
  });
});

describe('market policy contract stays fail-closed (AD-41)', () => {
  const policy = loadToml('market-policy.toml');

  it('defaults to unresolved', () => {
    expect((policy['posture'] as Record<string, unknown>)['default']).toBe('unresolved');
    expect((policy['posture'] as Record<string, unknown>)['fail_closed']).toBe(true);
  });

  it('leaves the local path available in every posture', () => {
    for (const entry of policy['posture_value'] as {
      id: string;
      local_path_available: boolean;
    }[]) {
      expect(entry.local_path_available, entry.id).toBe(true);
    }
  });
});

describe('consent receipt contract', () => {
  const receipt = loadToml('consent-receipt.toml');

  it('evaluates expiry at resolution time', () => {
    expect((receipt['validation'] as Record<string, unknown>)['evaluated_at']).toBe(
      'resolution-time',
    );
  });

  it('bounds receipt validity', () => {
    expect((receipt['validation'] as Record<string, unknown>)['max_validity_seconds']).toBe(900);
  });

  it('forbids nonzero provider retention', () => {
    const retention = (receipt['field'] as { name: string; maximum?: number }[]).find(
      (entry) => entry.name === 'retentionDays',
    );
    expect(retention?.maximum).toBe(0);
  });
});
