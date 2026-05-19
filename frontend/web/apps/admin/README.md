# Better Auth MongoDB Integration

## Database Collections

### Spring Boot Collections (Source of Truth)
- `users`: Authoritative user data managed by Spring Boot
  - DO NOT modify from Better Auth
  - All user creation/updates happen in Spring Boot

### Better Auth Collections (Session Management)
- `user`: Session user cache (synced from Spring Boot on login)
- `session`: Persistent session storage
  - `data.backendToken`: Backend JWT token (only field stored)
- `account`: Account linking (if needed)
- `verification`: Email/phone verification (if enabled)

## Session Data Structure

**Minimal Storage Strategy:**
- Only `backendToken` stored in `session.data`
- All other data (userId, role, expiration, permissions) extracted from token on demand
- Reduces storage and eliminates stale data

## Authentication Flow

1. User logs in via Better Auth `/api/auth/sign-in/admin`
2. Better Auth plugin authenticates against Spring Boot `/api/auth/admin/login`
3. Spring Boot returns JWT token and user data
4. Better Auth creates/updates user record in `user` collection
5. Better Auth creates session in `session` collection with `data.backendToken`
6. Session token stored in HTTP-only cookie
7. Backend token extracted from session.data when needed for API calls

## Environment Variables

Required environment variables (set in `.env.local`):

```bash
# Better Auth Configuration
BETTER_AUTH_URL=http://localhost:3000
BETTER_AUTH_SECRET=your-secret-key-change-in-production
NEXT_PUBLIC_BETTER_AUTH_URL=http://localhost:3000

# MongoDB Configuration (same as Spring Boot)
MONGODB_URI=mongodb://localhost:27017/event_ticketing_test?replicaSet=rs0

# Backend API
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
```

## Architecture

### Source of Truth
- **Spring Boot** = Single source of truth for user data
- **Better Auth** = Session management layer only
- Better Auth user records are "session user cache" - they reference Spring Boot users
- Better Auth syncs user data FROM Spring Boot on successful authentication
- Better Auth user records are read-only mirrors for session management

### Token Storage
- Backend JWT token stored in MongoDB `session.data.backendToken`
- Token NOT stored in cookie (avoids cookie size limits)
- All token data (userId, role, expiration, permissions) extracted from token on demand
- Session token (small) stored in HTTP-only cookie for session lookup

### Benefits
1. **No Conflicts**: User creation only happens in Spring Boot
2. **Single Source of Truth**: All business logic in Spring Boot
3. **Production Reliability**: Sessions persist across server restarts
4. **Minimal Storage**: Only token stored, everything else derived
5. **No Cookie Size Issues**: Large token stored in database, not cookie

