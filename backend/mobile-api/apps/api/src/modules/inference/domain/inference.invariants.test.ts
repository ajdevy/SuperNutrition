import { describe, it, expect } from 'vitest';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, dirname, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
import { bucketDuration, DURATION_BUCKETS } from './inference-orchestrator.port.js';
import { isRetryable, RETRYABLE_FAILURES, PROVIDER_TYPES } from './inference.types.js';

const here = dirname(fileURLToPath(import.meta.url));
const apiSrc = join(here, '..', '..', '..');

const walk = (dir: string): string[] =>
  readdirSync(dir).flatMap((entry) => {
    const full = join(dir, entry);
    return statSync(full).isDirectory() ? walk(full) : [full];
  });

const tsFiles = walk(apiSrc).filter((f) => f.endsWith('.ts') && !f.endsWith('.test.ts'));

describe('inference retry policy (docs/07 §7.5)', () => {
  it('retries only transient failures', () => {
    expect(isRetryable('timeout')).toBe(true);
    expect(isRetryable('rate-limited')).toBe(true);
    expect(isRetryable('provider-unavailable')).toBe(true);
  });

  // Retrying a rejected payload or a cancelled attempt burns cost and, for
  // cancellation, contradicts AD-6's single terminal outcome.
  it('never retries a terminal failure', () => {
    expect(isRetryable('content-rejected')).toBe(false);
    expect(isRetryable('malformed-response')).toBe(false);
    expect(isRetryable('cancelled')).toBe(false);
    expect(isRetryable('budget-exceeded')).toBe(false);
    expect(isRetryable('kill-switch')).toBe(false);
  });

  it('keeps the retryable set closed', () => {
    expect(RETRYABLE_FAILURES).toHaveLength(3);
  });
});

describe('telemetry duration bucketing (AD-14)', () => {
  it('buckets rather than reporting raw traces', () => {
    expect(bucketDuration(0)).toBe('under-1s');
    expect(bucketDuration(999)).toBe('under-1s');
    expect(bucketDuration(1_000)).toBe('1s-3s');
    expect(bucketDuration(2_999)).toBe('1s-3s');
    expect(bucketDuration(3_000)).toBe('3s-10s');
    expect(bucketDuration(9_999)).toBe('3s-10s');
    expect(bucketDuration(10_000)).toBe('over-10s');
    expect(bucketDuration(600_000)).toBe('over-10s');
  });

  it('returns only declared buckets', () => {
    for (const ms of [0, 500, 1_500, 5_000, 60_000]) {
      expect(DURATION_BUCKETS).toContain(bucketDuration(ms));
    }
  });
});

/**
 * `contracts/inference/v1` sets `numeric_nutrition = false`. The cheapest way
 * for a model to be wrong is to be asked for a number, so the absence is
 * asserted rather than assumed (docs/04 §4.5, docs/06 §6.5).
 */
describe('inference carries no nutrition (AD-7)', () => {
  const nutrientTokens = [
    'calories',
    'proteinG',
    'fatG',
    'carbG',
    'fiberG',
    'sugarG',
    'sodiumMg',
    'kcal',
  ];

  const inferenceFiles = tsFiles.filter((f) => f.includes(join('modules', 'inference')));

  it('finds inference module sources to scan', () => {
    expect(inferenceFiles.length).toBeGreaterThan(5);
  });

  it.each(nutrientTokens)('declares no %s field anywhere in the inference module', (token) => {
    const offenders = inferenceFiles.filter((file) => {
      const source = readFileSync(file, 'utf8');
      // Match a property declaration, not a word in a comment.
      return new RegExp(`(readonly\\s+)?${token}\\s*[?]?\\s*:`).test(source);
    });
    expect(offenders.map((f) => relative(apiSrc, f))).toEqual([]);
  });
});

/**
 * Principle 4 / AD-9: `config/model-profiles.toml` is the only place a model
 * identifier may appear. Discipline is not a control (docs/07 §7.4).
 */
describe('no hardcoded model identifiers', () => {
  const modelPatterns = [/google\/gemini/, /openai\/gpt-4o/, /anthropic\/claude/, /meta-llama\//];

  it.each(modelPatterns.map((p) => [p.source, p] as const))(
    'finds no %s outside the adapter and configuration',
    (_label, pattern) => {
      const offenders = tsFiles.filter((file) => {
        const rel = relative(apiSrc, file);
        if (rel.includes(join('infrastructure', 'openrouter'))) return false;
        return pattern.test(readFileSync(file, 'utf8'));
      });
      expect(offenders.map((f) => relative(apiSrc, f))).toEqual([]);
    },
  );

  it('keeps the provider type set closed and provider-agnostic downstream', () => {
    expect(PROVIDER_TYPES).toEqual(['openrouter']);
  });
});

/**
 * The gateway route group must have no persistence edge. The eslint boundaries
 * rule enforces it at lint time; this asserts it at test time too, because the
 * property is the reason "no durable raw photos" is structural rather than a
 * review convention (docs/09 §9.6).
 */
describe('gateway cannot persist (AD-8)', () => {
  const gatewayFiles = tsFiles.filter((f) => f.includes(join('routes', 'gateway')));

  it('finds gateway sources to scan', () => {
    expect(gatewayFiles.length).toBeGreaterThan(0);
  });

  it('imports no database package or repository', () => {
    const offenders = gatewayFiles.filter((file) => {
      const source = readFileSync(file, 'utf8');
      return (
        /@supernutrition\/database/.test(source) ||
        /from\s+['"].*[Rr]epository/.test(source) ||
        /drizzle/.test(source)
      );
    });
    expect(offenders.map((f) => relative(apiSrc, f))).toEqual([]);
  });
});
