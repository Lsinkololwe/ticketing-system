# Admin Dashboard Implementation Plan

## Overview

This document outlines the comprehensive implementation plan for the Event Ticketing System Admin Dashboard. The admin dashboard is designed with a **bento-style grid layout** using Radix UI Themes, providing role-based access control for different admin types.

## Admin Roles & Access Levels

| Role | Description | Primary Responsibilities |
|------|-------------|-------------------------|
| **SUPER_ADMIN** | System-wide administrator | Full system access, user management, system configuration |
| **ADMIN** | Standard administrator | Event management, organizer oversight, reporting |
| **FINANCE** | Financial administrator | Payments, refunds, financial reports, settlements |
| **SCANNER** | Event check-in staff | Ticket validation, attendance tracking |
| **ORGANIZER** | Event organizers | Own events management, sales reports |

## Navigation Architecture

```
/dashboard
├── /overview                    # Role-based overview dashboard
├── /events
│   ├── /                        # Event listing with filters
│   ├── /[id]                    # Event details
│   ├── /[id]/tickets            # Ticket management
│   ├── /[id]/orders             # Event orders
│   ├── /[id]/analytics          # Event analytics
│   └── /create                  # Create new event
├── /bookings
│   ├── /                        # All bookings listing
│   ├── /[id]                    # Booking details
│   └── /refunds                 # Refund requests
├── /users
│   ├── /                        # User management
│   ├── /[id]                    # User profile
│   ├── /organizers              # Organizer management
│   └── /admins                  # Admin user management
├── /finance
│   ├── /                        # Financial overview
│   ├── /transactions            # Transaction history
│   ├── /settlements             # Organizer settlements
│   ├── /refunds                 # Refund management
│   └── /reports                 # Financial reports
├── /venues
│   ├── /                        # Venue listing
│   ├── /[id]                    # Venue details
│   └── /create                  # Create venue
├── /categories
│   └── /                        # Category management
├── /scanner
│   ├── /                        # Scanner dashboard
│   ├── /validate                # Ticket validation
│   └── /history                 # Scan history
├── /reports
│   ├── /                        # Reports overview
│   ├── /sales                   # Sales reports
│   ├── /attendance              # Attendance reports
│   └── /custom                  # Custom report builder
├── /settings
│   ├── /                        # General settings
│   ├── /profile                 # Admin profile
│   ├── /security                # Security settings
│   └── /notifications           # Notification preferences
└── /audit
    └── /                        # Audit logs
```

## Implementation Phases

### Phase 1: Core Infrastructure (Week 1-2)
- [x] Authentication with Keycloak
- [x] Permission-based authorization
- [ ] Sidebar navigation component
- [ ] Bento dashboard layout system
- [ ] Common UI components (DataTable, StatCard, etc.)

### Phase 2: Dashboard & Overview (Week 2-3)
- [ ] Role-based dashboard overview
- [ ] Real-time statistics cards
- [ ] Quick action widgets
- [ ] Recent activity feed

### Phase 3: Event Management (Week 3-5)
- [ ] Event listing with advanced filters
- [ ] Event creation form
- [ ] Event editing and publishing
- [ ] Ticket type management
- [ ] Event analytics dashboard

### Phase 4: Order & Finance (Week 5-7)
- [ ] Order management system
- [ ] Refund processing workflow
- [ ] Financial dashboard
- [ ] Settlement management
- [ ] Payment reports

### Phase 5: User & Access Management (Week 7-8)
- [ ] User listing and search
- [ ] User profile management
- [ ] Organizer verification
- [ ] Admin user management

### Phase 6: Scanner & Validation (Week 8-9)
- [ ] QR code scanner interface
- [ ] Real-time ticket validation
- [ ] Attendance tracking
- [ ] Scan history and reports

### Phase 7: Reports & Analytics (Week 9-10)
- [ ] Sales analytics dashboard
- [ ] Attendance reports
- [ ] Financial reports
- [ ] Custom report builder

### Phase 8: Polish & Optimization (Week 10-11)
- [ ] Performance optimization
- [ ] Accessibility audit
- [ ] Mobile responsiveness
- [ ] Documentation

## File Structure

```
todos/
├── README.md                           # This file
├── 00-terminology-migration.md         # Domain terminology guide (Order → Booking)
├── 01-core-infrastructure.md           # Navigation, layout, components
├── 02-dashboard-overview.md            # Dashboard features by role
├── 03-event-management.md              # Event CRUD operations
├── 04-booking-finance.md               # Bookings and financial features
├── 05-user-management.md               # User and admin management
├── 06-scanner-validation.md            # Scanner features
├── 07-reports-analytics.md             # Reporting features
├── 08-settings-audit.md                # Settings and audit logs
└── components/
    ├── bento-layout.md                 # Bento grid system specs
    ├── data-table.md                   # Reusable data table
    └── stat-cards.md                   # Statistics card components
```

## Domain Terminology

This project uses **event-specific terminology** rather than e-commerce terms:

| Term | Definition |
|------|------------|
| **Booking** | A customer's ticket purchase transaction (not "Order") |
| **BookingItem** | A line item within a booking (ticket type + quantity) |
| **Ticket** | Individual admission credential with unique code |
| **Event** | A scheduled happening that customers can attend |
| **Organizer** | Entity that creates and manages events |
| **Check-in** | The act of validating a ticket at the event |

See `00-terminology-migration.md` for full details on database schema and code conventions.

## Technology Stack

- **Frontend**: Next.js 14+ with App Router
- **UI Library**: Radix UI Themes
- **State Management**: TanStack Query + Apollo Client
- **Authentication**: Keycloak with PKCE
- **API**: GraphQL (Apollo) + REST
- **Styling**: Tailwind CSS + Radix Themes
- **Icons**: Lucide React

## Bento Design System

The dashboard uses a bento-style grid layout inspired by modern dashboard designs:

```tsx
<Grid columns={{ initial: "1", sm: "2", lg: "4" }} gap="4">
  <Box gridColumn={{ lg: "span 2" }} gridRow={{ lg: "span 2" }}>
    {/* Large feature card */}
  </Box>
  <Box>
    {/* Small stat card */}
  </Box>
  {/* ... more cards */}
</Grid>
```

See `components/bento-layout.md` for detailed specifications.
