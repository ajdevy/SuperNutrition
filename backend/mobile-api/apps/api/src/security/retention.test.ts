import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

import { EFFECT_TTL_SECONDS, REQUEST_ID_TTL_SECONDS } from '../routes/gateway/inference.handler.js';
import { TERMINAL_RETENTION_SECONDS } from '../routes/gateway/transfer-state.js';

import { COORDINATION_TTL_CEILING_SECONDS } from './retention.js';

/**
 * Story 4.4 criterion 4 was closed by an amendment, so it needs a test — the
 * story's own complaint was that no test asserted it either way.
 *
 * Two properties. Each declared coordination TTL is within the horizon, and the
 * declaration is COMPLETE: a new TTL constant added anywhere in the API source
 * must appear here, or this fails. Without the completeness half, criterion 4
 * would hold for the constants someone remembered.
 */

/** Every TTL constant in the API source, and where its expiry obligation comes from. */
const INVENTORY: ReadonlyArray<{ name: string; seconds: number | 'exempt'; why: string }> = [
  { name: 'EFFECT_TTL_SECONDS', seconds: EFFECT_TTL_SECONDS, why: 'AD-18 at-most-once claim' },
  { name: 'REQUEST_ID_TTL_SECONDS', seconds: REQUEST_ID_TTL_SECONDS, why: 'AD-21 replay guard' },
  {
    name: 'TERMINAL_RETENTION_SECONDS',
    seconds: TERMINAL_RETENTION_SECONDS,
    why: 'how long a finished attempt can still be described',
  },
  {
    name: 'TRANSFER_REGISTRY_TTL_SECONDS',
    seconds: 86_400,
    why: 'attempt ownership; module-private in main.ts',
  },
  {
    name: 'IDEMPOTENCY_TTL',
    seconds: 86_400,
    why: "Product idempotency; module-private in routes/product. The pair's LONGER half — the completed replay window — is what this bound applies to; the in-flight half is 60s and is bounded by the request, not by retention (DS-F2)",
  },
  {
    name: 'CACHE_TTL_MS',
    seconds: 'exempt',
    why: 'process-local cache of PUBLIC USDA catalog data — not user content, not in Redis, no deletion obligation',
  },
];

const sourceFiles = (dir: string): string[] =>
  readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) return sourceFiles(path);
    return entry.isFile() && entry.name.endsWith('.ts') && !entry.name.includes('.test.')
      ? [path]
      : [];
  });

describe('coordination key retention (story 4.4 criterion 4)', () => {
  it('keeps every coordination expiry within the at-most-once horizon', () => {
    for (const entry of INVENTORY) {
      if (entry.seconds === 'exempt') continue;
      expect(
        entry.seconds,
        `${entry.name} (${entry.why}) outlives the horizon it exists to serve`,
      ).toBeLessThanOrEqual(COORDINATION_TTL_CEILING_SECONDS);
    }
  });

  it('has an entry for every TTL constant in the source, so none is closed by omission', () => {
    const declared = new Set(INVENTORY.map((entry) => entry.name));
    const found = new Set<string>();

    for (const file of sourceFiles(join(import.meta.dirname, '..'))) {
      const matches = readFileSync(file, 'utf8').matchAll(
        /(?:const|let|var)\s+([A-Z][A-Z0-9_]*(?:TTL|RETENTION)[A-Z0-9_]*)\s*[:=]/g,
      );
      for (const match of matches) {
        if (match[1] !== 'COORDINATION_TTL_CEILING_SECONDS') found.add(match[1]!);
      }
    }

    expect(
      found.size,
      'the scan found no TTL constants at all, so it proves nothing',
    ).toBeGreaterThan(0);
    const undeclared = [...found].filter((name) => !declared.has(name));
    expect(
      undeclared,
      'a new TTL constant must declare which retention obligation bounds it',
    ).toEqual([]);
  });
});
