# Security Refactoring Plan: Unified Keycloak Integration

## Executive Summary

This document outlines a comprehensive plan to consolidate authentication across your ticketing system applications, removing redundant technologies, and implementing a professional API client architecture similar to the Zanaco CRM webapp.

---

## Part 1: Current State Analysis

### 1.1 Technologies Currently In Use

| Application | Auth Technology | Token Storage | Issues |
|-------------|-----------------|---------------|--------|
| **pml.tickets/admin** | Better Auth + Custom Backend Auth | MongoDB sessions + HTTP-only cookies | Redundant - duplicates Keycloak functionality |
| **pml.tickets/ticketing** | Keycloak OAuth2 PKCE (manual) | localStorage | Insecure token storage, manual implementation |
| **event-ticketing-mobile** | Keycloak OAuth2 PKCE (expo-auth-session) | expo-secure-store | Good pattern, but duplicated logic |

### 1.2 Redundant Dependencies to Remove

```
FROM pml.tickets/package.json:
- "better-auth": "^1.3.34"        # REMOVE - Keycloak handles auth
- "jwt-decode": "^4.0.0"          # KEEP - useful for token inspection
- "mongodb": "^6.0.0"             # REVIEW - only if used for Better Auth sessions

FROM pml.tickets/apps/admin:
- All Better Auth related files    # REMOVE
- MongoDB session storage          # REMOVE (use Keycloak sessions)
```

### 1.3 Files to Remove (Admin App)

```
DELETE:
├── apps/admin/src/lib/better-auth.ts
├── apps/admin/src/lib/better-auth-client.ts
├── apps/admin/src/lib/token-validator.ts
├── apps/admin/src/lib/token-utils.ts
├── apps/admin/src/lib/api-client-integration.ts
├── apps/admin/src/app/api/auth/[...all]/route.ts
├── apps/admin/src/app/api/auth/backend-token/route.ts
└── (MongoDB session-related env vars)
```

---

## Part 2: Target Architecture

### 2.1 Unified Keycloak Strategy

All applications will use **keycloak-js** adapter with the following configuration:

```
┌─────────────────────────────────────────────────────────────────┐
│                        Keycloak Server                          │
│                    (Single Source of Truth)                     │
├─────────────────────────────────────────────────────────────────┤
│  Realm: event-ticketing                                         │
│  Clients:                                                       │
│    - ticketing-web (public, PKCE)                              │
│    - admin-web (confidential, for admin portal)                │
│    - event-ticketing-mobile (public, PKCE)                     │
└─────────────────────────────────────────────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        │                     │                     │
        ▼                     ▼                     ▼
┌───────────────┐   ┌───────────────┐   ┌───────────────────┐
│ Ticketing Web │   │  Admin Portal │   │   Mobile App      │
│ (Next.js)     │   │  (Next.js)    │   │   (Expo/RN)       │
│               │   │               │   │                   │
│ keycloak-js   │   │ keycloak-js   │   │ expo-auth-session │
│ + React Query │   │ + React Query │   │ + React Query     │
└───────────────┘   └───────────────┘   └───────────────────┘
```

### 2.2 Security Best Practices Implementation

| Feature | Implementation |
|---------|----------------|
| **Token Storage (Web)** | Memory-only (keycloak-js handles this) |
| **Token Storage (Mobile)** | expo-secure-store (Keychain/Keystore) |
| **Token Refresh** | Silent refresh via keycloak-js `updateToken()` |
| **PKCE** | Enabled for all public clients |
| **Session Management** | Keycloak SSO with silent check-sso |
| **Logout** | Keycloak end_session_endpoint |

---

## Part 3: New Directory Structure (CRM-Style)

### 3.1 Shared Library Architecture

```
libs/shared/src/
├── api/
│   ├── http-client.ts              # Axios instance with interceptors
│   ├── token-service.ts            # Token management (simplified)
│   ├── service-base-urls.ts        # Centralized URL config
│   └── index.ts
│
├── auth/
│   ├── keycloak-config.ts          # Keycloak configuration
│   ├── keycloak-provider.tsx       # React context provider
│   ├── use-auth.ts                 # Auth hook
│   ├── protected-route.tsx         # Route guard component
│   └── index.ts
│
├── domains/
│   ├── events/
│   │   ├── events.api-service.ts   # API methods
│   │   ├── events.hooks.ts         # React Query hooks
│   │   ├── events.keys.ts          # Query key factory
│   │   ├── events.types.ts         # TypeScript types
│   │   ├── events.schemas.ts       # Yup/Zod validation
│   │   └── index.ts
│   │
│   ├── tickets/
│   │   ├── tickets.api-service.ts
│   │   ├── tickets.hooks.ts
│   │   ├── tickets.keys.ts
│   │   ├── tickets.types.ts
│   │   └── index.ts
│   │
│   ├── users/
│   │   ├── users.api-service.ts
│   │   ├── users.hooks.ts
│   │   ├── users.keys.ts
│   │   ├── users.types.ts
│   │   └── index.ts
│   │
│   ├── bookings/
│   │   └── ... (same pattern)
│   │
│   ├── payments/
│   │   └── ... (same pattern)
│   │
│   └── admin/
│       ├── monitoring/
│       ├── reconciliation/
│       └── ... (same pattern)
│
├── graphql/
│   ├── client.ts                   # Apollo client with auth
│   ├── queries/                    # Organized by domain
│   └── mutations/
│
├── types/
│   ├── common.types.ts             # Shared types (pagination, etc.)
│   ├── api-error.types.ts          # Error types
│   └── index.ts
│
└── index.ts                        # Barrel exports
```

---

## Part 4: Implementation Details

### 4.1 Keycloak Provider (Web Apps)

```typescript
// libs/shared/src/auth/keycloak-provider.tsx
'use client';

import Keycloak from 'keycloak-js';
import { createContext, useContext, useEffect, useState, useCallback, ReactNode } from 'react';

interface KeycloakContextValue {
  keycloak: Keycloak | null;
  initialized: boolean;
  authenticated: boolean;
  token: string | undefined;
  user: KeycloakUser | null;
  login: () => Promise<void>;
  logout: () => Promise<void>;
  updateToken: (minValidity?: number) => Promise<boolean>;
}

const KeycloakContext = createContext<KeycloakContextValue | null>(null);

interface KeycloakProviderProps {
  children: ReactNode;
  config: {
    url: string;
    realm: string;
    clientId: string;
  };
  initOptions?: Keycloak.KeycloakInitOptions;
}

export function KeycloakProvider({ children, config, initOptions }: KeycloakProviderProps) {
  const [keycloak] = useState(() => new Keycloak(config));
  const [initialized, setInitialized] = useState(false);
  const [authenticated, setAuthenticated] = useState(false);
  const [user, setUser] = useState<KeycloakUser | null>(null);

  useEffect(() => {
    const init = async () => {
      try {
        const auth = await keycloak.init({
          onLoad: 'check-sso',           // Silent SSO check
          silentCheckSsoRedirectUri: `${window.location.origin}/silent-check-sso.html`,
          pkceMethod: 'S256',            // PKCE enabled
          checkLoginIframe: false,       // Disable iframe checks (CSP issues)
          ...initOptions,
        });

        setAuthenticated(auth);

        if (auth && keycloak.tokenParsed) {
          setUser({
            id: keycloak.tokenParsed.sub!,
            email: keycloak.tokenParsed.email,
            name: keycloak.tokenParsed.name,
            roles: keycloak.tokenParsed.realm_access?.roles || [],
          });
        }

        // Token refresh handler
        keycloak.onTokenExpired = () => {
          keycloak.updateToken(60).catch(() => {
            keycloak.logout();
          });
        };

        setInitialized(true);
      } catch (error) {
        console.error('Keycloak init failed:', error);
        setInitialized(true);
      }
    };

    init();
  }, [keycloak, initOptions]);

  const login = useCallback(async () => {
    await keycloak.login({
      redirectUri: window.location.origin,
    });
  }, [keycloak]);

  const logout = useCallback(async () => {
    await keycloak.logout({
      redirectUri: window.location.origin,
    });
  }, [keycloak]);

  const updateToken = useCallback(async (minValidity = 30) => {
    return keycloak.updateToken(minValidity);
  }, [keycloak]);

  return (
    <KeycloakContext.Provider
      value={{
        keycloak,
        initialized,
        authenticated,
        token: keycloak.token,
        user,
        login,
        logout,
        updateToken,
      }}
    >
      {children}
    </KeycloakContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(KeycloakContext);
  if (!context) {
    throw new Error('useAuth must be used within a KeycloakProvider');
  }
  return context;
}
```

### 4.2 HTTP Client with Token Injection

```typescript
// libs/shared/src/api/http-client.ts
import axios, { AxiosInstance, AxiosError } from 'axios';
import { ApiError, ProblemDetails } from '../types/api-error.types';

let tokenGetter: (() => Promise<string | undefined>) | null = null;

export function setTokenGetter(getter: () => Promise<string | undefined>) {
  tokenGetter = getter;
}

export const apiClient: AxiosInstance = axios.create({
  headers: {
    Accept: 'application/json',
    'Content-Type': 'application/json',
  },
});

// Request interceptor: Attach token
apiClient.interceptors.request.use(async (config) => {
  if (tokenGetter) {
    const token = await tokenGetter();
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
  }
  return config;
});

// Response interceptor: Normalize errors
apiClient.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    const status = error.response?.status;
    const problem = error.response?.data as ProblemDetails | undefined;

    // Handle 401 - trigger auth refresh or logout
    if (status === 401) {
      // Dispatch custom event for auth handling
      window.dispatchEvent(new CustomEvent('auth:unauthorized'));
    }

    return Promise.reject(
      new ApiError(
        problem?.detail || error.message || 'An error occurred',
        status,
        problem
      )
    );
  }
);
```

### 4.3 Domain API Service Pattern

```typescript
// libs/shared/src/domains/events/events.api-service.ts
import { apiClient } from '../../api/http-client';
import { eventsServiceBaseUrl } from '../../api/service-base-urls';
import type {
  Event,
  EventListFilters,
  EventListResponse,
  CreateEventRequest,
  UpdateEventRequest,
} from './events.types';

function buildQueryParams(filters: Record<string, unknown>): URLSearchParams {
  const params = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => {
    if (value === undefined || value === null) return;
    if (Array.isArray(value)) {
      value.forEach((item) => params.append(key, String(item)));
    } else {
      params.set(key, String(value));
    }
  });
  return params;
}

export const EventsApiService = {
  async listEvents(filters: EventListFilters): Promise<EventListResponse> {
    const params = buildQueryParams(filters);
    const response = await apiClient.get<EventListResponse>(
      `${eventsServiceBaseUrl}/events`,
      { params }
    );
    return response.data;
  },

  async getEvent(id: string): Promise<Event> {
    const response = await apiClient.get<Event>(
      `${eventsServiceBaseUrl}/events/${encodeURIComponent(id)}`
    );
    return response.data;
  },

  async createEvent(data: CreateEventRequest): Promise<Event> {
    const response = await apiClient.post<Event>(
      `${eventsServiceBaseUrl}/events`,
      data
    );
    return response.data;
  },

  async updateEvent(id: string, data: UpdateEventRequest): Promise<Event> {
    const response = await apiClient.put<Event>(
      `${eventsServiceBaseUrl}/events/${encodeURIComponent(id)}`,
      data
    );
    return response.data;
  },

  async deleteEvent(id: string): Promise<void> {
    await apiClient.delete(
      `${eventsServiceBaseUrl}/events/${encodeURIComponent(id)}`
    );
  },

  async publishEvent(id: string): Promise<Event> {
    const response = await apiClient.post<Event>(
      `${eventsServiceBaseUrl}/events/${encodeURIComponent(id)}/publish`
    );
    return response.data;
  },
};
```

### 4.4 Query Keys Pattern

```typescript
// libs/shared/src/domains/events/events.keys.ts
import type { EventListFilters } from './events.types';

export const eventQueryKeys = {
  all: ['events'] as const,

  lists: () => [...eventQueryKeys.all, 'list'] as const,

  list: (filters: EventListFilters) =>
    [...eventQueryKeys.lists(), filters] as const,

  details: () => [...eventQueryKeys.all, 'detail'] as const,

  detail: (id: string) =>
    [...eventQueryKeys.details(), id] as const,

  // Nested resources
  tickets: (eventId: string) =>
    [...eventQueryKeys.detail(eventId), 'tickets'] as const,

  ticketList: (eventId: string, filters?: object) =>
    [...eventQueryKeys.tickets(eventId), filters] as const,
};
```

### 4.5 React Query Hooks Pattern

```typescript
// libs/shared/src/domains/events/events.hooks.ts
'use client';

import {
  useQuery,
  useMutation,
  useQueryClient,
  keepPreviousData,
} from '@tanstack/react-query';
import { EventsApiService } from './events.api-service';
import { eventQueryKeys } from './events.keys';
import type {
  Event,
  EventListFilters,
  CreateEventRequest,
  UpdateEventRequest,
} from './events.types';

// Stale times
const STALE_TIME_LIST = 60_000; // 1 minute
const STALE_TIME_DETAIL = 5 * 60_000; // 5 minutes

// ============== QUERIES ==============

export function useEvents(filters: EventListFilters) {
  return useQuery({
    queryKey: eventQueryKeys.list(filters),
    queryFn: () => EventsApiService.listEvents(filters),
    staleTime: STALE_TIME_LIST,
    placeholderData: keepPreviousData,
  });
}

export function useEvent(id: string | undefined) {
  return useQuery({
    queryKey: eventQueryKeys.detail(id!),
    queryFn: () => EventsApiService.getEvent(id!),
    enabled: !!id,
    staleTime: STALE_TIME_DETAIL,
  });
}

// ============== MUTATIONS ==============

export function useCreateEvent() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: CreateEventRequest) =>
      EventsApiService.createEvent(data),
    onSuccess: (newEvent) => {
      // Add to cache
      queryClient.setQueryData(eventQueryKeys.detail(newEvent.id), newEvent);
      // Invalidate lists
      queryClient.invalidateQueries({ queryKey: eventQueryKeys.lists() });
    },
  });
}

export function useUpdateEvent(id: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: UpdateEventRequest) =>
      EventsApiService.updateEvent(id, data),
    onSuccess: (updatedEvent) => {
      // Update cache
      queryClient.setQueryData(eventQueryKeys.detail(id), updatedEvent);
      // Invalidate lists
      queryClient.invalidateQueries({ queryKey: eventQueryKeys.lists() });
    },
  });
}

export function useDeleteEvent() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => EventsApiService.deleteEvent(id),
    onSuccess: (_, id) => {
      // Remove from cache
      queryClient.removeQueries({ queryKey: eventQueryKeys.detail(id) });
      // Invalidate lists
      queryClient.invalidateQueries({ queryKey: eventQueryKeys.lists() });
    },
  });
}

export function usePublishEvent() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => EventsApiService.publishEvent(id),
    onSuccess: (updatedEvent, id) => {
      queryClient.setQueryData(eventQueryKeys.detail(id), updatedEvent);
      queryClient.invalidateQueries({ queryKey: eventQueryKeys.lists() });
    },
  });
}
```

---

## Part 5: Production Login Flow Design

### 5.1 Web Application Flow (Next.js)

```
┌─────────────────────────────────────────────────────────────────┐
│                     Production Login Flow                        │
└─────────────────────────────────────────────────────────────────┘

1. App Load
   └── KeycloakProvider.init({ onLoad: 'check-sso' })
       └── Silent SSO check via hidden iframe
           ├── Already logged in → Set authenticated state
           └── Not logged in → Show login button

2. User Clicks Login
   └── keycloak.login({ redirectUri: origin })
       └── Browser redirects to Keycloak
           └── User authenticates (email/phone/social)
               └── Keycloak redirects back with code
                   └── keycloak-js exchanges code for tokens (PKCE)
                       └── Tokens stored in memory (NOT localStorage)
                           └── App re-renders with authenticated state

3. API Request
   └── React Query hook triggers
       └── apiClient interceptor calls tokenGetter()
           └── keycloak.updateToken(30) called
               ├── Token valid → Return token
               └── Token expiring → Silent refresh
                   ├── Success → Return new token
                   └── Failure → keycloak.logout()

4. User Clicks Logout
   └── keycloak.logout({ redirectUri: origin })
       └── Session cleared on Keycloak server
           └── Tokens cleared from memory
               └── Browser redirects to origin
```

### 5.2 Mobile Application Flow (Expo)

```
┌─────────────────────────────────────────────────────────────────┐
│                     Mobile Login Flow                            │
└─────────────────────────────────────────────────────────────────┘

1. App Start
   └── Check expo-secure-store for refresh_token
       ├── Found → Attempt token refresh
       │   ├── Success → Set authenticated, cache access token
       │   └── Failure → Clear tokens, show login
       └── Not found → Show login screen

2. User Taps Login
   └── expo-auth-session.makeAuthRequest({
         usePKCE: true,
         scopes: ['openid', 'profile', 'email', 'offline_access']
       })
       └── System browser opens Keycloak
           └── User authenticates
               └── Browser redirects to app://callback
                   └── Exchange code for tokens
                       └── Store tokens in expo-secure-store
                           └── Navigate to home

3. API Request
   └── Apollo/React Query hook triggers
       └── Auth link calls getAccessToken()
           └── Check token expiration (5 min buffer)
               ├── Valid → Return token
               └── Expiring → Refresh with refresh_token
                   ├── Success → Update secure store, return token
                   └── Failure → Clear tokens, navigate to login

4. User Taps Logout
   └── Revoke refresh_token with Keycloak
       └── Clear expo-secure-store
           └── Reset navigation to login
```

### 5.3 Silent Check SSO HTML File

Create this file in your Next.js public folder:

```html
<!-- public/silent-check-sso.html -->
<!DOCTYPE html>
<html>
<head>
  <title>Silent SSO Check</title>
</head>
<body>
  <script>
    parent.postMessage(location.href, location.origin);
  </script>
</body>
</html>
```

---

## Part 6: Package Updates

### 6.1 Dependencies to Update (pml.tickets)

```json
{
  "dependencies": {
    "@apollo/client": "^4.0.7",
    "@tanstack/react-query": "^5.60.0",
    "@tanstack/react-query-devtools": "^5.60.0",
    "axios": "^1.7.9",
    "keycloak-js": "^26.0.0",
    "next": "^15.1.0",
    "react": "^19.0.0",
    "react-dom": "^19.0.0",
    "date-fns": "^4.1.0",
    "formik": "^2.4.6",
    "yup": "^1.7.1"
  },
  "devDependencies": {
    "@types/node": "^22.10.0",
    "@types/react": "^19.0.0",
    "@types/react-dom": "^19.0.0",
    "typescript": "~5.7.2",
    "tailwindcss": "^3.4.17",
    "nx": "^20.3.0"
  }
}
```

### 6.2 Dependencies to Remove

```json
{
  "remove": {
    "better-auth": "^1.3.34",
    "mongodb": "^6.0.0"
  }
}
```

### 6.3 Mobile App Updates (event-ticketing-mobile)

```json
{
  "dependencies": {
    "@apollo/client": "^4.1.4",
    "expo": "~52.0.0",
    "expo-auth-session": "~6.0.0",
    "expo-secure-store": "~14.0.0",
    "expo-web-browser": "~14.0.0",
    "@tanstack/react-query": "^5.60.0"
  }
}
```

---

## Part 7: Migration Steps

### Phase 1: Setup New Auth Infrastructure (Week 1)

- [ ] Create new Keycloak client configurations
- [ ] Install `keycloak-js` package
- [ ] Create `KeycloakProvider` component
- [ ] Create `useAuth` hook
- [ ] Create silent-check-sso.html
- [ ] Update HTTP client with token getter pattern

### Phase 2: Refactor API Layer (Week 2)

- [ ] Create new directory structure in `libs/shared/src/domains/`
- [ ] Migrate existing API calls to new service pattern
- [ ] Create query key factories for each domain
- [ ] Create React Query hooks for each domain
- [ ] Update Apollo client to use Keycloak tokens

### Phase 3: Migrate Admin App (Week 3)

- [ ] Remove Better Auth dependencies and files
- [ ] Update admin app to use KeycloakProvider
- [ ] Create admin-specific Keycloak client
- [ ] Update all API calls to use new hooks
- [ ] Remove MongoDB session storage
- [ ] Test all admin flows

### Phase 4: Migrate Ticketing App (Week 4)

- [ ] Replace manual OAuth implementation with keycloak-js
- [ ] Remove localStorage token storage
- [ ] Update all API calls to use new hooks
- [ ] Test all user flows

### Phase 5: Mobile App Updates (Week 5)

- [ ] Update expo packages to latest
- [ ] Refactor auth service to match new patterns
- [ ] Share types/schemas with web apps
- [ ] Test all mobile flows

### Phase 6: Testing & Cleanup (Week 6)

- [ ] End-to-end testing all apps
- [ ] Security audit
- [ ] Remove deprecated code
- [ ] Update documentation

---

## Part 8: Keycloak Client Configurations

### 8.1 Ticketing Web Client

```json
{
  "clientId": "ticketing-web",
  "enabled": true,
  "publicClient": true,
  "standardFlowEnabled": true,
  "directAccessGrantsEnabled": false,
  "implicitFlowEnabled": false,
  "fullScopeAllowed": true,
  "attributes": {
    "pkce.code.challenge.method": "S256"
  },
  "redirectUris": [
    "http://localhost:3001/*",
    "https://tickets.yourdomain.com/*"
  ],
  "webOrigins": [
    "http://localhost:3001",
    "https://tickets.yourdomain.com"
  ]
}
```

### 8.2 Admin Web Client

```json
{
  "clientId": "admin-web",
  "enabled": true,
  "publicClient": true,
  "standardFlowEnabled": true,
  "directAccessGrantsEnabled": false,
  "attributes": {
    "pkce.code.challenge.method": "S256"
  },
  "redirectUris": [
    "http://localhost:3000/*",
    "https://admin.yourdomain.com/*"
  ],
  "webOrigins": [
    "http://localhost:3000",
    "https://admin.yourdomain.com"
  ],
  "defaultClientScopes": [
    "openid",
    "profile",
    "email",
    "roles"
  ]
}
```

### 8.3 Mobile Client

```json
{
  "clientId": "event-ticketing-mobile",
  "enabled": true,
  "publicClient": true,
  "standardFlowEnabled": true,
  "directAccessGrantsEnabled": false,
  "attributes": {
    "pkce.code.challenge.method": "S256"
  },
  "redirectUris": [
    "com.pml.ticketing://callback",
    "exp://localhost:8081/--/callback"
  ],
  "webOrigins": ["+"]
}
```

---

## Part 9: Security Checklist

### Authentication
- [x] PKCE enabled for all public clients
- [x] Tokens stored in memory (web) / secure store (mobile)
- [x] Silent token refresh before expiration
- [x] Proper logout with session termination
- [ ] Implement refresh token rotation

### API Security
- [x] Bearer token in Authorization header
- [x] 401 handling with automatic logout
- [ ] Add request signing for sensitive operations
- [ ] Implement rate limiting headers handling

### Transport Security
- [ ] HTTPS only in production
- [ ] Secure cookie flags (HTTPOnly, Secure, SameSite)
- [ ] CORS properly configured

### Session Management
- [ ] Session timeout configured in Keycloak
- [ ] Idle timeout handling in apps
- [ ] Concurrent session limits

---

## Appendix A: Environment Variables

### pml.tickets/apps/ticketing/.env

```bash
# Keycloak Configuration
NEXT_PUBLIC_KEYCLOAK_URL=http://localhost:8084
NEXT_PUBLIC_KEYCLOAK_REALM=event-ticketing
NEXT_PUBLIC_KEYCLOAK_CLIENT_ID=ticketing-web

# API Configuration
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
NEXT_PUBLIC_GRAPHQL_ENDPOINT=http://localhost:8080/graphql
```

### pml.tickets/apps/admin/.env

```bash
# Keycloak Configuration
NEXT_PUBLIC_KEYCLOAK_URL=http://localhost:8084
NEXT_PUBLIC_KEYCLOAK_REALM=event-ticketing
NEXT_PUBLIC_KEYCLOAK_CLIENT_ID=admin-web

# API Configuration
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
NEXT_PUBLIC_GRAPHQL_ENDPOINT=http://localhost:8080/graphql

# REMOVE these (Better Auth related):
# BETTER_AUTH_URL=...
# BETTER_AUTH_SECRET=...
# MONGODB_URI=...
# MONGODB_DATABASE=...
```

### event-ticketing-mobile/.env

```bash
# Keycloak Configuration
EXPO_PUBLIC_KEYCLOAK_URL=http://localhost:8084
EXPO_PUBLIC_KEYCLOAK_REALM=event-ticketing
EXPO_PUBLIC_KEYCLOAK_CLIENT_ID=event-ticketing-mobile

# API Configuration
EXPO_PUBLIC_API_BASE_URL=http://localhost:8080
EXPO_PUBLIC_GRAPHQL_ENDPOINT=http://localhost:8080/graphql
```

---

## Appendix B: Quick Reference

### Domain Structure Template

```
domains/{domain-name}/
├── {domain}.api-service.ts    # Raw API calls
├── {domain}.hooks.ts          # React Query hooks
├── {domain}.keys.ts           # Query key factory
├── {domain}.types.ts          # TypeScript types
├── {domain}.schemas.ts        # Validation schemas (optional)
└── index.ts                   # Barrel exports
```

### Naming Conventions

| Type | Pattern | Example |
|------|---------|---------|
| API Service | `{Domain}ApiService` | `EventsApiService` |
| Query Keys | `{domain}QueryKeys` | `eventQueryKeys` |
| List Hook | `use{Domain}s` | `useEvents` |
| Detail Hook | `use{Domain}` | `useEvent` |
| Create Hook | `useCreate{Domain}` | `useCreateEvent` |
| Update Hook | `useUpdate{Domain}` | `useUpdateEvent` |
| Delete Hook | `useDelete{Domain}` | `useDeleteEvent` |

---

*Document Version: 1.0*
*Created: March 2026*
*Last Updated: March 2026*
