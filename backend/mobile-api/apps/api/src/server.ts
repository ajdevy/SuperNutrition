import type { AppConfig } from './config/index.js';
import type { AppLogger } from './logging/index.js';
import { MIDDLEWARE_ORDER } from './middleware/index.js';
import { GATEWAY_ROUTES, PRODUCT_ROUTES } from './routes/index.js';

/**
 * HTTP server assembly. Stage 2 fixes the shape so middleware order and route
 * registration can be reviewed; Fastify wiring is Stage 3.
 */
export interface ServerDeps {
  readonly config: AppConfig;
  readonly logger: AppLogger;
}

export interface ServerHandle {
  readonly routes: readonly { method: string; path: string }[];
  readonly middleware: readonly string[];
  listen(): Promise<void>;
  close(): Promise<void>;
}

export const describeServer = (): {
  middleware: readonly string[];
  routes: readonly { method: string; path: string }[];
} => ({
  middleware: MIDDLEWARE_ORDER,
  routes: [...GATEWAY_ROUTES, ...PRODUCT_ROUTES],
});
