# Identity Service Sync Flow - Detailed Documentation

## Build Status

```
✅ keycloak-extensions BUILD SUCCESS
   Output: target/keycloak-extensions-1.0.0.jar
   Size: ~300KB (includes shaded Gson dependency)
```

---

## Complete Registration & Sync Flow

```
┌─────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                    COMPLETE REGISTRATION FLOW                                        │
├─────────────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                                     │
│  ╔═══════════════════════════════════════════════════════════════════════════════════════════════╗ │
│  ║  STEP 1: USER FILLS REGISTRATION FORM (Browser)                                                ║ │
│  ╚═══════════════════════════════════════════════════════════════════════════════════════════════╝ │
│                                                                                                     │
│    register.ftl submits to Keycloak:                                                                │
│    ┌─────────────────────────────────────────────────────────────────────────────────────────────┐ │
│    │  POST /realms/event-ticketing/login-actions/registration                                    │ │
│    │                                                                                             │ │
│    │  Form Data:                                                                                 │ │
│    │  ├── firstName: "John"                                                                      │ │
│    │  ├── lastName: "Doe"                                                                        │ │
│    │  ├── username: "johndoe"                                                                    │ │
│    │  ├── email: "john@example.com"                                                              │ │
│    │  ├── password: "SecurePass123!"                                                             │ │
│    │  ├── password-confirm: "SecurePass123!"                                                     │ │
│    │  ├── user.attributes.phoneNumber: "+260971234567"                                           │ │
│    │  └── user.attributes.accountType: ["CUSTOMER", "ORGANIZER"]  ◄── Multi-select checkboxes   │ │
│    └─────────────────────────────────────────────────────────────────────────────────────────────┘ │
│                                           │                                                         │
│                                           ▼                                                         │
│  ╔═══════════════════════════════════════════════════════════════════════════════════════════════╗ │
│  ║  STEP 2: KEYCLOAK REGISTRATION FLOW EXECUTES                                                   ║ │
│  ╚═══════════════════════════════════════════════════════════════════════════════════════════════╝ │
│                                                                                                     │
│    Registration Flow Executions (in order):                                                         │
│    ┌─────────────────────────────────────────────────────────────────────────────────────────────┐ │
│    │  1. Registration User Creation    [REQUIRED]                                                │ │
│    │     └── Creates user in Keycloak database                                                   │ │
│    │                                                                                             │ │
│    │  2. Profile Validation            [REQUIRED]                                                │ │
│    │     └── Validates firstName, lastName, email format                                         │ │
│    │                                                                                             │ │
│    │  3. Password Validation           [REQUIRED]                                                │ │
│    │     └── Validates password meets policy (min length, complexity)                            │ │
│    │                                                                                             │ │
│    │  4. Account Type Role Mapper      [REQUIRED] ◄── NEW SPI                                    │ │
│    │     ┌─────────────────────────────────────────────────────────────────────────────────────┐│ │
│    │     │  AccountTypeRoleMapper.validate():                                                  ││ │
│    │     │    • Reads user.attributes.accountType from form                                    ││ │
│    │     │    • Validates at least one type selected                                           ││ │
│    │     │    • Returns error if empty: "accountTypeRequired"                                  ││ │
│    │     │                                                                                     ││ │
│    │     │  AccountTypeRoleMapper.success():                                                   ││ │
│    │     │    • Stores accountType as user attribute                                           ││ │
│    │     │    • Copies to "roles" attribute (for sync compatibility)                           ││ │
│    │     │    • Assigns CUSTOMER realm role (always)                                           ││ │
│    │     │    • Assigns ORGANIZER realm role (if selected)                                     ││ │
│    │     └─────────────────────────────────────────────────────────────────────────────────────┘│ │
│    └─────────────────────────────────────────────────────────────────────────────────────────────┘ │
│                                           │                                                         │
│                                           ▼                                                         │
│  ╔═══════════════════════════════════════════════════════════════════════════════════════════════╗ │
│  ║  STEP 3: USER CREATED IN KEYCLOAK DATABASE                                                     ║ │
│  ╚═══════════════════════════════════════════════════════════════════════════════════════════════╝ │
│                                                                                                     │
│    Keycloak User Record:                                                                            │
│    ┌─────────────────────────────────────────────────────────────────────────────────────────────┐ │
│    │  {                                                                                          │ │
│    │    "id": "550e8400-e29b-41d4-a716-446655440000",  ◄── UUID generated by Keycloak           │ │
│    │    "username": "johndoe",                                                                   │ │
│    │    "email": "john@example.com",                                                             │ │
│    │    "emailVerified": false,                                                                  │ │
│    │    "enabled": true,                                                                         │ │
│    │    "firstName": "John",                                                                     │ │
│    │    "lastName": "Doe",                                                                       │ │
│    │    "attributes": {                                                                          │ │
│    │      "phoneNumber": ["+260971234567"],                                                      │ │
│    │      "accountType": ["CUSTOMER", "ORGANIZER"],  ◄── From form                               │ │
│    │      "roles": ["CUSTOMER", "ORGANIZER"]         ◄── Set by AccountTypeRoleMapper           │ │
│    │    },                                                                                       │ │
│    │    "realmRoles": ["CUSTOMER", "ORGANIZER", "default-roles-event-ticketing"]                │ │
│    │  }                                   ▲                                                      │ │
│    │                                      └── Assigned by AccountTypeRoleMapper                  │ │
│    └─────────────────────────────────────────────────────────────────────────────────────────────┘ │
│                                           │                                                         │
│                                           ▼                                                         │
│  ╔═══════════════════════════════════════════════════════════════════════════════════════════════╗ │
│  ║  STEP 4: KEYCLOAK FIRES REGISTER EVENT                                                         ║ │
│  ╚═══════════════════════════════════════════════════════════════════════════════════════════════╝ │
│                                                                                                     │
│    Event Details:                                                                                   │
│    ┌─────────────────────────────────────────────────────────────────────────────────────────────┐ │
│    │  EventType: REGISTER                                                                        │ │
│    │  UserId: "550e8400-e29b-41d4-a716-446655440000"                                             │ │
│    │  RealmId: "event-ticketing"                                                                 │ │
│    │  ClientId: "event-ticketing-admin"                                                          │ │
│    │  IPAddress: "192.168.1.100"                                                                 │ │
│    │  Timestamp: 1717491285000                                                                   │ │
│    └─────────────────────────────────────────────────────────────────────────────────────────────┘ │
│                                           │                                                         │
│                                           ▼                                                         │
│  ╔═══════════════════════════════════════════════════════════════════════════════════════════════╗ │
│  ║  STEP 5: USER SYNC EVENT LISTENER PROCESSES EVENT                                              ║ │
│  ╚═══════════════════════════════════════════════════════════════════════════════════════════════╝ │
│                                                                                                     │
│    UserSyncEventListener.onEvent(event):                                                            │
│    ┌─────────────────────────────────────────────────────────────────────────────────────────────┐ │
│    │  1. Check if event type is in SYNC_EVENTS set:                                              │ │
│    │     SYNC_EVENTS = [REGISTER, UPDATE_PROFILE, UPDATE_EMAIL, VERIFY_EMAIL, ...]              │ │
│    │     ✓ REGISTER is in set → proceed                                                          │ │
│    │                                                                                             │ │
│    │  2. Call handleUserSyncEvent(userId, eventType, event):                                     │ │
│    │     identityServiceClient.syncUser("550e8400-...", "REGISTER")                              │ │
│    └─────────────────────────────────────────────────────────────────────────────────────────────┘ │
│                                           │                                                         │
│                                           ▼                                                         │
│  ╔═══════════════════════════════════════════════════════════════════════════════════════════════╗ │
│  ║  STEP 6: IDENTITY SERVICE CLIENT MAKES HTTP CALL                                               ║ │
│  ╚═══════════════════════════════════════════════════════════════════════════════════════════════╝ │
│                                                                                                     │
│    IdentityServiceClient.syncUser():                                                                │
│    ┌─────────────────────────────────────────────────────────────────────────────────────────────┐ │
│    │  1. Get OAuth2 access token (client credentials flow):                                      │ │
│    │     POST http://localhost:8084/realms/event-ticketing/protocol/openid-connect/token         │ │
│    │     Body: grant_type=client_credentials&client_id=internal-service&client_secret=xxx        │ │
│    │     Response: { "access_token": "eyJhbGciOi...", "expires_in": 300 }                        │ │
│    │                                                                                             │ │
│    │  2. Call Identity Service sync endpoint:                                                    │ │
│    │     POST http://identity-service:8083/api/internal/keycloak/sync/user                       │ │
│    │     Headers:                                                                                │ │
│    │       Authorization: Bearer eyJhbGciOi...                                                   │ │
│    │       Content-Type: application/json                                                        │ │
│    │     Body:                                                                                   │ │
│    │       {                                                                                     │ │
│    │         "keycloakUserId": "550e8400-e29b-41d4-a716-446655440000",                           │ │
│    │         "eventType": "REGISTER",                                                            │ │
│    │         "forceUpdate": true                                                                 │ │
│    │       }                                                                                     │ │
│    └─────────────────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                                     │
└─────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Identity Service Processing (POST /api/internal/keycloak/sync/user)

```
┌─────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                           IDENTITY SERVICE SYNC PROCESSING                                          │
├─────────────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                                     │
│  ╔═══════════════════════════════════════════════════════════════════════════════════════════════╗ │
│  ║  STEP 7: KEYCLOAK SYNC CONTROLLER RECEIVES REQUEST                                             ║ │
│  ╚═══════════════════════════════════════════════════════════════════════════════════════════════╝ │
│                                                                                                     │
│    KeycloakSyncController.syncUser(@RequestBody SyncUserRequest request):                           │
│    ┌─────────────────────────────────────────────────────────────────────────────────────────────┐ │
│    │  File: KeycloakSyncController.java:52-84                                                    │ │
│    │                                                                                             │ │
│    │  @PostMapping("/user")                                                                      │ │
│    │  @PreAuthorize("hasAnyAuthority('SCOPE_internal-write', 'ROLE_INTERNAL_SERVICE')")          │ │
│    │  public Mono<ResponseEntity<SyncResponse>> syncUser(SyncUserRequest request) {              │ │
│    │      log.info("Received sync request for user: {} (event: {})",                             │ │
│    │              request.getKeycloakUserId(), request.getEventType());                          │ │
│    │                                                                                             │ │
│    │      return userSyncService.syncUserFromKeycloak(request.getKeycloakUserId())               │ │
│    │              .map(user -> ResponseEntity.ok(SyncResponse.success(...)))                     │ │
│    │              ...                                                                            │ │
│    │  }                                                                                          │ │
│    │                                                                                             │ │
│    │  Input:                                                                                     │ │
│    │  ┌───────────────────────────────────────────────────────────────────────────────────────┐ │ │
│    │  │  SyncUserRequest {                                                                    │ │ │
│    │  │    keycloakUserId: "550e8400-e29b-41d4-a716-446655440000"                              │ │ │
│    │  │    eventType: "REGISTER"                                                              │ │ │
│    │  │    forceUpdate: true                                                                  │ │ │
│    │  │  }                                                                                    │ │ │
│    │  └───────────────────────────────────────────────────────────────────────────────────────┘ │ │
│    └─────────────────────────────────────────────────────────────────────────────────────────────┘ │
│                                           │                                                         │
│                                           ▼                                                         │
│  ╔═══════════════════════════════════════════════════════════════════════════════════════════════╗ │
│  ║  STEP 8: USER SYNC SERVICE FETCHES FROM KEYCLOAK ADMIN API                                     ║ │
│  ╚═══════════════════════════════════════════════════════════════════════════════════════════════╝ │
│                                                                                                     │
│    UserSyncServiceImpl.syncUserFromKeycloak(keycloakUserId):                                        │
│    ┌─────────────────────────────────────────────────────────────────────────────────────────────┐ │
│    │  File: UserSyncServiceImpl.java:46-60                                                       │ │
│    │                                                                                             │ │
│    │  public Mono<User> syncUserFromKeycloak(String keycloakUserId) {                            │ │
│    │      return keycloakService.findUserById(keycloakUserId)  ◄── Calls Keycloak Admin API      │ │
│    │              .flatMap(optionalUser -> {                                                     │ │
│    │                  UserRepresentation keycloakUser = optionalUser.get();                      │ │
│    │                  return syncKeycloakUserToMongo(keycloakUser);                              │ │
│    │              });                                                                            │ │
│    │  }                                                                                          │ │
│    └─────────────────────────────────────────────────────────────────────────────────────────────┘ │
│                                           │                                                         │
│    KeycloakService.findUserById():        │                                                         │
│    ┌──────────────────────────────────────┴──────────────────────────────────────────────────────┐ │
│    │  File: KeycloakService.java:298-309                                                         │ │
│    │                                                                                             │ │
│    │  GET http://localhost:8084/admin/realms/event-ticketing/users/550e8400-...                  │ │
│    │                                                                                             │ │
│    │  Response (UserRepresentation):                                                             │ │
│    │  ┌───────────────────────────────────────────────────────────────────────────────────────┐ │ │
│    │  │  {                                                                                    │ │ │
│    │  │    "id": "550e8400-e29b-41d4-a716-446655440000",                                       │ │ │
│    │  │    "username": "johndoe",                                                             │ │ │
│    │  │    "email": "john@example.com",                                                       │ │ │
│    │  │    "emailVerified": false,                                                            │ │ │
│    │  │    "enabled": true,                                                                   │ │ │
│    │  │    "firstName": "John",                                                               │ │ │
│    │  │    "lastName": "Doe",                                                                 │ │ │
│    │  │    "createdTimestamp": 1717491285000,                                                 │ │ │
│    │  │    "attributes": {                                                                    │ │ │
│    │  │      "phoneNumber": ["+260971234567"],                                                │ │ │
│    │  │      "accountType": ["CUSTOMER", "ORGANIZER"],                                        │ │ │
│    │  │      "roles": ["CUSTOMER", "ORGANIZER"]                                               │ │ │
│    │  │    },                                                                                 │ │ │
│    │  │    "realmRoles": ["CUSTOMER", "ORGANIZER", "default-roles-event-ticketing"]           │ │ │
│    │  │  }                                                                                    │ │ │
│    │  └───────────────────────────────────────────────────────────────────────────────────────┘ │ │
│    └─────────────────────────────────────────────────────────────────────────────────────────────┘ │
│                                           │                                                         │
│                                           ▼                                                         │
│  ╔═══════════════════════════════════════════════════════════════════════════════════════════════╗ │
│  ║  STEP 9: SYNC TO MONGODB - CHECK IF USER EXISTS                                                ║ │
│  ╚═══════════════════════════════════════════════════════════════════════════════════════════════╝ │
│                                                                                                     │
│    UserSyncServiceImpl.syncKeycloakUserToMongo(keycloakUser):                                       │
│    ┌─────────────────────────────────────────────────────────────────────────────────────────────┐ │
│    │  File: UserSyncServiceImpl.java:171-199                                                     │ │
│    │                                                                                             │ │
│    │  return userRepository.findById(keycloakUserId)  ◄── MongoDB query by Keycloak ID           │ │
│    │          .flatMap(existingUser -> {                                                         │ │
│    │              // UPDATE PATH: User exists in MongoDB                                         │ │
│    │              updateUserFromKeycloak(existingUser, keycloakUser);                            │ │
│    │              return userRepository.save(existingUser);                                      │ │
│    │          })                                                                                 │ │
│    │          .switchIfEmpty(Mono.defer(() -> {                                                  │ │
│    │              // CREATE PATH: New user (first sync)  ◄── This path for REGISTER events       │ │
│    │              User newUser = createUserFromKeycloak(keycloakUser);                           │ │
│    │              return userRepository.save(newUser)                                            │ │
│    │                      .flatMap(savedUser -> {                                                │ │
│    │                          publishUserRegisteredEvent(savedUser);                             │ │
│    │                          if (savedUser.hasRole(UserType.ORGANIZER)) {                       │ │
│    │                              return createOrganizationForNewUser(savedUser)             │ │
│    │                                      .thenReturn(savedUser);                                │ │
│    │                          }                                                                  │ │
│    │                          return Mono.just(savedUser);                                       │ │
│    │                      });                                                                    │ │
│    │          }));                                                                               │ │
│    └─────────────────────────────────────────────────────────────────────────────────────────────┘ │
│                                           │                                                         │
│                                           ▼                                                         │
│  ╔═══════════════════════════════════════════════════════════════════════════════════════════════╗ │
│  ║  STEP 10: EXTRACT ROLES FROM KEYCLOAK DATA                                                     ║ │
│  ╚═══════════════════════════════════════════════════════════════════════════════════════════════╝ │
│                                                                                                     │
│    UserSyncServiceImpl.extractRolesFromKeycloak(keycloakUser, attributes):                          │
│    ┌─────────────────────────────────────────────────────────────────────────────────────────────┐ │
│    │  File: UserSyncServiceImpl.java:278-328                                                     │ │
│    │                                                                                             │ │
│    │  Set<UserType> roles = EnumSet.of(UserType.CUSTOMER);  // Always include CUSTOMER           │ │
│    │                                                                                             │ │
│    │  // Source 1: Keycloak Realm Roles (MOST AUTHORITATIVE)                                     │ │
│    │  if (keycloakUser.getRealmRoles() != null) {                                                │ │
│    │      for (String roleName : keycloakUser.getRealmRoles()) {                                 │ │
│    │          UserType role = parseUserType(roleName);  // "ORGANIZER" → UserType.ORGANIZER      │ │
│    │          roles.add(role);                                                                   │ │
│    │      }                                                                                      │ │
│    │  }                                                                                          │ │
│    │                                                                                             │ │
│    │  // Source 2: 'roles' attribute (set by AccountTypeRoleMapper)                              │ │
│    │  if (attributes.containsKey("roles")) {                                                     │ │
│    │      List<String> roleValues = attributes.get("roles");  // ["CUSTOMER", "ORGANIZER"]       │ │
│    │      for (String roleValue : roleValues) {                                                  │ │
│    │          UserType role = parseUserType(roleValue);                                          │ │
│    │          roles.add(role);                                                                   │ │
│    │      }                                                                                      │ │
│    │  }                                                                                          │ │
│    │                                                                                             │ │
│    │  // Source 3: 'accountType' attribute (from registration form - FALLBACK)                   │ │
│    │  if (attributes.containsKey("accountType")) {                                               │ │
│    │      List<String> accountTypes = attributes.get("accountType");  // ["CUSTOMER","ORGANIZER"]│ │
│    │      for (String accountType : accountTypes) {                                              │ │
│    │          UserType role = parseUserType(accountType);                                        │ │
│    │          roles.add(role);                                                                   │ │
│    │      }                                                                                      │ │
│    │  }                                                                                          │ │
│    │                                                                                             │ │
│    │  Result: EnumSet[CUSTOMER, ORGANIZER]                                                       │ │
│    └─────────────────────────────────────────────────────────────────────────────────────────────┘ │
│                                           │                                                         │
│                                           ▼                                                         │
│  ╔═══════════════════════════════════════════════════════════════════════════════════════════════╗ │
│  ║  STEP 11: CREATE USER ENTITY FROM KEYCLOAK DATA                                                ║ │
│  ╚═══════════════════════════════════════════════════════════════════════════════════════════════╝ │
│                                                                                                     │
│    UserSyncServiceImpl.createUserFromKeycloak(keycloakUser):                                        │
│    ┌─────────────────────────────────────────────────────────────────────────────────────────────┐ │
│    │  File: UserSyncServiceImpl.java:237-265                                                     │ │
│    │                                                                                             │ │
│    │  DATA MAPPING: Keycloak → MongoDB User                                                      │ │
│    │  ┌───────────────────────────────────────────────────────────────────────────────────────┐ │ │
│    │  │  Keycloak Field               │  MongoDB User Field      │  Transformation            │ │ │
│    │  │  ─────────────────────────────┼──────────────────────────┼────────────────────────────│ │ │
│    │  │  id                           │  _id (id)                │  Direct copy (String)      │ │ │
│    │  │  username                     │  username                │  Direct copy               │ │ │
│    │  │  email                        │  email                   │  Direct copy               │ │ │
│    │  │  firstName                    │  firstName               │  Default "" if null        │ │ │
│    │  │  lastName                     │  lastName                │  Default "" if null        │ │ │
│    │  │  emailVerified                │  emailVerified           │  Direct copy (boolean)     │ │ │
│    │  │  enabled                      │  active                  │  Direct copy (boolean)     │ │ │
│    │  │  realmRoles + attributes      │  roles                   │  extractRolesFromKeycloak()│ │ │
│    │  │  attributes.phoneNumber[0]    │  phoneNumber             │  First value from list     │ │ │
│    │  │  attributes.phoneVerified[0]  │  phoneVerified           │  Parse boolean             │ │ │
│    │  │  (generated)                  │  createdAt               │  Instant.now()             │ │ │
│    │  │  (generated)                  │  updatedAt               │  Instant.now()             │ │ │
│    │  └───────────────────────────────────────────────────────────────────────────────────────┘ │ │
│    │                                                                                             │ │
│    │  Result: User Entity                                                                        │ │
│    │  ┌───────────────────────────────────────────────────────────────────────────────────────┐ │ │
│    │  │  User {                                                                               │ │ │
│    │  │    id: "550e8400-e29b-41d4-a716-446655440000",                                         │ │ │
│    │  │    username: "johndoe",                                                               │ │ │
│    │  │    email: "john@example.com",                                                         │ │ │
│    │  │    firstName: "John",                                                                 │ │ │
│    │  │    lastName: "Doe",                                                                   │ │ │
│    │  │    phoneNumber: "+260971234567",                                                      │ │ │
│    │  │    roles: [CUSTOMER, ORGANIZER],                                                      │ │ │
│    │  │    emailVerified: false,                                                              │ │ │
│    │  │    phoneVerified: false,                                                              │ │ │
│    │  │    active: true,                                                                      │ │ │
│    │  │    createdAt: "2024-06-04T08:54:45Z",                                                 │ │ │
│    │  │    updatedAt: "2024-06-04T08:54:45Z"                                                  │ │ │
│    │  │  }                                                                                    │ │ │
│    │  └───────────────────────────────────────────────────────────────────────────────────────┘ │ │
│    └─────────────────────────────────────────────────────────────────────────────────────────────┘ │
│                                           │                                                         │
│                                           ▼                                                         │
│  ╔═══════════════════════════════════════════════════════════════════════════════════════════════╗ │
│  ║  STEP 12: SAVE USER TO MONGODB                                                                 ║ │
│  ╚═══════════════════════════════════════════════════════════════════════════════════════════════╝ │
│                                                                                                     │
│    userRepository.save(newUser):                                                                    │
│    ┌─────────────────────────────────────────────────────────────────────────────────────────────┐ │
│    │  MongoDB Operation:                                                                         │ │
│    │  db.users.insertOne({                                                                       │ │
│    │    "_id": "550e8400-e29b-41d4-a716-446655440000",                                            │ │
│    │    "username": "johndoe",                                                                   │ │
│    │    "email": "john@example.com",                                                             │ │
│    │    "firstName": "John",                                                                     │ │
│    │    "lastName": "Doe",                                                                       │ │
│    │    "phoneNumber": "+260971234567",                                                          │ │
│    │    "roles": ["CUSTOMER", "ORGANIZER"],                                                      │ │
│    │    "emailVerified": false,                                                                  │ │
│    │    "phoneVerified": false,                                                                  │ │
│    │    "active": true,                                                                          │ │
│    │    "locked": false,                                                                         │ │
│    │    "accountStatus": "ACTIVE",                                                               │ │
│    │    "twoFactorEnabled": false,                                                               │ │
│    │    "totalTicketsPurchased": 0,                                                              │ │
│    │    "totalEventsAttended": 0,                                                                │ │
│    │    "createdAt": ISODate("2024-06-04T08:54:45.123Z"),                                         │ │
│    │    "updatedAt": ISODate("2024-06-04T08:54:45.123Z"),                                         │ │
│    │    "_class": "com.pml.identity.domain.model.User"                                           │ │
│    │  })                                                                                         │ │
│    └─────────────────────────────────────────────────────────────────────────────────────────────┘ │
│                                           │                                                         │
│                                           ▼                                                         │
│  ╔═══════════════════════════════════════════════════════════════════════════════════════════════╗ │
│  ║  STEP 13: CHECK IF USER IS ORGANIZER → CREATE ORGANIZER PROFILE                                ║ │
│  ╚═══════════════════════════════════════════════════════════════════════════════════════════════╝ │
│                                                                                                     │
│    if (savedUser.hasRole(UserType.ORGANIZER)):                                                      │
│    ┌─────────────────────────────────────────────────────────────────────────────────────────────┐ │
│    │  File: UserSyncServiceImpl.java:209-232                                                     │ │
│    │                                                                                             │ │
│    │  createOrganizationForNewUser(user):                                                    │ │
│    │                                                                                             │ │
│    │  1. Check if profile already exists:                                                        │ │
│    │     organizationRepository.existsByUserId(user.getId())                                 │ │
│    │     → false (new user)                                                                      │ │
│    │                                                                                             │ │
│    │  2. Create Organization:                                                                │ │
│    │     Organization.builder()                                                              │ │
│    │         .userId("550e8400-e29b-41d4-a716-446655440000")                                     │ │
│    │         .companyName(null)              // To be filled by user later                       │ │
│    │         .businessEmail("john@example.com")  // Pre-filled from user                         │ │
│    │         .businessPhone("+260971234567")     // Pre-filled from user                         │ │
│    │         .status(OrganizerStatus.DRAFT)      // Requires completion & approval               │ │
│    │         .build()                                                                            │ │
│    │                                                                                             │ │
│    │  3. Save to MongoDB:                                                                        │ │
│    │     organizationRepository.save(profile)                                                │ │
│    └─────────────────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                                     │
│    MongoDB Operation:                                                                               │
│    ┌─────────────────────────────────────────────────────────────────────────────────────────────┐ │
│    │  db.organizer_profiles.insertOne({                                                          │ │
│    │    "_id": ObjectId("..."),  // Auto-generated                                               │ │
│    │    "userId": "550e8400-e29b-41d4-a716-446655440000",                                         │ │
│    │    "companyName": null,                                                                     │ │
│    │    "businessEmail": "john@example.com",                                                     │ │
│    │    "businessPhone": "+260971234567",                                                        │ │
│    │    "status": "DRAFT",                                                                       │ │
│    │    "verified": false,                                                                       │ │
│    │    "documentsVerified": false,                                                              │ │
│    │    "bankVerified": false,                                                                   │ │
│    │    "commissionRate": 0.05,                                                                  │ │
│    │    "payoutSchedule": "WEEKLY",                                                              │ │
│    │    "createdAt": ISODate("2024-06-04T08:54:45.456Z"),                                         │ │
│    │    "updatedAt": ISODate("2024-06-04T08:54:45.456Z"),                                         │ │
│    │    "_class": "com.pml.identity.domain.model.Organization"                               │ │
│    │  })                                                                                         │ │
│    └─────────────────────────────────────────────────────────────────────────────────────────────┘ │
│                                           │                                                         │
│                                           ▼                                                         │
│  ╔═══════════════════════════════════════════════════════════════════════════════════════════════╗ │
│  ║  STEP 14: PUBLISH USER REGISTERED EVENT TO AZURE SERVICE BUS                                   ║ │
│  ╚═══════════════════════════════════════════════════════════════════════════════════════════════╝ │
│                                                                                                     │
│    publishUserRegisteredEvent(savedUser):                                                           │
│    ┌─────────────────────────────────────────────────────────────────────────────────────────────┐ │
│    │  File: UserSyncServiceImpl.java:394-417                                                     │ │
│    │                                                                                             │ │
│    │  UserRegisteredEvent event = new UserRegisteredEvent(                                       │ │
│    │      userId: "550e8400-e29b-41d4-a716-446655440000",                                        │ │
│    │      email: "john@example.com",                                                             │ │
│    │      phoneNumber: "+260971234567",                                                          │ │
│    │      roles: ["CUSTOMER", "ORGANIZER"],                                                      │ │
│    │      occurredAt: "2024-06-04T08:54:45.789Z"                                                 │ │
│    │  );                                                                                         │ │
│    │                                                                                             │ │
│    │  streamBridge.send("userOutput-out-0", event);                                              │ │
│    │  → Published to Azure Service Bus topic: "user-events"                                      │ │
│    └─────────────────────────────────────────────────────────────────────────────────────────────┘ │
│                                           │                                                         │
│                                           ▼                                                         │
│  ╔═══════════════════════════════════════════════════════════════════════════════════════════════╗ │
│  ║  STEP 15: RETURN RESPONSE TO KEYCLOAK EVENT LISTENER                                           ║ │
│  ╚═══════════════════════════════════════════════════════════════════════════════════════════════╝ │
│                                                                                                     │
│    Response:                                                                                        │
│    ┌─────────────────────────────────────────────────────────────────────────────────────────────┐ │
│    │  HTTP/1.1 200 OK                                                                            │ │
│    │  Content-Type: application/json                                                             │ │
│    │                                                                                             │ │
│    │  {                                                                                          │ │
│    │    "success": true,                                                                         │ │
│    │    "userId": "550e8400-e29b-41d4-a716-446655440000",                                         │ │
│    │    "action": "SYNCED",                                                                      │ │
│    │    "message": "User synced successfully from Keycloak"                                      │ │
│    │  }                                                                                          │ │
│    └─────────────────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                                     │
└─────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Data Mapping Summary

### Keycloak UserRepresentation → MongoDB User

| Keycloak Field | MongoDB Field | Type | Notes |
|----------------|---------------|------|-------|
| `id` | `_id` | String | Keycloak UUID = MongoDB document ID |
| `username` | `username` | String | Unique index |
| `email` | `email` | String | Unique index |
| `firstName` | `firstName` | String | Default to "" if null |
| `lastName` | `lastName` | String | Default to "" if null |
| `emailVerified` | `emailVerified` | Boolean | |
| `enabled` | `active` | Boolean | |
| `realmRoles` | `roles` | Set<UserType> | Primary source |
| `attributes.roles` | `roles` | Set<UserType> | Secondary source |
| `attributes.accountType` | `roles` | Set<UserType> | Fallback source |
| `attributes.phoneNumber[0]` | `phoneNumber` | String | E.164 format |
| `attributes.phoneVerified[0]` | `phoneVerified` | Boolean | Parsed from string |

### Organization Creation (if ORGANIZER role)

| Source | Organization Field | Notes |
|--------|------------------------|-------|
| `user.id` | `userId` | Links to User |
| `null` | `companyName` | User fills later |
| `user.email` | `businessEmail` | Pre-filled |
| `user.phoneNumber` | `businessPhone` | Pre-filled |
| `DRAFT` | `status` | Requires completion |
| `0.05` | `commissionRate` | Default 5% |
| `"WEEKLY"` | `payoutSchedule` | Default weekly |

---

## Role Extraction Priority

```
┌─────────────────────────────────────────────────────────────────┐
│                    ROLE EXTRACTION SOURCES                       │
│                    (in priority order)                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. Keycloak Realm Roles (MOST AUTHORITATIVE)                   │
│     ───────────────────────────────────────                     │
│     keycloakUser.getRealmRoles()                                │
│     → ["CUSTOMER", "ORGANIZER", "default-roles-event-ticketing"]│
│     → Filtered to valid UserType enums                          │
│                                                                 │
│  2. 'roles' Attribute (Set by AccountTypeRoleMapper)            │
│     ─────────────────────────────────────────────               │
│     keycloakUser.getAttributes().get("roles")                   │
│     → ["CUSTOMER", "ORGANIZER"]                                 │
│     → Added to role set                                         │
│                                                                 │
│  3. 'accountType' Attribute (From Form - FALLBACK)              │
│     ──────────────────────────────────────────────              │
│     keycloakUser.getAttributes().get("accountType")             │
│     → ["CUSTOMER", "ORGANIZER"]                                 │
│     → Added to role set                                         │
│                                                                 │
│  4. Default Role (ALWAYS PRESENT)                               │
│     ─────────────────────────────                               │
│     EnumSet.of(UserType.CUSTOMER)                               │
│     → Ensures every user has at least CUSTOMER role             │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Event Flow to Other Services

```
┌─────────────────────────────────────────────────────────────────┐
│            CROSS-SERVICE EVENT PROPAGATION                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  UserRegisteredEvent                                            │
│  {                                                              │
│    userId: "550e8400-...",                                      │
│    email: "john@example.com",                                   │
│    phoneNumber: "+260971234567",                                │
│    roles: ["CUSTOMER", "ORGANIZER"],                            │
│    occurredAt: "2024-06-04T08:54:45Z"                           │
│  }                                                              │
│           │                                                     │
│           ▼                                                     │
│  Azure Service Bus: "user-events" topic                         │
│           │                                                     │
│           ├──► Notification Service                             │
│           │    └── Send welcome email/SMS                       │
│           │                                                     │
│           ├──► Analytics Service                                │
│           │    └── Track new user registration                  │
│           │                                                     │
│           ├──► Booking Service                                  │
│           │    └── Initialize user wallet (if applicable)       │
│           │                                                     │
│           └──► Catalog Service                                  │
│                └── No action (uses user ID references)          │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Files Involved

| File | Location | Purpose |
|------|----------|---------|
| `register.ftl` | `docker-resources/keycloak/themes/myticket/login/` | Registration form |
| `AccountTypeRoleMapper.java` | `keycloak-extensions/.../registration/` | Assigns realm roles |
| `UserSyncEventListener.java` | `keycloak-extensions/.../eventlistener/` | Triggers sync |
| `IdentityServiceClient.java` | `keycloak-extensions/.../client/` | HTTP client |
| `KeycloakSyncController.java` | `identity-service/.../web/rest/` | REST endpoint |
| `UserSyncServiceImpl.java` | `identity-service/.../service/impl/` | Sync logic |
| `KeycloakService.java` | `identity-service/.../infrastructure/keycloak/` | Keycloak Admin API |
| `UserRepository.java` | `identity-service/.../repository/` | MongoDB CRUD |
| `OrganizationRepository.java` | `identity-service/.../repository/` | MongoDB CRUD |
