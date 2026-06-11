Perfect! Now I have all the pieces. Let me create a comprehensive technical document showing the complete registration flow.

# MyTicket Zambia - Complete Registration Business Flow
## Technical Implementation Documentation

---

## Overview

This document traces the complete registration flow from when a user submits the Keycloak registration form through to the creation of their User document in MongoDB and automatic Organization creation for organizer accounts.

---

## Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                     REGISTRATION BUSINESS FLOW                                  │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  ┌─────────────────┐                                                           │
│  │ STEP 1:         │                                                           │
│  │ Keycloak        │  User submits registration form with:                     │
│  │ Registration    │  - firstName, lastName, email                             │
│  │ Form            │  - user.attributes.accountType[] = ["CUSTOMER", "ORGANIZER"]│
│  └────────┬────────┘                                                           │
│           │                                                                     │
│           ▼                                                                     │
│  ┌─────────────────────────────────────────────────────────────────────────┐  │
│  │ STEP 2: AccountTypeRoleMapper (Keycloak FormAction SPI)                  │  │
│  │ File: backend/keycloak-extensions/.../AccountTypeRoleMapper.java         │  │
│  └───────────────────────────────────────────────────────────────────────────┘│
│           │                                                                     │
│           │ Line 117: List<String> accountTypes = formData.get(FIELD_ACCOUNT_TYPE);│
│           │ Line 127: user.setAttribute(ATTR_ACCOUNT_TYPE, accountTypes);     │
│           │ Line 130: user.setAttribute("roles", accountTypes);               │
│           │                                                                     │
│           │ Line 133-135: Assign realm roles                                  │
│           │   for (String accountType : accountTypes) {                       │
│           │       assignRealmRole(realm, user, accountType);                  │
│           │   }                                                                │
│           │                                                                     │
│           ▼                                                                     │
│  ┌─────────────────────────────────────────────────────────────────────────┐  │
│  │ Result: User in Keycloak with:                                           │  │
│  │ - Realm roles: CUSTOMER, ORGANIZER (granted via grantRole())            │  │
│  │ - Attributes:                                                            │  │
│  │   * accountType = ["CUSTOMER", "ORGANIZER"]                              │  │
│  │   * roles = ["CUSTOMER", "ORGANIZER"]                                    │  │
│  └───────────────────────────────────────────────────────────────────────────┘│
│           │                                                                     │
│           │ (Registration completes - REGISTER event fired)                    │
│           │                                                                     │
│           ▼                                                                     │
│  ┌─────────────────────────────────────────────────────────────────────────┐  │
│  │ STEP 3: UserSyncEventListener (Keycloak EventListener SPI)               │  │
│  │ File: backend/keycloak-extensions/.../UserSyncEventListener.java         │  │
│  └───────────────────────────────────────────────────────────────────────────┘│
│           │                                                                     │
│           │ Line 62: onEvent(Event event) triggered                           │
│           │ Line 75-77: Detects REGISTER event in SYNC_EVENTS                 │
│           │ Line 131: calls identityServiceClient.syncUser(userId, eventType) │
│           │                                                                     │
│           ▼                                                                     │
│  ┌─────────────────────────────────────────────────────────────────────────┐  │
│  │ STEP 4: IdentityServiceClient REST Call                                  │  │
│  │ File: backend/keycloak-extensions/.../IdentityServiceClient.java         │  │
│  └───────────────────────────────────────────────────────────────────────────┘│
│           │                                                                     │
│           │ Line 295: syncUser(String keycloakUserId, String eventType)       │
│           │ Line 297: POST /api/internal/keycloak/sync/user                   │
│           │                                                                     │
│           │ Request Body (JSON):                                              │
│           │ {                                                                  │
│           │   "keycloakUserId": "abc123...",                                  │
│           │   "eventType": "REGISTER"                                          │
│           │ }                                                                  │
│           │                                                                     │
│           │ Line 311: Authorization: Bearer <token> (OAuth2 client credentials)│
│           │                                                                     │
│           ▼                                                                     │
│  ┌─────────────────────────────────────────────────────────────────────────┐  │
│  │ STEP 5: KeycloakSyncController (Identity Service)                        │  │
│  │ File: backend/identity-service/.../KeycloakSyncController.java           │  │
│  └───────────────────────────────────────────────────────────────────────────┘│
│           │                                                                     │
│           │ Line 52-84: @PostMapping("/user")                                 │
│           │ Line 53: @PreAuthorize("hasAnyAuthority('SCOPE_internal-write')") │
│           │ Line 58: userSyncService.syncUserFromKeycloak(keycloakUserId)     │
│           │                                                                     │
│           ▼                                                                     │
│  ┌─────────────────────────────────────────────────────────────────────────┐  │
│  │ STEP 6: UserSyncServiceImpl.syncUserFromKeycloak()                       │  │
│  │ File: backend/identity-service/.../UserSyncServiceImpl.java              │  │
│  └───────────────────────────────────────────────────────────────────────────┘│
│           │                                                                     │
│           │ Line 47-59: Main entry point                                      │
│           │ Line 50: keycloakService.findUserById(keycloakUserId)             │
│           │ Line 58: syncKeycloakUserToMongo(keycloakUser)                    │
│           │                                                                     │
│           ▼                                                                     │
│  ┌─────────────────────────────────────────────────────────────────────────┐  │
│  │ STEP 7: Create User from Keycloak Data                                   │  │
│  │ Method: createUserFromKeycloak() (Line 237-265)                          │  │
│  └───────────────────────────────────────────────────────────────────────────┘│
│           │                                                                     │
│           │ Line 241: extractRolesFromKeycloak(keycloakUser, attributes)      │
│           │                                                                     │
│           │ ROLE EXTRACTION LOGIC (Lines 278-328):                            │
│           │ ┌─────────────────────────────────────────────────────────────┐  │
│           │ │ Priority 1: Keycloak Realm Roles (most authoritative)       │  │
│           │ │ Line 282-290: keycloakUser.getRealmRoles()                  │  │
│           │ │   - Returns: ["CUSTOMER", "ORGANIZER"]                      │  │
│           │ │                                                              │  │
│           │ │ Priority 2: 'roles' attribute (from AccountTypeRoleMapper)  │  │
│           │ │ Line 295-307: attributes.get("roles")                       │  │
│           │ │   - Returns: ["CUSTOMER", "ORGANIZER"]                      │  │
│           │ │                                                              │  │
│           │ │ Priority 3: 'accountType' attribute (from registration form)│  │
│           │ │ Line 311-324: attributes.get("accountType")                 │  │
│           │ │   - Returns: ["CUSTOMER", "ORGANIZER"]                      │  │
│           │ └─────────────────────────────────────────────────────────────┘  │
│           │                                                                     │
│           │ Line 243: User.builder()                                           │
│           │   .id(keycloakUser.getId())     // MongoDB ID = Keycloak ID       │
│           │   .username(keycloakUser.getUsername())                            │
│           │   .email(keycloakUser.getEmail())                                  │
│           │   .firstName(keycloakUser.getFirstName())                          │
│           │   .lastName(keycloakUser.getLastName())                            │
│           │   .emailVerified(keycloakUser.isEmailVerified())                   │
│           │   .active(keycloakUser.isEnabled())                                │
│           │   .roles(roles)  // EnumSet<UserType> = {CUSTOMER, ORGANIZER}     │
│           │   .createdAt(Instant.now())                                        │
│           │   .build()                                                         │
│           │                                                                     │
│           │ Line 259-262: Extract custom attributes (phoneNumber, etc.)       │
│           │                                                                     │
│           ▼                                                                     │
│  ┌─────────────────────────────────────────────────────────────────────────┐  │
│  │ STEP 8: Save User & Trigger Side Effects                                 │  │
│  │ Method: syncKeycloakUserToMongo() (Lines 171-199)                        │  │
│  └───────────────────────────────────────────────────────────────────────────┘│
│           │                                                                     │
│           │ Line 185: userRepository.save(newUser)                            │
│           │ Line 188: publishUserRegisteredEvent(savedUser)                   │
│           │                                                                     │
│           │ Lines 191-195: Auto-create Organization if ORGANIZER          │
│           │   if (savedUser.hasRole(UserType.ORGANIZER)) {                    │
│           │       return createOrganizationForNewUser(savedUser)          │
│           │           .thenReturn(savedUser);                                 │
│           │   }                                                                │
│           │                                                                     │
│           ▼                                                                     │
│  ┌─────────────────────────────────────────────────────────────────────────┐  │
│  │ STEP 9: Create Organization (if ORGANIZER role)                      │  │
│  │ Method: createOrganizationForNewUser() (Lines 209-232)               │  │
│  └───────────────────────────────────────────────────────────────────────────┘│
│           │                                                                     │
│           │ Line 212: organizationRepository.existsByUserId(user.getId()) │
│           │                                                                     │
│           │ Lines 219-225: Create Organization                            │
│           │   Organization.builder()                                      │
│           │       .userId(user.getId())          // Link to User              │
│           │       .companyName(null)             // To be filled later        │
│           │       .businessEmail(user.getEmail()) // Pre-fill                 │
│           │       .businessPhone(user.getPhoneNumber()) // Pre-fill           │
│           │       .status(OrganizerStatus.DRAFT) // Initial status            │
│           │       .build()                                                    │
│           │                                                                     │
│           │ Line 227: organizationRepository.save(profile)                │
│           │                                                                     │
│           ▼                                                                     │
│  ┌─────────────────────────────────────────────────────────────────────────┐  │
│  │ STEP 10: Publish UserRegisteredEvent to Azure Service Bus                │  │
│  │ Method: publishUserRegisteredEvent() (Lines 394-417)                     │  │
│  └───────────────────────────────────────────────────────────────────────────┘│
│           │                                                                     │
│           │ Line 397-399: Convert roles to Set<String>                        │
│           │   Set<String> roleNames = user.getRoles().stream()                │
│           │       .map(Enum::name)                                             │
│           │       .collect(Collectors.toSet());                                │
│           │   // Result: ["CUSTOMER", "ORGANIZER"]                             │
│           │                                                                     │
│           │ Lines 401-406: Create event                                       │
│           │   UserRegisteredEvent event = new UserRegisteredEvent(            │
│           │       user.getId(),                                                │
│           │       user.getEmail(),                                             │
│           │       user.getPhoneNumber(),                                       │
│           │       roleNames                                                    │
│           │   );                                                               │
│           │                                                                     │
│           │ Line 408: streamBridge.send("userOutput-out-0", event)            │
│           │   → Azure Service Bus topic: "identity-events"                     │
│           │                                                                     │
│           ▼                                                                     │
│  ┌─────────────────────────────────────────────────────────────────────────┐  │
│  │ FINAL STATE: MongoDB Collections                                         │  │
│  └───────────────────────────────────────────────────────────────────────────┘│
│                                                                                 │
│   users collection:                                                            │
│   {                                                                             │
│     "_id": "abc123...",              // Keycloak user ID                       │
│     "username": "user_26097XXXX",                                              │
│     "email": "john@example.com",                                               │
│     "firstName": "John",                                                        │
│     "lastName": "Doe",                                                          │
│     "phoneNumber": "+260971234567",                                             │
│     "roles": ["CUSTOMER", "ORGANIZER"],  // EnumSet<UserType>                  │
│     "emailVerified": true,                                                      │
│     "phoneVerified": true,                                                      │
│     "active": true,                                                             │
│     "createdAt": "2024-06-04T10:30:00Z",                                        │
│     "updatedAt": "2024-06-04T10:30:00Z"                                         │
│   }                                                                             │
│                                                                                 │
│   organizer_profiles collection:                                               │
│   {                                                                             │
│     "_id": "xyz789...",                                                         │
│     "userId": "abc123...",           // Link to User document                  │
│     "companyName": null,             // To be filled by organizer              │
│     "businessEmail": "john@example.com",  // Pre-filled from User              │
│     "businessPhone": "+260971234567",     // Pre-filled from User              │
│     "status": "DRAFT",                    // Requires completion & approval    │
│     "verified": false,                                                          │
│     "documentsVerified": false,                                                 │
│     "createdAt": "2024-06-04T10:30:00Z",                                        │
│     "updatedAt": "2024-06-04T10:30:00Z"                                         │
│   }                                                                             │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## Detailed Code Analysis

### STEP 1: Keycloak Registration Form Submission

**What happens**: User fills out the registration form with checkboxes for account types.

**Form Data Submitted**:
```
firstName: "John"
lastName: "Doe"
email: "john@example.com"
user.attributes.accountType: ["CUSTOMER", "ORGANIZER"]  ← Multiple checkboxes
```

---

### STEP 2: AccountTypeRoleMapper (Keycloak FormAction SPI)

**File**: `/Users/lazarous.sinkololwe/Documents/Software Projects/personal/ticketing-system/backend/keycloak-extensions/src/main/java/com/pml/keycloak/registration/AccountTypeRoleMapper.java`

**Purpose**: Process account type selections and assign corresponding Keycloak realm roles.

**Key Code Sections**:

#### Validation Phase (Lines 80-109)
```java
@Override
public void validate(ValidationContext context) {
    MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
    List<FormMessage> errors = new ArrayList<>();

    // Get selected account types
    List<String> accountTypes = formData.get(FIELD_ACCOUNT_TYPE);  // Line 85

    // Validate at least one account type is selected
    if (accountTypes == null || accountTypes.isEmpty()) {
        errors.add(new FormMessage(FIELD_ACCOUNT_TYPE, "accountTypeRequired"));
        LOG.warn("Registration validation failed: No account type selected");
    } else {
        // Validate that selected types are valid
        for (String type : accountTypes) {
            if (!TYPE_CUSTOMER.equals(type) && !TYPE_ORGANIZER.equals(type)) {
                errors.add(new FormMessage(FIELD_ACCOUNT_TYPE, "invalidAccountType"));
                LOG.warnf("Registration validation failed: Invalid account type '%s'", type);
                break;
            }
        }
    }
}
```

#### Success Phase - Store Attributes (Lines 112-148)
```java
@Override
public void success(FormContext context) {
    UserModel user = context.getUser();
    RealmModel realm = context.getRealm();
    MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();

    List<String> accountTypes = formData.get(FIELD_ACCOUNT_TYPE);  // Line 117

    if (accountTypes == null || accountTypes.isEmpty()) {
        // Default to CUSTOMER if somehow no selection made
        accountTypes = List.of(TYPE_CUSTOMER);
    }

    LOG.infof("Processing account types for user %s: %s", user.getUsername(), accountTypes);

    // Store account types as user attribute (for MongoDB sync)
    user.setAttribute(ATTR_ACCOUNT_TYPE, accountTypes);  // Line 127
    
    // Also store as 'roles' attribute for backward compatibility with sync service
    user.setAttribute("roles", accountTypes);  // Line 130

    // Assign realm roles based on account type selection
    for (String accountType : accountTypes) {  // Lines 133-135
        assignRealmRole(realm, user, accountType);
    }

    // Always ensure CUSTOMER role is assigned (base role for all users)
    if (!accountTypes.contains(TYPE_CUSTOMER)) {  // Lines 138-145
        assignRealmRole(realm, user, TYPE_CUSTOMER);
        // Also add to attributes
        List<String> updatedTypes = new ArrayList<>(accountTypes);
        updatedTypes.add(TYPE_CUSTOMER);
        user.setAttribute(ATTR_ACCOUNT_TYPE, updatedTypes);
        user.setAttribute("roles", updatedTypes);
    }

    LOG.infof("Successfully assigned roles to user %s: %s", user.getUsername(), accountTypes);
}
```

#### Role Assignment Logic (Lines 157-173)
```java
private void assignRealmRole(RealmModel realm, UserModel user, String roleName) {
    RoleModel role = realm.getRole(roleName);  // Line 158

    if (role == null) {
        LOG.warnf("Realm role '%s' not found in realm '%s'. Creating it now.", roleName, realm.getName());
        // Create the role if it doesn't exist
        role = realm.addRole(roleName);  // Line 163
        role.setDescription("Auto-created role for " + roleName + " users");
    }

    if (!user.hasRole(role)) {
        user.grantRole(role);  // Line 168 - CRITICAL: Grants Keycloak realm role
        LOG.infof("Assigned role '%s' to user '%s'", roleName, user.getUsername());
    } else {
        LOG.debugf("User '%s' already has role '%s'", user.getUsername(), roleName);
    }
}
```

**Data Mapping After This Step**:
- **Keycloak Realm Roles**: `CUSTOMER`, `ORGANIZER` (granted via `user.grantRole(role)`)
- **Keycloak User Attributes**:
  - `accountType`: `["CUSTOMER", "ORGANIZER"]`
  - `roles`: `["CUSTOMER", "ORGANIZER"]`

---

### STEP 3: UserSyncEventListener (Keycloak EventListener SPI)

**File**: `/Users/lazarous.sinkololwe/Documents/Software Projects/personal/ticketing-system/backend/keycloak-extensions/src/main/java/com/pml/keycloak/eventlistener/UserSyncEventListener.java`

**Purpose**: Listen for Keycloak events and trigger synchronization to Identity Service.

**Key Code Sections**:

#### Event Detection (Lines 62-90)
```java
@Override
public void onEvent(Event event) {
    // Only process events for our configured realm
    if (!shouldProcessEvent(event)) {  // Line 64
        return;
    }

    String userId = event.getUserId();  // Line 68
    EventType eventType = event.getType();  // Line 69

    LOG.debugf("Processing event: type=%s, userId=%s, realm=%s",
            eventType, userId, event.getRealmName());

    try {
        if (SYNC_EVENTS.contains(eventType)) {  // Line 75
            // Full user sync needed
            handleUserSyncEvent(userId, eventType, event);  // Line 77
        } else if (LOGIN_EVENTS.contains(eventType)) {
            // Just update login timestamp
            handleLoginEvent(userId, eventType, event);
        } else {
            // Log other events for debugging
            LOG.debugf("Ignoring event type: %s for user: %s", eventType, userId);
        }
    } catch (Exception e) {
        // Don't let sync failures break the authentication flow
        LOG.errorf(e, "Failed to process event %s for user %s: %s",
                eventType, userId, e.getMessage());
    }
}
```

#### Sync Events Configuration (Lines 36-43)
```java
// Events that should trigger a user sync
private static final Set<EventType> SYNC_EVENTS = Set.of(
        EventType.REGISTER,       // ← Registration triggers this
        EventType.UPDATE_PROFILE,
        EventType.UPDATE_EMAIL,
        EventType.VERIFY_EMAIL,
        EventType.UPDATE_TOTP,
        EventType.REMOVE_TOTP
);
```

#### Sync Handler (Lines 128-139)
```java
private void handleUserSyncEvent(String userId, EventType eventType, Event event) {
    LOG.infof("Syncing user %s due to event: %s", userId, eventType);  // Line 129

    IdentityServiceClient.SyncResult result = identityServiceClient.syncUser(userId, eventType.name());  // Line 131

    if (result.isSuccess()) {  // Line 133
        LOG.infof("Successfully synced user %s for event %s", userId, eventType);
    } else {
        LOG.warnf("Failed to sync user %s for event %s: %s",
                userId, eventType, result.getMessage());
    }
}
```

**Data Flow**:
- **Input**: Keycloak REGISTER event with `userId`
- **Output**: REST call to Identity Service with `userId` and event type

---

### STEP 4: IdentityServiceClient REST Call

**File**: `/Users/lazarous.sinkololwe/Documents/Software Projects/personal/ticketing-system/backend/keycloak-extensions/src/main/java/com/pml/keycloak/client/IdentityServiceClient.java`

**Purpose**: HTTP client for calling Identity Service endpoints with OAuth2 authentication.

**Key Code Sections**:

#### Sync User Request (Lines 295-339)
```java
public SyncResult syncUser(String keycloakUserId, String eventType) {
    try {
        String url = serviceUrl + "/api/internal/keycloak/sync/user";  // Line 297

        JsonObject requestBody = new JsonObject();  // Line 299
        requestBody.addProperty("keycloakUserId", keycloakUserId);  // Line 300
        requestBody.addProperty("eventType", eventType);  // Line 301

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()  // Line 303
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody)));

        // Add authorization header if token is available
        String token = getAccessToken();  // Line 311
        if (token != null) {
            requestBuilder.header("Authorization", "Bearer " + token);  // Line 312
        }

        HttpRequest request = requestBuilder.build();  // Line 315

        LOG.infof("Syncing user %s (event: %s) to Identity Service", keycloakUserId, eventType);  // Line 318

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());  // Line 320

        LOG.debugf("Sync user response status: %d", response.statusCode());  // Line 322

        if (response.statusCode() == 200 || response.statusCode() == 201) {  // Line 324
            LOG.infof("User %s synced successfully", keycloakUserId);
            return new SyncResult(true, "User synced successfully");
        } else {
            String errorMsg = String.format("Failed to sync user: %d - %s", response.statusCode(), response.body());
            LOG.errorf(errorMsg);
            return new SyncResult(false, errorMsg);
        }
    } catch (IOException | InterruptedException e) {
        LOG.errorf(e, "Failed to sync user %s", keycloakUserId);
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        return new SyncResult(false, "Error: " + e.getMessage());
    }
}
```

#### OAuth2 Token Acquisition (Lines 68-121)
```java
private String getAccessToken() {
    tokenLock.lock();
    try {
        // Check if we have a valid cached token
        if (accessToken != null && tokenExpiry != null && Instant.now().isBefore(tokenExpiry)) {  // Line 72
            return accessToken;
        }

        // If no token URL configured, skip authentication (for development)
        if (tokenUrl == null || tokenUrl.isEmpty() || clientId == null || clientSecret == null) {
            LOG.warn("Token URL or credentials not configured - skipping authentication");
            return null;
        }

        LOG.debug("Fetching new access token from Keycloak");

        String formData = String.format(  // Lines 84-88
                "grant_type=client_credentials&client_id=%s&client_secret=%s&scope=%s",
                URLEncoder.encode(clientId, StandardCharsets.UTF_8),
                URLEncoder.encode(clientSecret, StandardCharsets.UTF_8),
                URLEncoder.encode("internal-read internal-write", StandardCharsets.UTF_8)
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(formData))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {  // Line 100
            JsonObject tokenResponse = JsonParser.parseString(response.body()).getAsJsonObject();
            this.accessToken = tokenResponse.get("access_token").getAsString();  // Line 102
            int expiresIn = tokenResponse.get("expires_in").getAsInt();
            // Refresh token 30 seconds before expiry
            this.tokenExpiry = Instant.now().plusSeconds(expiresIn - 30);  // Line 105
            LOG.debug("Access token obtained successfully");
            return accessToken;
        } else {
            LOG.errorf("Failed to obtain access token: %d - %s", response.statusCode(), response.body());
            return null;
        }
    } finally {
        tokenLock.unlock();
    }
}
```

**Request Details**:
```json
POST http://identity-service:8083/api/internal/keycloak/sync/user
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
Content-Type: application/json

{
  "keycloakUserId": "abc123-def456-ghi789",
  "eventType": "REGISTER"
}
```

---

### STEP 5: KeycloakSyncController (Identity Service)

**File**: `/Users/lazarous.sinkololwe/Documents/Software Projects/personal/ticketing-system/backend/identity-service/src/main/java/com/pml/identity/web/rest/KeycloakSyncController.java`

**Purpose**: Receive sync requests from Keycloak EventListener and delegate to UserSyncService.

**Key Code Sections**:

#### Sync User Endpoint (Lines 52-84)
```java
@PostMapping("/user")
@PreAuthorize("hasAnyAuthority('SCOPE_internal-write', 'ROLE_INTERNAL_SERVICE')")  // Line 53
public Mono<ResponseEntity<SyncResponse>> syncUser(@Valid @RequestBody SyncUserRequest request) {
    log.info("Received sync request for user: {} (event: {})",  // Lines 55-56
            request.getKeycloakUserId(), request.getEventType());

    return userSyncService.syncUserFromKeycloak(request.getKeycloakUserId())  // Line 58
            .map(user -> {
                SyncResponse response = SyncResponse.success(  // Lines 60-63
                        user.getId(),
                        "SYNCED",
                        "User synced successfully from Keycloak"
                );
                return ResponseEntity.ok(response);  // Line 65
            })
            .switchIfEmpty(Mono.just(  // Line 67
                    ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(SyncResponse.error(
                                    request.getKeycloakUserId(),
                                    "User not found in Keycloak"
                            ))
            ))
            .onErrorResume(e -> {  // Line 74
                log.error("Failed to sync user {}: {}", request.getKeycloakUserId(), e.getMessage());
                return Mono.just(
                        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(SyncResponse.error(
                                        request.getKeycloakUserId(),
                                        "Sync failed: " + e.getMessage()
                                ))
                );
            });
}
```

**Security**: Endpoint is restricted to internal service calls via OAuth2 scope `internal-write`.

---

### STEP 6-7: UserSyncServiceImpl - User Creation

**File**: `/Users/lazarous.sinkololwe/Documents/Software Projects/personal/ticketing-system/backend/identity-service/src/main/java/com/pml/identity/service/impl/UserSyncServiceImpl.java`

**Purpose**: Core sync logic - fetch user from Keycloak, extract roles, create User document, and trigger side effects.

#### Main Entry Point (Lines 47-59)
```java
@Override
public Mono<User> syncUserFromKeycloak(String keycloakUserId) {
    log.info("Syncing user from Keycloak: {}", keycloakUserId);  // Line 48

    return keycloakService.findUserById(keycloakUserId)  // Line 50
            .flatMap(optionalUser -> {
                if (optionalUser.isEmpty()) {  // Line 52
                    log.warn("User not found in Keycloak: {}", keycloakUserId);
                    return Mono.empty();
                }

                UserRepresentation keycloakUser = optionalUser.get();  // Line 57
                return syncKeycloakUserToMongo(keycloakUser);  // Line 58
            });
}
```

#### Sync to MongoDB Logic (Lines 171-199)
```java
private Mono<User> syncKeycloakUserToMongo(UserRepresentation keycloakUser) {
    String keycloakUserId = keycloakUser.getId();  // Line 172

    return userRepository.findById(keycloakUserId)  // Line 174
            .flatMap(existingUser -> {
                // Update existing user
                log.debug("Updating existing user: {}", keycloakUserId);  // Line 177
                updateUserFromKeycloak(existingUser, keycloakUser);  // Line 178
                return userRepository.save(existingUser);  // Line 179
            })
            .switchIfEmpty(Mono.defer(() -> {  // Line 181
                // Create new user
                log.info("Creating new user from Keycloak: {}", keycloakUserId);  // Line 183
                User newUser = createUserFromKeycloak(keycloakUser);  // Line 184
                return userRepository.save(newUser)  // Line 185
                        .flatMap(savedUser -> {
                            // Publish event
                            publishUserRegisteredEvent(savedUser);  // Line 188

                            // Auto-create Organization if user has ORGANIZER role
                            if (savedUser.hasRole(UserType.ORGANIZER)) {  // Line 191
                                return createOrganizationForNewUser(savedUser)  // Line 192
                                        .thenReturn(savedUser);  // Line 193
                            }
                            return Mono.just(savedUser);  // Line 195
                        });
            }))
            .doOnSuccess(user -> log.debug("User synced successfully: {}", keycloakUserId));  // Line 198
}
```

#### Create User from Keycloak (Lines 237-265)
```java
private User createUserFromKeycloak(UserRepresentation keycloakUser) {
    Map<String, List<String>> attributes = keycloakUser.getAttributes();  // Line 238

    // Extract roles from Keycloak
    Set<UserType> roles = extractRolesFromKeycloak(keycloakUser, attributes);  // Line 241

    User user = User.builder()  // Line 243
            .id(keycloakUser.getId())  // Use Keycloak ID as MongoDB ID
            .username(keycloakUser.getUsername())  // Line 245
            .email(keycloakUser.getEmail())  // Line 246
            .firstName(keycloakUser.getFirstName() != null ? keycloakUser.getFirstName() : "")  // Line 247
            .lastName(keycloakUser.getLastName() != null ? keycloakUser.getLastName() : "")  // Line 248
            .emailVerified(keycloakUser.isEmailVerified())  // Line 249
            .active(keycloakUser.isEnabled())  // Line 250
            .roles(roles)  // Line 251 - EnumSet<UserType>
            .createdAt(Instant.now())  // Line 252
            .updatedAt(Instant.now())  // Line 253
            .build();

    // Extract custom attributes
    if (attributes != null) {  // Line 258
        extractAttribute(attributes, "phoneNumber").ifPresent(user::setPhoneNumber);  // Line 259
        extractAttribute(attributes, "phoneVerified")  // Line 260
                .ifPresent(v -> user.setPhoneVerified(Boolean.parseBoolean(v)));  // Line 261
    }

    return user;  // Line 264
}
```

#### CRITICAL: Role Extraction Logic (Lines 278-328)

This is the **most important** logic - it extracts roles from multiple sources with priority order:

```java
private Set<UserType> extractRolesFromKeycloak(UserRepresentation keycloakUser, Map<String, List<String>> attributes) {
    Set<UserType> roles = EnumSet.of(UserType.CUSTOMER); // Always include CUSTOMER  // Line 279

    // 1. Try to get roles from Keycloak realm roles (most authoritative)
    if (keycloakUser.getRealmRoles() != null) {  // Line 282
        for (String roleName : keycloakUser.getRealmRoles()) {  // Line 283
            UserType role = parseUserType(roleName);  // Line 284
            if (role != null && role != UserType.CUSTOMER) {  // Line 285
                roles.add(role);  // Line 286
                log.debug("Added role from realmRoles: {}", role);  // Line 287
            }
        }
    }

    // 2. Check attributes for roles
    if (attributes != null) {  // Line 293
        // Check for 'roles' attribute (set by AccountTypeRoleMapper)
        if (attributes.containsKey("roles")) {  // Line 295
            List<String> roleValues = attributes.get("roles");  // Line 296
            for (String roleValue : roleValues) {  // Line 297
                // Handle comma-separated values
                for (String roleName : roleValue.split(",")) {  // Line 299
                    UserType role = parseUserType(roleName.trim());  // Line 300
                    if (role != null && role != UserType.CUSTOMER) {  // Line 301
                        roles.add(role);  // Line 302
                        log.debug("Added role from 'roles' attribute: {}", role);  // Line 303
                    }
                }
            }
        }

        // 3. Check for 'accountType' attribute (from registration form)
        // This is the primary source during registration
        if (attributes.containsKey("accountType")) {  // Line 311
            List<String> accountTypes = attributes.get("accountType");  // Line 312
            for (String accountType : accountTypes) {  // Line 313
                // Handle comma-separated values (just in case)
                for (String typeName : accountType.split(",")) {  // Line 315
                    UserType role = parseUserType(typeName.trim());  // Line 316
                    if (role != null) {  // Line 317
                        roles.add(role);  // Line 318
                        log.debug("Added role from 'accountType' attribute: {}", role);  // Line 319
                    }
                }
            }
        }
    }

    log.info("Extracted roles for user {}: {}", keycloakUser.getUsername(), roles);  // Line 326
    return roles;  // Line 327
}
```

**Data Sources Checked** (in priority order):
1. **Keycloak Realm Roles** (Line 282) - Most authoritative, set by AccountTypeRoleMapper via `user.grantRole()`
2. **`roles` attribute** (Line 295) - Set by AccountTypeRoleMapper as backup
3. **`accountType` attribute** (Line 311) - Original form data

**Parse User Type** (Lines 379-389):
```java
private UserType parseUserType(String value) {
    if (value == null || value.isEmpty()) {
        return UserType.CUSTOMER;
    }
    try {
        return UserType.valueOf(value.toUpperCase());  // Line 384
    } catch (IllegalArgumentException e) {
        log.warn("Unknown user type: {}, defaulting to CUSTOMER", value);
        return UserType.CUSTOMER;
    }
}
```

---

### STEP 8-9: Auto-Create Organization

**Purpose**: Automatically create an Organization document for users who selected ORGANIZER role during registration.

#### Create Organizer Profile (Lines 209-232)
```java
private Mono<Organization> createOrganizationForNewUser(User user) {
    log.info("Auto-creating Organization for new organizer: {} ({})", user.getId(), user.getEmail());  // Line 210

    return organizationRepository.existsByUserId(user.getId())  // Line 212
            .flatMap(exists -> {
                if (exists) {  // Line 214
                    log.debug("Organization already exists for user: {}", user.getId());
                    return organizationRepository.findByUserId(user.getId());  // Line 216
                }

                Organization profile = Organization.builder()  // Line 219
                        .userId(user.getId())  // Line 220 - Link to User document
                        .companyName(null)  // To be filled by user  // Line 221
                        .businessEmail(user.getEmail())  // Pre-fill with user's email  // Line 222
                        .businessPhone(user.getPhoneNumber())  // Pre-fill with user's phone  // Line 223
                        .status(com.pml.identity.domain.enums.OrganizerStatus.DRAFT)  // Line 224
                        .build();

                return organizationRepository.save(profile)  // Line 227
                        .doOnSuccess(p -> log.info(
                                "Created Organization {} for user {} (status: DRAFT)",  // Lines 228-230
                                p.getId(), user.getId()));
            });
}
```

**Organization Fields** (from model):
- `userId`: Keycloak user ID (links to User document)
- `companyName`: `null` (to be filled later by organizer)
- `businessEmail`: Pre-filled from `user.getEmail()`
- `businessPhone`: Pre-filled from `user.getPhoneNumber()`
- `status`: `DRAFT` (requires completion and admin approval)
- `verified`: `false`
- `documentsVerified`: `false`
- `bankVerified`: `false`

**Lifecycle**:
1. **DRAFT** → User completes profile details
2. **PENDING_REVIEW** → User submits for review
3. **APPROVED** → Admin approves (creates Organization entity)
4. **REJECTED** → Admin rejects (reason stored in `rejectionReason`)

---

### STEP 10: Publish UserRegisteredEvent

**Purpose**: Notify other services (Catalog, Booking, Analytics) that a new user has registered.

#### Event Publishing (Lines 394-417)
```java
private void publishUserRegisteredEvent(User user) {
    try {
        // Convert EnumSet<UserType> to Set<String> for the event
        Set<String> roleNames = user.getRoles() != null && !user.getRoles().isEmpty()  // Lines 397-399
                ? user.getRoles().stream().map(Enum::name).collect(Collectors.toSet())
                : Set.of("CUSTOMER");

        UserRegisteredEvent event = new UserRegisteredEvent(  // Lines 401-406
                user.getId(),
                user.getEmail(),
                user.getPhoneNumber(),
                roleNames
        );

        boolean sent = streamBridge.send("userOutput-out-0", event);  // Line 408
        if (sent) {
            log.info("Published UserRegisteredEvent for user: {} with roles: {}", user.getId(), roleNames);  // Line 410
        } else {
            log.warn("Failed to publish UserRegisteredEvent for user: {}", user.getId());
        }
    } catch (Exception e) {
        log.error("Error publishing UserRegisteredEvent for user {}: {}", user.getId(), e.getMessage());  // Line 415
    }
}
```

**Event Structure** (UserRegisteredEvent.java):
```java
@Externalized("user-events::UserRegistered")
public record UserRegisteredEvent(
        String userId,
        String email,
        String phoneNumber,
        Set<String> roles,
        Instant occurredAt
) {
    public UserRegisteredEvent(
            String userId,
            String email,
            String phoneNumber,
            Set<String> roles
    ) {
        this(userId, email, phoneNumber, roles != null ? roles : Set.of("CUSTOMER"), Instant.now());
    }
}
```

**Published to Azure Service Bus**:
- **Topic**: `identity-events` (via Spring Cloud Stream binding `userOutput-out-0`)
- **Message**:
```json
{
  "userId": "abc123-def456-ghi789",
  "email": "john@example.com",
  "phoneNumber": "+260971234567",
  "roles": ["CUSTOMER", "ORGANIZER"],
  "occurredAt": "2024-06-04T10:30:00Z"
}
```

**Potential Subscribers**:
- **Notification Service**: Send welcome email/WhatsApp
- **Analytics Service**: Track registration metrics
- **Catalog Service**: Initialize user preferences
- **Booking Service**: Create customer profile

---

## Final MongoDB State

### users Collection
```json
{
  "_id": "abc123-def456-ghi789",  // Keycloak user ID
  "username": "user_26097XXXX",
  "email": "john@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "phoneNumber": "+260971234567",
  "roles": ["CUSTOMER", "ORGANIZER"],  // EnumSet<UserType> stored as array
  "emailVerified": true,
  "phoneVerified": true,
  "active": true,
  "accountStatus": "ACTIVE",
  "locked": false,
  "identityVerified": false,
  "twoFactorEnabled": false,
  "totalTicketsPurchased": 0,
  "totalEventsAttended": 0,
  "createdAt": "2024-06-04T10:30:00.000Z",
  "updatedAt": "2024-06-04T10:30:00.000Z",
  "lastLoginAt": null,
  "lastActiveAt": null
}
```

### organizer_profiles Collection
```json
{
  "_id": "xyz789-abc123-def456",
  "userId": "abc123-def456-ghi789",  // Link to User document
  "companyName": null,  // To be filled by organizer
  "companyDescription": null,
  "tagline": null,
  "logoUrl": null,
  "bannerUrl": null,
  "website": null,
  "socialLinks": null,
  "taxId": null,
  "businessRegistrationNumber": null,
  "businessType": null,
  "yearEstablished": null,
  "businessPhone": "+260971234567",  // Pre-filled from User
  "businessEmail": "john@example.com",  // Pre-filled from User
  "businessAddress": null,
  "city": null,
  "province": null,
  "country": null,
  "postalCode": null,
  "defaultBankAccountId": null,
  "commissionRate": 0.05,  // Default 5%
  "payoutSchedule": "WEEKLY",  // Default
  "status": "DRAFT",  // Requires completion and approval
  "rejectionReason": null,
  "verified": false,
  "verifiedAt": null,
  "verifiedBy": null,
  "documentsVerified": false,
  "bankVerified": false,
  "reviewedBy": null,
  "reviewedAt": null,
  "adminNotes": null,
  "submittedAt": null,
  "approvedAt": null,
  "createdAt": "2024-06-04T10:30:00.000Z",
  "updatedAt": "2024-06-04T10:30:00.000Z"
}
```

---

## Data Transformation Summary

| Stage | Component | Input Data | Output Data |
|-------|-----------|------------|-------------|
| **Step 1** | Registration Form | `user.attributes.accountType: ["CUSTOMER", "ORGANIZER"]` | Form submission to Keycloak |
| **Step 2** | AccountTypeRoleMapper | Form data | Keycloak user with:<br>- Realm roles: `CUSTOMER`, `ORGANIZER`<br>- Attributes: `accountType`, `roles` |
| **Step 3** | UserSyncEventListener | Keycloak REGISTER event | HTTP request to Identity Service |
| **Step 4** | IdentityServiceClient | `userId`, `eventType` | POST `/api/internal/keycloak/sync/user` |
| **Step 5** | KeycloakSyncController | REST request | Calls `UserSyncService.syncUserFromKeycloak()` |
| **Step 6-7** | UserSyncServiceImpl | Keycloak UserRepresentation | MongoDB User document with `EnumSet<UserType>` roles |
| **Step 8-9** | Auto-create Profile | User with ORGANIZER role | Organization document (status: DRAFT) |
| **Step 10** | Event Publisher | User document | Azure Service Bus event with `roles: ["CUSTOMER", "ORGANIZER"]` |

---

## Key Architecture Insights

1. **Three Role Representations**:
   - **Keycloak Realm Roles**: The authoritative source (JWT `realm_access.roles`)
   - **Keycloak User Attributes**: Backup storage (`accountType`, `roles` attributes)
   - **MongoDB User Document**: Denormalized cache (`EnumSet<UserType> roles`)

2. **Idempotency**: 
   - Line 212: `existsByUserId()` prevents duplicate Organization creation
   - Line 174: `findById()` checks for existing User before creating new

3. **Error Handling**:
   - Line 86: Event listener catches exceptions to prevent auth flow disruption
   - Line 74: Controller returns proper HTTP status codes
   - Lines 414-416: Event publishing failures are logged but don't fail the sync

4. **Multi-Role Support**:
   - Users can have **multiple roles** (e.g., `CUSTOMER + ORGANIZER`)
   - `CUSTOMER` is **always included** as the base role (Line 279)
   - Roles are stored as `EnumSet<UserType>` for efficient set operations

5. **Reactive Programming**:
   - All MongoDB operations return `Mono<T>` or `Flux<T>`
   - Line 186: `flatMap()` chains async operations sequentially
   - Line 192: `thenReturn()` ignores intermediate result but waits for completion

6. **Separation of Concerns**:
   - **User**: Personal identity and platform roles
   - **Organization**: Business-specific data (KYB)
   - **Organization**: Public business entity (created after approval)

---

## File Paths Reference

```
backend/keycloak-extensions/
├── src/main/java/com/pml/keycloak/
│   ├── registration/
│   │   └── AccountTypeRoleMapper.java ← STEP 2
│   ├── eventlistener/
│   │   └── UserSyncEventListener.java ← STEP 3
│   └── client/
│       └── IdentityServiceClient.java ← STEP 4

backend/identity-service/
├── src/main/java/com/pml/identity/
│   ├── web/rest/
│   │   └── KeycloakSyncController.java ← STEP 5
│   ├── service/impl/
│   │   └── UserSyncServiceImpl.java ← STEPS 6-10
│   ├── domain/model/
│   │   ├── User.java
│   │   └── Organization.java
│   └── event/domain/
│       └── UserRegisteredEvent.java
```

---

This completes the end-to-end technical trace of the registration flow. The system ensures that Keycloak (source of truth for authentication) and MongoDB (source of truth for business data) stay synchronized through a robust event-driven architecture.
