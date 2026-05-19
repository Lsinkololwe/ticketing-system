# Navigation & Screen Flow Diagrams

## App Navigation Structure

```mermaid
graph TB
    subgraph "Bottom Navigation"
        HOME["🏠 Home"]
        EXPLORE["🔍 Explore"]
        TICKETS["🎫 My Tickets"]
        PROFILE["👤 Profile"]
    end

    subgraph "Home Tab Stack"
        HOME --> HS[Home Screen]
        HS --> ED[Event Detail]
        ED --> PF[Purchase Flow]
    end

    subgraph "Explore Tab Stack"
        EXPLORE --> ES[Explore Screen]
        ES --> SR[Search Results]
        SR --> ED2[Event Detail]
        ES --> CE[Category Events]
        CE --> ED3[Event Detail]
    end

    subgraph "Tickets Tab Stack (Auth Required)"
        TICKETS --> MT[My Tickets]
        MT --> TD[Ticket Detail]
        TD --> QR[QR Full Screen]
    end

    subgraph "Profile Tab Stack (Auth Required)"
        PROFILE --> PS[Profile Screen]
        PS --> EP[Edit Profile]
        PS --> PM[Payment Methods]
        PS --> NS[Notification Settings]
    end

    subgraph "Auth Flow (Modal)"
        AUTH[Auth Modal]
        AUTH --> PL[Phone Login]
        PL --> OTP[OTP Verify]
        OTP --> CP[Complete Profile]
    end

    PF -.->|"If not logged in"| AUTH
    MT -.->|"If not logged in"| AUTH
    PS -.->|"If not logged in"| AUTH
```

## Purchase Flow State Machine

```mermaid
stateDiagram-v2
    [*] --> Browsing

    Browsing --> SelectTickets: Tap "Buy Tickets"

    SelectTickets --> AuthRequired: Not Logged In
    AuthRequired --> SelectTickets: Auth Success
    AuthRequired --> Browsing: Auth Cancelled

    SelectTickets --> ReviewOrder: Continue
    ReviewOrder --> SelectTickets: Back

    ReviewOrder --> SelectPayment: Pay Now
    SelectPayment --> ReviewOrder: Back

    SelectPayment --> Processing: Confirm Payment
    Processing --> PaymentFailed: Error
    Processing --> Confirmation: Success

    PaymentFailed --> SelectPayment: Retry
    PaymentFailed --> Browsing: Cancel

    Confirmation --> ViewTicket: View Tickets
    Confirmation --> Browsing: Back to Home

    ViewTicket --> [*]
```

## Authentication State Flow

```mermaid
stateDiagram-v2
    [*] --> Guest

    Guest --> AuthPrompt: Access Protected Feature

    AuthPrompt --> PhoneEntry: Select Phone Login
    AuthPrompt --> KeycloakBrowser: Select Email/Social
    AuthPrompt --> Guest: Skip/Cancel

    PhoneEntry --> OTPVerify: Send OTP
    OTPVerify --> PhoneEntry: Resend OTP
    OTPVerify --> Authenticated: Verify Success
    OTPVerify --> Guest: Cancel

    KeycloakBrowser --> Authenticated: OAuth Success
    KeycloakBrowser --> Guest: OAuth Failed/Cancel

    Authenticated --> Guest: Logout

    note right of Authenticated
        Token stored securely
        Auto-refresh enabled
    end note
```

## Screen Wireframes

### Home Screen Layout

```
┌─────────────────────────────────────────┐
│ ◀ ▪▪▪▪▪  📍 Lusaka           🔔  ≡     │ ← App Bar
├─────────────────────────────────────────┤
│ ┌─────────────────────────────────────┐ │
│ │                                     │ │
│ │         FEATURED EVENT              │ │ ← Hero Carousel
│ │           BANNER                    │ │
│ │                                     │ │
│ │  ●  ○  ○  ○                        │ │
│ └─────────────────────────────────────┘ │
│                                         │
│ Categories                              │
│ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐   │ ← Horizontal Scroll
│ │ 🎵   │ │ 🎭   │ │ 🏀   │ │ 🎪   │   │
│ │Music │ │Drama │ │Sports│ │Comedy│   │
│ └──────┘ └──────┘ └──────┘ └──────┘   │
│                                         │
│ Trending Now                    See All │
│ ┌───────────────────────────────────┐   │
│ │ [IMG] Event Name                  │   │ ← Event Card
│ │       📅 Jan 25 • 📍 Lusaka      │   │
│ │       From K150                   │   │
│ └───────────────────────────────────┘   │
│ ┌───────────────────────────────────┐   │
│ │ [IMG] Another Event               │   │
│ │       📅 Feb 1 • 📍 Kitwe        │   │
│ │       From K200                   │   │
│ └───────────────────────────────────┘   │
│                                         │
├─────────────────────────────────────────┤
│  🏠      🔍        🎫        👤         │ ← Bottom Nav
│ Home   Explore   Tickets   Profile      │
└─────────────────────────────────────────┘
```

### Event Detail Screen

```
┌─────────────────────────────────────────┐
│ ← Back                           ❤ Share│ ← Transparent over image
├─────────────────────────────────────────┤
│ ┌─────────────────────────────────────┐ │
│ │                                     │ │
│ │                                     │ │
│ │         EVENT BANNER                │ │ ← Hero Image (40% height)
│ │          IMAGE                      │ │
│ │                                     │ │
│ │                                     │ │
│ └─────────────────────────────────────┘ │
│                                         │
│ EVENT TITLE HERE                        │
│ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │
│                                         │
│ 📅 Saturday, January 25, 2025           │
│ 🕐 7:00 PM - 11:00 PM                   │
│ 📍 Mulungushi Conference Center         │
│    View Map →                           │
│                                         │
│ ┌─────────────────────────────────────┐ │
│ │ Ticket Options                      │ │
│ │                                     │ │
│ │ ┌───────────────────────────────┐   │ │ ← Ticket Tier Cards
│ │ │ GENERAL                       │   │ │
│ │ │ Standard entry   K150    [+]  │   │ │
│ │ └───────────────────────────────┘   │ │
│ │ ┌───────────────────────────────┐   │ │
│ │ │ VIP                           │   │ │
│ │ │ Premium seating  K500    [+]  │   │ │
│ │ └───────────────────────────────┘   │ │
│ └─────────────────────────────────────┘ │
│                                         │
│ About This Event                        │
│ ─────────────────                       │
│ Lorem ipsum dolor sit amet...           │
│ Read more →                             │
│                                         │
│ Organizer                               │
│ ─────────────                           │
│ [Avatar] Event Company                  │
│          15 events hosted               │
│                                         │
├─────────────────────────────────────────┤
│ ┌─────────────────────────────────────┐ │
│ │  From K150        [  Buy Tickets  ] │ │ ← Sticky Bottom
│ └─────────────────────────────────────┘ │
└─────────────────────────────────────────┘
```

### My Tickets Screen

```
┌─────────────────────────────────────────┐
│          My Tickets                     │
├─────────────────────────────────────────┤
│ ┌─────────────────┬───────────────────┐ │
│ │   Upcoming (3)  │     Past (12)     │ │ ← Tab Bar
│ └─────────────────┴───────────────────┘ │
│                                         │
│ January 25, 2025                        │
│ ┌─────────────────────────────────────┐ │
│ │ ┌─────┐                       [QR]  │ │
│ │ │     │  Event Name                 │ │
│ │ │ IMG │  7:00 PM                    │ │
│ │ │     │  VIP • 2 tickets            │ │
│ │ └─────┘                             │ │
│ └─────────────────────────────────────┘ │
│                                         │
│ February 14, 2025                       │
│ ┌─────────────────────────────────────┐ │
│ │ ┌─────┐                       [QR]  │ │
│ │ │     │  Another Event              │ │
│ │ │ IMG │  8:00 PM                    │ │
│ │ │     │  General • 1 ticket         │ │
│ │ └─────┘                             │ │
│ └─────────────────────────────────────┘ │
│                                         │
├─────────────────────────────────────────┤
│  🏠      🔍        🎫        👤         │
│ Home   Explore   Tickets   Profile      │
└─────────────────────────────────────────┘
```

### Purchase Flow - Bottom Sheet

```
┌─────────────────────────────────────────┐
│ (dimmed background - event detail)      │
│                                         │
│                                         │
├─────────────────────────────────────────┤
│           ═══════════                   │ ← Drag Handle
│                                         │
│ Select Tickets                    ✕     │
│ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │
│                                         │
│ ┌─────────────────────────────────────┐ │
│ │ GENERAL                             │ │
│ │ Standard entry                      │ │
│ │                                     │ │
│ │ K150          [-]  2  [+]    K300   │ │
│ └─────────────────────────────────────┘ │
│                                         │
│ ┌─────────────────────────────────────┐ │
│ │ VIP                                 │ │
│ │ Premium seating • Front rows        │ │
│ │                                     │ │
│ │ K500          [-]  0  [+]    K0     │ │
│ └─────────────────────────────────────┘ │
│                                         │
│ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │
│                                         │
│ 2 tickets                    Total K300 │
│                                         │
│ ┌─────────────────────────────────────┐ │
│ │           Continue                  │ │ ← CTA Button (Orange)
│ └─────────────────────────────────────┘ │
│                                         │
│ 🔒 Tickets reserved for 10:00           │
│                                         │
└─────────────────────────────────────────┘
```

## Color System Visual

```
┌─────────────────────────────────────────────────────────────┐
│                     COLOR PALETTE                            │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  PRIMARY                                                     │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐                     │
│  │ #4C1D95  │ │ #7C3AED  │ │ #A78BFA  │                     │
│  │  Dark    │ │  Main    │ │  Light   │                     │
│  └──────────┘ └──────────┘ └──────────┘                     │
│                                                              │
│  ACCENT / CTA                                                │
│  ┌──────────┐                                               │
│  │ #F97316  │  ← Buy buttons, urgent actions                │
│  │  Orange  │                                               │
│  └──────────┘                                               │
│                                                              │
│  SEMANTIC                                                    │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐                     │
│  │ #10B981  │ │ #F59E0B  │ │ #EF4444  │                     │
│  │ Success  │ │ Warning  │ │  Error   │                     │
│  └──────────┘ └──────────┘ └──────────┘                     │
│                                                              │
│  BACKGROUNDS                                                 │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐                     │
│  │ #FAF5FF  │ │ #FFFFFF  │ │ #1E1B4B  │                     │
│  │ Light BG │ │ Surface  │ │ Dark BG  │                     │
│  └──────────┘ └──────────┘ └──────────┘                     │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

## Typography Scale

```
┌─────────────────────────────────────────────────────────────┐
│                    TYPOGRAPHY SCALE                          │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  RIGHTEOUS (Display)                                         │
│  ┌─────────────────────────────────────────────────────────┐│
│  │ Event Tickets                           32px / 400      ││
│  └─────────────────────────────────────────────────────────┘│
│                                                              │
│  POPPINS (All other text)                                    │
│  ┌─────────────────────────────────────────────────────────┐│
│  │ Headline Large                          28px / Bold     ││
│  └─────────────────────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────────────┐│
│  │ Headline Medium                    24px / SemiBold      ││
│  └─────────────────────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────────────┐│
│  │ Title Large                   20px / SemiBold           ││
│  └─────────────────────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────────────┐│
│  │ Title Medium            16px / Medium                   ││
│  └─────────────────────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────────────┐│
│  │ Body Large         16px / Regular                       ││
│  └─────────────────────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────────────┐│
│  │ Body Medium    14px / Regular                           ││
│  └─────────────────────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────────────┐│
│  │ Caption   12px / Regular                                ││
│  └─────────────────────────────────────────────────────────┘│
│                                                              │
└─────────────────────────────────────────────────────────────┘
```
