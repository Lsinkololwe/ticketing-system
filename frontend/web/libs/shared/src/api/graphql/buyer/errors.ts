import { resolveError, type GraphQLLikeError, type ResolvedError } from '../../../lib/errors';

/**
 * The server's own error out of whatever Apollo threw: the first GraphQL error of a combined error carries the
 * `extensions.errorCode` the UI branches on; anything else is passed through unchanged.
 */
export function serverError(e: unknown): GraphQLLikeError {
  const any = e as { errors?: GraphQLLikeError[]; graphQLErrors?: GraphQLLikeError[] } | null | undefined;
  return any?.errors?.[0] ?? any?.graphQLErrors?.[0] ?? (e as GraphQLLikeError) ?? {};
}

/** Resolves a thrown Apollo error to the buyer-facing error contract. */
export function resolveServerError(e: unknown): ResolvedError {
  return resolveError(serverError(e));
}
