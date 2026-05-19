# API Layer Structure Design

This document outlines the recommended structure for the GraphQL API layer.

## Option A: App-First Structure (Recommended)

Organizes by target application first, then by domain. Best for clear separation and tree-shaking.

```
api/
├── admin/                          # Admin App APIs
│   ├── organizers/
│   │   ├── queries.ts              # Admin organizer queries
│   │   ├── mutations.ts            # Admin organizer mutations
│   │   └── hooks.ts                # Admin organizer hooks
│   ├── users/
│   │   ├── queries.ts
│   │   ├── mutations.ts
│   │   └── hooks.ts
│   ├── events/
│   │   └── ...
│   ├── analytics/
│   │   └── ...
│   └── index.ts                    # Re-exports all admin APIs
│
├── organizer/                      # Organizer App APIs (self-service)
│   ├── profile/
│   │   ├── queries.ts
│   │   ├── mutations.ts
│   │   └── hooks.ts
│   ├── events/
│   │   └── ...
│   ├── tickets/
│   │   └── ...
│   ├── payouts/
│   │   └── ...
│   └── index.ts
│
├── buyer/                          # Buyer/Consumer App APIs
│   ├── events/
│   │   ├── queries.ts              # Browse events
│   │   └── hooks.ts
│   ├── tickets/
│   │   ├── queries.ts              # My tickets
│   │   ├── mutations.ts            # Purchase tickets
│   │   └── hooks.ts
│   ├── profile/
│   │   └── ...
│   └── index.ts
│
├── shared/                         # Shared across all apps
│   ├── client.ts                   # Apollo client factory
│   ├── fragments.ts                # Common GraphQL fragments
│   └── types.ts                    # Re-exported codegen types
│
└── index.ts                        # Main entry point
```

**Pros:**
- Clear app separation - import from `api/admin` or `api/buyer`
- Better tree-shaking - only load what each app needs
- Easy to find code - "where is admin organizer hook?" -> `api/admin/organizers/`
- Scales well with more apps

**Cons:**
- Some duplication of shared types/fragments
- Need to decide which app "owns" shared operations

---

## Option B: Domain-First with App Subfolders

Organizes by business domain first, then separates by app within each domain.

```
api/
├── organizers/
│   ├── admin/                      # Admin operations on organizers
│   │   ├── queries.ts
│   │   ├── mutations.ts
│   │   └── hooks.ts
│   ├── self/                       # Organizer self-service
│   │   ├── queries.ts
│   │   ├── mutations.ts
│   │   └── hooks.ts
│   ├── shared/                     # Shared fragments, types
│   │   ├── fragments.ts
│   │   └── types.ts
│   └── index.ts
│
├── events/
│   ├── admin/
│   ├── organizer/
│   ├── buyer/
│   └── shared/
│
├── tickets/
│   ├── admin/
│   ├── organizer/
│   ├── buyer/
│   └── shared/
│
└── ...
```

**Pros:**
- Domain experts know where their code is
- Shared code stays close to domain
- Good for teams organized by domain

**Cons:**
- Hard to see "all admin APIs" at a glance
- May import wrong app's hooks by accident

---

## Recommendation: Option A (App-First)

For this ticketing system with 3 distinct apps (Admin, Organizer Dashboard, Buyer/Mobile), **Option A is cleaner** because:

1. **Import clarity**:
   ```typescript
   // Admin app imports
   import { useOrganizerApplicationsAdmin } from '@pml.tickets/shared/api/admin';

   // Buyer app imports
   import { useEventsSearch } from '@pml.tickets/shared/api/buyer';
   ```

2. **Tree-shaking**: Admin app won't bundle buyer hooks

3. **Onboarding**: New devs immediately understand structure

4. **Scaling**: Easy to add new apps (e.g., `api/scanner/` for venue scanning app)
