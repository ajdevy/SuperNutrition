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
 * Drop every non-allowlisted key, and every structured value.
 *
 * The filter walked one level: `isAllowedTelemetryField(key)` decided, and the
 * value went through whatever it was. So an allowlisted key holding an object
 * carried every field inside it, unread — `outcome: { transcript: … }` emits the
 * transcript, because `outcome` is on the list and nothing looked further.
 *
 * Scalars-only rather than recursion, because it is the stronger rule and the
 * true one: every field on this list is an id, a code, a bucket or a version.
 * None is legitimately an object, so there is no structure to recurse INTO, and
 * a rule that permits nesting invites a future field to smuggle content through
 * a shape nobody reviewed. An array of scalars is kept, since a list of codes is
 * still a list of codes.
 *
 * Applied at the serializer, so a caller cannot opt out by passing a richer
 * object.
 */
const isScalar = (value: unknown): boolean =>
  value === null ||
  typeof value === 'string' ||
  typeof value === 'number' ||
  typeof value === 'boolean' ||
  typeof value === 'undefined';

export const applyTelemetryAllowlist = (
  record: Readonly<Record<string, unknown>>,
): Record<string, unknown> => {
  const out: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(record)) {
    if (!isAllowedTelemetryField(key)) continue;
    if (isScalar(value)) {
      out[key] = value;
      continue;
    }
    if (Array.isArray(value) && value.every(isScalar)) {
      out[key] = value;
      continue;
    }
    // Structured value under an allowlisted name: dropped, not inspected.
  }
  return out;
};
