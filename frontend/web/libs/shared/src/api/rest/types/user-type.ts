/**
 * REST API User Type Enum
 *
 * Matches backend UserType enum from com.pml.identity.domain.enums.UserType
 * This enum is REST-specific and should NOT be confused with GraphQL enums.
 *
 * Used for:
 * - Admin user creation endpoints
 * - User registration DTOs
 * - Role mapping in REST responses
 */
export enum RestUserType {
  EVENT_ORGANIZER = 'EVENT_ORGANIZER',
  EVENT_ATTENDEE = 'EVENT_ATTENDEE',
  FINANCE_ADMIN = 'FINANCE_ADMIN',
  OPERATIONS_ADMIN = 'OPERATIONS_ADMIN',
  SYSTEM_ADMIN = 'SYSTEM_ADMIN',
  SUPER_ADMIN = 'SUPER_ADMIN'
}
