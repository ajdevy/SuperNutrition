import { applyTelemetryAllowlist } from '@supernutrition/shared';

/**
 * AD-14 enforcement point.
 *
 * The serializer is built from the allowlist, so a caller cannot opt out by
 * passing a richer object. An allowlist implemented as a review convention is
 * not an allowlist (docs/09 §9.10).
 */
export const telemetrySerializer = (
  record: Readonly<Record<string, unknown>>,
): Record<string, unknown> => applyTelemetryAllowlist(record);

/**
 * Pino serializers for the request and response objects. Bodies and headers
 * never appear: photo payloads, transcripts, prompts, and tokens are all
 * carried in places these deliberately do not read.
 */
export const requestSerializer = (req: {
  method?: string;
  url?: string;
  id?: unknown;
}): Record<string, unknown> => ({
  method: req.method,
  route: req.url,
  requestId: typeof req.id === 'string' ? req.id : undefined,
});

export const responseSerializer = (res: { statusCode?: number }): Record<string, unknown> => ({
  statusCode: res.statusCode,
});
