# Dependency Injection Implementation

This document describes the dependency injection (DI) architecture implemented in the auth module.

## Architecture Overview

The auth module follows industry-standard dependency injection patterns inspired by:
- Next.js singleton patterns (e.g., Prisma client)
- TypeScript factory patterns
- Better Auth integration

```
┌─────────────────────────────────────────────────────────────┐
│                     PUBLIC API (index.ts)                    │
│  - auth (Better Auth instance)                               │
│  - db, redis, env (infrastructure)                           │
│  - getSessionService(), getOrganizationService(), etc.       │
├─────────────────────────────────────────────────────────────┤
│                   DAL (dal.ts) - Facade                      │
│  - verifySession() → delegates to SessionService             │
│  - getSession() → delegates to SessionService                │
│  - getOrganizationStatus() → delegates to OrgService         │
│  - getRouteForStatus() → delegates to OrgService             │
├─────────────────────────────────────────────────────────────┤
│              CONTAINER (container.ts) - DI Factory           │
│  - getSessionService() → lazy singleton                      │
│  - getOrganizationService() → lazy singleton                 │
│  - getTokenService() → lazy singleton                        │
│  - resetServices() → testing utility                         │
│  - setMockServices() → testing utility                       │
├─────────────────────────────────────────────────────────────┤
│                  SERVICES (services/)                        │
│  - SessionService: implements ISessionService                │
│  - OrganizationService: implements IOrganizationService      │
│  - TokenService: implements ITokenService                    │
├─────────────────────────────────────────────────────────────┤
│                INTERFACES (interfaces/)                      │
│  - ISessionService                                           │
│  - IOrganizationService                                      │
│  - ITokenService                                             │
│  - IAuthConfig                                               │
│  - OrganizationStatus, Session, User (types)                 │
└─────────────────────────────────────────────────────────────┘
```

## Key Components

### 1. Container (container.ts)

Implements lazy singleton pattern with hot reload support:

```typescript
// Development: global cache survives hot reload
if (process.env.NODE_ENV === 'development') {
  if (!global._sessionService) {
    global._sessionService = new SessionService(auth);
  }
  return global._sessionService;
}

// Production: fresh instance per request
return new SessionService(auth);
```

**Features**:
- Lazy initialization (created only when first requested)
- Global singleton cache in development (survives hot reload)
- Fresh instances in production (per-request isolation)
- Test-friendly (resetServices, setMockServices)

### 2. Services (services/)

Three core services implementing business logic:

**SessionService** (`ISessionService`)
- `verifySession()`: Validates session, redirects if invalid
- `getSession()`: Returns session or null (no redirect)
- `signOut()`: Invalidates session

**OrganizationService** (`IOrganizationService`)
- `getStatus()`: Fetches organization status from GraphQL
- `getRouteForStatus()`: Maps status to route path

**TokenService** (`ITokenService`)
- `getKeycloakAccessToken()`: Retrieves access token
- `getLogoutUrl()`: Builds Keycloak logout URL

### 3. DAL (dal.ts)

Thin facade providing backward compatibility:

```typescript
// Before (direct implementation)
export const verifySession = cache(async () => {
  // ... implementation ...
});

// After (delegates to service)
export const verifySession = () => getSessionService().verifySession();
```

**Benefits**:
- Maintains existing API contract
- Zero breaking changes for consumers
- Implementation can be swapped without touching callsites

### 4. Interfaces (interfaces/)

Contracts for services enabling:
- Type safety
- Loose coupling
- Mock injection for testing
- Future extensibility

## Usage Patterns

### Standard Usage (Server Components)

```typescript
import { verifySession, getOrganizationStatus } from '@/lib/auth/dal';

export default async function DashboardLayout({ children }) {
  const session = await verifySession(); // Redirects if invalid
  const orgStatus = await getOrganizationStatus();

  if (!orgStatus.isApproved) {
    redirect('/apply/status');
  }

  return <>{children}</>;
}
```

### Direct Service Access

```typescript
import { getSessionService, getOrganizationService } from '@/lib/auth';

const sessionService = getSessionService();
const orgService = getOrganizationService();

const session = await sessionService.verifySession();
const status = await orgService.getStatus();
```

### Testing with Mocks

```typescript
import { setMockServices, resetServices } from '@/lib/auth';

describe('MyComponent', () => {
  beforeEach(() => {
    resetServices(); // Clear singletons
  });

  it('renders with authenticated user', async () => {
    const mockSession = {
      verifySession: jest.fn().mockResolvedValue({
        user: { id: '123', email: 'test@example.com' },
      }),
      getSession: jest.fn(),
      signOut: jest.fn(),
    };

    setMockServices({ session: mockSession });

    // Now all calls to getSessionService() return the mock
    const { container } = render(<MyComponent />);
    // ...assertions
  });
});
```

## Design Patterns

### 1. Lazy Singleton

Services are created only when first requested:

```typescript
let config: IAuthConfig | null = null;

function getConfig(): IAuthConfig {
  if (!config) {
    config = buildAuthConfig(); // Lazy initialization
  }
  return config;
}
```

### 2. Factory Pattern

Container acts as a service factory:

```typescript
export function getSessionService(): ISessionService {
  // Factory creates or returns cached instance
}
```

### 3. Dependency Injection

Services declare dependencies via constructor:

```typescript
export class OrganizationService implements IOrganizationService {
  constructor(
    private readonly sessionService: ISessionService,  // Injected
    private readonly config: IAuthConfig              // Injected
  ) {}
}
```

### 4. Interface Segregation

Small, focused interfaces (ISP principle):

```typescript
interface ISessionService {
  verifySession(): Promise<Session>;
  getSession(): Promise<Session | null>;
  signOut(headers: Headers): Promise<void>;
}
```

## Configuration

Auth configuration is built lazily from environment:

```typescript
function buildAuthConfig(): IAuthConfig {
  return {
    graphqlEndpoint: process.env.GRAPHQL_ENDPOINT || 'http://localhost:8080/graphql',
    keycloakUrl: env.KEYCLOAK_ISSUER.replace('/realms/event-ticketing', ''),
    keycloakRealm: 'event-ticketing',
    keycloakClientId: env.KEYCLOAK_CLIENT_ID,
    postLogoutRedirectUri: `${env.APP_URL}/login`,
  };
}
```

## Testing Utilities

### resetServices()

Clears all singleton instances. Use in test setup:

```typescript
beforeEach(() => {
  resetServices();
});
```

### setMockServices()

Injects mock implementations for testing:

```typescript
setMockServices({
  session: mockSessionService,
  organization: mockOrgService,
  token: mockTokenService,
});
```

## Benefits

1. **Testability**: Easy to inject mocks for unit testing
2. **Loose Coupling**: Services depend on interfaces, not concrete implementations
3. **Hot Reload Support**: Global cache survives Next.js hot module reload
4. **Type Safety**: Strong TypeScript contracts via interfaces
5. **Single Responsibility**: Each service has one clear purpose
6. **Backward Compatibility**: DAL facade maintains existing API
7. **Performance**: Lazy initialization, singleton caching, React cache() deduplication

## Migration Path

Existing code continues to work unchanged:

```typescript
// No changes needed - still works!
import { verifySession, getSession } from '@/lib/auth/dal';

export default async function Page() {
  const session = await verifySession();
  return <div>Hello {session.user.email}</div>;
}
```

New code can leverage services directly:

```typescript
import { getSessionService } from '@/lib/auth';

const sessionService = getSessionService();
const session = await sessionService.verifySession();
```

## References

- [Next.js Prisma Singleton Pattern](https://github.com/vercel/next.js/blob/canary/examples/with-mysql/lib/prisma.ts)
- [TypeScript Factory Pattern](https://github.com/microsoft/typescript/blob/main/tests/baselines/reference/parserRealSource12.errors.txt)
- [Better Auth Documentation](https://better-auth.com/docs/integrations/next)
- [Next.js DAL Pattern](https://nextjs.org/docs/app/guides/authentication#creating-a-data-access-layer-dal)
