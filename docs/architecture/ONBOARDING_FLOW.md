# User Onboarding Flow

This document describes the progressive onboarding flow for the Event Ticketing System, from anonymous visitor to organization owner.

## Table of Contents

1. [Progressive Onboarding Overview](#progressive-onboarding-overview)
2. [Phase 1: Anonymous Browsing](#phase-1-anonymous-browsing)
3. [Phase 2: Customer Registration](#phase-2-customer-registration)
4. [Phase 3: Organizer Application](#phase-3-organizer-application)
5. [Phase 4: Organizer Approval](#phase-4-organizer-approval)
6. [Phase 5: Team Growth](#phase-5-team-growth)
7. [Team Invitation Flow](#team-invitation-flow)

---

## Progressive Onboarding Overview

The system uses **progressive onboarding** where users start as customers and can optionally upgrade to organizers:

```
┌──────────────┐    ┌──────────────┐    ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│   Visitor    │───▶│   Customer   │───▶│  Applicant   │───▶│  Organizer   │───▶│  Team Owner  │
│  (Anonymous) │    │ (Registered) │    │  (Pending)   │    │  (Approved)  │    │  (w/ Team)   │
└──────────────┘    └──────────────┘    └──────────────┘    └──────────────┘    └──────────────┘
     │                    │                    │                    │                    │
     │                    │                    │                    │                    │
     ▼                    ▼                    ▼                    ▼                    ▼
 Browse only         Buy tickets         Fill profile        Create events       Manage team
                                         Upload docs         Publish events      Invite members
                                         Draft events        Accept payments     Assign roles
```

---

## Phase 1: Anonymous Browsing

### Capabilities
- Browse public events
- View event details
- View venue information
- Search events by category, date, location

### Cannot Do
- Purchase tickets
- Save favorites
- Set reminders
- View order history

### Technical State
- No Keycloak session
- No MongoDB User document
- Frontend uses unauthenticated GraphQL queries

---

## Phase 2: Customer Registration

### Trigger
User clicks "Buy Ticket" or "Sign Up"

### Flow
```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                        CUSTOMER REGISTRATION FLOW                               │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│   Mobile App/Web                Keycloak                    Identity Service    │
│       │                           │                              │              │
│       │  1. Initiate login        │                              │              │
│       │────────────────────────▶│                              │              │
│       │                           │                              │              │
│       │  2. Show phone form       │                              │              │
│       │◀────────────────────────│                              │              │
│       │                           │                              │              │
│       │  3. Submit phone (+260...) │                              │              │
│       │────────────────────────▶│                              │              │
│       │                           │                              │              │
│       │                           │  4. Request OTP              │              │
│       │                           │─────────────────────────────▶│              │
│       │                           │                              │              │
│       │                           │                    5. Generate OTP          │
│       │                           │                    6. Store in Redis (5min) │
│       │                           │                    7. Send via WhatsApp/SMS │
│       │                           │                              │              │
│       │                           │  8. OTP sent                 │              │
│       │                           │◀─────────────────────────────│              │
│       │                           │                              │              │
│       │  9. Show OTP form         │                              │              │
│       │◀────────────────────────│                              │              │
│       │                           │                              │              │
│       │  10. Submit OTP           │                              │              │
│       │────────────────────────▶│                              │              │
│       │                           │                              │              │
│       │                           │  11. Verify OTP              │              │
│       │                           │─────────────────────────────▶│              │
│       │                           │                              │              │
│       │                           │  12. Valid=true              │              │
│       │                           │◀─────────────────────────────│              │
│       │                           │                              │              │
│       │                           │  13. Find/Create Keycloak user              │
│       │                           │  14. Grant CUSTOMER role                    │
│       │                           │  15. Issue JWT tokens                       │
│       │                           │                              │              │
│       │  16. Return tokens        │                              │              │
│       │◀────────────────────────│                              │              │
│       │                           │                              │              │
│       │                           │  17. REGISTER event          │              │
│       │                           │─────────────────────────────▶│              │
│       │                           │                              │              │
│       │                           │                    18. Create MongoDB User  │
│       │                           │                    19. Publish UserRegisteredEvent
│       │                           │                              │              │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Created Entities
1. **Keycloak User**
   - id: UUID (auto-generated)
   - username: "user_260971234567"
   - phone_number: "+260971234567"
   - phone_verified: true
   - realm_roles: ["CUSTOMER"]

2. **MongoDB User**
   - id: Keycloak user ID
   - username: "user_260971234567"
   - phoneNumber: "+260971234567"
   - phoneVerified: true
   - userType: CUSTOMER
   - accountStatus: ACTIVE

### Capabilities Gained
- Purchase tickets
- View order history
- Save favorites
- Set event reminders
- Transfer tickets

---

## Phase 3: Organizer Application

### Trigger
Customer clicks "Become an Organizer" or "Create Event"

### Flow
```graphql
mutation ApplyToBeOrganizer {
  applyToBeOrganizer {
    id
    status  # DRAFT
    companyName
  }
}
```

### Steps
1. **Create OrganizerProfile (DRAFT)**
   - System creates OrganizerProfile with status: DRAFT
   - User sees onboarding wizard

2. **Complete Business Profile**
   ```graphql
   mutation UpdateOrganizerProfile($input: UpdateOrganizerProfileInput!) {
     updateOrganizerProfile(input: $input) {
       companyName
       taxId
       businessPhone
       businessEmail
       status  # Still DRAFT
     }
   }
   ```

3. **Upload Verification Documents**
   ```graphql
   mutation UploadVerificationDocument(
     $documentType: String!
     $documentUrl: String!
     $fileName: String!
   ) {
     uploadVerificationDocument(
       documentType: $documentType
       documentUrl: $documentUrl
       fileName: $fileName
     ) {
       id
       status  # PENDING
     }
   }
   ```

4. **Submit for Review**
   ```graphql
   mutation SubmitOrganizerApplication {
     submitOrganizerApplication {
       status  # PENDING_REVIEW
       submittedAt
     }
   }
   ```

### Pre-Approval Capabilities
| Allowed | Not Allowed |
|---------|-------------|
| Complete organizer profile | Publish events |
| Upload verification documents | Accept payments |
| Create events in DRAFT mode | Invite team members |
| Configure ticket types (drafts) | Request payouts |
| Preview event appearance | Access financial reports |

---

## Phase 4: Organizer Approval

### Admin Review
```graphql
mutation ApproveOrganizer($profileId: ID!) {
  approveOrganizer(profileId: $profileId) {
    status  # APPROVED
    organization {
      id
      name
      slug
    }
  }
}
```

### Automatic Actions on Approval
```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                         ORGANIZER APPROVAL WORKFLOW                             │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│   1. Update OrganizerProfile                                                    │
│      ├── status: PENDING_REVIEW → APPROVED                                      │
│      ├── approvedAt: now()                                                      │
│      └── reviewedBy: admin.id                                                   │
│                                                                                 │
│   2. Create Organization                                                        │
│      ├── name: profile.companyName                                              │
│      ├── slug: slugify(companyName)                                             │
│      ├── organizerProfileId: profile.id                                         │
│      ├── ownerId: profile.userId                                                │
│      └── status: ACTIVE                                                         │
│                                                                                 │
│   3. Create OrganizationMember (OWNER)                                          │
│      ├── userId: profile.userId                                                 │
│      ├── organizationId: organization.id                                        │
│      ├── role: OWNER                                                            │
│      └── joinedAt: now()                                                        │
│                                                                                 │
│   4. Update User                                                                │
│      ├── userType: CUSTOMER → ORGANIZER                                         │
│      └── primaryOrganizationId: organization.id                                 │
│                                                                                 │
│   5. Keycloak Updates                                                           │
│      ├── Grant ORGANIZER realm role                                             │
│      ├── Create group: /organizations/{slug}                                    │
│      ├── Create subgroups: /owners, /admins, /managers, etc.                    │
│      └── Add user to /organizations/{slug}/owners                               │
│                                                                                 │
│   6. Publish Events                                                             │
│      ├── OrganizerApprovedEvent → Azure Service Bus                             │
│      └── Notify user via email/push                                             │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Organizer Capabilities
- Create and publish events
- Set ticket prices and quantities
- Accept payments (mobile money)
- View sales dashboard
- Request payouts
- Invite team members

---

## Phase 5: Team Growth

### Invite Team Members
```graphql
mutation InviteTeamMember($input: InviteMemberInput!) {
  inviteTeamMember(input: $input) {
    id
    email
    proposedRole
    status  # PENDING
    expiresAt
    invitationToken
  }
}

input InviteMemberInput {
  email: String!
  role: OrganizationRole!
  message: String
  eventAccessGrants: [EventAccessInput!]
}
```

### Role Hierarchy
```
OWNER (1 per org)
  │
  └── ADMIN (Full access except ownership)
        │
        ├── MANAGER (Events, limited finance)
        │
        ├── MARKETER (Analytics, promotions)
        │
        └── CONTRIBUTOR (View-only, check-in)
```

---

## Team Invitation Flow

### Question: Must invitee be an existing user?

**NO** - Invitees do not need to be existing users. The system supports both:

### Scenario A: New User Invited
```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    INVITATION FLOW - NEW USER                                   │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│   1. Owner invites john@example.com as MANAGER                                  │
│      └── TeamInvitation created with email, no userId                           │
│                                                                                 │
│   2. Email sent with link: /accept-invitation?token=abc123                      │
│                                                                                 │
│   3. John clicks link, not logged in                                            │
│      └── Redirected to registration with invitation context                     │
│                                                                                 │
│   4. John registers (Phone OTP flow)                                            │
│      └── Creates Keycloak User + MongoDB User                                   │
│                                                                                 │
│   5. After registration, auto-accept invitation                                 │
│      ├── Create OrganizationMember (role: MANAGER)                              │
│      ├── Add to Keycloak group: /organizations/{slug}/managers                  │
│      └── Update invitation status: ACCEPTED                                     │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Scenario B: Existing User Invited
```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    INVITATION FLOW - EXISTING USER                              │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│   1. Owner invites jane@example.com as ADMIN                                    │
│      └── TeamInvitation created with email                                      │
│                                                                                 │
│   2. Jane is already a CUSTOMER on the platform                                 │
│                                                                                 │
│   3. Email sent with link: /accept-invitation?token=xyz789                      │
│                                                                                 │
│   4. Jane clicks link, already logged in                                        │
│      └── Show invitation details with Accept/Decline buttons                    │
│                                                                                 │
│   5. Jane clicks Accept                                                         │
│      ├── Create OrganizationMember (role: ADMIN)                                │
│      ├── Add to Keycloak group: /organizations/{slug}/admins                    │
│      └── Update invitation status: ACCEPTED                                     │
│                                                                                 │
│   Note: Jane remains a CUSTOMER in userType (platform role)                     │
│         Her organization access is through OrganizationMember                   │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Invitation Acceptance API
```graphql
mutation AcceptTeamInvitation($token: String!) {
  acceptTeamInvitation(token: $token) {
    success
    organizationMember {
      id
      role
      organization {
        name
      }
    }
  }
}
```

---

## Key Decisions

### 1. Platform Role vs Organization Role
| Concept | Storage | Purpose |
|---------|---------|---------|
| Platform Role (UserType) | User.userType, Keycloak realm role | System-wide capabilities (CUSTOMER, ORGANIZER, ADMIN) |
| Organization Role | OrganizationMember.role, Keycloak group | Per-organization capabilities |
| Event Role | EventAccessGrant.eventRole | Per-event overrides |

### 2. Upgrade Path
- CUSTOMER → ORGANIZER requires OrganizerProfile approval
- ORGANIZER can have multiple organizations (via OrganizationMember)
- Team members can be CUSTOMERS who work for an organization

### 3. Multiple Organization Membership
A user can be:
- OWNER of Organization A
- ADMIN of Organization B
- MANAGER of Organization C

Each membership is tracked in OrganizationMember with appropriate role.
