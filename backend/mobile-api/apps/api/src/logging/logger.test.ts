import { describe, it, expect } from 'vitest';
import { createLogger } from './logger.js';
import { runWithContext, currentContext, currentCorrelationId } from './correlation.js';
import type { AppConfig } from '../config/index.js';

const config = {
  env: 'development',
  nodeEnv: 'development',
  server: { host: '0.0.0.0', port: 3000 },
  database: {
    url: 'postgres://x',
    maxConnections: 1,
    idleTimeoutSeconds: 1,
    connectTimeoutSeconds: 1,
  },
  redis: { url: 'redis://x' },
  auth: { issuer: 'i', accessTtlSeconds: 900, refreshTtlSeconds: 1000, testMode: false },
  inference: { remoteEnabled: true, modelProfilesPath: 'p', openRouterBaseUrl: 'https://x' },
  logging: { level: 'info', allowlistPath: 'a' },
} as AppConfig;

describe('logger', () => {
  it('creates a logger at the configured level', () => {
    expect(createLogger(config).level).toBe('info');
  });

  it('labels every record with the resolved auth mode', () => {
    const testModeConfig = { ...config, auth: { ...config.auth, testMode: true } } as AppConfig;
    expect(createLogger(testModeConfig)).toBeDefined();
    expect(createLogger(config)).toBeDefined();
  });
});

describe('correlation context', () => {
  it('exposes the context inside the scope', () => {
    runWithContext({ correlationId: 'corr-1', requestId: 'req-1' }, () => {
      expect(currentContext()?.correlationId).toBe('corr-1');
      expect(currentCorrelationId()).toBe('corr-1');
    });
  });

  it('is absent outside any scope', () => {
    expect(currentContext()).toBeUndefined();
    expect(currentCorrelationId()).toBe('unknown');
  });

  it('carries an attempt id when one is in play', () => {
    runWithContext({ correlationId: 'c', requestId: 'r', attemptId: 'att_x' }, () => {
      expect(currentContext()?.attemptId).toBe('att_x');
    });
  });
});
