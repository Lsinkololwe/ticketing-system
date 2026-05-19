# Ticketing Mobile App - Design Document

## Overview

A Flutter mobile application for event discovery and ticket purchasing in Zambia/Africa. Users can browse events freely but must authenticate via Keycloak to purchase tickets.

---

## Design System

### Color Palette

| Token | Color | Hex | Usage |
|-------|-------|-----|-------|
| **Primary** | Purple | `#7C3AED` | Buttons, active states, links |
| **Primary Light** | Light Purple | `#A78BFA` | Secondary actions, highlights |
| **Primary Dark** | Deep Purple | `#4C1D95` | Text, headers |
| **CTA / Accent** | Orange | `#F97316` | Buy buttons, urgent actions |
| **Background** | Soft Lavender | `#FAF5FF` | Main background (light mode) |
| **Surface** | White | `#FFFFFF` | Cards, sheets |
| **Surface Dark** | Dark Purple | `#1E1B4B` | Background (dark mode) |
| **Success** | Green | `#10B981` | Confirmed, validated |
| **Warning** | Amber | `#F59E0B` | Pending, attention |
| **Error** | Red | `#EF4444` | Errors, cancelled |
| **Text Primary** | Deep Purple | `#4C1D95` | Headings, body |
| **Text Secondary** | Gray Purple | `#6B7280` | Captions, hints |

### Typography

| Style | Font | Weight | Size | Line Height |
|-------|------|--------|------|-------------|
| **Display** | Righteous | 400 | 32px | 1.2 |
| **Headline Large** | Poppins | 700 | 28px | 1.3 |
| **Headline Medium** | Poppins | 600 | 24px | 1.3 |
| **Title Large** | Poppins | 600 | 20px | 1.4 |
| **Title Medium** | Poppins | 500 | 16px | 1.4 |
| **Body Large** | Poppins | 400 | 16px | 1.5 |
| **Body Medium** | Poppins | 400 | 14px | 1.5 |
| **Label Large** | Poppins | 500 | 14px | 1.4 |
| **Label Small** | Poppins | 500 | 12px | 1.4 |
| **Caption** | Poppins | 400 | 12px | 1.4 |

---

## Navigation Architecture

### Bottom Navigation (4 tabs)

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│                        [App Content]                            │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   🏠 Home     🔍 Explore     🎫 My Tickets     👤 Profile      │
│   (Events)    (Search)       (Auth Req)        (Auth Req)       │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Tab Details

| Tab | Label | Icon | Auth Required | Description |
|-----|-------|------|---------------|-------------|
| 1 | **Home** | `home` | No | Featured events, categories, trending |
| 2 | **Explore** | `search` | No | Search, filter, discover events |
| 3 | **My Tickets** | `ticket` | **Yes** | User's purchased tickets with QR |
| 4 | **Profile** | `person` | **Yes** | Account settings, payment methods |

### Authentication Behavior

```
┌─────────────────────────────────────────────────────────────────┐
│                    AUTHENTICATION FLOW                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  GUEST MODE (Not Logged In):                                   │
│  ├─ Home Tab → Full access (browse events)                     │
│  ├─ Explore Tab → Full access (search events)                  │
│  ├─ My Tickets Tab → Shows "Login to view tickets" prompt      │
│  ├─ Profile Tab → Shows "Login" button                         │
│  └─ Buy Ticket → Triggers auth modal before purchase           │
│                                                                 │
│  AUTHENTICATED:                                                 │
│  ├─ All tabs fully accessible                                  │
│  └─ Seamless ticket purchase flow                              │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Screen Hierarchy

### 1. Home Tab

```
home/
├── HomeScreen                    # Main home screen
│   ├── FeaturedEventsCarousel    # Hero carousel (auto-scroll)
│   ├── CategoriesRow             # Horizontal category chips
│   ├── TrendingEventsSection     # "Trending Now" list
│   ├── UpcomingEventsSection     # "Coming Soon" list
│   └── NearbyEventsSection       # Location-based events
│
├── EventDetailScreen             # Full event details
│   ├── EventHeroImage            # Banner with gradient overlay
│   ├── EventInfoCard             # Title, date, location, price
│   ├── TicketTiersSection        # Available ticket types
│   ├── EventDescriptionSection   # Full description
│   ├── VenueMapSection           # Location with map preview
│   ├── OrganizerSection          # Organizer info
│   └── BuyTicketFAB              # Floating action button
│
└── TicketPurchaseFlow/           # Modal bottom sheet flow
    ├── SelectTicketsSheet        # Choose tier & quantity
    ├── ReviewOrderSheet          # Order summary
    ├── PaymentMethodSheet        # Select payment (Mobile Money)
    ├── PaymentProcessingSheet    # Loading + status
    └── ConfirmationSheet         # Success + ticket preview
```

### 2. Explore Tab

```
explore/
├── ExploreScreen                 # Search & discovery
│   ├── SearchBar                 # Search input
│   ├── RecentSearches            # Search history
│   ├── PopularCategories         # Category grid
│   └── SearchResults             # Results list
│
├── CategoryEventsScreen          # Events by category
│   ├── CategoryHeader            # Category banner
│   ├── FilterBar                 # Date, price, location filters
│   └── EventsList                # Filtered events
│
└── FilterScreen                  # Full filter options
    ├── DateRangeFilter           # Today, Weekend, Custom
    ├── PriceRangeFilter          # Free, Budget, Premium
    ├── LocationFilter            # Province, City
    └── SortOptions               # Date, Price, Popularity
```

### 3. My Tickets Tab (Auth Required)

```
tickets/
├── MyTicketsScreen               # User's tickets
│   ├── UpcomingTicketsTab        # Future events
│   ├── PastTicketsTab            # Attended events
│   └── EmptyState                # No tickets illustration
│
├── TicketDetailScreen            # Single ticket view
│   ├── TicketQRCode              # Large scannable QR
│   ├── TicketInfo                # Event, date, seat info
│   ├── AddToWalletButton         # Apple/Google Wallet
│   └── ShareTicketButton         # Share options
│
└── TicketQRFullScreen            # Full screen QR for scanning
    └── BrightnessBoost           # Max brightness for scanning
```

### 4. Profile Tab (Auth Required)

```
profile/
├── ProfileScreen                 # Account overview
│   ├── UserInfoCard              # Name, email, avatar
│   ├── QuickStats                # Events attended, tickets
│   ├── MenuItems                 # Settings list
│   │   ├── Payment Methods
│   │   ├── Notifications
│   │   ├── Language
│   │   ├── Help & Support
│   │   └── About
│   └── LogoutButton
│
├── PaymentMethodsScreen          # Manage payments
│   ├── MobileMoneyAccounts       # MTN, Airtel, Zamtel
│   └── AddPaymentMethodSheet
│
├── NotificationSettingsScreen    # Push notification prefs
│
└── EditProfileScreen             # Edit user details
```

### 5. Authentication Screens

```
auth/
├── LoginPromptSheet              # Bottom sheet for guest users
│   ├── WelcomeMessage            # "Login to continue"
│   ├── LoginWithPhoneButton      # Primary action
│   ├── LoginWithEmailButton      # Secondary action
│   └── SkipButton                # Continue browsing
│
├── PhoneLoginScreen              # Phone number entry
│   ├── PhoneInput                # Country code + number
│   └── SendOTPButton
│
├── OTPVerificationScreen         # OTP code entry
│   ├── OTPInput                  # 6-digit code
│   ├── ResendTimer               # Countdown to resend
│   └── VerifyButton
│
└── CompleteProfileScreen         # First-time user setup
    ├── NameInput
    ├── EmailInput (optional)
    └── SaveButton
```

---

## Key User Flows

### Flow 1: Guest Browsing Events

```
┌─────────┐    ┌─────────┐    ┌─────────────┐    ┌──────────────┐
│  Open   │───▶│  Home   │───▶│ Tap Event   │───▶│ Event Detail │
│   App   │    │  Tab    │    │   Card      │    │    Screen    │
└─────────┘    └─────────┘    └─────────────┘    └──────────────┘
                                                        │
                                                        ▼
                                              ┌──────────────────┐
                                              │ Browse freely,   │
                                              │ no login needed  │
                                              └──────────────────┘
```

### Flow 2: Guest Attempts Purchase → Auth Required

```
┌─────────────┐    ┌────────────┐    ┌─────────────────┐
│ Event Detail│───▶│ Tap "Buy   │───▶│ Auth Modal      │
│   Screen    │    │  Tickets"  │    │ Appears         │
└─────────────┘    └────────────┘    └─────────────────┘
                                            │
           ┌────────────────────────────────┼────────────────────────────────┐
           │                                │                                │
           ▼                                ▼                                ▼
    ┌──────────────┐              ┌──────────────────┐              ┌──────────────┐
    │ Login with   │              │ Login with       │              │ Skip         │
    │ Phone (OTP)  │              │ Email            │              │ (Cancel)     │
    └──────────────┘              └──────────────────┘              └──────────────┘
           │                                │
           ▼                                ▼
    ┌──────────────┐              ┌──────────────────┐
    │ Keycloak     │              │ Keycloak         │
    │ Phone Auth   │              │ Browser Auth     │
    └──────────────┘              └──────────────────┘
           │                                │
           └────────────────┬───────────────┘
                            ▼
                   ┌──────────────────┐
                   │ Auth Success     │
                   │ Continue Purchase│
                   └──────────────────┘
```

### Flow 3: Ticket Purchase Flow

```
┌─────────────────┐
│  Select Tickets │  Step 1: Choose tier & quantity
│  ─────────────  │
│  VIP     x [2]  │
│  General x [0]  │
│                 │
│  [Continue]     │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Review Order   │  Step 2: Order summary
│  ─────────────  │
│  2x VIP K500    │
│  Service Fee K50│
│  ─────────────  │
│  Total: K1,050  │
│                 │
│  [Pay Now]      │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Payment Method  │  Step 3: Select payment
│  ─────────────  │
│  ○ MTN MoMo     │
│  ○ Airtel Money │
│  ○ Zamtel       │
│                 │
│  [Confirm]      │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Processing...  │  Step 4: Wait for payment
│  ─────────────  │
│     [Spinner]   │
│                 │
│  Waiting for    │
│  payment...     │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  ✓ Success!     │  Step 5: Confirmation
│  ─────────────  │
│  Your tickets   │
│  are ready      │
│                 │
│  [View Tickets] │
│  [Back to Home] │
└─────────────────┘
```

---

## Component Library

### 1. Event Card (Horizontal)

```
┌──────────────────────────────────────────────────────┐
│ ┌──────────┐                                         │
│ │          │  Event Title Here                       │
│ │  IMAGE   │  📅 Sat, Jan 25 • 7:00 PM              │
│ │  (120px) │  📍 Mulungushi Conference Center       │
│ │          │                                         │
│ │          │  From K150                              │
│ └──────────┘                                         │
└──────────────────────────────────────────────────────┘
```

### 2. Event Card (Vertical - Featured)

```
┌────────────────────────┐
│                        │
│        IMAGE           │
│       (180px)          │
│                        │
├────────────────────────┤
│ Event Title            │
│ 📅 Sat, Jan 25         │
│ 📍 Lusaka              │
│                        │
│ From K150  [🎫 Buy]    │
└────────────────────────┘
```

### 3. Category Chip

```
┌──────────────────┐
│  🎵 Music        │   Active: Purple bg, white text
└──────────────────┘

┌──────────────────┐
│  🎭 Theatre      │   Inactive: Light purple bg, dark text
└──────────────────┘
```

### 4. Ticket Card

```
┌──────────────────────────────────────────────────────┐
│  EVENT TITLE                             ┌─────────┐ │
│  ──────────────────────                  │   QR    │ │
│  📅 Saturday, January 25, 2025           │  CODE   │ │
│  🕐 7:00 PM                              │         │ │
│  📍 Mulungushi Conference Center         └─────────┘ │
│                                                      │
│  ┌────────────────────────────────────────────────┐ │
│  │  VIP TICKET  •  Seat: A-12                     │ │
│  │  Ticket #: TKT-2024-001234                     │ │
│  └────────────────────────────────────────────────┘ │
│                                                      │
│  [View Full QR]              [Add to Wallet]        │
└──────────────────────────────────────────────────────┘
```

### 5. Bottom Sheet (Purchase Flow)

```
┌──────────────────────────────────────────────────────┐
│  ═══════════  (Drag handle)                         │
│                                                      │
│  Select Tickets                                      │
│  ──────────────                                      │
│                                                      │
│  [Content Area - Scrollable]                         │
│                                                      │
│  ────────────────────────────────────────────────── │
│  Total: K1,050                    [Continue Button] │
└──────────────────────────────────────────────────────┘
```

### 6. Payment Method Selector

```
┌──────────────────────────────────────────────────────┐
│  ┌─────────────────────────────────────────────────┐│
│  │ ○  [MTN Logo]  MTN Mobile Money                 ││
│  │                 **** 7890                        ││
│  └─────────────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────┐│
│  │ ●  [Airtel Logo]  Airtel Money                  ││
│  │                    **** 1234  ✓ Selected        ││
│  └─────────────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────┐│
│  │ +  Add Payment Method                           ││
│  └─────────────────────────────────────────────────┘│
└──────────────────────────────────────────────────────┘
```

---

## State Management Architecture

### Riverpod Provider Structure

```
providers/
├── auth/
│   ├── auth_state_provider.dart      # Current auth state
│   ├── auth_notifier.dart            # Auth actions (login/logout)
│   └── user_provider.dart            # Current user data
│
├── events/
│   ├── events_provider.dart          # Events list (cached)
│   ├── event_detail_provider.dart    # Single event (family)
│   ├── featured_events_provider.dart # Featured events
│   └── search_provider.dart          # Search state & results
│
├── tickets/
│   ├── my_tickets_provider.dart      # User's tickets
│   ├── ticket_detail_provider.dart   # Single ticket
│   └── purchase_flow_provider.dart   # Purchase state machine
│
├── categories/
│   └── categories_provider.dart      # Event categories
│
└── payment/
    ├── payment_methods_provider.dart # User's saved methods
    └── payment_state_provider.dart   # Payment processing state
```

---

## Folder Structure

```
lib/
├── main.dart                         # App entry point
├── app.dart                          # MaterialApp + theme + router
│
├── core/
│   ├── config/
│   │   ├── env.dart                  # Environment variables
│   │   └── constants.dart            # App constants
│   │
│   ├── api/
│   │   ├── dio_client.dart           # Configured Dio instance
│   │   ├── graphql_client.dart       # GraphQL client setup
│   │   ├── interceptors/
│   │   │   ├── auth_interceptor.dart # Token injection
│   │   │   └── error_interceptor.dart# Error handling
│   │   └── exceptions.dart           # Custom API exceptions
│   │
│   ├── auth/
│   │   ├── keycloak_config.dart      # Keycloak configuration
│   │   ├── auth_service.dart         # AppAuth integration
│   │   └── token_storage.dart        # Secure token storage
│   │
│   ├── router/
│   │   ├── app_router.dart           # GoRouter configuration
│   │   ├── routes.dart               # Route definitions
│   │   └── guards/
│   │       └── auth_guard.dart       # Auth route protection
│   │
│   └── theme/
│       ├── app_theme.dart            # ThemeData configuration
│       ├── colors.dart               # Color tokens
│       └── typography.dart           # Text styles
│
├── shared/
│   ├── widgets/
│   │   ├── buttons/
│   │   ├── cards/
│   │   ├── inputs/
│   │   ├── loading/
│   │   └── empty_states/
│   │
│   ├── extensions/
│   │   ├── context_extensions.dart
│   │   └── date_extensions.dart
│   │
│   └── utils/
│       ├── formatters.dart
│       └── validators.dart
│
├── features/
│   ├── home/
│   │   ├── presentation/
│   │   │   ├── screens/
│   │   │   │   └── home_screen.dart
│   │   │   └── widgets/
│   │   │       ├── featured_carousel.dart
│   │   │       ├── categories_row.dart
│   │   │       └── event_section.dart
│   │   ├── data/
│   │   │   ├── repositories/
│   │   │   └── models/
│   │   └── providers/
│   │       └── home_providers.dart
│   │
│   ├── explore/
│   │   ├── presentation/
│   │   │   ├── screens/
│   │   │   │   ├── explore_screen.dart
│   │   │   │   └── search_results_screen.dart
│   │   │   └── widgets/
│   │   ├── data/
│   │   └── providers/
│   │
│   ├── events/
│   │   ├── presentation/
│   │   │   ├── screens/
│   │   │   │   └── event_detail_screen.dart
│   │   │   └── widgets/
│   │   │       ├── event_hero.dart
│   │   │       ├── ticket_tier_card.dart
│   │   │       └── venue_section.dart
│   │   ├── data/
│   │   │   ├── repositories/
│   │   │   │   └── events_repository.dart
│   │   │   └── models/
│   │   │       ├── event.dart
│   │   │       └── ticket_tier.dart
│   │   └── providers/
│   │       └── events_providers.dart
│   │
│   ├── tickets/
│   │   ├── presentation/
│   │   │   ├── screens/
│   │   │   │   ├── my_tickets_screen.dart
│   │   │   │   ├── ticket_detail_screen.dart
│   │   │   │   └── ticket_qr_screen.dart
│   │   │   └── widgets/
│   │   │       └── ticket_card.dart
│   │   ├── data/
│   │   │   ├── repositories/
│   │   │   │   └── tickets_repository.dart
│   │   │   └── models/
│   │   │       └── ticket.dart
│   │   └── providers/
│   │       └── tickets_providers.dart
│   │
│   ├── purchase/
│   │   ├── presentation/
│   │   │   ├── sheets/
│   │   │   │   ├── select_tickets_sheet.dart
│   │   │   │   ├── review_order_sheet.dart
│   │   │   │   ├── payment_method_sheet.dart
│   │   │   │   └── confirmation_sheet.dart
│   │   │   └── widgets/
│   │   ├── data/
│   │   │   └── models/
│   │   │       └── cart_item.dart
│   │   └── providers/
│   │       └── purchase_providers.dart
│   │
│   ├── profile/
│   │   ├── presentation/
│   │   │   ├── screens/
│   │   │   │   ├── profile_screen.dart
│   │   │   │   └── edit_profile_screen.dart
│   │   │   └── widgets/
│   │   ├── data/
│   │   └── providers/
│   │
│   └── auth/
│       ├── presentation/
│       │   ├── sheets/
│       │   │   └── login_prompt_sheet.dart
│       │   └── screens/
│       │       └── phone_login_screen.dart
│       ├── data/
│       └── providers/
│           └── auth_providers.dart
│
└── generated/                        # Build runner output
    └── ...
```

---

## API Integration

### GraphQL Operations (Mobile Client)

```graphql
# Queries (Public - No Auth)
query FeaturedEvents {
  featuredEvents(first: 10) {
    edges { node { ...EventCard } }
  }
}

query UpcomingEvents($first: Int, $after: String) {
  upcomingEvents(first: $first, after: $after) {
    edges { node { ...EventCard } }
    pageInfo { hasNextPage, endCursor }
  }
}

query EventDetail($id: ID!) {
  event(id: $id) {
    ...EventDetail
    ticketTiers { ...TicketTier }
  }
}

query SearchEvents($query: String!, $filters: EventFilters) {
  searchEvents(query: $query, filters: $filters) {
    edges { node { ...EventCard } }
  }
}

query Categories {
  eventCategories { id, name, iconUrl, eventCount }
}

# Queries (Auth Required)
query MyTickets {
  myTickets {
    edges { node { ...TicketDetail } }
  }
}

query TicketDetail($id: ID!) {
  ticket(id: $id) {
    ...TicketDetail
    qrCode
  }
}

# Mutations (Auth Required)
mutation CreateTicketReservation($input: ReservationInput!) {
  createReservation(input: $input) {
    id, expiresAt, tickets { ... }
  }
}

mutation InitiatePayment($reservationId: ID!, $method: PaymentMethod!) {
  initiatePayment(reservationId: $reservationId, method: $method) {
    id, status, mobileMoneyPrompt
  }
}
```

---

## Next Steps

1. **Review this design** - Confirm navigation, screens, and flows
2. **Approve color scheme** - Confirm purple/orange palette
3. **Approve typography** - Confirm Righteous + Poppins fonts
4. **Implementation priority**:
   - Phase 1: Core navigation + Home + Explore (public)
   - Phase 2: Auth integration (Keycloak/AppAuth)
   - Phase 3: Purchase flow
   - Phase 4: My Tickets + Profile

---

## Questions for Clarification

1. **Offline support**: Should tickets be viewable offline?
2. **Multi-language**: Swahili, Bemba, or English only?
3. **Push notifications**: For ticket reminders, event updates?
4. **Wallet integration**: Apple Wallet / Google Wallet for tickets?
5. **Dark mode**: Support from launch or phase 2?
