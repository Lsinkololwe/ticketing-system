# Event Ticketing Platform - Keycloak Integration Implementation Plan

## Document Metadata
- **Version:** 1.0
- **Created:** 2026-02-18
- **Target Audience:** Development Team, Technical Leads
- **Prerequisites:** Keycloak 26.4.0, Spring Boot 3.5.4, Expo SDK 52+

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Architecture Overview](#architecture-overview)
3. [User Types & Roles](#user-types--roles)
4. [Phase 1: Keycloak Configuration](#phase-1-keycloak-configuration)
5. [Phase 2: Mobile App Updates](#phase-2-mobile-app-updates)
6. [Phase 3: Backend Updates](#phase-3-backend-updates)
7. [Phase 4: API Design](#phase-4-api-design-for-adminorganizerfinance)
8. [Phase 5: Cleanup](#phase-5-cleanup---remove-redundant-code)
9. [Phase 6: Testing](#phase-6-testing-checklist)

---

## Executive Summary

This plan implements Keycloak as the **single source of truth** for user identity management across mobile and web applications. The architecture eliminates data duplication by storing all identity data in Keycloak while retaining business-specific data in MongoDB.

### Key Principles

1. **Direct Authentication**: Mobile/web apps authenticate directly with Keycloak using OAuth2 PKCE
2. **No Password Handling**: Backend never sees user passwords - Keycloak handles all credential management
3. **Single Source of Truth**: All identity data (name, email, phone) stored in Keycloak
4. **Business Data Separation**: MongoDB stores only business-specific data (organizer profiles, bank accounts, escrow)

### Authentication Flow

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         AUTHENTICATION FLOW                              │
│                    (Login, Registration, Token Refresh)                  │
└─────────────────────────────────────────────────────────────────────────┘

  ┌──────────────┐         Direct OAuth2 PKCE          ┌──────────────────┐
  │  Mobile App  │ ◄──────────────────────────────────►│    Keycloak      │
  │  (Expo/RN)   │    Authorization Code + Tokens      │  (IdP/Auth Server)│
  └──────────────┘                                      └──────────────────┘
                                                                  │
  ┌──────────────┐         Direct OAuth2 PKCE                     │
  │   Web App    │ ◄──────────────────────────────────────────────┘
  │   (React)    │    Authorization Code + Tokens
  └──────────────┘


┌─────────────────────────────────────────────────────────────────────────┐
│                           API CALLS FLOW                                 │
│                    (Business Operations with JWT)                        │
└─────────────────────────────────────────────────────────────────────────┘

  ┌──────────────┐                                     ┌──────────────────┐
  │  Mobile App  │───┐                                 │    Keycloak      │
  └──────────────┘   │      Bearer Token               │  (Token Issuer)  │
                     │         │                       └────────┬─────────┘
  ┌──────────────┐   │         ▼                                │
  │   Web App    │───┼──► ┌─────────────┐  Validate Token       │
  └──────────────┘   │    │ API Gateway │◄──────────────────────┘
                     │    │             │  (JWKS/Introspection)
                     │    └──────┬──────┘
                     │           │
                     │           ▼
                     │    ┌─────────────────────────────────────┐
                     │    │         Microservices               │
                     │    │  ┌─────────┬─────────┬─────────┐   │
                     │    │  │Catalog  │ Booking │Identity │   │
                     │    │  │Service  │ Service │ Service │   │
                     │    │  └─────────┴─────────┴─────────┘   │
                     │    └─────────────────────────────────────┘
```

---

## Architecture Overview

### Data Storage Strategy

| Data Type | Storage Location | Rationale |
|-----------|------------------|-----------|
| User Identity (name, email, phone) | Keycloak | Single source of truth, OAuth2 standard |
| Authentication Credentials | Keycloak | Security best practice |
| User Roles & Permissions | Keycloak | Centralized RBAC |
| Organizer Business Profile | MongoDB | Business-specific, not identity |
| Bank Accounts | MongoDB | Financial/business data |
| Escrow Accounts | MongoDB | Platform business logic |
| Payout Requests | MongoDB | Transaction data |

### Service Responsibilities

| Service | Responsibility |
|---------|---------------|
| **Keycloak** | Authentication, Authorization, User Identity, Password Management |
| **Identity Service** | Business profiles, Keycloak Admin API proxy, User data aggregation |
| **Catalog Service** | Events, Ticket Categories |
| **Booking Service** | Tickets, Orders, Payments |

---

## User Types & Roles

### Role Hierarchy

```
SUPER_ADMIN
    └── ADMIN
            ├── CUSTOMER
            ├── ORGANIZER (includes CUSTOMER)
            ├── SCANNER
            └── FINANCE
```

### Role Definitions

| Role | Description | Registration Path | Profile Data |
|------|-------------|-------------------|--------------|
| **CUSTOMER** | Event attendees, ticket buyers | Self-registration via Keycloak | Keycloak only |
| **ORGANIZER** | Event creators and managers | Self-registration → Admin approval | Keycloak + MongoDB (business) |
| **SCANNER** | Ticket validators at events | Admin-created only | Keycloak only |
| **FINANCE** | Financial operations team | Admin-created only | Keycloak only |
| **ADMIN** | Platform administrators | Admin-created only | Keycloak only |
| **SUPER_ADMIN** | Super administrators | Initial setup only | Keycloak only |

### Default Role Assignment

- New users registering via Keycloak receive `CUSTOMER` role by default
- Users selecting "Organizer" during registration receive `CUSTOMER` initially, then `ORGANIZER` after admin approval

---

## Phase 1: Keycloak Configuration

### 1.1 User Profile Schema

Configure Keycloak User Profile to enforce required attributes during registration.

**Location:** Keycloak Admin Console → Realm Settings → User Profile

```json
{
  "attributes": [
    {
      "name": "username",
      "displayName": "Username",
      "validations": {
        "length": { "min": 3, "max": 255 },
        "username-prohibited-characters": {},
        "up-username-not-idn-homograph": {}
      },
      "required": { "roles": ["user"] },
      "permissions": {
        "view": ["admin", "user"],
        "edit": ["admin", "user"]
      }
    },
    {
      "name": "email",
      "displayName": "Email",
      "validations": {
        "email": {},
        "length": { "max": 255 }
      },
      "required": { "roles": ["user"] },
      "permissions": {
        "view": ["admin", "user"],
        "edit": ["admin", "user"]
      }
    },
    {
      "name": "firstName",
      "displayName": "First name",
      "validations": {
        "length": { "max": 255 },
        "person-name-prohibited-characters": {}
      },
      "required": { "roles": ["user"] },
      "permissions": {
        "view": ["admin", "user"],
        "edit": ["admin", "user"]
      }
    },
    {
      "name": "lastName",
      "displayName": "Last name",
      "validations": {
        "length": { "max": 255 },
        "person-name-prohibited-characters": {}
      },
      "required": { "roles": ["user"] },
      "permissions": {
        "view": ["admin", "user"],
        "edit": ["admin", "user"]
      }
    },
    {
      "name": "phoneNumber",
      "displayName": "Phone Number",
      "validations": {
        "pattern": {
          "pattern": "^\\+?[0-9]{10,15}$",
          "error-message": "Invalid phone number format"
        }
      },
      "required": { "roles": ["user"] },
      "permissions": {
        "view": ["admin", "user"],
        "edit": ["admin", "user"]
      },
      "annotations": {
        "inputType": "tel"
      }
    },
    {
      "name": "userType",
      "displayName": "Account Type",
      "validations": {
        "options": {
          "options": ["CUSTOMER", "ORGANIZER"]
        }
      },
      "required": {},
      "permissions": {
        "view": ["admin", "user"],
        "edit": ["admin"]
      },
      "annotations": {
        "inputType": "select",
        "inputOptionLabels": {
          "CUSTOMER": "Ticket Buyer",
          "ORGANIZER": "Event Organizer"
        }
      }
    }
  ],
  "groups": [
    {
      "name": "user-metadata",
      "displayHeader": "User Metadata",
      "displayDescription": "User type and internal identifiers"
    }
  ],
  "unmanagedAttributePolicy": "ADMIN_EDIT"
}
```

### 1.2 Mobile Client Configuration

**Client ID:** `event-ticketing-mobile`

```json
{
  "clientId": "event-ticketing-mobile",
  "name": "Event Ticketing Mobile Application",
  "description": "Mobile app (iOS/Android) - Public client with PKCE",
  "enabled": true,
  "publicClient": true,
  "standardFlowEnabled": true,
  "implicitFlowEnabled": false,
  "directAccessGrantsEnabled": false,
  "serviceAccountsEnabled": false,
  "redirectUris": [
    "com.pml.ticketing://callback",
    "com.pml.ticketing://auth",
    "com.pml.ticketing://*",
    "exp://localhost:8081/*",
    "exp://192.168.*.*:8081/*"
  ],
  "webOrigins": [],
  "protocol": "openid-connect",
  "attributes": {
    "pkce.code.challenge.method": "S256",
    "post.logout.redirect.uris": "com.pml.ticketing://logout##com.pml.ticketing://",
    "display.on.consent.screen": "false"
  },
  "fullScopeAllowed": false,
  "defaultClientScopes": [
    "openid",
    "profile",
    "email",
    "phone",
    "roles",
    "custom-claims"
  ],
  "optionalClientScopes": [
    "events-read",
    "tickets-purchase",
    "bookings-read"
  ]
}
```

### 1.3 Admin Portal Client Configuration

**Client ID:** `event-ticketing-admin`

```json
{
  "clientId": "event-ticketing-admin",
  "name": "Event Ticketing Admin Portal",
  "description": "Admin, Finance, and Scanner web portal",
  "enabled": true,
  "clientAuthenticatorType": "client-secret",
  "secret": "${ADMIN_PORTAL_CLIENT_SECRET}",
  "redirectUris": [
    "http://localhost:3001/*",
    "https://admin.yourdomain.com/*"
  ],
  "webOrigins": [
    "http://localhost:3001",
    "https://admin.yourdomain.com"
  ],
  "standardFlowEnabled": true,
  "implicitFlowEnabled": false,
  "directAccessGrantsEnabled": false,
  "serviceAccountsEnabled": false,
  "publicClient": false,
  "frontchannelLogout": true,
  "protocol": "openid-connect",
  "attributes": {
    "post.logout.redirect.uris": "http://localhost:3001/*##https://admin.yourdomain.com/*",
    "pkce.code.challenge.method": "S256",
    "backchannel.logout.session.required": "true"
  },
  "fullScopeAllowed": false,
  "defaultClientScopes": [
    "openid",
    "profile",
    "email",
    "roles",
    "custom-claims"
  ],
  "optionalClientScopes": [
    "phone",
    "events-read",
    "events-write",
    "tickets-purchase",
    "bookings-read",
    "finance-read",
    "users-read"
  ]
}
```

### 1.4 Custom Token Mappers

Ensure the following protocol mappers are configured for custom claims:

| Mapper Name | Mapper Type | User Attribute | Token Claim Name |
|-------------|-------------|----------------|------------------|
| userId | User Attribute | userId | userId |
| userType | User Attribute | userType | userType |
| phoneNumber | User Attribute | phoneNumber | phone_number |
| phoneVerified | User Attribute | phoneVerified | phone_number_verified |
| authorities | Realm Role | - | authorities |

---

## Phase 2: Mobile App Updates

### 2.1 File Changes Summary

| File | Action | Description |
|------|--------|-------------|
| `src/config/keycloak.config.ts` | UPDATE | Add registration configuration |
| `src/services/keycloak-auth.service.ts` | UPDATE | Add register() method |
| `src/services/auth.service.ts` | UPDATE | Simplify, delegate to Keycloak |
| `src/providers/AuthProvider.tsx` | UPDATE | Add register function |
| `app/(auth)/register.tsx` | REPLACE | Remove form, add user type selection |
| `app/(auth)/pending-approval.tsx` | CREATE | New screen for pending organizers |

### 2.2 Registration Flow

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  Select Type    │────►│  Open Keycloak  │────►│  Registration   │
│  (Customer or   │     │  Registration   │     │  Complete       │
│   Organizer)    │     │  Page           │     │                 │
└─────────────────┘     └─────────────────┘     └────────┬────────┘
                                                         │
                        ┌────────────────────────────────┼────────────────────────────────┐
                        │                                │                                │
                        ▼                                ▼                                ▼
               ┌─────────────────┐              ┌─────────────────┐              ┌─────────────────┐
               │  Email Verify   │              │  Pending        │              │  Home Screen    │
               │  Required       │              │  Approval       │              │  (Customer)     │
               │                 │              │  (Organizer)    │              │                 │
               └─────────────────┘              └─────────────────┘              └─────────────────┘
```

### 2.3 Key Implementation Details

**Registration uses `kc_action=register` parameter:**
```typescript
const extraParams: Record<string, string> = {
  kc_action: 'register', // Opens registration instead of login
};

const request = new AuthSession.AuthRequest({
  clientId: KEYCLOAK_CONFIG.clientId,
  scopes: KEYCLOAK_CONFIG.registrationScopes,
  redirectUri: KEYCLOAK_CONFIG.redirectUri,
  usePKCE: true,
  responseType: AuthSession.ResponseType.Code,
  extraParams,
});
```

---

## Phase 3: Backend Updates

### 3.1 Service Layer Changes

| Service | Changes |
|---------|---------|
| `KeycloakService` | Add methods: `findUserById`, `getUserRoles`, `updateUserAttribute`, `addRoleToUser` |
| `UserDataService` | NEW - Aggregates Keycloak identity + MongoDB business data |
| `UserService` | Refactor for admin operations only |
| `AuthService` | Deprecate registration endpoint |

### 3.2 New Entities

**OrganizerProfile (MongoDB)**
```java
@Document(collection = "organizer_profiles")
public class OrganizerProfile {
    @Id
    private String id;

    @Indexed(unique = true)
    private String keycloakUserId;

    private String companyName;
    private String companyDescription;
    private String logoUrl;
    private String website;
    private SocialLinks socialLinks;
    private String taxId;
    private String businessRegistrationNumber;
    private List<BankAccount> bankAccounts;
    private String defaultBankAccountId;
    private Double commissionRate = 0.05;
    private boolean verified = false;
    private Instant verifiedAt;
    private String verifiedBy;
    private OrganizerStatus status = OrganizerStatus.PENDING;
    private String rejectionReason;
    private Instant createdAt;
    private Instant updatedAt;
}
```

### 3.3 Keycloak Admin API Usage

The Identity Service uses Keycloak Admin API for:

| Operation | API Endpoint | Method |
|-----------|--------------|--------|
| Get user by ID | `/admin/realms/{realm}/users/{id}` | GET |
| Update user | `/admin/realms/{realm}/users/{id}` | PUT |
| Add role to user | `/admin/realms/{realm}/users/{id}/role-mappings/realm` | POST |
| Remove role | `/admin/realms/{realm}/users/{id}/role-mappings/realm` | DELETE |
| Search users | `/admin/realms/{realm}/users?search=` | GET |
| Get users by role | `/admin/realms/{realm}/roles/{role}/users` | GET |

---

## Phase 4: API Design for Admin/Organizer/Finance

### 4.1 Admin API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/admin/users` | GET | List all users (paginated) |
| `/api/v1/admin/users/{userId}` | GET | Get user details |
| `/api/v1/admin/users/{userId}/roles` | PUT | Update user roles |
| `/api/v1/admin/users/{userId}/disable` | POST | Disable user account |
| `/api/v1/admin/users/{userId}/enable` | POST | Enable user account |
| `/api/v1/admin/organizers/pending` | GET | List pending organizer applications |
| `/api/v1/admin/organizers/{userId}/approve` | POST | Approve organizer |
| `/api/v1/admin/organizers/{userId}/reject` | POST | Reject organizer |
| `/api/v1/admin/events/pending` | GET | List events pending approval |
| `/api/v1/admin/events/{eventId}/approve` | POST | Approve event |
| `/api/v1/admin/events/{eventId}/reject` | POST | Reject event |
| `/api/v1/admin/refunds/pending` | GET | List pending refunds |
| `/api/v1/admin/refunds/{refundId}/approve` | POST | Approve refund |
| `/api/v1/admin/refunds/{refundId}/reject` | POST | Reject refund |
| `/api/v1/admin/dashboard/stats` | GET | Dashboard statistics |

### 4.2 Organizer API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/organizer/profile` | GET | Get organizer profile |
| `/api/v1/organizer/profile` | PUT | Update organizer profile |
| `/api/v1/organizer/profile/logo` | POST | Upload company logo |
| `/api/v1/organizer/bank-accounts` | GET | List bank accounts |
| `/api/v1/organizer/bank-accounts` | POST | Add bank account |
| `/api/v1/organizer/bank-accounts/{id}` | DELETE | Remove bank account |
| `/api/v1/organizer/bank-accounts/{id}/set-default` | POST | Set default account |
| `/api/v1/organizer/earnings` | GET | Get earnings summary |
| `/api/v1/organizer/earnings/by-event` | GET | Earnings by event |
| `/api/v1/organizer/payouts/request` | POST | Request payout |
| `/api/v1/organizer/payouts` | GET | List payout history |
| `/api/v1/organizer/dashboard/stats` | GET | Dashboard statistics |

### 4.3 Finance API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/finance/payouts/pending` | GET | List pending payouts |
| `/api/v1/finance/payouts/{id}/approve` | POST | Approve payout |
| `/api/v1/finance/payouts/{id}/reject` | POST | Reject payout |
| `/api/v1/finance/payouts/{id}/process` | POST | Mark as processed |
| `/api/v1/finance/payouts/failed` | GET | List failed payouts |
| `/api/v1/finance/payouts/{id}/retry` | POST | Retry failed payout |
| `/api/v1/finance/escrow` | GET | List escrow accounts |
| `/api/v1/finance/escrow/{eventId}` | GET | Get escrow details |
| `/api/v1/finance/escrow/{eventId}/transactions` | GET | Escrow transactions |
| `/api/v1/finance/reports/revenue` | GET | Revenue report |
| `/api/v1/finance/reports/commissions` | GET | Commissions report |
| `/api/v1/finance/reports/payouts` | GET | Payouts report |
| `/api/v1/finance/reports/refunds` | GET | Refunds report |
| `/api/v1/finance/dashboard/stats` | GET | Dashboard statistics |
| `/api/v1/finance/dashboard/platform-balance` | GET | Platform balances |

---

## Phase 5: Cleanup - Remove Redundant Code

### 5.1 Mobile App Cleanup

| File | Action |
|------|--------|
| `src/utils/validation.ts` | DELETE (if only used for registration form) |
| `app/(auth)/register.tsx` | REPLACE (remove Formik, use simplified version) |

### 5.2 Backend Cleanup

| File/Code | Action |
|-----------|--------|
| `AuthController.register()` | DEPRECATE (return 410 Gone) |
| `AuthServiceImpl.register()` | DEPRECATE |
| `RegisterRequest.java` | DEPRECATE |

### 5.3 MongoDB Collections

| Collection | Action | Timeline |
|------------|--------|----------|
| `users` | Archive → DELETE | After migration verified |
| `organizer_profiles` | KEEP | Business data |
| `bank_accounts` | KEEP | Business data |
| `payout_requests` | KEEP | Business data |
| `escrow_accounts` | KEEP | Financial data |

---

## Phase 6: Testing Checklist

### 6.1 Mobile App Tests

- [ ] Customer registration via Keycloak
- [ ] Organizer registration via Keycloak
- [ ] Login (existing user)
- [ ] Token refresh (silent)
- [ ] Logout (ends Keycloak session)
- [ ] Session expiry detection
- [ ] Offline handling

### 6.2 Backend API Tests

- [ ] Admin creates SCANNER user
- [ ] Admin creates FINANCE user
- [ ] Admin approves organizer
- [ ] Admin rejects organizer
- [ ] Get user profile (Keycloak + MongoDB)
- [ ] Update organizer profile
- [ ] Finance approves payout

### 6.3 Integration Tests

- [ ] Full customer journey: Register → Verify → Login → Browse → Purchase
- [ ] Full organizer journey: Register → Verify → Approval → Profile → Create Event
- [ ] Token validation across all services
- [ ] Role-based access control
- [ ] Keycloak event webhook handling

### 6.4 Security Tests

- [ ] PKCE flow verification
- [ ] Token signature validation
- [ ] Role escalation prevention
- [ ] Rate limiting on auth endpoints

---

## Appendix A: Environment Variables

### Mobile App (.env)

```env
EXPO_PUBLIC_KEYCLOAK_URL=https://auth.yourdomain.com
EXPO_PUBLIC_KEYCLOAK_REALM=event-ticketing
EXPO_PUBLIC_KEYCLOAK_CLIENT_ID=event-ticketing-mobile
EXPO_PUBLIC_API_URL=https://api.yourdomain.com
```

### Backend (application.yml)

```yaml
keycloak:
  server-url: ${KEYCLOAK_SERVER_URL:http://localhost:8080}
  realm: ${KEYCLOAK_REALM:event-ticketing}
  admin-realm: ${KEYCLOAK_ADMIN_REALM:master}
  admin-username: ${KEYCLOAK_ADMIN_USERNAME:admin}
  admin-password: ${KEYCLOAK_ADMIN_PASSWORD:admin}
  client-id: ${KEYCLOAK_CLIENT_ID:identity-service}
  client-secret: ${KEYCLOAK_CLIENT_SECRET:secret}
```

---

## Appendix B: Migration Checklist

### Pre-Migration

- [ ] Backup existing MongoDB `users` collection
- [ ] Document current user count and roles
- [ ] Test Keycloak configuration in staging
- [ ] Verify all clients configured correctly

### During Migration

- [ ] Deploy Keycloak configuration changes
- [ ] Deploy backend updates
- [ ] Deploy mobile app update
- [ ] Monitor error rates

### Post-Migration

- [ ] Verify user login success rates
- [ ] Check registration flow
- [ ] Validate token refresh
- [ ] Monitor Keycloak metrics
- [ ] Archive old MongoDB data

---

## Appendix C: Rollback Plan

If critical issues arise:

1. **Mobile App**: Revert to previous version in app stores
2. **Backend**: Revert deployment, re-enable old registration endpoint
3. **Keycloak**: Restore previous realm configuration from backup
4. **Data**: MongoDB users collection remains unchanged during migration

---

**Document End**

*This document should be reviewed by: Solutions Architect, Lead Backend Engineer, Lead Mobile Engineer, DevOps Lead*
