/**
 * Errors the fail-closed guard raises.
 *
 * Both carry an HTTP status so route handlers and server actions can translate them without
 * re-deriving intent. The distinction matters to the person on the other end: one means "sign in
 * again", the other means "this is our problem, try shortly".
 *
 * @module libs/shared/src/auth/revocation/errors
 */

/** The presented token has been revoked — logout, ban, admin revoke, compromise response. */
export class TokenRevokedError extends Error {
  readonly status = 401 as const;
  readonly code = 'TOKEN_REVOKED' as const;

  constructor(public readonly operation: string) {
    super('Your session has been revoked. Please sign in again.');
    this.name = 'TokenRevokedError';
  }
}

/**
 * Neither the cache nor the durable store could resolve the token, and the operation is
 * fail-closed.
 *
 * Carries 503 rather than 403 because this is a transient infrastructure condition rather than a
 * permission problem, so the user is told to retry rather than that they lack access.
 */
export class RevocationUnavailableError extends Error {
  readonly status = 503 as const;
  readonly code = 'REVOCATION_UNAVAILABLE' as const;

  constructor(public readonly operation: string) {
    super(
      'This action is temporarily unavailable because the session-revocation service ' +
        'cannot be reached. Please try again in a moment.'
    );
    this.name = 'RevocationUnavailableError';
  }
}

/** A revocation could not be written to the system of record. The token is still live. */
export class RevocationWriteError extends Error {
  readonly code = 'REVOCATION_WRITE_FAILED' as const;

  constructor(message: string, public override readonly cause?: unknown) {
    super(message);
    this.name = 'RevocationWriteError';
  }
}
