# Route Security Analysis - Admin App

This document analyzes all files responsible for securing routes in the admin application.

## Architecture Overview

The admin app uses a **multi-layered route security approach**:

1. **Layout-Level Protection**: All dashboard routes are protected at the layout level
2. **Page-Level Protection**: Individual pages can specify additional permission requirements
3. **Component-Level Protection**: Fine-grained control using PermissionGate for UI elements
4. **Menu-Level Filtering**: Navigation menu items are filtered based on permissions

## Core Security Files

### 1. Authentication & Session Management

#### `libs/shared/src/auth/keycloak-provider.tsx`
- **Purpose**: Keycloak authentication provider
- **Role**: Manages authentication session via keycloak-js adapter
- **Key Features**:
  - Wraps app with KeycloakProvider
  - Provides `useKeycloak()` hook for auth state
  - Automatic token refresh (onTokenExpired, periodic, on-demand)
  - PKCE (S256) for secure authentication

#### `apps/admin/src/app/page.tsx`
- **Purpose**: Root page redirect logic
- **Role**: Redirects authenticated users to `/dashboard`, unauthenticated to `/login`
- **Security**: Uses `usePermissions()` to check authentication status

### 2. Permission Management

#### `apps/admin/src/lib/hooks/usePermissions.ts`
- **Purpose**: Core permission checking hook
- **Role**: Provides permission checking utilities and authentication state
- **Key Functions**:
  - `hasPermission(permission)`: Check single permission
  - `hasAnyPermission(permissions[])`: Check if user has any of the permissions
  - `hasAllPermissions(permissions[])`: Check if user has all permissions
  - `isAuthenticated`: Authentication status
  - `permissions`: Array of user permissions from backend
- **Data Source**: GraphQL query `CURRENT_USER_PERMISSIONS`
- **Dependencies**: Better Auth session, Apollo Client

#### `apps/admin/src/lib/route-permissions.ts`
- **Purpose**: Route-to-permission mapping configuration
- **Role**: Defines which permissions are required for each route
- **Key Components**:
  - `ROUTE_PERMISSIONS`: Array of route permission configurations
  - `MENU_ITEMS`: Menu item configurations with permission requirements
  - `getRoutePermissions(route)`: Get permission config for a route
  - `canAccessRoute(route, permissions)`: Check if user can access a route
  - `filterMenuItemsByPermissions(items, permissions)`: Filter menu items by permissions
- **Permission Format**: Uses dot.case format (e.g., `users.view`, `events.manage`)

### 3. Route Protection Components

#### `apps/admin/src/components/ProtectedRoute.tsx`
- **Purpose**: Page-level route protection component
- **Role**: Wraps pages to enforce authentication and permission checks
- **Features**:
  - Checks authentication status
  - Validates required permissions
  - Redirects unauthenticated users to `/login`
  - Redirects unauthorized users to `/dashboard`
  - Shows loading state during permission checks
  - Displays "Access Denied" UI for unauthorized access
- **Usage Pattern**:
  ```tsx
  <ProtectedRoute requiredPermissions={['users.view']}>
    <PageContent />
  </ProtectedRoute>
  ```

#### `apps/admin/src/components/PermissionGate.tsx`
- **Purpose**: Component-level permission gating
- **Role**: Conditionally renders children based on permissions
- **Features**:
  - Supports single or multiple permissions
  - `requireAll` option for requiring all vs any permission
  - Optional fallback UI for unauthorized users
- **Usage Pattern**:
  ```tsx
  <PermissionGate permission="users.manage">
    <Button>Delete User</Button>
  </PermissionGate>
  ```

#### `apps/admin/src/components/security-components.tsx`
- **Purpose**: Barrel export for security components
- **Role**: Centralized exports for `ProtectedRoute` and `PermissionGate`
- **Exports**:
  - `ProtectedRoute`
  - `PermissionGate`

### 4. Layout-Level Protection

#### `apps/admin/src/app/(dashboard)/layout.tsx`
- **Purpose**: Dashboard layout wrapper
- **Role**: Applies base-level authentication protection to all dashboard routes
- **Protection**: Wraps all dashboard pages in `<ProtectedRoute>` (no specific permissions)
- **Effect**: All routes under `/dashboard/*` require authentication

### 5. Navigation Security

#### `apps/admin/src/components/layout/AdminLayout.tsx`
- **Purpose**: Main admin layout with navigation
- **Role**: Filters menu items based on user permissions
- **Key Functions**:
  - `getManagementMenuItems(permissions)`: Filters management menu items
  - `getFinancialMenuItems(permissions)`: Filters financial menu items
  - `getSystemMenuItems(permissions)`: Filters system menu items
  - Uses `filterMenuItemsByPermissions()` from `route-permissions.ts`
- **Security**: Only shows menu items user has permission to access

## Security Flow Diagram

```
User Navigation Request
    ↓
1. Root Page (page.tsx)
    ├─→ Check Authentication (usePermissions)
    ├─→ Authenticated? → /dashboard
    └─→ Not Authenticated? → /login
    ↓
2. Dashboard Layout ((dashboard)/layout.tsx)
    ├─→ ProtectedRoute (authentication check)
    ├─→ Authenticated? → Continue
    └─→ Not Authenticated? → Redirect to /login
    ↓
3. Individual Page (e.g., users/page.tsx)
    ├─→ ProtectedRoute with requiredPermissions
    ├─→ Check Permissions (hasAllPermissions)
    ├─→ Has Permissions? → Render Page
    └─→ No Permissions? → Show "Access Denied" or Redirect
    ↓
4. Navigation Menu (AdminLayout.tsx)
    ├─→ Filter Menu Items (filterMenuItemsByPermissions)
    ├─→ Show Only Accessible Routes
    └─→ Hide Routes User Cannot Access
    ↓
5. Component-Level (PermissionGate)
    ├─→ Check Permission for UI Element
    ├─→ Has Permission? → Render Component
    └─→ No Permission? → Render Fallback or Nothing
```

## Permission Checking Logic

### Authentication Check
1. `usePermissions()` hook checks Keycloak session via `useKeycloak()`
2. If not authenticated → `isAuthenticated = false`
3. If authenticated → `isAuthenticated = true`

### Permission Check
1. `usePermissions()` fetches permissions via GraphQL query
2. Permissions come from backend JWT token
3. Permission format: dot.case (e.g., `users.view`, `events.manage`)
4. Checks are case-sensitive and exact match

### Route Access Logic
1. Get route config from `ROUTE_PERMISSIONS`
2. If no config or empty permissions → Allow access
3. If permissions required:
   - Check if user has required permissions
   - Default: requires ANY permission (can be changed to ALL)
   - If user lacks permissions → Deny access

## Files by Security Layer

### Layer 1: Authentication
- `libs/shared/src/auth/keycloak-provider.tsx` - Keycloak provider & hooks
- `apps/admin/src/components/Providers.tsx` - App providers setup
- `apps/admin/src/app/page.tsx` - Root redirect
- `apps/admin/src/lib/hooks/usePermissions.ts` - Auth state hook

### Layer 2: Route Protection
- `apps/admin/src/app/(dashboard)/layout.tsx` - Layout-level protection
- `apps/admin/src/components/ProtectedRoute.tsx` - Page-level protection
- `apps/admin/src/lib/route-permissions.ts` - Permission mappings

### Layer 3: Navigation Security
- `apps/admin/src/components/layout/AdminLayout.tsx` - Menu filtering
- `apps/admin/src/lib/route-permissions.ts` - Menu item permissions

### Layer 4: Component Security
- `apps/admin/src/components/PermissionGate.tsx` - Component-level gating
- `apps/admin/src/components/security-components.tsx` - Exports

## Page-Level Protection Examples

### Example 1: Users Page
```tsx
// apps/admin/src/app/(dashboard)/users/page.tsx
export default function UsersPage() {
  return (
    <ProtectedRoute requiredPermissions={['users.view']}>
      <UsersPageContent />
    </ProtectedRoute>
  );
}
```

### Example 2: Events Pending Approval
```tsx
// apps/admin/src/app/(dashboard)/events/pending-approval/page.tsx
export default function PendingApprovalPage() {
  return (
    <ProtectedRoute requiredPermissions={['events.manage']}>
      <PendingApprovalContent />
    </ProtectedRoute>
  );
}
```

### Example 3: Accounts (Escrow)
```tsx
// apps/admin/src/app/(dashboard)/accounts/escrow/page.tsx
export default function EscrowAccountsPage() {
  return (
    <ProtectedRoute requiredPermissions={['escrow.view']}>
      <EscrowAccountsContent />
    </ProtectedRoute>
  );
}
```

## Permission Format

All permissions use **dot.case format** matching backend `Permissions.java`:

- `users.view`, `users.manage`
- `events.view`, `events.create`, `events.manage`
- `tickets.view`, `tickets.manage`
- `transactions.view`, `transactions.manage`
- `payouts.view`, `payouts.manage`
- `escrow.view`, `escrow.manage`
- `analytics.view`
- `system.manage`
- `audit.view`

## Security Best Practices

1. **Defense in Depth**: Multiple layers of protection
   - Layout-level (all routes)
   - Page-level (specific routes)
   - Component-level (UI elements)

2. **Permission Consistency**: 
   - Menu items match route permissions
   - Frontend permissions match backend permissions
   - Use `route-permissions.ts` as single source of truth

3. **User Experience**:
   - Show loading states during permission checks
   - Redirect unauthorized users gracefully
   - Hide inaccessible menu items (don't show then deny)

4. **Error Handling**:
   - Log permission denials for auditing
   - Provide clear "Access Denied" messages
   - Redirect to safe fallback routes

## Common Issues & Solutions

### Issue 1: Permission Mismatch
**Problem**: Menu requires `users.manage` but route requires `users.view`
**Solution**: Align menu permissions with route permissions in `route-permissions.ts`

### Issue 2: Missing Route Config
**Problem**: Route not in `ROUTE_PERMISSIONS` array
**Solution**: Add route configuration to `ROUTE_PERMISSIONS`

### Issue 3: Wrong Permission Name
**Problem**: Using `accounts.view` but backend has `escrow.view`
**Solution**: Verify permission names match backend `Permissions.java`

### Issue 4: Navigation Not Working
**Problem**: Links don't navigate (nested links, missing handlers)
**Solution**: Ensure proper Link component usage, check for nested anchors

## Testing Route Security

To test route security:

1. **Authentication Test**: Log out and try accessing `/dashboard` → Should redirect to `/login`
2. **Permission Test**: Use account with limited permissions → Should see "Access Denied" for restricted routes
3. **Menu Test**: Check navigation menu → Should only show accessible routes
4. **Component Test**: Check buttons/actions → Should only show for users with permissions

## Summary

**Primary Security Files:**
1. `ProtectedRoute.tsx` - Page-level protection
2. `PermissionGate.tsx` - Component-level protection
3. `usePermissions.ts` - Permission checking hook
4. `route-permissions.ts` - Permission configuration
5. `(dashboard)/layout.tsx` - Layout-level protection
6. `AdminLayout.tsx` - Navigation filtering
7. `keycloak-provider.tsx` - Keycloak authentication

**Security Flow:**
Authentication → Layout Protection → Page Protection → Component Protection

**Key Principle:**
All routes are protected by default at the layout level, with additional permission checks at the page and component levels for fine-grained access control.

