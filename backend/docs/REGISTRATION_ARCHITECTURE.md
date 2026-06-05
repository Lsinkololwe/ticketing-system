# Registration Architecture - User Type & Role Mapping

This document describes the production-ready registration architecture for the MyTicket Zambia event ticketing platform, including how user types are captured, stored, and synchronized across Keycloak and MongoDB.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                     REGISTRATION FLOW ARCHITECTURE                              │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  1. USER FILLS REGISTRATION FORM                                                │
│  ────────────────────────────────                                               │
│     ┌─────────────────────────────────────────────────────────────┐            │
│     │  register.ftl (Keycloak Theme)                               │            │
│     │  ─────────────────────────────                               │            │
│     │  Form fields submitted:                                      │            │
│     │  ├─ firstName, lastName, username, email, password          │            │
│     │  ├─ user.attributes.phoneNumber  → "phoneNumber" attribute  │            │
│     │  └─ user.attributes.accountType  → "accountType" attribute  │            │
│     │       Values: ["CUSTOMER"], ["ORGANIZER"], or both          │            │
│     └─────────────────────────────────────────────────────────────┘            │
│                              │                                                  │
│                              ▼                                                  │
│  2. KEYCLOAK REGISTRATION FLOW                                                  │
│  ─────────────────────────────                                                  │
│     ┌─────────────────────────────────────────────────────────────┐            │
│     │  Registration Form (sub-flow)                                │            │
│     │  ───────────────────────────                                 │            │
│     │  Executions (in order):                                      │            │
│     │  ├─ Registration User Creation           [REQUIRED]         │            │
│     │  ├─ Profile Validation                   [REQUIRED]         │            │
│     │  ├─ Password Validation                  [REQUIRED]         │            │
│     │  └─ Account Type Role Mapper (NEW)       [REQUIRED]         │            │
│     │       ↓                                                      │            │
│     │       Reads "accountType" attribute                          │            │
│     │       Assigns CUSTOMER and/or ORGANIZER realm roles          │            │
│     │       Copies to "roles" attribute for sync                   │            │
│     └─────────────────────────────────────────────────────────────┘            │
│                              │                                                  │
│                              ▼                                                  │
│  3. USER CREATED IN KEYCLOAK                                                    │
│  ────────────────────────────                                                   │
│     User record with:                                                           │
│     ├─ Basic profile (firstName, lastName, email, username)                     │
│     ├─ Attributes: {phoneNumber, accountType, roles}                            │
│     └─ Realm Roles: [CUSTOMER] or [CUSTOMER, ORGANIZER]                         │
│                              │                                                  │
│                              ▼                                                  │
│  4. EVENT LISTENER TRIGGERED                                                    │
│  ────────────────────────────                                                   │
│     ┌─────────────────────────────────────────────────────────────┐            │
│     │  UserSyncEventListener (keycloak-extensions)                 │            │
│     │  ───────────────────────────────────────────                 │            │
│     │  onEvent(REGISTER):                                          │            │
│     │    identityServiceClient.syncUser(userId, "REGISTER")        │            │
│     │        │                                                     │            │
│     │        ▼ POST /api/internal/keycloak/sync/user               │            │
│     └─────────────────────────────────────────────────────────────┘            │
│                              │                                                  │
│                              ▼                                                  │
│  5. IDENTITY SERVICE SYNCS USER                                                 │
│  ──────────────────────────────                                                 │
│     ┌─────────────────────────────────────────────────────────────┐            │
│     │  UserSyncServiceImpl (identity-service)                      │            │
│     │  ───────────────────────────────────────                     │            │
│     │  syncUserFromKeycloak(keycloakUserId):                       │            │
│     │    1. Fetch user from Keycloak Admin API                     │            │
│     │    2. Extract roles from:                                    │            │
│     │       - Realm roles (primary source)                         │            │
│     │       - "roles" attribute (set by AccountTypeRoleMapper)     │            │
│     │       - "accountType" attribute (from form, backup)          │            │
│     │    3. Create/Update User document in MongoDB                 │            │
│     │    4. IF roles contains ORGANIZER:                           │            │
│     │       └─ Auto-create OrganizerProfile (status: DRAFT)        │            │
│     │    5. Publish UserRegisteredEvent to Azure Service Bus       │            │
│     └─────────────────────────────────────────────────────────────┘            │
│                              │                                                  │
│                              ▼                                                  │
│  6. CROSS-SERVICE EVENTS                                                        │
│  ────────────────────────────                                                   │
│     UserRegisteredEvent → Azure Service Bus "user-events" topic                 │
│         │                                                                       │
│         ├─► Notification Service: Send welcome email/SMS                        │
│         ├─► Analytics Service: Track registration                               │
│         └─► Other services as needed                                            │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

## Key Components

### 1. Registration Form (`register.ftl`)

Location: `docker-resources/keycloak/themes/myticket/login/register.ftl`

The form submits account type via checkboxes:

```html
<input type="checkbox"
       name="user.attributes.accountType"
       value="CUSTOMER" />

<input type="checkbox"
       name="user.attributes.accountType"
       value="ORGANIZER" />
```

### 2. Account Type Role Mapper (Keycloak SPI)

Location: `backend/keycloak-extensions/src/main/java/com/pml/keycloak/registration/`

Files:
- `AccountTypeRoleMapper.java` - FormAction implementation
- `AccountTypeRoleMapperFactory.java` - SPI factory

**What it does:**
1. Validates at least one account type is selected
2. Reads `accountType` attribute from registration form
3. Assigns corresponding Keycloak **realm roles** (CUSTOMER, ORGANIZER)
4. Stores values in `roles` attribute for sync compatibility

**Why realm roles matter:**
- Appear in JWT `realm_access.roles` claim
- Visible in Keycloak Admin Console
- Authoritative source for role-based access control

### 3. User Sync Event Listener (Keycloak SPI)

Location: `backend/keycloak-extensions/src/main/java/com/pml/keycloak/eventlistener/`

Triggers on `REGISTER` event and calls Identity Service to sync user to MongoDB.

### 4. User Sync Service (Identity Service)

Location: `backend/identity-service/src/main/java/com/pml/identity/service/impl/UserSyncServiceImpl.java`

**Role extraction sources (in priority order):**
1. `keycloakUser.getRealmRoles()` - Most authoritative
2. `attributes["roles"]` - Set by AccountTypeRoleMapper
3. `attributes["accountType"]` - From form (fallback)

**Organizer profile auto-creation:**
```java
if (savedUser.hasRole(UserType.ORGANIZER)) {
    createOrganizerProfileForNewUser(savedUser)
}
```

Creates `OrganizerProfile` with:
- `status: DRAFT`
- `businessEmail`: Pre-filled from user email
- `businessPhone`: Pre-filled from user phone

## Data Models

### MongoDB User Document

```json
{
  "_id": "keycloak-uuid",
  "username": "johndoe",
  "email": "john@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "phoneNumber": "+260971234567",
  "roles": ["CUSTOMER", "ORGANIZER"],
  "emailVerified": false,
  "phoneVerified": false,
  "active": true,
  "createdAt": "2024-01-15T10:30:00Z"
}
```

### MongoDB OrganizerProfile Document (auto-created)

```json
{
  "_id": "auto-generated-uuid",
  "userId": "keycloak-uuid",
  "companyName": null,
  "businessEmail": "john@example.com",
  "businessPhone": "+260971234567",
  "status": "DRAFT",
  "createdAt": "2024-01-15T10:30:00Z"
}
```

### JWT Token Claims

```json
{
  "sub": "keycloak-uuid",
  "preferred_username": "johndoe",
  "email": "john@example.com",
  "realm_access": {
    "roles": ["CUSTOMER", "ORGANIZER", "default-roles-event-ticketing"]
  },
  "accountType": ["CUSTOMER", "ORGANIZER"]
}
```

## Deployment Steps

### 1. Build Keycloak Extensions

```bash
cd backend/keycloak-extensions
mvn clean package
```

Output: `target/keycloak-extensions-1.0.0.jar`

### 2. Deploy to Keycloak

**Option A: Docker Volume Mount**
```yaml
# docker-compose.yml
services:
  keycloak:
    volumes:
      - ./backend/keycloak-extensions/target/keycloak-extensions-1.0.0.jar:/opt/keycloak/providers/keycloak-extensions.jar
```

**Option B: Copy to Providers Directory**
```bash
cp target/keycloak-extensions-1.0.0.jar $KEYCLOAK_HOME/providers/
$KEYCLOAK_HOME/bin/kc.sh build
```

### 3. Create Realm Roles

Ensure these roles exist in Keycloak:
- `CUSTOMER` - Base role for all users
- `ORGANIZER` - Can create events
- `ADMIN` - Platform administrator
- `SCANNER` - Ticket validation
- `FINANCE` - Payout management

### 4. Configure Registration Flow

**Option A: Manual (Admin Console)**

1. Go to Authentication → Flows
2. Select "registration" flow
3. Click on "registration form" → Add execution
4. Select "Account Type Role Mapper"
5. Set requirement to **REQUIRED**
6. Move it after "Password Validation"

**Option B: Automated (Script)**

```bash
cd backend/keycloak-extensions/scripts
./setup-registration-flow.sh
```

### 5. Enable User Sync Event Listener

1. Go to Realm Settings → Events → Event Listeners
2. Add `user-sync` to the list
3. Save

## Testing

### Test Case 1: Customer Only Registration

1. Register with only "CUSTOMER" checkbox selected
2. Verify:
   - Keycloak user has `CUSTOMER` realm role
   - MongoDB user has `roles: ["CUSTOMER"]`
   - No OrganizerProfile created

### Test Case 2: Organizer Registration

1. Register with "ORGANIZER" checkbox selected
2. Verify:
   - Keycloak user has `CUSTOMER` and `ORGANIZER` realm roles
   - MongoDB user has `roles: ["CUSTOMER", "ORGANIZER"]`
   - OrganizerProfile created with `status: DRAFT`

### Test Case 3: Both Customer and Organizer

1. Register with both checkboxes selected
2. Same verification as Test Case 2

## Troubleshooting

### AccountTypeRoleMapper not appearing in Admin Console

1. Check JAR is in `/opt/keycloak/providers/`
2. Verify SPI registration file exists:
   `META-INF/services/org.keycloak.authentication.FormActionFactory`
3. Restart Keycloak with `kc.sh build` for production mode

### Roles not syncing to MongoDB

1. Check `user-sync` event listener is enabled
2. Verify `IDENTITY_SERVICE_URL` environment variable
3. Check Identity Service logs for sync errors
4. Verify OAuth2 credentials (`OTP_CLIENT_ID`, `OTP_CLIENT_SECRET`)

### OrganizerProfile not created

1. Check Identity Service logs for errors
2. Verify `OrganizerProfileRepository` is properly injected
3. Ensure MongoDB connection is working

### Validation fails with "accountTypeRequired"

1. Ensure at least one checkbox is selected in the form
2. Check browser console for JavaScript errors
3. Verify form field names match: `user.attributes.accountType`

## Security Considerations

1. **Realm roles are authoritative** - JWT claims come from Keycloak, not MongoDB
2. **MongoDB caches profile data** - For GraphQL performance, not authorization
3. **Internal API secured** - Sync endpoints require `internal-read`/`internal-write` scopes
4. **OrganizerProfile requires approval** - Users with ORGANIZER role still need admin approval to create events

## Related Files

| File | Purpose |
|------|---------|
| `register.ftl` | Registration form template |
| `AccountTypeRoleMapper.java` | Keycloak FormAction for role assignment |
| `AccountTypeRoleMapperFactory.java` | Keycloak SPI factory |
| `UserSyncEventListener.java` | Keycloak event listener |
| `UserSyncServiceImpl.java` | MongoDB sync logic |
| `OrganizerProfileServiceImpl.java` | Organizer profile management |
| `messages_en.properties` | Error messages |
