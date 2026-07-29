import { describe, it, expect } from 'vitest';
import { OpenRouterInferenceProvider } from './openrouter.provider.js';
import type { ModelProfile } from '../../domain/model-profile.js';

const profile = (over: Partial<ModelProfile> = {}): ModelProfile => ({
  id: 'fast-identify',
  pass: 1,
  provider: 'openrouter',
  model: 'test-model',
  maxOutputTokens: 1024,
  temperature: 0,
  costPer1kInputMicros: 75,
  costPer1kOutputMicros: 300,
  enabled: true,
  ...over,
});

const config = { baseUrl: 'https://example.invalid/v1', timeoutMs: 8000 };

describe('OpenRouterInferenceProvider', () => {
  const provider = new OpenRouterInferenceProvider('sk-or-secret-value', config);

  it('identifies itself as the openrouter adapter', () => {
    expect(provider.id).toBe('openrouter');
  });

  it('supports its own enabled profiles', () => {
    expect(provider.supports(profile())).toBe(true);
  });

  it('refuses a disabled profile', () => {
    expect(provider.supports(profile({ enabled: false }))).toBe(false);
  });

  it('refuses another provider profile', () => {
    expect(provider.supports(profile({ provider: 'someone-else' }))).toBe(false);
  });

  // docs/09 §9.8: the key is read into the adapter's closure and is not
  // reachable from an object other modules hold.
  it('does not expose the API key on the instance', () => {
    expect(JSON.stringify(provider)).not.toContain('sk-or-secret-value');
    expect(Object.values(provider)).not.toContain('sk-or-secret-value');
    expect(Object.keys(provider)).not.toContain('apiKey');
  });

  it('fails loudly rather than returning an empty result', async () => {
    await expect(provider.infer({} as never)).rejects.toThrow(/Not implemented/);
  });
});
