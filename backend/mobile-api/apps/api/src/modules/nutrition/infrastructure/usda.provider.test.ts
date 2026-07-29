import { describe, it, expect } from 'vitest';
import { USDAProvider } from './usda.provider.js';

const config = {
  baseUrl: 'https://api.nal.usda.gov/fdc/v1',
  connectTimeoutMs: 3000,
  totalTimeoutMs: 5000,
  maxRetries: 2,
  circuitBreakerThreshold: 5,
};

describe('USDAProvider', () => {
  const provider = new USDAProvider('usda-secret-key', config, 'usda-2026.1');

  it('identifies its catalog source', () => {
    expect(provider.id).toBe('usda');
  });

  // Cache keys include the dataset version, so a package upgrade invalidates by
  // construction (docs/08 §8.6).
  it('reports the dataset version it is pinned to', () => {
    expect(provider.datasetVersion).toBe('usda-2026.1');
  });

  it('does not expose the API key on the instance', () => {
    expect(JSON.stringify(provider)).not.toContain('usda-secret-key');
    expect(Object.keys(provider)).not.toContain('apiKey');
  });

  it('fails loudly rather than returning an empty result', async () => {
    await expect(
      provider.lookup({ foodRef: 'usda:fdc:171705' as never, catalogCountry: 'US' }),
    ).rejects.toThrow(/Not implemented/);
  });
});
