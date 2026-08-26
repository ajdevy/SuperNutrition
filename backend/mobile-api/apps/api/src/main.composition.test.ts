import { describe, it, expect, afterEach } from 'vitest';
import { buildApplication } from './main.js';
import { createLogger } from './logging/index.js';
import type { AppConfig } from './config/index.js';

/**
 * M-05 — the composition root was excluded from the coverage denominator while
 * AD-33 requires every compiled production source to belong to exactly one.
 *
 * The exclusion mattered more than a percentage: `buildApplication` is where the
 * memory/redis choice, the instance fence, the provider fence, the configuration
 * snapshot and the shutdown path are decided, and none of it was exercised. The
 * dead `RedisAttemptRegistry` — written, tested in isolation, composed nowhere —
 * is exactly the class of defect a covered composition root catches.
 */

const baseConfig = (over: Record<string, unknown> = {}): AppConfig =>
  ({
    env: 'development',
    nodeEnv: 'development',
    server: { host: '127.0.0.1', port: 0 },
    database: {
      url: 'postgres://user:pass@127.0.0.1:5432/none',
      maxConnections: 1,
      idleTimeoutSeconds: 1,
      connectTimeoutSeconds: 1,
    },
    redis: { url: 'redis://127.0.0.1:6379' },
    deployment: { instanceCount: 1, gatewayStateBackend: 'memory' },
    auth: {
      issuer: 'i',
      accessTtlSeconds: 900,
      refreshTtlSeconds: 1000,
      testMode: true,
      privateKey: '',
      publicKey: '',
    },
    inference: {
      remoteEnabled: true,
      modelProfilesPath: './config/model-profiles.toml',
      configurationSnapshotPath: './config/configuration-snapshot.toml',
      openRouterBaseUrl: 'https://example.invalid',
    },
    stubInference: false,
    logging: { level: 'silent' },
    ...over,
  }) as unknown as AppConfig;

let handle: { close: () => Promise<void> } | null = null;

afterEach(async () => {
  await handle?.close();
  handle = null;
});

describe('the composition root builds a working application', () => {
  it('composes the memory backend and serves the health route', async () => {
    const config = baseConfig();
    handle = await buildApplication(config, createLogger(config));

    const response = await (
      handle as never as { app: { inject: (o: unknown) => Promise<{ statusCode: number }> } }
    ).app.inject({
      method: 'GET',
      url: '/health',
    });
    expect(response.statusCode).toBeLessThan(500);
  });

  it('refuses to build with more than one instance on the memory backend', async () => {
    // The fence, exercised through the real composition rather than a unit call.
    const config = baseConfig({
      deployment: { instanceCount: 2, gatewayStateBackend: 'memory' },
    });
    await expect(buildApplication(config, createLogger(config))).rejects.toThrow(/per-process/);
  });

  it('refuses a stub provider without test mode', async () => {
    const config = baseConfig({
      stubInference: true,
      auth: { issuer: 'i', accessTtlSeconds: 900, refreshTtlSeconds: 1000, testMode: false },
    });
    await expect(buildApplication(config, createLogger(config))).rejects.toThrow(/STUB_INFERENCE/);
  });

  it('refuses a free-tier roster in production', async () => {
    const config = baseConfig({ env: 'production', nodeEnv: 'production' });
    await expect(buildApplication(config, createLogger(config))).rejects.toThrow(/Zero-cost/);
  });

  it('refuses a configuration snapshot that is not there', async () => {
    const config = baseConfig({
      inference: {
        remoteEnabled: true,
        modelProfilesPath: './config/model-profiles.toml',
        configurationSnapshotPath: './config/does-not-exist.toml',
        openRouterBaseUrl: 'https://example.invalid',
      },
    });
    await expect(buildApplication(config, createLogger(config))).rejects.toThrow();
  });
});
