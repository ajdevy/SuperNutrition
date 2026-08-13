/**
 * Explicit success/failure without exceptions.
 *
 * Expected failures are typed and returned; unexpected failures throw. This is
 * the spine's error convention applied at the type level: a caller cannot
 * ignore a failure it was handed, and a domain that can only return `Result`
 * cannot leak a platform exception outward.
 */
export type Result<T, E> = Ok<T> | Err<E>;

export interface Ok<T> {
  readonly ok: true;
  readonly value: T;
}

export interface Err<E> {
  readonly ok: false;
  readonly error: E;
}

export const ok = <T>(value: T): Ok<T> => ({ ok: true, value });

export const err = <E>(error: E): Err<E> => ({ ok: false, error });

export const isOk = <T, E>(result: Result<T, E>): result is Ok<T> => result.ok;

export const isErr = <T, E>(result: Result<T, E>): result is Err<E> => !result.ok;

/** Transform the success value, leaving a failure untouched. */
export const map = <T, U, E>(result: Result<T, E>, fn: (value: T) => U): Result<U, E> =>
  result.ok ? ok(fn(result.value)) : result;

/** Transform the failure, leaving a success untouched. */
export const mapErr = <T, E, F>(result: Result<T, E>, fn: (error: E) => F): Result<T, F> =>
  result.ok ? result : err(fn(result.error));

/** Chain an operation that may itself fail. */
export const flatMap = <T, U, E>(
  result: Result<T, E>,
  fn: (value: T) => Result<U, E>,
): Result<U, E> => (result.ok ? fn(result.value) : result);

/** Extract the value, substituting a fallback for a failure. */
export const unwrapOr = <T, E>(result: Result<T, E>, fallback: T): T =>
  result.ok ? result.value : fallback;

/**
 * Extract the value or throw. Reserved for call sites that have already proven
 * success; using it to avoid handling a failure defeats the type.
 */
export const unwrap = <T, E>(result: Result<T, E>): T => {
  if (result.ok) return result.value;
  throw new Error(`Called unwrap on a failed Result: ${JSON.stringify(result.error)}`);
};

/** Collect results, short-circuiting on the first failure. */
export const all = <T, E>(results: readonly Result<T, E>[]): Result<T[], E> => {
  const values: T[] = [];
  for (const result of results) {
    if (!result.ok) return result;
    values.push(result.value);
  }
  return ok(values);
};
