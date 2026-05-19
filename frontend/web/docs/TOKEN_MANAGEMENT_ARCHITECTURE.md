# Production-Grade Token Management Architecture

## Executive Summary

This document outlines a production-grade authentication and token management architecture for the PML Ticketing System frontend applications. The design addresses token injection for both Server and Client Components in Next.js 16 App Router, leveraging Redis for centralized session storage.

---

## Current Problems

### 1. Token Not Injected in Client Components
- Apollo Client is created before Keycloak initializes
- `tokenRef` only updated on `onTokenRefresh`, missing initial token
- Race condition between Apollo Client creation and authentication

### 2. Server Components Cannot Access Tokens
- Current implementation is purely client-side (keycloak-js)
- Server Components cannot use React hooks
- No way to access tokens for server-side data fetching

### 3. No Centralized Token Storage
- Tokens exist only in browser memory
- Lost on page refresh (before Keycloak re-initializes)
- Cannot share tokens between tabs/windows

---

## Recommended Architecture: Auth.js v5 + Redis Sessions

### Why Auth.js (NextAuth v5)?

| Feature | keycloak-js (Current) | Auth.js v5 (Recommended) |
|---------|----------------------|--------------------------|
| Server Components | ❌ Not supported | ✅ Native support |
| Client Components | ✅ Works | ✅ Works |
| Token Refresh | Manual implementation | ✅ Built-in |
| SSR/SSG | ❌ Not supported | ✅ Full support |
| Middleware Auth | ❌ Not supported | ✅ Native support |
| Redis Sessions | ❌ Manual | ✅ Adapter support |
| Type Safety | Partial | ✅ Full TypeScript |
| Code Complexity | High (manual) | Low (declarative) |

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           NEXT.JS APPLICATION                                    │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  ┌─────────────────────────────────────────────────────────────────────────┐   │
│  │                         MIDDLEWARE (middleware.ts)                       │   │
│  │  • Runs BEFORE every request                                            │   │
│  │  • Checks session cookie validity                                       │   │
│  │  • Refreshes token if needed (via Auth.js)                              │   │
│  │  • Redirects unauthenticated users to login                             │   │
│  └─────────────────────────────────────────────────────────────────────────┘   │
│                                      │                                          │
│                                      ▼                                          │
│  ┌─────────────────────────────────────────────────────────────────────────┐   │
│  │                      SERVER COMPONENTS                                   │   │
│  │                                                                          │   │
│  │  import { auth } from "@/auth"                                          │   │
│  │                                                                          │   │
│  │  export default async function Page() {                                 │   │
│  │    const session = await auth()  // ← Direct access to session         │   │
│  │    const token = session?.accessToken  // ← Access token available     │   │
│  │                                                                          │   │
│  │    // Fetch data with token                                             │   │
│  │    const data = await fetch(API_URL, {                                  │   │
│  │      headers: { Authorization: `Bearer ${token}` }                      │   │
│  │    })                                                                   │   │
│  │  }                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────────┘   │
│                                      │                                          │
│                                      ▼                                          │
│  ┌─────────────────────────────────────────────────────────────────────────┐   │
│  │                      CLIENT COMPONENTS                                   │   │
│  │                                                                          │   │
│  │  'use client'                                                           │   │
│  │  import { useSession } from "next-auth/react"                           │   │
│  │                                                                          │   │
│  │  export function ClientComponent() {                                    │   │
│  │    const { data: session } = useSession()                               │   │
│  │    const token = session?.accessToken  // ← Same token available       │   │
│  │  }                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                 │
├─────────────────────────────────────────────────────────────────────────────────┤
│                           TOKEN STORAGE LAYER                                   │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  ┌─────────────────────────────────────────────────────────────────────────┐   │
│  │                      BROWSER (HttpOnly Cookie)                          │   │
│  │                                                                          │   │
│  │  Cookie: next-auth.session-token=<encrypted-session-id>                 │   │
│  │                                                                          │   │
│  │  • Session ID only (not the actual tokens)                              │   │
│  │  • HttpOnly, Secure, SameSite=Lax                                       │   │
│  │  • Cannot be accessed by JavaScript (XSS protection)                    │   │
│  │  • Automatically sent with every request                                │   │
│  └─────────────────────────────────────────────────────────────────────────┘   │
│                                      │                                          │
│                                      │ Session ID                              │
│                                      ▼                                          │
│  ┌─────────────────────────────────────────────────────────────────────────┐   │
│  │                      REDIS (Token Storage)                              │   │
│  │                                                                          │   │
│  │  Key: session:<session-id>                                              │   │
│  │  Value: {                                                               │   │
│  │    "userId": "keycloak-user-id",                                        │   │
│  │    "accessToken": "eyJhbG...",                                          │   │
│  │    "refreshToken": "eyJhbG...",                                         │   │
│  │    "accessTokenExpires": 1699999999,                                    │   │
│  │    "user": { "name": "...", "email": "...", "roles": [...] }           │   │
│  │  }                                                                      │   │
│  │  TTL: 7 days (configurable)                                             │   │
│  │                                                                          │   │
│  │  Benefits:                                                              │   │
│  │  • Tokens never exposed to browser JavaScript                           │   │
│  │  • Centralized session management                                       │   │
│  │  • Easy session invalidation (logout all devices)                       │   │
│  │  • Horizontal scaling (multiple Next.js instances)                      │   │
│  │  • Session persistence across deployments                               │   │
│  └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       │ Token Refresh
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              KEYCLOAK                                           │
│                                                                                 │
│  Token Endpoint: /realms/event-ticketing/protocol/openid-connect/token         │
│  JWKS Endpoint:  /realms/event-ticketing/protocol/openid-connect/certs         │
│                                                                                 │
│  Grants:                                                                        │
│  • authorization_code (user login)                                             │
│  • refresh_token (token renewal)                                               │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## Implementation Plan

### File Structure

```
apps/admin/
├── src/
│   ├── app/
│   │   ├── api/
│   │   │   └── auth/
│   │   │       └── [...nextauth]/
│   │   │           └── route.ts        # Auth.js API routes
│   │   ├── (auth)/
│   │   │   ├── login/
│   │   │   │   └── page.tsx            # Login page (triggers OAuth)
│   │   │   └── logout/
│   │   │       └── page.tsx            # Logout page
│   │   └── (dashboard)/
│   │       └── ...                     # Protected routes
│   ├── lib/
│   │   ├── auth.ts                     # Auth.js configuration
│   │   ├── auth.config.ts              # Auth config (for middleware)
│   │   └── redis.ts                    # Redis client
│   └── middleware.ts                   # Route protection
└── ...

libs/shared/
├── src/
│   └── auth/
│       ├── index.ts                    # Public exports
│       ├── session.ts                  # Session utilities
│       ├── redis-adapter.ts            # Custom Redis adapter
│       └── keycloak-provider.ts        # Enhanced Keycloak provider
```

### Step 1: Redis Client Setup

```typescript
// libs/shared/src/auth/redis.ts
import { Redis } from 'ioredis';

const getRedisUrl = () => {
  const url = process.env.REDIS_URL;
  if (!url) {
    throw new Error('REDIS_URL environment variable is not set');
  }
  return url;
};

// Singleton Redis client
let redis: Redis | null = null;

export function getRedisClient(): Redis {
  if (!redis) {
    redis = new Redis(getRedisUrl(), {
      maxRetriesPerRequest: 3,
      retryStrategy: (times) => {
        if (times > 3) return null;
        return Math.min(times * 100, 3000);
      },
    });

    redis.on('error', (err) => {
      console.error('Redis connection error:', err);
    });
  }
  return redis;
}

// Session storage functions
export const sessionStore = {
  async get(sessionId: string) {
    const redis = getRedisClient();
    const data = await redis.get(`session:${sessionId}`);
    return data ? JSON.parse(data) : null;
  },

  async set(sessionId: string, data: unknown, ttlSeconds = 7 * 24 * 60 * 60) {
    const redis = getRedisClient();
    await redis.setex(`session:${sessionId}`, ttlSeconds, JSON.stringify(data));
  },

  async delete(sessionId: string) {
    const redis = getRedisClient();
    await redis.del(`session:${sessionId}`);
  },

  async deleteAllForUser(userId: string) {
    const redis = getRedisClient();
    const keys = await redis.keys(`session:*:${userId}`);
    if (keys.length > 0) {
      await redis.del(...keys);
    }
  },
};
```

### Step 2: Auth.js Configuration with Keycloak

```typescript
// apps/admin/src/lib/auth.ts
import NextAuth from "next-auth";
import Keycloak from "next-auth/providers/keycloak";
import type { NextAuthConfig } from "next-auth";
import { sessionStore } from "@pml.tickets/shared/auth/redis";

// Extend the built-in types
declare module "next-auth" {
  interface Session {
    accessToken?: string;
    refreshToken?: string;
    error?: "RefreshTokenError";
    user: {
      id: string;
      email: string;
      name: string;
      roles: string[];
    };
  }
}

declare module "next-auth/jwt" {
  interface JWT {
    accessToken: string;
    refreshToken: string;
    accessTokenExpires: number;
    error?: "RefreshTokenError";
    roles: string[];
  }
}

const KEYCLOAK_ISSUER = process.env.AUTH_KEYCLOAK_ISSUER!;
const KEYCLOAK_TOKEN_URL = `${KEYCLOAK_ISSUER}/protocol/openid-connect/token`;

/**
 * Refresh the access token using the refresh token
 */
async function refreshAccessToken(token: JWT): Promise<JWT> {
  try {
    const response = await fetch(KEYCLOAK_TOKEN_URL, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        client_id: process.env.AUTH_KEYCLOAK_ID!,
        client_secret: process.env.AUTH_KEYCLOAK_SECRET!,
        grant_type: "refresh_token",
        refresh_token: token.refreshToken,
      }),
    });

    const tokens = await response.json();

    if (!response.ok) {
      throw tokens;
    }

    // Extract roles from the new access token
    const payload = JSON.parse(
      Buffer.from(tokens.access_token.split(".")[1], "base64").toString()
    );
    const roles = payload.realm_access?.roles || [];

    return {
      ...token,
      accessToken: tokens.access_token,
      refreshToken: tokens.refresh_token ?? token.refreshToken,
      accessTokenExpires: Date.now() + tokens.expires_in * 1000,
      roles,
    };
  } catch (error) {
    console.error("Error refreshing access token:", error);
    return {
      ...token,
      error: "RefreshTokenError",
    };
  }
}

export const authConfig: NextAuthConfig = {
  providers: [
    Keycloak({
      clientId: process.env.AUTH_KEYCLOAK_ID!,
      clientSecret: process.env.AUTH_KEYCLOAK_SECRET!,
      issuer: process.env.AUTH_KEYCLOAK_ISSUER!,
      authorization: {
        params: {
          scope: "openid email profile",
        },
      },
    }),
  ],

  session: {
    strategy: "jwt",
    maxAge: 7 * 24 * 60 * 60, // 7 days
  },

  callbacks: {
    /**
     * JWT Callback - Handles token rotation
     * Called whenever a JWT is created or updated
     */
    async jwt({ token, account, user }) {
      // Initial sign in
      if (account && user) {
        // Extract roles from access token
        const payload = JSON.parse(
          Buffer.from(account.access_token!.split(".")[1], "base64").toString()
        );
        const roles = payload.realm_access?.roles || [];

        const newToken = {
          ...token,
          accessToken: account.access_token!,
          refreshToken: account.refresh_token!,
          accessTokenExpires: account.expires_at! * 1000,
          roles,
          sub: user.id,
        };

        // Store in Redis for server-side access
        await sessionStore.set(token.sub!, {
          accessToken: newToken.accessToken,
          refreshToken: newToken.refreshToken,
          accessTokenExpires: newToken.accessTokenExpires,
          roles: newToken.roles,
          user: {
            id: user.id,
            email: user.email,
            name: user.name,
          },
        });

        return newToken;
      }

      // Return previous token if not expired (with 60 second buffer)
      if (Date.now() < token.accessTokenExpires - 60000) {
        return token;
      }

      // Token expired, refresh it
      const refreshedToken = await refreshAccessToken(token);

      // Update Redis with new token
      if (!refreshedToken.error) {
        await sessionStore.set(token.sub!, {
          accessToken: refreshedToken.accessToken,
          refreshToken: refreshedToken.refreshToken,
          accessTokenExpires: refreshedToken.accessTokenExpires,
          roles: refreshedToken.roles,
        });
      }

      return refreshedToken;
    },

    /**
     * Session Callback - Exposes token to client
     * Called whenever session is checked
     */
    async session({ session, token }) {
      if (token.error) {
        session.error = token.error;
      }

      session.accessToken = token.accessToken;
      session.user = {
        id: token.sub!,
        email: token.email!,
        name: token.name!,
        roles: token.roles,
      };

      return session;
    },

    /**
     * Authorized Callback - Route protection
     * Called by middleware for each request
     */
    authorized({ auth, request }) {
      const isLoggedIn = !!auth?.user;
      const isAuthRoute = request.nextUrl.pathname.startsWith("/login");

      if (isAuthRoute) {
        if (isLoggedIn) {
          return Response.redirect(new URL("/dashboard", request.nextUrl));
        }
        return true;
      }

      return isLoggedIn;
    },
  },

  pages: {
    signIn: "/login",
    error: "/login",
  },

  events: {
    async signOut({ token }) {
      // Clear Redis session on logout
      if (token?.sub) {
        await sessionStore.delete(token.sub);
      }
    },
  },
};

export const { handlers, auth, signIn, signOut } = NextAuth(authConfig);
```

### Step 3: Middleware for Route Protection

```typescript
// apps/admin/src/middleware.ts
import { auth } from "@/lib/auth";

export default auth((req) => {
  const isLoggedIn = !!req.auth;
  const isPublicRoute = ["/login", "/api/auth"].some((path) =>
    req.nextUrl.pathname.startsWith(path)
  );

  if (!isLoggedIn && !isPublicRoute) {
    const loginUrl = new URL("/login", req.nextUrl);
    loginUrl.searchParams.set("callbackUrl", req.nextUrl.pathname);
    return Response.redirect(loginUrl);
  }

  // Check for token refresh errors
  if (req.auth?.error === "RefreshTokenError") {
    const loginUrl = new URL("/login", req.nextUrl);
    loginUrl.searchParams.set("error", "SessionExpired");
    return Response.redirect(loginUrl);
  }
});

export const config = {
  matcher: [
    // Match all routes except static files and api routes that don't need auth
    "/((?!_next/static|_next/image|favicon.ico|api/health).*)",
  ],
};
```

### Step 4: Server Component Token Access

```typescript
// Example: Server Component with authenticated data fetching
// apps/admin/src/app/(dashboard)/organizers/page.tsx

import { auth } from "@/lib/auth";
import { redirect } from "next/navigation";

export default async function OrganizersPage() {
  const session = await auth();

  if (!session) {
    redirect("/login");
  }

  // Direct access to token in Server Component
  const response = await fetch(
    `${process.env.GRAPHQL_ENDPOINT}/graphql`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${session.accessToken}`,
      },
      body: JSON.stringify({
        query: `
          query GetOrganizers {
            organizerApplicationsOffsetPagination(status: PENDING_REVIEW) {
              content { id companyName status }
            }
          }
        `,
      }),
      // Important: Revalidate to ensure fresh data
      next: { revalidate: 0 },
    }
  );

  const { data } = await response.json();

  return (
    <div>
      <h1>Organizers</h1>
      <OrganizersTable data={data.organizerApplicationsOffsetPagination.content} />
    </div>
  );
}
```

### Step 5: Client Component Token Access

```typescript
// Example: Client Component with Apollo Client
// apps/admin/src/components/OrganizersTable.tsx
"use client";

import { useSession } from "next-auth/react";
import { useQuery } from "@apollo/client/react";
import { GET_ORGANIZERS } from "@/graphql/queries";

export function OrganizersTable({ initialData }) {
  const { data: session } = useSession();

  // Apollo will use the token from session via the provider
  const { data, loading, error } = useQuery(GET_ORGANIZERS, {
    skip: !session?.accessToken,
  });

  // ...
}
```

### Step 6: Apollo Client with Auth.js Integration

```typescript
// apps/admin/src/lib/apollo-client.ts
import { ApolloClient, InMemoryCache, HttpLink, ApolloLink } from "@apollo/client";
import { setContext } from "@apollo/client/link/context";
import { getSession } from "next-auth/react";

const httpLink = new HttpLink({
  uri: process.env.NEXT_PUBLIC_GRAPHQL_ENDPOINT,
});

const authLink = setContext(async (_, { headers }) => {
  // Get session which includes the access token
  const session = await getSession();

  return {
    headers: {
      ...headers,
      authorization: session?.accessToken
        ? `Bearer ${session.accessToken}`
        : "",
    },
  };
});

export const apolloClient = new ApolloClient({
  link: ApolloLink.from([authLink, httpLink]),
  cache: new InMemoryCache(),
});
```

### Step 7: Provider Setup

```typescript
// apps/admin/src/components/Providers.tsx
"use client";

import { SessionProvider } from "next-auth/react";
import { ApolloProvider } from "@apollo/client/react";
import { apolloClient } from "@/lib/apollo-client";

export function Providers({ children }: { children: React.ReactNode }) {
  return (
    <SessionProvider>
      <ApolloProvider client={apolloClient}>
        {children}
      </ApolloProvider>
    </SessionProvider>
  );
}
```

---

## Environment Variables

```bash
# .env.local (apps/admin)

# Auth.js
AUTH_SECRET=<generate-with-openssl-rand-base64-32>

# Keycloak
AUTH_KEYCLOAK_ID=event-ticketing-admin
AUTH_KEYCLOAK_SECRET=<your-client-secret>
AUTH_KEYCLOAK_ISSUER=http://localhost:8084/realms/event-ticketing

# Redis (for session storage)
REDIS_URL=redis://localhost:6379

# GraphQL
NEXT_PUBLIC_GRAPHQL_ENDPOINT=http://localhost:8080/graphql
```

---

## Security Considerations

### 1. Token Storage
- **Access tokens**: Stored in Redis, keyed by session ID
- **Session ID**: Stored in HttpOnly cookie (cannot be accessed by JS)
- **Never expose refresh tokens to browser**

### 2. Token Refresh
- Automatic refresh in JWT callback
- 60-second buffer before expiry
- Graceful handling of refresh failures

### 3. Session Management
- Redis TTL matches session max age
- Logout clears both cookie and Redis
- Support for "logout all devices" via Redis

### 4. CSRF Protection
- Auth.js includes built-in CSRF protection
- SameSite=Lax cookies prevent CSRF attacks

---

## Migration Path

### Phase 1: Install Dependencies
```bash
npm install next-auth@beta @auth/core ioredis
```

### Phase 2: Configure Auth.js
1. Create `src/lib/auth.ts`
2. Create API routes at `src/app/api/auth/[...nextauth]/route.ts`
3. Add middleware

### Phase 3: Update Components
1. Replace `useKeycloak` with `useSession` in client components
2. Use `auth()` in server components
3. Update Apollo Client configuration

### Phase 4: Remove Old Implementation
1. Remove `keycloak-js` dependency
2. Remove old `KeycloakProvider`
3. Clean up unused code

---

## Comparison: Current vs New Architecture

| Aspect | Current (keycloak-js) | New (Auth.js + Redis) |
|--------|----------------------|----------------------|
| Server Components | ❌ No token access | ✅ `await auth()` |
| Client Components | ⚠️ Race conditions | ✅ `useSession()` |
| Token Refresh | Manual, error-prone | ✅ Automatic |
| Session Storage | Browser memory only | ✅ Redis (persistent) |
| Multi-tab Support | ❌ Issues | ✅ Shared session |
| Horizontal Scaling | ❌ Not supported | ✅ Redis-backed |
| Code Complexity | High | Low |
| Security | Good | Better (HttpOnly) |

---

## Conclusion

The recommended architecture using **Auth.js v5 with Redis sessions** provides:

1. **Unified token access** for both Server and Client Components
2. **Automatic token refresh** with proper error handling
3. **Centralized session storage** in Redis
4. **Better security** with HttpOnly cookies
5. **Horizontal scaling** support
6. **Reduced code complexity**

This is the industry-standard approach used by production applications at scale.
