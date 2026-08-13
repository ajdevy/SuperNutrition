import { AsyncLocalStorage } from 'node:async_hooks';

export interface RequestContext {
  readonly correlationId: string;
  readonly requestId: string;
  readonly attemptId?: string;
}

const storage = new AsyncLocalStorage<RequestContext>();

/** Correlation ID appears in every log line and every error response. */
export const runWithContext = <T>(context: RequestContext, fn: () => T): T =>
  storage.run(context, fn);

export const currentContext = (): RequestContext | undefined => storage.getStore();

export const currentCorrelationId = (): string => currentContext()?.correlationId ?? 'unknown';
