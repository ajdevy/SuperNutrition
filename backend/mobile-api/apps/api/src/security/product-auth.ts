import { AppError, type AuthMode } from '@supernutrition/shared';
import type { TestModeAuthenticator } from './auth.js';

/**
 * Product API identity (docs/09 §9.2).
 *
 * The gateway's test-mode authenticator hands out a context for any request,
 * including one with no credential at all — correct there, because the gateway
 * is fenced by market policy and its own test-mode refusal. The Product API
 * owns durable user data, so an anonymous request is refused here rather than
 * silently resolving to somebody's account.
 */
export interface ProductPrincipal {
  readonly userId: string;
  readonly installationId: string;
  readonly authMode: AuthMode;
}

/**
 * Narrow port over the users module. Declared here rather than imported so the
 * security layer keeps no edge to a module or to persistence; the composition
 * root supplies the implementation.
 */
export interface InstallationUserResolver {
  /** The account for this installation, provisioning on first access. */
  resolve(externalRef: string): Promise<string | null>;
}

const bearer = (header: string | undefined): string =>
  header?.replace(/^Bearer\s+/i, '').trim() ?? '';

export class TestModeProductAuthenticator {
  readonly #tokens: TestModeAuthenticator;
  readonly #users: InstallationUserResolver;

  constructor(tokens: TestModeAuthenticator, users: InstallationUserResolver) {
    this.#tokens = tokens;
    this.#users = users;
  }

  async authenticate(authorizationHeader: string | undefined): Promise<ProductPrincipal> {
    if (bearer(authorizationHeader).length === 0) throw AppError.unauthenticated();

    const context = this.#tokens.authenticate(authorizationHeader);
    const userId = await this.#users.resolve(context.installationId);

    // Null means erased or unavailable. An erased account does not come back by
    // presenting the same installation id (docs/09 §9.7).
    if (userId === null) throw AppError.unauthenticated('Account is not available.');

    return { userId, installationId: context.installationId, authMode: context.authMode };
  }
}
