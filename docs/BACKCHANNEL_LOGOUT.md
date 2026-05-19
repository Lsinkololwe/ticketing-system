# Back-Channel Logout Implementation Guide

## Overview

Back-channel logout enables immediate session invalidation across all applications when a user logs out from any Keycloak client. Unlike front-channel logout (browser-based), Keycloak sends a direct HTTP POST to your application with a signed `logout_token`.

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                         BACK-CHANNEL LOGOUT FLOW                                │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  1. User logs out from any app (mobile, another web app, Keycloak account)     │
│                                                                                 │
│  ┌──────────────────┐                                                          │
│  │  User clicks     │                                                          │
│  │  "Logout"        │                                                          │
│  └────────┬─────────┘                                                          │
│           │                                                                     │
│           ▼                                                                     │
│  ┌──────────────────┐     POST /api/auth/backchannel-logout                    │
│  │    Keycloak      │─────────────────────────────────────┐                    │
│  │    Server        │     logout_token (signed JWT)       │                    │
│  └──────────────────┘                                     │                    │
│           │                                               │                    │
│           │ (to all clients with                          │                    │
│           │  backchannel_logout_uri)                      │                    │
│           │                                               ▼                    │
│           │                                     ┌──────────────────┐           │
│           │                                     │   Admin App      │           │
│           │                                     │   (Next.js)      │           │
│           │                                     └────────┬─────────┘           │
│           │                                              │                     │
│           │                                     1. Validate JWT signature      │
│           │                                     2. Validate claims (iss, aud)  │
│           │                                     3. Extract sid/sub             │
│           │                                              │                     │
│           │                                              ▼                     │
│           │                                     ┌──────────────────┐           │
│           │                                     │     Redis        │           │
│           │                                     │   Blacklist      │           │
│           │                                     │                  │           │
│           │                                     │ session:blacklist│           │
│           │                                     │ :abc123 = revoked│           │
│           │                                     │ TTL: 300s        │           │
│           │                                     └────────┬─────────┘           │
│           │                                              │                     │
│  2. Subsequent API requests with revoked session         │                     │
│                                                          │                     │
│  ┌──────────────────┐     Bearer token (with sid)        │                     │
│  │  Browser/Client  │─────────────────────────────┐      │                     │
│  └──────────────────┘                             │      │                     │
│                                                   ▼      │                     │
│                                          ┌──────────────────┐                  │
│                                          │   API Gateway    │                  │
│                                          │   (Spring Cloud) │                  │
│                                          └────────┬─────────┘                  │
│                                                   │                            │
│                                          Check blacklist ◄────────────────────┘│
│                                                   │                            │
│                                          ┌───────┴───────┐                     │
│                                          │               │                     │
│                                    Blacklisted?    Not blacklisted             │
│                                          │               │                     │
│                                          ▼               ▼                     │
│                                    401 Unauthorized   Continue to              │
│                                                       backend services         │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

## Components

### 1. Admin App - Back-Channel Logout Endpoint

**Location:** `frontend/web/apps/admin/src/app/api/auth/backchannel-logout/route.ts`

**Endpoint:** `POST /api/auth/backchannel-logout`

**Responsibilities:**
- Receive `logout_token` from Keycloak
- Validate JWT signature using Keycloak's JWKS
- Validate required claims (iss, aud, events, sid/sub)
- Blacklist the session in Redis
- Return 200 OK within 5 seconds

### 2. Session Blacklist Service

**Location:** `frontend/web/apps/admin/src/lib/session-blacklist.ts`

**Methods:**
| Method | Purpose |
|--------|---------|
| `blacklistSession(sid, ttl)` | Add session to blacklist |
| `isSessionBlacklisted(sid)` | Check if session is revoked |
| `blacklistUserSessions(sub, ttl)` | Revoke all user sessions |
| `isUserBlacklisted(sub)` | Check if user is revoked |

### 3. Redis Client

**Location:** `frontend/web/apps/admin/src/lib/redis-client.ts`

**Features:**
- Singleton connection with lazy connect
- Fail-open behavior (returns gracefully if Redis unavailable)
- Automatic reconnection with exponential backoff

### 4. API Gateway - Session Blacklist Filter

**Location:** `backend/api-gateway/src/main/java/com/pml/gateway/filter/SessionBlacklistFilter.java`

**Behavior:**
- Extracts `sid` and `sub` claims from JWT
- Checks Redis blacklist
- Returns 401 Unauthorized if blacklisted
- Fail-open if Redis unavailable

## Keycloak Configuration

### Step 1: Enable Back-Channel Logout on Client

1. Go to **Keycloak Admin Console**
2. Navigate to **Clients** → **event-ticketing-admin**
3. Go to **Settings** tab
4. Under **Logout settings**:
   - Enable **Backchannel logout**
   - Set **Backchannel logout URL**:
     - Development: `http://localhost:3030/api/auth/backchannel-logout`
     - Production: `https://admin.yourdomain.com/api/auth/backchannel-logout`
   - Enable **Backchannel logout session required**
   - Enable **Backchannel logout revoke offline sessions** (optional)

### Step 2: Via Admin API (Programmatic)

```bash
# Get admin token
ACCESS_TOKEN=$(curl -s -X POST \
  "http://localhost:8084/realms/master/protocol/openid-connect/token" \
  -d "grant_type=client_credentials" \
  -d "client_id=admin-cli" \
  -d "client_secret=YOUR_ADMIN_SECRET" | jq -r '.access_token')

# Update client configuration
curl -X PUT \
  "http://localhost:8084/admin/realms/event-ticketing/clients/CLIENT_UUID" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "attributes": {
      "backchannel.logout.url": "http://localhost:3030/api/auth/backchannel-logout",
      "backchannel.logout.session.required": "true",
      "backchannel.logout.revoke.offline.tokens": "false"
    }
  }'
```

### Step 3: Verify Configuration

```bash
# Get client configuration
curl -X GET \
  "http://localhost:8084/admin/realms/event-ticketing/clients/CLIENT_UUID" \
  -H "Authorization: Bearer $ACCESS_TOKEN" | jq '.attributes'
```

Expected output:
```json
{
  "backchannel.logout.url": "http://localhost:3030/api/auth/backchannel-logout",
  "backchannel.logout.session.required": "true"
}
```

## Environment Variables

### Admin App

```bash
# .env.local
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# Keycloak (already configured)
NEXT_PUBLIC_KEYCLOAK_URL=http://localhost:8084
NEXT_PUBLIC_KEYCLOAK_REALM=event-ticketing
NEXT_PUBLIC_KEYCLOAK_CLIENT_ID=event-ticketing-admin
```

### API Gateway

```yaml
# application.yml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
```

## Logout Token Structure

Per [OIDC Back-Channel Logout Spec](https://openid.net/specs/openid-connect-backchannel-1_0.html):

```json
{
  "iss": "http://localhost:8084/realms/event-ticketing",
  "sub": "user-uuid-here",
  "aud": "event-ticketing-admin",
  "iat": 1709721234,
  "exp": 1709721534,
  "jti": "unique-token-id",
  "sid": "session-id-here",
  "events": {
    "http://schemas.openid.net/event/backchannel-logout": {}
  }
}
```

**Important:**
- `sid` identifies the specific session to invalidate
- `sub` identifies the user (can invalidate all sessions)
- `nonce` must NOT be present (distinguishes from ID token)
- `events` must contain the back-channel logout event claim

## Fail-Open Behavior

When Redis is unavailable:

| Component | Behavior |
|-----------|----------|
| Admin App Logout Endpoint | Logs warning, returns 200 OK |
| Admin App Session Check | Returns `false` (not blacklisted) |
| API Gateway Filter | Allows request through |

**Rationale:** System availability is prioritized over immediate session revocation. Tokens will naturally expire after their TTL (5 minutes).

## TTL Configuration

The blacklist TTL should match the **access token lifetime**:

| Setting | Value | Location |
|---------|-------|----------|
| Access Token Lifespan | 300 seconds (5 min) | Keycloak Realm Settings |
| Blacklist TTL | 300 seconds | `session-blacklist.ts` |

## Testing

### 1. Test Logout Endpoint Health

```bash
curl http://localhost:3030/api/auth/backchannel-logout
# Expected: {"status":"ok","endpoint":"backchannel-logout",...}
```

### 2. Simulate Keycloak Logout Token

```bash
# Note: In production, only Keycloak sends this request
# This is for testing only

# First, get a valid logout token by triggering a logout in Keycloak
# Or create a test token (requires signing with Keycloak's private key)

curl -X POST http://localhost:3030/api/auth/backchannel-logout \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "logout_token=<VALID_JWT_HERE>"
```

### 3. Verify Redis Blacklist

```bash
redis-cli
> KEYS session:blacklist:*
> GET session:blacklist:YOUR_SESSION_ID
```

### 4. Test API Gateway Rejection

```bash
# After session is blacklisted, make an API call
curl -H "Authorization: Bearer <TOKEN_WITH_BLACKLISTED_SID>" \
  http://localhost:8080/api/protected/endpoint
# Expected: 401 Unauthorized with X-Session-Revoked: true header
```

## Monitoring

### Logs to Watch

```
# Admin App
[BackChannelLogout] Session blacklisted via sid: abc1...ef23, TTL: 300s
[BackChannelLogout] Completed in 45ms, blacklisted: true

# API Gateway
[SessionBlacklist] Blacklisted session detected: abc1...ef23
[SessionBlacklist] Rejecting blacklisted session: sid=abc1...ef23, path=/api/...
```

### Metrics to Track

- Logout token processing time (should be < 5 seconds)
- Redis blacklist hit rate
- 401 responses due to blacklisted sessions

## Troubleshooting

### Logout Token Not Received

1. Verify `backchannel.logout.url` is set correctly in Keycloak
2. Check network connectivity from Keycloak to admin app
3. Verify admin app is accessible from Keycloak server

### JWT Verification Fails

1. Check JWKS endpoint is reachable: `curl <KEYCLOAK_URL>/realms/<REALM>/protocol/openid-connect/certs`
2. Verify issuer URL matches exactly
3. Check client ID is in audience

### Redis Connection Issues

1. Verify Redis is running: `redis-cli ping`
2. Check environment variables (`REDIS_HOST`, `REDIS_PORT`)
3. Check firewall/network rules

### Session Not Being Rejected

1. Verify `sid` claim is present in JWT
2. Check Redis has the blacklisted key
3. Verify API Gateway filter is registered (check logs)

## Security Considerations

| Concern | Mitigation |
|---------|------------|
| Token tampering | JWT signature validated against JWKS |
| Replay attacks | `jti` claim can be tracked (optional enhancement) |
| DoS on logout endpoint | Rate limiting, strict issuer validation |
| Redis unavailability | Fail-open with token natural expiry |

## References

- [OIDC Back-Channel Logout Spec](https://openid.net/specs/openid-connect-backchannel-1_0.html)
- [Keycloak Logout Documentation](https://www.keycloak.org/docs/latest/server_admin/#_oidc-logout)
- [jose JWT Library](https://github.com/panva/jose)
