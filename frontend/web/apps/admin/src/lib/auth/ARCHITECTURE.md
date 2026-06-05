# Better Auth + Keycloak + MongoDB Architecture

## Overview

This document describes the production-grade authentication architecture that eliminates data duplication between Better Auth, Keycloak, and MongoDB.

## The Problem (Before)

```
DUPLICATION ISSUE:
┌─────────────────────┐    ┌─────────────────────┐    ┌─────────────────────┐
│  KEYCLOAK           │    │  BETTER AUTH        │    │  IDENTITY SERVICE   │
│  (PostgreSQL)       │    │  (MongoDB)          │    │  (MongoDB)          │
├─────────────────────┤    ├─────────────────────┤    ├─────────────────────┤
│  USER_ENTITY        │    │  users collection   │    │  users collection   │
│  ├─ id              │    │  ├─ id              │    │  ├─ _id             │
│  ├─ email           │    │  ├─ email      ←────┼────┼──├─ email           │
│  ├─ firstName       │    │  ├─ name            │    │  ├─ firstName       │
│  ├─ lastName        │    │  ├─ emailVerified   │    │  ├─ lastName        │
│  ├─ emailVerified   │    │  ├─ image           │    │  ├─ emailVerified   │
│  └─ ...             │    │  └─ ...        ←────┼────┼──├─ roles           │
└─────────────────────┘    └─────────────────────┘    │  └─ ...             │
                                                      └─────────────────────┘

⚠️ PROBLEM: Same user data in 3 places = sync nightmares, inconsistencies
```

## The Solution (After)

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                    UNIFIED ARCHITECTURE (NO DUPLICATION)                             │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                     │
│  DATA OWNERSHIP PRINCIPLE:                                                          │
│  ─────────────────────────                                                          │
│  • Keycloak: Source of truth for AUTHENTICATION (identity, credentials, roles)     │
│  • Identity Service: Source of truth for BUSINESS DATA (profiles, preferences)     │
│  • Better Auth: Session management ONLY (no user storage)                          │
│                                                                                     │
│  ┌─────────────────────────────────────────────────────────────────────────────┐   │
│  │                           KEYCLOAK (PostgreSQL)                              │   │
│  │                           Source of Truth: Identity                          │   │
│  ├─────────────────────────────────────────────────────────────────────────────┤   │
│  │  USER_ENTITY              CREDENTIAL              USER_SESSION               │   │
│  │  ├─ id (UUID)             ├─ id                   ├─ id                      │   │
│  │  ├─ username              ├─ user_id (FK)         ├─ user_id (FK)            │   │
│  │  ├─ email                 ├─ type                 ├─ ip_address              │   │
│  │  ├─ firstName             ├─ secret_data          ├─ started                 │   │
│  │  ├─ lastName              └─ ...                  └─ ...                     │   │
│  │  ├─ emailVerified                                                            │   │
│  │  └─ ...                   USER_ROLE_MAPPING                                  │   │
│  │                           ├─ user_id (FK)                                    │   │
│  │                           └─ role_id (FK) → KEYCLOAK_ROLE                    │   │
│  └────────────────────────────────────┬────────────────────────────────────────┘   │
│                                       │                                             │
│                            ┌──────────┴──────────┐                                 │
│                            │ UserSyncEventListener│                                 │
│                            │ (Keycloak → MongoDB) │                                 │
│                            └──────────┬──────────┘                                 │
│                                       │                                             │
│                                       ▼                                             │
│  ┌─────────────────────────────────────────────────────────────────────────────┐   │
│  │                           MONGODB (dev_ticketing)                            │   │
│  ├─────────────────────────────────────────────────────────────────────────────┤   │
│  │                                                                              │   │
│  │  ┌─────────────────────────────────────────────────────────────────────┐    │   │
│  │  │  IDENTITY SERVICE COLLECTIONS (Business Data)                        │    │   │
│  │  ├─────────────────────────────────────────────────────────────────────┤    │   │
│  │  │                                                                      │    │   │
│  │  │  users (SHARED - Single Source of Truth)                             │    │   │
│  │  │  ┌─────────────────────────┐       organizerProfiles                 │    │   │
│  │  │  │ _id: "keycloak-uuid"    │──────▶┌─────────────────────────┐      │    │   │
│  │  │  │ email: "j@example.com"  │       │ userId: "keycloak-uuid" │      │    │   │
│  │  │  │ firstName: "John"       │       │ companyName: "Acme Ltd" │      │    │   │
│  │  │  │ lastName: "Doe"         │       │ tradingName: "Acme"     │      │    │   │
│  │  │  │ phoneNumber: "+260..."  │       │ registrationNumber: ... │      │    │   │
│  │  │  │ roles: ["CUSTOMER",     │       │ taxId: "..."            │      │    │   │
│  │  │  │        "ORGANIZER"]     │       │ status: "APPROVED"      │      │    │   │
│  │  │  │ emailVerified: true     │       └─────────────────────────┘      │    │   │
│  │  │  │ phoneVerified: true     │                                        │    │   │
│  │  │  │ active: true            │                                        │    │   │
│  │  │  │ createdAt: Date         │                                        │    │   │
│  │  │  │ updatedAt: Date         │                                        │    │   │
│  │  │  └─────────────────────────┘                                        │    │   │
│  │  │        ▲                                                             │    │   │
│  │  │        │ READ-ONLY access from Better Auth                          │    │   │
│  │  │        │ (via custom keycloakMongoAdapter)                          │    │   │
│  │  └────────┼────────────────────────────────────────────────────────────┘    │   │
│  │           │                                                                  │   │
│  │  ┌────────┴────────────────────────────────────────────────────────────┐    │   │
│  │  │  BETTER AUTH COLLECTIONS (Session Management Only)                   │    │   │
│  │  ├─────────────────────────────────────────────────────────────────────┤    │   │
│  │  │                                                                      │    │   │
│  │  │  auth_sessions                   auth_accounts                       │    │   │
│  │  │  ┌─────────────────────────┐    ┌─────────────────────────┐         │    │   │
│  │  │  │ _id: "session-uuid"     │    │ _id: "account-uuid"     │         │    │   │
│  │  │  │ userId: "keycloak-uuid" │    │ userId: "keycloak-uuid" │         │    │   │
│  │  │  │ token: "secure-token"   │    │ providerId: "keycloak"  │         │    │   │
│  │  │  │ expiresAt: Date         │    │ accountId: "kc-uuid"    │         │    │   │
│  │  │  │ ipAddress: "1.2.3.4"    │    │ accessToken: "jwt..."   │         │    │   │
│  │  │  │ userAgent: "Chrome..."  │    │ refreshToken: "..."     │         │    │   │
│  │  │  │ createdAt: Date         │    │ expiresAt: Date         │         │    │   │
│  │  │  │ updatedAt: Date         │    │ scope: "openid profile" │         │    │   │
│  │  │  └─────────────────────────┘    └─────────────────────────┘         │    │   │
│  │  │                                                                      │    │   │
│  │  │  auth_verifications (Optional)                                       │    │   │
│  │  │  ┌─────────────────────────┐                                        │    │   │
│  │  │  │ _id: "verify-uuid"      │  (Password reset tokens, etc.)         │    │   │
│  │  │  │ identifier: "email"     │                                        │    │   │
│  │  │  │ value: "token"          │                                        │    │   │
│  │  │  │ expiresAt: Date         │                                        │    │   │
│  │  │  └─────────────────────────┘                                        │    │   │
│  │  │                                                                      │    │   │
│  │  │  ❌ NO auth_users collection (uses shared `users` collection)       │    │   │
│  │  │                                                                      │    │   │
│  │  └─────────────────────────────────────────────────────────────────────┘    │   │
│  │                                                                              │   │
│  └─────────────────────────────────────────────────────────────────────────────┘   │
│                                                                                     │
│  ┌─────────────────────────────────────────────────────────────────────────────┐   │
│  │                              REDIS (Session Cache)                           │   │
│  ├─────────────────────────────────────────────────────────────────────────────┤   │
│  │  pml-admin:session:{sessionId} → Cached session data (TTL: 8 hours)         │   │
│  │  (Secondary storage for fast reads)                                          │   │
│  └─────────────────────────────────────────────────────────────────────────────┘   │
│                                                                                     │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

## Authentication Flow

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                           AUTHENTICATION FLOW                                        │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                     │
│  1. USER CLICKS "SIGN IN WITH KEYCLOAK"                                            │
│  ───────────────────────────────────────                                            │
│                                                                                     │
│  Next.js App              Better Auth              Keycloak                         │
│      │                        │                        │                            │
│      │  GET /api/auth/signin/keycloak                  │                            │
│      │───────────────────────▶│                        │                            │
│      │                        │                        │                            │
│      │                        │  Redirect to Keycloak  │                            │
│      │                        │  /realms/event-ticketing/protocol/openid-connect/  │
│      │◀───────────────────────│────────────────────────│                            │
│      │                        │                        │                            │
│      │                        │                        │                            │
│  2. USER AUTHENTICATES WITH KEYCLOAK                   │                            │
│  ────────────────────────────────────                  │                            │
│      │                        │                        │                            │
│      │  Enter credentials / OTP                        │                            │
│      │─────────────────────────────────────────────────▶                            │
│      │                        │                        │                            │
│      │                        │  Authorization code    │                            │
│      │◀─────────────────────────────────────────────────                            │
│      │                        │                        │                            │
│      │                        │                        │                            │
│  3. BETTER AUTH EXCHANGES CODE FOR TOKENS              │                            │
│  ────────────────────────────────────────              │                            │
│      │                        │                        │                            │
│      │  GET /api/auth/callback/keycloak                │                            │
│      │───────────────────────▶│                        │                            │
│      │                        │                        │                            │
│      │                        │  POST /token           │                            │
│      │                        │───────────────────────▶│                            │
│      │                        │                        │                            │
│      │                        │  {access_token,        │                            │
│      │                        │   refresh_token,       │                            │
│      │                        │   id_token}            │                            │
│      │                        │◀───────────────────────│                            │
│      │                        │                        │                            │
│      │                        │                        │                            │
│  4. BETTER AUTH LOOKS UP USER IN SHARED COLLECTION     │                            │
│  ─────────────────────────────────────────────────     │                            │
│      │                        │                        │                            │
│      │                        │  keycloakMongoAdapter.findUserById(sub)             │
│      │                        │─────────────────────────────────────────▶ MongoDB   │
│      │                        │                        │               `users`      │
│      │                        │  User document (if exists)                          │
│      │                        │◀─────────────────────────────────────────           │
│      │                        │                        │                            │
│      │                        │  IF user not found:                                 │
│      │                        │    → Error (user must register via Keycloak first) │
│      │                        │                        │                            │
│      │                        │                        │                            │
│  5. BETTER AUTH CREATES SESSION & STORES ACCOUNT       │                            │
│  ──────────────────────────────────────────────        │                            │
│      │                        │                        │                            │
│      │                        │  INSERT into auth_sessions                          │
│      │                        │  INSERT into auth_accounts (OAuth tokens)           │
│      │                        │─────────────────────────────────────────▶ MongoDB   │
│      │                        │                        │                            │
│      │                        │  Cache session in Redis                             │
│      │                        │─────────────────────────────────────────▶ Redis     │
│      │                        │                        │                            │
│      │  Set-Cookie: pml_session=...                    │                            │
│      │◀───────────────────────│                        │                            │
│      │                        │                        │                            │
│      │                        │                        │                            │
│  6. SUBSEQUENT REQUESTS (Server Components)            │                            │
│  ──────────────────────────────────────────            │                            │
│      │                        │                        │                            │
│      │  Cookie: pml_session=...                        │                            │
│      │───────────────────────▶│                        │                            │
│      │                        │                        │                            │
│      │                        │  1. Check Redis cache                               │
│      │                        │  2. If miss, check MongoDB auth_sessions            │
│      │                        │  3. Validate session                                │
│      │                        │  4. Return session + user                           │
│      │                        │                        │                            │
│      │  { session, user }     │                        │                            │
│      │◀───────────────────────│                        │                            │
│                                                                                     │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

## Key Components

### 1. Custom MongoDB Adapter (`keycloak-mongo-adapter.ts`)

```typescript
keycloakMongoAdapter(mongoDb, {
  allowUserCreation: false, // Users must exist in Identity Service
  syncUserUpdates: false,   // Identity Service is source of truth
})
```

**What it does:**
- Reads from shared `users` collection (Identity Service data)
- Writes to `auth_sessions` collection (Better Auth sessions)
- Writes to `auth_accounts` collection (OAuth tokens)
- Does NOT create duplicate user documents

### 2. Collection Naming Strategy

| Collection | Owner | Purpose |
|------------|-------|---------|
| `users` | Identity Service | User profiles, roles, business data |
| `organizerProfiles` | Identity Service | Organizer KYB data |
| `auth_sessions` | Better Auth | Session management |
| `auth_accounts` | Better Auth | OAuth token storage |
| `auth_verifications` | Better Auth | Verification tokens |

### 3. User Lifecycle

```
1. User registers via Keycloak (register.ftl form)
         ↓
2. AccountTypeRoleMapper assigns roles (CUSTOMER, ORGANIZER)
         ↓
3. UserSyncEventListener triggers
         ↓
4. Identity Service creates user in MongoDB `users` collection
         ↓
5. User signs in via Next.js app (Better Auth + Keycloak OAuth)
         ↓
6. Better Auth reads user from shared `users` collection
         ↓
7. Better Auth creates session in `auth_sessions`
         ↓
8. User is authenticated
```

## Security Considerations

### OWASP Compliance

| Requirement | Implementation |
|-------------|----------------|
| Session ID entropy | UUID v4 (128 bits) |
| Session timeout | 8 hours absolute |
| Session binding | IP address tracking |
| Cookie security | HttpOnly, Secure, SameSite=Lax |
| CSRF protection | Enabled |
| Token storage | Server-side (MongoDB + Redis) |

### Token Flow

```
Keycloak Token (JWT)     →  Stored in auth_accounts (encrypted at rest)
Session Token            →  Stored in auth_sessions + Redis cache
Cookie                   →  Contains only session ID (not user data)
```

## MongoDB Indexes

```javascript
// auth_sessions collection
db.auth_sessions.createIndex({ token: 1 }, { unique: true });
db.auth_sessions.createIndex({ userId: 1 });
db.auth_sessions.createIndex({ expiresAt: 1 }, { expireAfterSeconds: 0 });

// auth_accounts collection
db.auth_accounts.createIndex({ providerId: 1, accountId: 1 }, { unique: true });
db.auth_accounts.createIndex({ userId: 1 });

// auth_verifications collection
db.auth_verifications.createIndex({ identifier: 1 });
db.auth_verifications.createIndex({ expiresAt: 1 }, { expireAfterSeconds: 0 });
```

## Environment Variables

```bash
# Keycloak
AUTH_KEYCLOAK_ID=event-ticketing-admin
AUTH_KEYCLOAK_SECRET=<client-secret>
AUTH_KEYCLOAK_ISSUER=http://localhost:8084/realms/event-ticketing

# MongoDB (shared with Identity Service)
MONGODB_URI=mongodb://app_user:app_password@localhost:27017/dev_ticketing
MONGODB_DATABASE=dev_ticketing

# Redis (session caching)
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=<optional>

# App
NEXT_PUBLIC_APP_URL=http://localhost:3030
AUTH_SECRET=<strong-random-secret>
```

## Troubleshooting

### User not found during OAuth sign-in

**Cause:** User hasn't registered through Keycloak yet.

**Solution:** User must register via Keycloak registration flow first. The `UserSyncEventListener` will create the user in MongoDB.

### Session not persisting

**Cause:** Redis connection issue or MongoDB write failure.

**Solution:** Check Redis connectivity and MongoDB write permissions.

### Roles not appearing in session

**Cause:** Better Auth reads from `users` collection which has roles.

**Solution:** Ensure Identity Service sync is working and roles are in the `users` document.
