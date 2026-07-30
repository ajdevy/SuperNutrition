/**
 * Surface A — the first-party inference gateway (docs/06 §6.5).
 *
 * This route group imports NO repository and NO database package. Its
 * inability to durably store user content is a dependency-graph property
 * enforced by the eslint boundaries rule, not a review convention
 * (docs/09 §9.6). Adding a persistence import here fails the lint build.
 */
export const GATEWAY_ROUTES = [
  { method: 'POST', path: '/gw/v1/session' },
  { method: 'GET', path: '/gw/v1/policy/market' },
  { method: 'POST', path: '/gw/v1/inference/photo' },
  { method: 'POST', path: '/gw/v1/inference/text' },
  { method: 'DELETE', path: '/gw/v1/inference/:attemptId' },
] as const;

/**
 * Enforcement order for the gateway. Every step from 1 to 5 must be able to
 * reject the request WITHOUT having called a provider, because a provider call
 * is the expensive, externally visible, privacy-relevant act (docs/10 §10.4).
 */
export const GATEWAY_PIPELINE = [
  'validate-size-and-type',
  'authenticate-and-replay-check',
  'rate-limit',
  'consent-and-market-gate',
  'effect-deduplication',
  'orchestrate-inference',
  'normalize-to-contract-v1',
  'dispose-payload',
  'record-telemetry',
] as const;

export type GatewayPipelineStep = (typeof GATEWAY_PIPELINE)[number];

// --- implementations ---------------------------------------------------------

export {
  validateRequest,
  assertRemoteEnabled,
  assertTransferAuthorized,
  verifyConsentAndMarket,
  magicBytesMatch,
  SUPPORTED_CONTRACT_VERSIONS,
  ALLOWED_CONTENT_TYPES,
  MAX_PAYLOAD_BYTES,
  EFFECT_TTL_SECONDS,
} from './inference.handler.js';
export type {
  GatewayResponseBody,
  OutcomeOut,
  CandidateOut,
  InferenceRequestInput,
} from './inference.handler.js';

export { TransferStateTracker, cancellationStatus } from './transfer-state.js';
export type { CancellationRecord } from './transfer-state.js';

export { runInferencePipeline } from './pipeline.js';
export type { PipelineDeps, PipelineInput, PayloadSink } from './pipeline.js';

export { registerGatewayRoutes } from './routes.js';
export type { GatewayRouteDeps } from './routes.js';
