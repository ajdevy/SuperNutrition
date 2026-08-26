import { randomBytes } from 'node:crypto';

/**
 * Correlation IDs.
 *
 * Assigned first, before anything can fail, so every log line and every error
 * response carries one — a user report is then traceable without asking for a
 * screenshot (docs/06 §6.2).
 */
export const generateCorrelationId = (): string => randomBytes(16).toString('hex');

const CORRELATION_PATTERN = /^[A-Za-z0-9._-]{8,128}$/;

/**
 * A client-supplied ID is accepted only if it is well-formed. An arbitrary
 * header value would end up in structured logs, where a crafted string could
 * forge log entries.
 */
export const resolveCorrelationId = (supplied: string | undefined): string =>
  supplied !== undefined && CORRELATION_PATTERN.test(supplied) ? supplied : generateCorrelationId();
