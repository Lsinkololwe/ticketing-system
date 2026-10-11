/**
 * Whether the signed-in account already carries the platform's organizer access.
 *
 * Joining an organization (accepting an invitation) creates the membership at once. The platform
 * role that the organizer operations check is granted by the membership sync a short while later,
 * and a session only sees it after its next sign-in. In between, a member is a member without the
 * role: the console says so and offers a re-check, instead of showing screens whose operations
 * would all be refused.
 */
export const ORGANIZER_ACCESS_ROLES = ['ORGANIZER', 'ADMIN'] as const;

export function hasOrganizerAccess(roles: readonly string[] | null | undefined): boolean {
  return (roles ?? []).some((role) => (ORGANIZER_ACCESS_ROLES as readonly string[]).includes(role));
}
