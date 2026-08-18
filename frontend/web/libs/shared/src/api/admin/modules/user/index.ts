/**
 * User Module (Admin App)
 *
 * The admin users table and its filters. Organizations live in the sibling
 * `organization` module; the two are shown together on one surface per
 * `Admin - Users & Organizations.dc.html`.
 */

export { useAdminUsers, type UseAdminUsersOptions, type UseAdminUsersResult } from './user.hooks';
export { ADMIN_USERS, USER_LIST_FIELDS } from './user.queries';
