# Better Auth + Keycloak + Redis Integration Analysis

## Executive Summary

This document analyzes the integration options for authentication in the PML Ticketing System, comparing the current Keycloak-js approach with Better Auth as a modern alternative. The goal is to determine the industry-standard approach that eliminates redundancy while providing seamless token management for both Server and Client Components.

---

## Current Architecture Problems

### 1. Race Condition in Token Injection
- Apollo Client created before Keycloak fully initializes
- Token not available for initial GraphQL requests
- Causes "Access denied" errors on protected endpoints

### 2. Server Components Cannot Access Tokens
- `keycloak-js` is purely client-side
- No way to fetch data with authentication in Server Components
- Limits RSC adoption and SSR capabilities

### 3. No Centralized Session Storage
- Tokens exist only in browser memory
- Lost on page refresh before Keycloak re-initializes
- No cross-tab session sharing

---

## Architecture Options Comparison

### Option A: Keep Keycloak-js + Add Cookie Bridge (Current Approach)

```
┌─────────────────────────────────────────────────────────────────┐
│                    CURRENT + COOKIE BRIDGE                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│   Browser                                                        │
│   ┌──────────────────┐    ┌──────────────────┐                 │
│   │   keycloak-js    │───▶│  Cookie Bridge   │                 │
│   │   (Client Auth)  │    │  /api/auth/session│                 │
│   └────────┬─────────┘    └────────┬─────────┘                 │
│            │                       │                            │
│            ▼                       ▼                            │
│   ┌──────────────────┐    ┌──────────────────┐                 │
│   │  Client Component│    │ Server Component │                 │
│   │  (useKeycloak)   │    │ (cookies().get)  │                 │
│   └──────────────────┘    └──────────────────┘                 │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘

Pros:
✅ Minimal changes to existing code
✅ Works with current Keycloak setup
✅ No new dependencies

Cons:
❌ Cookie sync adds complexity
❌ Token in cookie still needs manual refresh
❌ Two sources of truth (keycloak-js + cookie)
❌ No proper session management
❌ Manual token refresh logic
```

### Option B: Better Auth with Keycloak OIDC Provider (Recommended)

```
┌─────────────────────────────────────────────────────────────────┐
│                    BETTER AUTH + KEYCLOAK                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│   ┌──────────────────────────────────────────────────────────┐  │
│   │                    BETTER AUTH SERVER                     │  │
│   │                                                           │  │
│   │  ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐  │  │
│   │  │  Keycloak   │  │   Session   │  │     Redis       │  │  │
│   │  │  OIDC       │  │   Manager   │  │  Secondary      │  │  │
│   │  │  Provider   │  │             │  │  Storage        │  │  │
│   │  └─────────────┘  └─────────────┘  └─────────────────┘  │  │
│   └────────────────────────┬─────────────────────────────────┘  │
│                            │                                     │
│            ┌───────────────┼───────────────┐                    │
│            ▼               ▼               ▼                    │
│   ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
│   │   Middleware │  │   Server     │  │   Client     │         │
│   │   (Route     │  │   Component  │  │   Component  │         │
│   │   Protection)│  │   (getSession)│  │  (useSession)│         │
│   └──────────────┘  └──────────────┘  └──────────────┘         │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘

Pros:
✅ Native Next.js App Router support
✅ Server Components get session directly
✅ Client Components use hooks
✅ Redis for session storage/caching
✅ Automatic token refresh
✅ Single source of truth
✅ Built-in middleware support
✅ Keycloak still handles identity (SSO, user management)

Cons:
❌ Migration effort required
❌ New dependency to maintain
```

---

## Industry Standard Analysis

### What Enterprise Applications Use

| Company Type | Identity Provider | Session Management | Pattern |
|--------------|-------------------|-------------------|---------|
| Enterprise B2B | Keycloak/Okta/Azure AD | Application-level sessions | OIDC + App Sessions |
| SaaS Platforms | Auth0/Keycloak | Redis sessions | Centralized sessions |
| High-traffic Apps | Any OIDC provider | Redis + Cookie cache | Stateless + Cache |

### The Industry-Standard Pattern

The recommended pattern separates concerns:

1. **Identity Provider (Keycloak)**: Handles authentication, user management, SSO, MFA
2. **Application Auth Layer (Better Auth)**: Handles session management, token storage, API access
3. **Session Storage (Redis)**: Centralizes session data for scalability

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      INDUSTRY STANDARD ARCHITECTURE                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────┐        ┌──────────────────────┐        ┌────────────────┐ │
│  │   Browser   │◀──────▶│   Next.js App        │◀──────▶│    Backend     │ │
│  │             │        │                      │        │    Services    │ │
│  └─────────────┘        └──────────┬───────────┘        └────────────────┘ │
│                                    │                                        │
│                         ┌──────────┴───────────┐                           │
│                         ▼                      ▼                           │
│                  ┌─────────────┐       ┌─────────────┐                     │
│                  │ Better Auth │       │   Redis     │                     │
│                  │   Server    │◀─────▶│   Session   │                     │
│                  └──────┬──────┘       │   Storage   │                     │
│                         │              └─────────────┘                     │
│                         │                                                   │
│                         ▼                                                   │
│                  ┌─────────────┐                                           │
│                  │  Keycloak   │  ◀── Identity Provider                    │
│                  │  (OIDC)     │      (Authentication, Users, SSO)         │
│                  └─────────────┘                                           │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Why This Pattern Works

1. **Separation of Concerns**:
   - Keycloak: "Who is this user?" (Identity)
   - Better Auth: "Can this user access this?" (Session/Authorization)
   - Redis: "Where do we store sessions?" (Scalability)

2. **No Redundancy**:
   - Keycloak handles identity ONCE
   - Better Auth consumes OIDC tokens and manages sessions
   - No duplicate user databases

3. **Scalability**:
   - Redis enables horizontal scaling
   - Session invalidation across instances
   - Fast session lookups

---

## Recommended Implementation

### Step 1: Install Dependencies

```bash
npm install better-auth @better-auth/redis-storage ioredis
```

### Step 2: Configure Better Auth with Keycloak

```typescript
// apps/admin/src/lib/auth.ts
import { betterAuth } from "better-auth";
import { genericOAuth, keycloak } from "better-auth/plugins";
import { Redis } from "ioredis";
import { redisStorage } from "@better-auth/redis-storage";

const redis = new Redis({
  host: process.env.REDIS_HOST || "localhost",
  port: parseInt(process.env.REDIS_PORT || "6379"),
});

export const auth = betterAuth({
  // Keycloak as OIDC provider (handles all user authentication)
  plugins: [
    genericOAuth({
      config: [
        keycloak({
          clientId: process.env.KEYCLOAK_CLIENT_ID!,
          clientSecret: process.env.KEYCLOAK_CLIENT_SECRET!,
          issuer: process.env.KEYCLOAK_ISSUER!, // e.g., http://localhost:8084/realms/event-ticketing
        }),
      ],
    }),
  ],

  // Redis for session storage (fast, scalable)
  secondaryStorage: redisStorage({
    client: redis,
    keyPrefix: "pml-admin:",
  }),

  // Session configuration
  session: {
    cookieCache: {
      maxAge: 5 * 60, // 5-minute cookie cache
      refreshCache: true,
    },
    expiresIn: 60 * 60 * 24 * 7, // 7 days session
    updateAge: 60 * 60, // Refresh session every hour
  },

  // Advanced options
  advanced: {
    generateId: () => crypto.randomUUID(),
  },
});

// Export type for client
export type Auth = typeof auth;
```

### Step 3: Create API Route Handler

```typescript
// apps/admin/src/app/api/auth/[...all]/route.ts
import { auth } from "@/lib/auth";
import { toNextJsHandler } from "better-auth/next-js";

export const { GET, POST } = toNextJsHandler(auth);
```

### Step 4: Create Auth Client

```typescript
// apps/admin/src/lib/auth-client.ts
import { createAuthClient } from "better-auth/client";
import { genericOAuthClient } from "better-auth/client/plugins";

export const authClient = createAuthClient({
  baseURL: process.env.NEXT_PUBLIC_APP_URL,
  plugins: [genericOAuthClient()],
});

export const { signIn, signOut, useSession } = authClient;
```

### Step 5: Middleware for Route Protection

```typescript
// apps/admin/src/middleware.ts
import { NextRequest, NextResponse } from "next/server";
import { auth } from "@/lib/auth";
import { headers } from "next/headers";

export async function middleware(request: NextRequest) {
  const session = await auth.api.getSession({
    headers: await headers(),
  });

  const isAuthRoute = request.nextUrl.pathname.startsWith("/login");
  const isProtectedRoute = !isAuthRoute && !request.nextUrl.pathname.startsWith("/api/auth");

  if (isProtectedRoute && !session) {
    return NextResponse.redirect(new URL("/login", request.url));
  }

  if (isAuthRoute && session) {
    return NextResponse.redirect(new URL("/dashboard", request.url));
  }

  return NextResponse.next();
}

export const config = {
  runtime: "nodejs",
  matcher: ["/((?!_next/static|_next/image|favicon.ico).*)"],
};
```

### Step 6: Server Component Usage

```typescript
// apps/admin/src/app/(dashboard)/page.tsx
import { auth } from "@/lib/auth";
import { headers } from "next/headers";
import { redirect } from "next/navigation";

export default async function DashboardPage() {
  const session = await auth.api.getSession({
    headers: await headers(),
  });

  if (!session) {
    redirect("/login");
  }

  // Access token available for API calls
  const accessToken = session.session?.token;

  return (
    <div>
      <h1>Welcome {session.user.name}</h1>
      {/* Token available: {accessToken} */}
    </div>
  );
}
```

### Step 7: Client Component Usage

```typescript
// apps/admin/src/components/UserMenu.tsx
"use client";

import { useSession, signOut } from "@/lib/auth-client";

export function UserMenu() {
  const { data: session, isPending } = useSession();

  if (isPending) return <div>Loading...</div>;
  if (!session) return null;

  return (
    <div>
      <span>{session.user.name}</span>
      <button onClick={() => signOut()}>Sign Out</button>
    </div>
  );
}
```

### Step 8: Apollo Client with Better Auth

```typescript
// apps/admin/src/lib/apollo-client.ts
import { ApolloClient, InMemoryCache, HttpLink, ApolloLink } from "@apollo/client";
import { setContext } from "@apollo/client/link/context";
import { auth } from "@/lib/auth";
import { headers } from "next/headers";

// For server-side usage
export async function getServerApolloClient() {
  const session = await auth.api.getSession({
    headers: await headers(),
  });

  return new ApolloClient({
    link: new HttpLink({
      uri: process.env.NEXT_PUBLIC_GRAPHQL_ENDPOINT,
      headers: {
        Authorization: session?.session?.token ? `Bearer ${session.session.token}` : "",
      },
    }),
    cache: new InMemoryCache(),
  });
}

// For client-side usage (in Providers.tsx)
export function createClientApolloClient(getSession: () => Promise<any>) {
  const authLink = setContext(async (_, { headers }) => {
    const session = await getSession();
    return {
      headers: {
        ...headers,
        authorization: session?.session?.token ? `Bearer ${session.session.token}` : "",
      },
    };
  });

  const httpLink = new HttpLink({
    uri: process.env.NEXT_PUBLIC_GRAPHQL_ENDPOINT,
  });

  return new ApolloClient({
    link: ApolloLink.from([authLink, httpLink]),
    cache: new InMemoryCache(),
  });
}
```

---

## Token Flow with Better Auth + Keycloak

```
1. User clicks "Sign in with Keycloak"
   │
   ▼
2. Better Auth redirects to Keycloak /authorize endpoint
   │
   ▼
3. User authenticates in Keycloak (username/password, OTP, etc.)
   │
   ▼
4. Keycloak redirects back with authorization code
   │
   ▼
5. Better Auth exchanges code for tokens (access_token, refresh_token, id_token)
   │
   ▼
6. Better Auth creates session, stores in Redis:
   {
     "sessionId": "xxx",
     "userId": "keycloak-sub",
     "accessToken": "eyJ...",  ◀── Keycloak access token
     "refreshToken": "eyJ...", ◀── Keycloak refresh token
     "expiresAt": 1234567890
   }
   │
   ▼
7. Better Auth sets session cookie (session ID only, NOT tokens)
   │
   ▼
8. On subsequent requests:
   - Server Components: auth.api.getSession() → Redis lookup → tokens
   - Client Components: useSession() → API call → Redis lookup → tokens
   │
   ▼
9. Token refresh:
   - Better Auth auto-refreshes using Keycloak refresh_token
   - Updates Redis session
   - No client-side logic needed
```

---

## Migration Path

### Phase 1: Install and Configure (1-2 hours)
1. Install Better Auth dependencies
2. Create auth configuration
3. Create API routes

### Phase 2: Update Components (2-4 hours)
1. Replace `useKeycloak` with `useSession` in client components
2. Add `auth.api.getSession()` to server components
3. Update Apollo Client integration

### Phase 3: Clean Up (1 hour)
1. Remove `keycloak-js` dependency
2. Remove old `KeycloakProvider`
3. Remove cookie bridge code

### Phase 4: Test (1-2 hours)
1. Test login/logout flow
2. Test token refresh
3. Test protected routes
4. Test GraphQL queries with authentication

---

## Summary: Why Better Auth + Keycloak + Redis

| Concern | Solution |
|---------|----------|
| **Identity Management** | Keycloak (unchanged - SSO, users, MFA) |
| **Session Management** | Better Auth (handles token lifecycle) |
| **Session Storage** | Redis (scalable, fast, centralized) |
| **Server Components** | `auth.api.getSession()` - direct access |
| **Client Components** | `useSession()` hook |
| **Token Refresh** | Automatic via Better Auth |
| **Middleware** | Built-in route protection |

This architecture follows industry standards used by major SaaS platforms:
- **Single Source of Truth**: Sessions in Redis, not scattered across browser memory
- **No Redundancy**: Keycloak for identity, Better Auth for sessions
- **Scalability**: Redis enables horizontal scaling
- **Security**: Tokens never exposed to client JavaScript (stored in Redis)
- **Developer Experience**: Clean APIs for both server and client
