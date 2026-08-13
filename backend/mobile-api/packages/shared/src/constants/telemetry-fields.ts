/**
 * AD-14 telemetry allowlist.
 *
 * Fields not listed here are dropped before a record is emitted. The list is
 * data, and the logger's serializer is built from it, because an allowlist
 * implemented as a review convention is not an allowlist (docs/09 §9.10).
 */
export const TELEMETRY_ALLOWLIST = [
  'correlationId',
  'requestId',
  'attemptId',
  'effectId',
  'route',
  'method',
  'statusCode',
  'capabilityTier',
  'durationBucket',
  'resourceBucket',
  'outcome',
  'actionCode',
  'sourceCoverageCode',
  'modelId',
  'modelProfileVersion',
  'catalogDatasetVersion',
  'configurationVersion',
  'contractVersion',
  'environment',
  'authMode',
  'marketJurisdiction',
  'processingRegion',
  'failureCode',
  'passNumber',
] as const;

export type TelemetryField = (typeof TELEMETRY_ALLOWLIST)[number];

/**
 * Named explicitly so the negative test has something to assert against. A
 * record containing any of these must emit none of them (docs/09 §9.10).
 */
export const FORBIDDEN_TELEMETRY_FIELDS = [
  'payload',
  'photo',
  'imageBytes',
  'photoDigest',
  'filename',
  'path',
  'label',
  'foodLabel',
  'transcript',
  'prompt',
  'promptText',
  'responseBody',
  'requestBody',
  'headers',
  'authorization',
  'token',
  'refreshToken',
  'apiKey',
  'userId',
  'email',
] as const;

const ALLOWED = new Set<string>(TELEMETRY_ALLOWLIST);

export const isAllowedTelemetryField = (field: string): field is TelemetryField =>
  ALLOWED.has(field);

/**
 * Drop every non-allowlisted key. Applied at the serializer, so a caller
 * cannot opt out by passing a richer object.
 */
export const applyTelemetryAllowlist = (
  record: Readonly<Record<string, unknown>>,
): Record<string, unknown> => {
  const out: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(record)) {
    if (isAllowedTelemetryField(key)) out[key] = value;
  }
  return out;
};
