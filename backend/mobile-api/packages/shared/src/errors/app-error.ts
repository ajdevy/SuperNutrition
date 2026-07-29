import { ERROR_CODES, ERROR_STATUS, type ErrorCode } from './error-codes.js';

export interface ErrorDetail {
  readonly field: string;
  readonly issue: string;
}

/**
 * The one error type crossing the presentation boundary.
 *
 * `message` is for developers and is never user-facing copy — localized copy is
 * the client's concern (docs/06 §6.2). Anything a caller should not see stays
 * in `cause`, which is logged and never serialized.
 */
export class AppError extends Error {
  readonly code: ErrorCode;
  readonly status: number;
  readonly details: readonly ErrorDetail[];
  override readonly cause?: unknown;

  constructor(
    code: ErrorCode,
    message: string,
    options: { details?: readonly ErrorDetail[]; cause?: unknown } = {},
  ) {
    super(message);
    this.name = 'AppError';
    this.code = code;
    this.status = ERROR_STATUS[code];
    this.details = options.details ?? [];
    if (options.cause !== undefined) this.cause = options.cause;
  }

  static validation(message: string, details: readonly ErrorDetail[] = []): AppError {
    return new AppError(ERROR_CODES.VALIDATION_FAILED, message, { details });
  }

  static unauthenticated(message = 'Authentication required.'): AppError {
    return new AppError(ERROR_CODES.UNAUTHENTICATED, message);
  }

  static forbidden(message = 'Not permitted.'): AppError {
    return new AppError(ERROR_CODES.FORBIDDEN, message);
  }

  static notFound(code: ErrorCode, message: string): AppError {
    return new AppError(code, message);
  }

  static conflict(message: string): AppError {
    return new AppError(ERROR_CODES.CONFLICT, message);
  }

  static internal(message = 'Internal error.', cause?: unknown): AppError {
    return new AppError(ERROR_CODES.INTERNAL_ERROR, message, { cause });
  }

  /** Wire shape. `cause` is deliberately absent. */
  toResponse(correlationId: string): {
    error: {
      code: ErrorCode;
      message: string;
      details: readonly ErrorDetail[];
      correlationId: string;
    };
  } {
    return {
      error: {
        code: this.code,
        message: this.message,
        details: this.details,
        correlationId,
      },
    };
  }
}

export const isAppError = (value: unknown): value is AppError => value instanceof AppError;
