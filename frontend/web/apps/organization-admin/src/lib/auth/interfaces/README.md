# Auth Module Interfaces

This directory contains TypeScript interfaces defining contracts for the authentication system's service-oriented architecture.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    AUTH MODULE INTERFACES                    │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  types.ts                                                   │
│  ├─ OrganizationStatus                                      │
│  ├─ OrganizationStatusEnum                                  │
│  ├─ Session (re-exported from Better Auth)                  │
│  ├─ User (re-exported from Better Auth)                     │
│  ├─ KeycloakTokenResponse                                   │
│  └─ Helper types (SessionOptions, RouteMapping, etc.)       │
│                                                             │
│  ISessionService.ts                                         │
│  ├─ verifySession(): Promise<Session>                       │
│  ├─ getSession(): Promise<Session | null>                   │
│  ├─ signOut(headers: Headers): Promise<void>                │
│  ├─ refreshSession(): Promise<Session | null>               │
│  └─ isAuthenticated(): Promise<boolean>                     │
│                                                             │
│  IOrganizationService.ts                                    │
│  ├─ getStatus(): Promise<OrganizationStatus>                │
│  ├─ getRouteForStatus(status): string                       │
│  ├─ isApproved(): Promise<boolean>                          │
│  └─ validateRouteAccess(path): Promise<ValidationResult>    │
│                                                             │
│  ITokenService.ts                                           │
│  ├─ getKeycloakAccessToken(): Promise<string | null>        │
│  ├─ getLogoutUrl(idTokenHint?): string                      │
│  ├─ exchangeToken(sessionToken): Promise<TokenResponse>     │
│  ├─ validateToken(token): Promise<boolean>                  │
│  ├─ decodeToken(token): Record<string, unknown>             │
│  └─ revokeToken(token): Promise<void>                       │
│                                                             │
│  IAuthConfig.ts                                             │
│  ├─ Configuration interface with readonly properties        │
│  ├─ AuthConfigFactory type                                  │
│  └─ validateAuthConfig() utility                            │
│                                                             │
│  index.ts                                                   │
│  └─ Barrel exports for all interfaces and types             │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

## Files

### `types.ts`
Core type definitions including:
- **OrganizationStatus**: Organization application state
- **OrganizationStatusEnum**: Status enumeration (NOT_STARTED, IN_PROGRESS, PENDING_REVIEW, APPROVED, REJECTED)
- **Session/User**: Re-exported from Better Auth
- **KeycloakTokenResponse**: Keycloak OAuth2 token structure
- Helper types for sessions, routes, and GraphQL responses

### `ISessionService.ts`
Session management interface:
- `verifySession()`: Validates session, throws if invalid (use for protected routes)
- `getSession()`: Returns session or null (use for conditional rendering)
- `signOut()`: Terminates session
- `refreshSession()`: Extends session lifetime
- `isAuthenticated()`: Lightweight authentication check

### `IOrganizationService.ts`
Organization status and routing interface:
- `getStatus()`: Fetches organization application status from GraphQL
- `getRouteForStatus()`: Maps status to appropriate route path
- `isApproved()`: Quick check for approval status
- `validateRouteAccess()`: Validates if user can access requested route

### `ITokenService.ts`
Token management and Keycloak integration interface:
- `getKeycloakAccessToken()`: Exchanges Better Auth session for Keycloak token
- `getLogoutUrl()`: Constructs Keycloak logout endpoint URL
- `exchangeToken()`: OAuth2 token exchange mechanism
- `validateToken()`: Checks token validity
- `decodeToken()`: JWT inspection (no verification)
- `revokeToken()`: Token revocation

### `IAuthConfig.ts`
Configuration interface with:
- Required fields: `graphqlEndpoint`, `keycloakIssuer`, `keycloakClientId`, `appUrl`
- Optional fields: `keycloakClientSecret`, `tokenEndpoint`, `logoutEndpoint`, etc.
- `validateAuthConfig()`: Runtime validation utility
- Factory types for dynamic configuration

### `index.ts`
Barrel export file providing centralized imports

## Design Principles

### 1. Interface Segregation
Each interface focuses on a single responsibility:
- **ISessionService**: Session lifecycle only
- **IOrganizationService**: Organization state and routing only
- **ITokenService**: Token operations only
- **IAuthConfig**: Configuration only

### 2. Readonly Properties
All interfaces use `readonly` modifiers to prevent accidental mutation:
```typescript
export interface IAuthConfig {
  readonly graphqlEndpoint: string;
  readonly keycloakIssuer: string;
  // ...
}
```

### 3. Generic Type Parameters
Interfaces use generics where appropriate for flexibility:
```typescript
validateRouteAccess<T extends string>(path: T): Promise<RouteValidationResult>;
```

### 4. Comprehensive JSDoc
All interfaces and methods include:
- Purpose and behavior description
- `@param` tags for parameters
- `@returns` tags for return values
- `@throws` tags for error conditions
- `@example` blocks for usage patterns

### 5. Server-Only Directive
All files include `import 'server-only'` to prevent accidental client-side usage

## Usage Examples

### Implementing a Service
```typescript
import type { ISessionService, Session, SessionOptions } from './interfaces';

export class SessionService implements ISessionService {
  async verifySession(options?: SessionOptions): Promise<Session> {
    // Implementation
  }

  async getSession(): Promise<Session | null> {
    // Implementation
  }

  async signOut(headers: Headers): Promise<void> {
    // Implementation
  }

  async refreshSession(): Promise<Session | null> {
    // Implementation
  }

  async isAuthenticated(): Promise<boolean> {
    // Implementation
  }
}
```

### Dependency Injection
```typescript
import type { ISessionService, IOrganizationService, ITokenService } from './interfaces';

export class AuthGuard {
  constructor(
    private sessionService: ISessionService,
    private organizationService: IOrganizationService,
    private tokenService: ITokenService
  ) {}

  async validateAccess(path: string): Promise<boolean> {
    const session = await this.sessionService.getSession();
    if (!session) return false;

    const validation = await this.organizationService.validateRouteAccess(path);
    return validation.allowed;
  }
}
```

### Testing with Mocks
```typescript
import type { ISessionService, Session } from './interfaces';

class MockSessionService implements ISessionService {
  async verifySession(): Promise<Session> {
    return {
      user: { id: 'test-user', email: 'test@example.com' },
      token: 'mock-token',
      expiresAt: new Date(Date.now() + 3600000),
    };
  }

  async getSession(): Promise<Session | null> {
    return null;
  }

  async signOut(): Promise<void> {
    // No-op
  }

  async refreshSession(): Promise<Session | null> {
    return null;
  }

  async isAuthenticated(): Promise<boolean> {
    return false;
  }
}
```

## Next Steps

1. **Implement Services**: Create concrete implementations in `../services/`
2. **Create Factories**: Build service factories for dependency injection
3. **Add Tests**: Write unit tests for each service implementation
4. **Document Flows**: Create sequence diagrams for authentication flows

## Related Documentation

- Better Auth: https://www.better-auth.com/docs
- Keycloak OAuth2: https://www.keycloak.org/docs/latest/securing_apps/
- TypeScript Interfaces: https://www.typescriptlang.org/docs/handbook/interfaces.html
