# Session & Token Invalidation Analysis

## PML Ticketing System - Admin Frontend

**Architecture**: Better Auth + Keycloak OIDC + Redis Sessions

This document analyzes how session and token invalidation work in the current implementation, ensuring that when a user logs out, both the Better Auth session and Keycloak access tokens are properly invalidated.

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Token & Session Types](#2-token--session-types)
3. [Logout Flow Analysis](#3-logout-flow-analysis)
4. [Invalidation Mechanisms](#4-invalidation-mechanisms)
5. [Current Implementation Review](#5-current-implementation-review)
6. [Security Considerations](#6-security-considerations)
7. [Recommendations](#7-recommendations)

---

## 1. Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                     SESSION & TOKEN ARCHITECTURE                                 │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   ┌──────────────────┐                                                          │
│   │   Admin Web App   │                                                          │
│   │   (Next.js)       │                                                          │
│   └────────┬─────────┘                                                          │
│            │                                                                     │
│            │  Cookie: pml_session (session ID only)                             │
│            ▼                                                                     │
│   ┌──────────────────┐      ┌──────────────────┐      ┌──────────────────┐     │
│   │   Better Auth    │─────▶│     Redis        │      │    Keycloak      │     │
│   │   (Session Mgmt) │      │ (Session Store)  │      │    (OIDC IdP)    │     │
│   └────────┬─────────┘      └──────────────────┘      └────────┬─────────┘     │
│            │                                                    │               │
│            │  Stores:                                           │  Issues:      │
│            │  - Session ID                                      │  - Access Token│
│            │  - User info                                       │  - Refresh Token│
│            │  - OAuth tokens (encrypted)                        │  - ID Token    │
│            │  - IP address                                      │               │
│            │  - Expiration                                      │               │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Components & Responsibilities

| Component | Responsibility |
|-----------|----------------|
| **Better Auth** | Session lifecycle, cookie management, OAuth flow orchestration |
| **Redis** | Server-side session storage (OWASP compliant) |
| **Keycloak** | OIDC provider, token issuance, SSO session management |
| **Cookie** | Contains only session ID (HttpOnly, Secure, SameSite=Lax) |

---

## 2. Token & Session Types

### 2.1 Better Auth Session (Redis)

```json
{
  "id": "pml-admin:session:abc123-uuid",
  "userId": "keycloak-sub-uuid",
  "user": {
    "id": "keycloak-sub-uuid",
    "email": "user@example.com",
    "name": "John Doe"
  },
  "token": "eyJhbG...",  // Keycloak access token (stored server-side)
  "createdAt": "2026-05-18T10:00:00Z",
  "expiresAt": "2026-05-18T18:00:00Z",  // 8-hour absolute timeout
  "ipAddress": "192.168.1.100"
}
```

**Characteristics**:
- Stored entirely server-side (Redis)
- Cookie only contains session ID reference
- 8-hour absolute expiration (OWASP compliant)
- IP address tracked for session binding

### 2.2 Keycloak Tokens

| Token Type | Purpose | Lifetime | Storage |
|------------|---------|----------|---------|
| **Access Token** | API authorization | 5-15 minutes | Redis (via Better Auth) |
| **Refresh Token** | Obtain new access tokens | 30 days (configurable) | Redis (via Better Auth) |
| **ID Token** | User identity claims | 5-15 minutes | Redis (via Better Auth) |

### 2.3 Session Cookie

```
Name: pml_session
Value: abc123-session-uuid (reference only)
Attributes:
  - HttpOnly: true (prevents XSS access)
  - Secure: true (HTTPS only in production)
  - SameSite: Lax (CSRF protection)
  - Path: /
```

---

## 3. Logout Flow Analysis

### 3.1 User-Initiated Logout (RP-Initiated)

When a user clicks "Logout" in the admin app:

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                        RP-INITIATED LOGOUT FLOW                                  │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   User clicks "Logout"                                                          │
│          │                                                                       │
│          ▼                                                                       │
│   ┌──────────────────┐                                                          │
│   │ 1. signOut()     │  Better Auth client calls signOut                        │
│   │    (client.ts)   │                                                          │
│   └────────┬─────────┘                                                          │
│            │                                                                     │
│            ▼                                                                     │
│   ┌──────────────────┐                                                          │
│   │ 2. POST /api/auth│  Better Auth server handles signOut                      │
│   │    /sign-out     │                                                          │
│   └────────┬─────────┘                                                          │
│            │                                                                     │
│            ├──────────────────────────────────────────────┐                     │
│            │                                              │                     │
│            ▼                                              ▼                     │
│   ┌──────────────────┐                         ┌──────────────────┐            │
│   │ 3. Delete Redis  │                         │ 4. Clear Cookie  │            │
│   │    Session       │                         │    pml_session   │            │
│   │    (pml-admin:   │                         │                  │            │
│   │    session:xxx)  │                         │                  │            │
│   └──────────────────┘                         └──────────────────┘            │
│            │                                                                     │
│            ▼                                                                     │
│   ┌──────────────────┐                                                          │
│   │ 5. Redirect to   │  Optionally trigger Keycloak RP-Initiated Logout        │
│   │    /login        │                                                          │
│   └──────────────────┘                                                          │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 3.2 Back-Channel Logout (SSO)

When user logs out from ANY Keycloak client (mobile app, another web app):

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                      BACK-CHANNEL LOGOUT FLOW                                    │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   User logs out from Mobile App                                                 │
│          │                                                                       │
│          ▼                                                                       │
│   ┌──────────────────┐                                                          │
│   │ 1. Keycloak      │  User logs out, SSO session terminates                   │
│   │    Session End   │                                                          │
│   └────────┬─────────┘                                                          │
│            │                                                                     │
│            │  For EACH registered client with backchannel-logout URL:           │
│            │                                                                     │
│            ▼                                                                     │
│   ┌──────────────────┐                                                          │
│   │ 2. Keycloak POSTs│  POST with logout_token (signed JWT)                     │
│   │    to Admin App  │  Content-Type: application/x-www-form-urlencoded        │
│   │    /api/auth/    │                                                          │
│   │    backchannel-  │                                                          │
│   │    logout        │                                                          │
│   └────────┬─────────┘                                                          │
│            │                                                                     │
│            ▼                                                                     │
│   ┌──────────────────┐                                                          │
│   │ 3. Verify JWT    │  Validate signature against Keycloak JWKS               │
│   │    Signature     │  Validate issuer, audience, events claim                │
│   └────────┬─────────┘                                                          │
│            │                                                                     │
│            ▼                                                                     │
│   ┌──────────────────┐                                                          │
│   │ 4. Find & Delete │  Scan Redis for sessions with matching user ID          │
│   │    User Sessions │  Delete all matching sessions                           │
│   │    in Redis      │                                                          │
│   └────────┬─────────┘                                                          │
│            │                                                                     │
│            ▼                                                                     │
│   ┌──────────────────┐                                                          │
│   │ 5. Return 200 OK │  Keycloak expects 200 for success                       │
│   └──────────────────┘                                                          │
│                                                                                  │
│   Result: User's session in Admin App is immediately invalidated                │
│           even though they logged out from a different application              │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Invalidation Mechanisms

### 4.1 Keycloak Token Revocation

Keycloak provides RFC 7009 compliant token revocation:

```
POST /realms/event-ticketing/protocol/openid-connect/revoke
Content-Type: application/x-www-form-urlencoded

token=<access_token_or_refresh_token>
token_type_hint=access_token  (or refresh_token)
client_id=event-ticketing-admin
client_secret=<secret>
```

**Important**: Revoking a **refresh token** also revokes user consent for the client.

### 4.2 Better Auth Session Revocation

Better Auth provides session management via:

```typescript
// Single session revocation
await auth.api.revokeSession({
  headers: headers(),
  body: { sessionId: 'session-to-revoke' }
});

// All sessions for user (requires multi-session plugin)
await auth.api.revokeSessions({
  headers: headers(),
});
```

### 4.3 Redis Direct Deletion

The backchannel-logout handler directly deletes from Redis:

```typescript
// From backchannel-logout/route.ts
async function invalidateUserSessions(sub: string): Promise<number> {
  const client = getRedis();
  const keys = await client.keys(`${SESSION_PREFIX}*`);

  for (const key of keys) {
    const sessionData = await client.get(key);
    const session = JSON.parse(sessionData);

    if (session.userId === sub || session.user?.id === sub) {
      await client.del(key);  // Direct deletion
    }
  }
}
```

### 4.4 Token Introspection

Backend services should validate tokens via introspection:

```
POST /realms/event-ticketing/protocol/openid-connect/token/introspect
Content-Type: application/x-www-form-urlencoded
Authorization: Basic <base64(client_id:client_secret)>

token=<access_token>
```

Response when token is revoked:
```json
{
  "active": false
}
```

---

## 5. Current Implementation Review

### 5.1 What's Implemented

| Feature | Status | Location |
|---------|--------|----------|
| Better Auth session in Redis | ✅ Implemented | `src/lib/auth/index.ts` |
| HttpOnly/Secure cookies | ✅ Implemented | `src/lib/auth/index.ts` |
| Back-channel logout handler | ✅ Implemented | `src/app/api/auth/backchannel-logout/route.ts` |
| JWT signature verification | ✅ Implemented | Uses `jose` library |
| Session deletion on logout | ✅ Implemented | Redis DEL operation |
| RP-Initiated logout | ✅ Implemented | `signOutAndRedirect()` in client.ts |

### 5.2 Logout Flow Code

**Client-side logout** (`src/lib/auth/client.ts`):
```typescript
export async function signOutAndRedirect() {
  await authClient.signOut({
    fetchOptions: {
      onSuccess: () => {
        window.location.href = '/login';
      },
    },
  });
}
```

**Back-channel logout** (`src/app/api/auth/backchannel-logout/route.ts`):
```typescript
export async function POST(request: NextRequest) {
  // 1. Validate content-type
  // 2. Extract logout_token from form data
  // 3. Verify JWT signature against Keycloak JWKS
  // 4. Validate claims (iss, aud, events, sid/sub)
  // 5. Delete matching sessions from Redis
  // 6. Return 200 OK
}
```

### 5.3 Gap Analysis

| Gap | Impact | Recommendation |
|-----|--------|----------------|
| Access token not explicitly revoked at Keycloak | Low - tokens are short-lived (5-15 min) | Consider adding token revocation call |
| No Keycloak end_session_endpoint redirect | Medium - SSO session may persist | Add redirect to Keycloak logout endpoint |
| Redis KEYS operation inefficient | Low for small user base | Consider using Redis SCAN or index by userId |

---

## 6. Security Considerations

### 6.1 OWASP Compliance

| OWASP Requirement | Status | Implementation |
|-------------------|--------|----------------|
| Session ID in cookie only | ✅ | Redis stores full session, cookie has ID |
| HttpOnly flag | ✅ | Prevents XSS access to session ID |
| Secure flag | ✅ | HTTPS only in production |
| SameSite attribute | ✅ | Lax - CSRF protection |
| Absolute session timeout | ✅ | 8 hours max |
| Session regeneration | ✅ | Better Auth handles on auth |
| IP address binding | ✅ | Tracked but not enforced |

### 6.2 Token Lifetime Strategy

```
┌─────────────────────────────────────────────────────────────────┐
│                    TOKEN LIFETIME STRATEGY                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│   Access Token (5-15 min)                                        │
│   ├── Short-lived → minimal damage if leaked                    │
│   └── Revoked by expiration (not explicit revocation)           │
│                                                                  │
│   Refresh Token (configurable, e.g., 30 days)                   │
│   ├── Stored server-side in Redis (never exposed to browser)    │
│   ├── Used to obtain new access tokens                          │
│   └── Revoked by: session deletion in Redis                     │
│                                                                  │
│   Better Auth Session (8 hours)                                  │
│   ├── Contains: user info, tokens, metadata                     │
│   ├── Revoked by: signOut(), backchannel-logout, expiration    │
│   └── Stored: Redis only (cookie has reference)                 │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 6.3 Attack Vectors & Mitigations

| Attack | Mitigation |
|--------|------------|
| **Session Hijacking** | HttpOnly cookies prevent XSS theft; session binding to IP |
| **CSRF** | SameSite=Lax cookies; CSRF token validation enabled |
| **Token Theft** | Tokens stored server-side; short access token lifetime |
| **Session Fixation** | Session regenerated on authentication |
| **Replay Attack** | Token expiration; nonce validation in logout tokens |

---

## 7. Recommendations

### 7.1 Enhance Logout Completeness

Add Keycloak token revocation and end_session redirect:

```typescript
// Enhanced signOut in client.ts
export async function signOutComplete() {
  // 1. Get current session to extract tokens
  const session = await getSession();

  // 2. Sign out from Better Auth (clears Redis session + cookie)
  await authClient.signOut();

  // 3. Redirect to Keycloak end_session endpoint for SSO logout
  const keycloakLogoutUrl = new URL(
    `${KEYCLOAK_ISSUER}/protocol/openid-connect/logout`
  );
  keycloakLogoutUrl.searchParams.set('post_logout_redirect_uri', '/login');
  keycloakLogoutUrl.searchParams.set('client_id', KEYCLOAK_CLIENT_ID);

  window.location.href = keycloakLogoutUrl.toString();
}
```

### 7.2 Optimize Redis Session Lookup

For better performance with many users, index sessions by user ID:

```typescript
// Store session with user index
await redis.set(`session:${sessionId}`, sessionData);
await redis.sadd(`user:${userId}:sessions`, sessionId);

// Invalidate all user sessions efficiently
async function invalidateUserSessions(userId: string) {
  const sessionIds = await redis.smembers(`user:${userId}:sessions`);
  if (sessionIds.length > 0) {
    await redis.del(...sessionIds.map(id => `session:${id}`));
    await redis.del(`user:${userId}:sessions`);
  }
}
```

### 7.3 Backend Token Validation

Ensure backend services validate tokens on every request:

```java
// Spring Security configuration
.oauth2ResourceServer(oauth2 -> oauth2
    .jwt(jwt -> jwt
        .decoder(jwtDecoder())  // Validates signature
        // Optionally add introspection for real-time revocation check
    )
)
```

For high-security operations, use token introspection:

```java
// Call Keycloak introspection endpoint
public boolean isTokenActive(String accessToken) {
    MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
    params.add("token", accessToken);

    TokenIntrospectionResponse response = webClient
        .post()
        .uri(keycloakIntrospectionUri)
        .header("Authorization", "Basic " + clientCredentials)
        .bodyValue(params)
        .retrieve()
        .bodyToMono(TokenIntrospectionResponse.class)
        .block();

    return response.isActive();
}
```

---

## Summary

The current implementation provides robust session and token invalidation:

| Scenario | Session Invalidated | Token Invalidated |
|----------|---------------------|-------------------|
| User clicks Logout | ✅ Redis session deleted | ⚠️ Token expires naturally |
| Backchannel logout (SSO) | ✅ Redis session deleted | ⚠️ Token expires naturally |
| Session timeout (8h) | ✅ Automatic expiration | ⚠️ Token expires naturally |
| Token expiration (5-15min) | N/A | ✅ Natural expiration |

**Key Security Properties**:
1. Sessions stored server-side (Redis) - never in browser
2. Cookies contain only session ID reference
3. OWASP-compliant cookie attributes
4. SSO logout propagation via backchannel
5. Short access token lifetime minimizes exposure window

**For maximum security**, consider adding:
1. Keycloak end_session_endpoint redirect on logout
2. Token introspection for high-security operations
3. Redis session indexing for performance at scale
