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
  routeOptions?: { url?: string };
}): Record<string, unknown> => ({
  method: req.method,
  // The route PATTERN, never the resolved URL.
  //
  // `req.url` carries the query string, and a query string is user input:
  // `/api/v1/catalog/search?q=<whatever the user typed>` put a food search — a
  // free-text field — into every request log line, under a field named `route`
  // that reads as structural. The pattern (`/api/v1/catalog/search`) is what a
  // reader of telemetry actually wants, and it is bounded by the routes this
  // service declares rather than by what a caller sends.
  //
  // Falls back to the path portion when the route is unknown — a 404 has no
  // matched pattern, and logging nothing there would hide the requests most
  // worth seeing.
  route: req.routeOptions?.url ?? req.url?.split('?')[0],
  requestId: typeof req.id === 'string' ? req.id : undefined,
});

export const responseSerializer = (res: { statusCode?: number }): Record<string, unknown> => ({
  statusCode: res.statusCode,
});
