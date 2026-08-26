import { describe, it, expect, vi } from 'vitest';
import { isAppError } from '@supernutrition/shared';
import { TestModeAuthenticator } from './auth.js';
import { TestModeProductAuthenticator } from './product-auth.js';

/**
 * The Product API owns durable user data, so it refuses an anonymous request
 * rather than resolving it to an account. The gateway's test-mode authenticator
 * deliberately accepts anything — correct there, wrong here (docs/09 §9.2).
 */

const authenticator = (resolve = vi.fn(async () => 'user-1' as string | null)) => ({
  resolve,
  auth: new TestModeProductAuthenticator(new TestModeAuthenticator('development'), { resolve }),
});

const codeOf = async (call: Promise<unknown>): Promise<{ code: string; status: number }> => {
  try {
    await call;
  } catch (error) {
    if (isAppError(error)) return { code: error.code, status: error.status };
    throw error;
  }
  throw new Error('expected the call to reject');
};

describe('product authentication', () => {
  it('refuses a request with no credential', async () => {
    const { auth, resolve } = authenticator();
    expect(await codeOf(auth.authenticate(undefined))).toEqual({
      code: 'UNAUTHENTICATED',
      status: 401,
    });
    expect(resolve).not.toHaveBeenCalled();
  });

  it.each([
    ['an empty header', ''],
    ['a bearer prefix with no token', 'Bearer '],
    ['whitespace', '   '],
  ])('refuses %s', async (_name, header) => {
    const { auth } = authenticator();
    expect((await codeOf(auth.authenticate(header))).code).toBe('UNAUTHENTICATED');
  });

  it('resolves the account behind the installation the token identifies', async () => {
    const { auth, resolve } = authenticator();
    const principal = await auth.authenticate('Bearer test.abcdefghijklmnop');

    expect(principal).toEqual({
      userId: 'user-1',
      installationId: 'test.abcdefghijklmnop',
      authMode: 'test-mode',
    });
    expect(resolve).toHaveBeenCalledWith('test.abcdefghijklmnop');
  });

  it('labels the principal test-mode, which is what keeps it out of a real market', async () => {
    const { auth } = authenticator();
    expect((await auth.authenticate('Bearer test.abcdefghijklmnop')).authMode).toBe('test-mode');
  });

  // An erased account does not come back by presenting the same installation id.
  it('refuses when the account is erased or unavailable', async () => {
    const { auth } = authenticator(vi.fn(async () => null));
    expect((await codeOf(auth.authenticate('Bearer test.abcdefghijklmnop'))).code).toBe(
      'UNAUTHENTICATED',
    );
  });

  it('leaks nothing about why the account is unavailable', async () => {
    const { auth } = authenticator(vi.fn(async () => null));
    try {
      await auth.authenticate('Bearer test.abcdefghijklmnop');
    } catch (error) {
      if (!isAppError(error)) throw error;
      expect(error.message).not.toMatch(/deleted|erased|suspended/i);
    }
  });
});
