import { pino, type Logger, type LoggerOptions } from 'pino';
import type { AppConfig } from '../config/index.js';
import {
  telemetrySerializer,
  requestSerializer,
  responseSerializer,
} from './telemetry-serializer.js';

export type AppLogger = Logger;

/**
 * Structured Pino with the AD-14 allowlist wired into the serializer chain.
 * Stage 2 provides the skeleton; transports and destinations are Stage 3.
 */
export const createLogger = (config: AppConfig): AppLogger => {
  const options: LoggerOptions = {
    level: config.logging.level,
    base: { environment: config.env, authMode: config.auth.testMode ? 'test-mode' : 'attested' },
    redact: {
      // Defence in depth. The allowlist already drops these; this catches a
      // path that bypasses the telemetry serializer.
      paths: ['req.headers.authorization', 'req.headers.cookie', '*.token', '*.apiKey'],
      remove: true,
    },
    serializers: {
      req: requestSerializer,
      res: responseSerializer,
      telemetry: telemetrySerializer,
    },
  };
  return pino(options);
};
