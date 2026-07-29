import { loadConfig, testModeBanner, TestModeFenceError, ConfigError } from './config/index.js';
import { createLogger } from './logging/index.js';

/**
 * Composition root — the only place concrete adapters are chosen (AD-1, AD-10).
 *
 * Startup order matters. Configuration is validated and the TEST_MODE fence is
 * asserted BEFORE anything else is constructed, so a misconfigured process
 * exits non-zero rather than starting and serving (docs/09 §9.2).
 */
const main = async (): Promise<void> => {
  let config;
  try {
    config = loadConfig(process.env);
  } catch (error) {
    if (error instanceof TestModeFenceError || error instanceof ConfigError) {
      process.stderr.write(`${error.name}: ${error.message}\n`);
      process.exit(1);
    }
    throw error;
  }

  const banner = testModeBanner({
    TEST_MODE: config.auth.testMode,
    NODE_ENV: config.nodeEnv,
    APP_ENVIRONMENT: config.env,
  });
  if (banner) process.stderr.write(`${banner}\n`);

  const logger = createLogger(config);
  logger.info({ telemetry: { environment: config.env } }, 'mobile-api starting');

  // Stage 3 constructs the database, Redis, providers, repositories, use cases,
  // and the HTTP server here, and binds them to their ports.
  throw new Error('Not implemented in the Stage 2 skeleton: composition root wiring');
};

void main();
