# Auth Services

This directory contains service implementations for the authentication module, following the dependency injection pattern.

## Architecture

The services follow a layered architecture with clear separation of concerns:

```
┌─────────────────────────────────────────────────────────┐
│                    Service Layer                         │
├─────────────────────────────────────────────────────────┤
│  SessionService    │ OrganizationService │ TokenService  │
│  - Session verify  │ - Org status query  │ - Token ops  │
│  - Session get     │ - Route mapping     │ - Exchange   │
│  - Sign out        │ - Access validation │ - Validation │
│  - Refresh         │                     │ - Revocation │
└─────────────────────────────────────────────────────────┘
           │                    │                   │
           └────────────────────┼───────────────────┘
                                │
┌─────────────────────────────────────────────────────────┐
│                 Infrastructure Layer                     │
├─────────────────────────────────────────────────────────┤
│  Better Auth      │  GraphQL Backend    │  Keycloak     │
│  - Session API    │  - Organization     │  - OAuth2     │
│  - Cookie mgmt    │    queries          │  - JWT tokens │
└─────────────────────────────────────────────────────────┘
```

## Services

### SessionService

Manages user authentication sessions using Better Auth.

**Responsibilities:**
- Session verification with automatic redirect
- Session retrieval without redirect
- Sign out operations
- Session refresh
- Authentication status checks

**Dependencies:**
- Better Auth instance

**Example:**
```typescript
import { SessionService } from '@/lib/auth/services';
import { auth } from '@/lib/auth';

const sessionService = new SessionService(auth);
const session = await sessionService.verifySession();
```

### OrganizationService

Handles organization application status and routing logic.

**Responsibilities:**
- Fetch organization status from GraphQL
- Determine appropriate routes based on status
- Validate route access permissions
- Check approval status

**Dependencies:**
- ISessionService (for authentication)
- IAuthConfig (for GraphQL endpoint)

**Example:**
```typescript
import { OrganizationService } from '@/lib/auth/services';
import { authConfig } from '@/lib/auth/services/AuthConfig';

const orgService = new OrganizationService(sessionService, authConfig);
const status = await orgService.getStatus();
const route = orgService.getRouteForStatus(status.status);
```

### TokenService

Manages Keycloak tokens and OAuth2 operations.

**Responsibilities:**
- Retrieve Keycloak access tokens
- Exchange session tokens for access tokens
- Validate token expiration
- Decode JWT tokens
- Revoke tokens
- Generate logout URLs

**Dependencies:**
- ISessionService (for session data)
- IAuthConfig (for Keycloak endpoints)

**Example:**
```typescript
import { TokenService } from '@/lib/auth/services';
import { authConfig } from '@/lib/auth/services/AuthConfig';

const tokenService = new TokenService(sessionService, authConfig);
const token = await tokenService.getKeycloakAccessToken();
```

### AuthConfig

Centralized configuration for authentication services.

**Responsibilities:**
- Provide environment-based configuration
- GraphQL endpoint URL
- Keycloak server settings
- Client IDs and redirect URIs

**Example:**
```typescript
import { authConfig } from '@/lib/auth/services/AuthConfig';

console.log(authConfig.graphqlEndpoint);
console.log(authConfig.keycloakUrl);
```

## Dependency Injection Pattern

Services use constructor injection for dependencies:

```typescript
// Service definition
export class OrganizationService implements IOrganizationService {
  constructor(
    private readonly sessionService: ISessionService,
    private readonly config: IAuthConfig
  ) {}
}

// Manual instantiation
const sessionService = new SessionService(auth);
const orgService = new OrganizationService(sessionService, authConfig);
const tokenService = new TokenService(sessionService, authConfig);
```

## React Cache Pattern

All query methods use React's `cache()` for request deduplication:

```typescript
export class SessionService {
  verifySession = cache(async (): Promise<Session> => {
    // Implementation
  });

  getSession = cache(async (): Promise<Session | null> => {
    // Implementation
  });
}
```

This ensures that multiple calls to the same method within a single render pass return the same result without additional network requests.

## Testing

Services implement interfaces, making them easy to mock for testing:

```typescript
// Mock implementation
class MockSessionService implements ISessionService {
  async verifySession(): Promise<Session> {
    return mockSession;
  }
  
  async getSession(): Promise<Session | null> {
    return mockSession;
  }
  
  async signOut(): Promise<void> {}
  async refreshSession(): Promise<Session | null> { return null; }
  async isAuthenticated(): Promise<boolean> { return true; }
}

// Test with mock
const mockSession = new MockSessionService();
const orgService = new OrganizationService(mockSession, mockConfig);
```

## Environment Variables

Services read configuration from environment variables via AuthConfig:

```bash
# GraphQL endpoint
GRAPHQL_ENDPOINT=http://localhost:8080/graphql

# Keycloak configuration
KEYCLOAK_URL=http://localhost:8084
KEYCLOAK_REALM=event-ticketing
KEYCLOAK_CLIENT_ID=event-ticketing-admin

# Application URL for redirects
NEXT_PUBLIC_APP_URL=http://localhost:3030
```

## Type Safety

All services are strongly typed with no `any` types:

- Use TypeScript interfaces for contracts
- Use Better Auth inferred types for sessions
- Use discriminated unions for organization status
- Use branded types for IDs where appropriate

## Server-Only

All service files include the `'server-only'` directive to prevent accidental client-side usage:

```typescript
import 'server-only';
```

This ensures services with sensitive operations (session verification, token exchange) never run in the browser.
