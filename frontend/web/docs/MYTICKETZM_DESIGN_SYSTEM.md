# MyTicketZM Design System — Implementation Spec

Canonical source: Claude Design project **MyTicketZM Design System**
`https://claude.ai/design/p/af157d5b-36fd-4e93-a690-0aa633d73e5c`
(project id `af157d5b-36fd-4e93-a690-0aa633d73e5c`, readable via the `DesignSync` tool)

Shared token layer (the runtime source of truth in this repo):
`libs/shared/src/styles/design-tokens.css`

This document is the contract every screen in all three web apps must satisfy.

---

## 1. Brand contexts

| App | Path | Radix accent | Radix gray | radius | `data-brand` | Display font |
|-----|------|--------------|------------|--------|--------------|--------------|
| Admin Portal | `apps/admin` | `teal` | `slate` | `medium` | `admin` | Inter |
| Organization Admin | `apps/organization-admin` | `teal` | `slate` | `medium` | `org-admin` | Inter |
| Ticketing (customer) | `apps/ticketing` | `iris` | `slate` | `medium` | `ticketing` | Space Grotesk |

`scaling="100%"` and `panelBackground="solid"` on all three.
`<html data-brand="...">` must be set in each app's `src/app/layout.tsx`.

**Fira Code is mandatory in all three apps** for anything tabular/data/code:
ticket IDs, currency amounts, order references, table figures.

---

## 2. Color roles — style through ROLES, never raw scale steps

| Role | Token | Hue | Use |
|------|-------|-----|-----|
| **Primary** | `--color-primary` (teal-9) | 174 | Brand backbone, primary actions, admin identity |
| **Secondary** | `--color-secondary` (iris-9 `#5b5fc7`) | 237 | Customer identity, links, focus |
| **Highlight** | `--color-highlight` (copper-9 `#c05a2e`) | 18 | Promos, featured tags — **max ~10% of any screen** |
| **Money** | `--color-money` (jade-9) | 160 | **SEMANTIC ONLY**: prices, payouts, revenue, "paid" |

Each role is ≥28° apart on the wheel so they stay distinguishable under
color-blindness. **Jade is money-only** — a "paid" chip must never be mistaken
for a brand-teal chip.

Status colors are shared by all three apps:
`--status-success-*` (green), `--status-warning-*` (amber),
`--status-danger-*` (red), `--status-info-*` (blue).

### Hard rules
- **No raw hex in TSX/TS.** The only pinned hex are `--brand-indigo`,
  `--brand-emerald` (+ dark/light variants) and the three `--momo-*` provider
  colors, all already declared in `design-tokens.css`.
- **No Tailwind default palette classes** (`bg-blue-500`, `text-emerald-600`,
  `border-gray-200`, …). Use the token-bridged Tailwind colors
  (`bg-accent-9`, `text-gray-11`, `bg-surface-secondary`, …) or `var(--token)`.
- Everything routes through a scale. If a color is missing, add a token — do not
  inline it.

---

## 3. Typography

```
--font-sans     Inter          admin + org-admin body/headings/tables/labels
--font-display  Space Grotesk  ticketing headings only
--font-mono     Fira Code      currency, ticket IDs, order refs, table figures (ALL apps)
```

Scale tokens: `--heading-1..9-{size,line}`, `--text-1..9-{size,line}`,
`--weight-{light,regular,medium,semibold,bold,extrabold}`.

**Micro labels** are the one deliberate ALL-CAPS exception — nav section
headers, form labels, table headers. Always `--label-size` (10px) /
`--label-weight` (600) / `--label-tracking` (0.08em), via `.ds-label`.
Everything else is **sentence case** ("Create Event", "Request Payout").

`font-variant-numeric: tabular-nums` globally so stat cards don't jitter.

---

## 4. Layout — bento-grid first

```css
/* stat rows, quick-action rows */
display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 16px;
/* activity / upcoming-event panels */
display: grid; grid-template-columns: repeat(auto-fit, minmax(340px, 1fr)); gap: 24px;
```

Use `.ds-bento-grid` / `.ds-bento-grid-wide`. Genuinely asymmetric — **not** a
fixed 12-column grid.

Fixed chrome in both admin apps: sidebar 280px fixed left (collapses to an
overlay drawer under 1024px), header 64px fixed top with a frosted background
(`.ds-glass-header`). The ticketing app uses a simple non-fixed top nav.

---

## 5. Surfaces, borders, shadows, radii

- **Cards**: flat fill (`--card-bg`), 1px hairline alpha-gray border
  (`--gray-a5`), soft 2-step shadow (`--card-shadow`).
- **Radii**: buttons/inputs 6–8px (`--radius-3`/`--radius-4`); standard cards
  8px (`--card-radius`); bento tiles **14px** (`--card-radius-bento`) so tiles
  read as a distinct language from form controls.
- **NO colored left-border accent cards.** This pattern is banned.
- **Shadows**: `--shadow-2` resting, `--shadow-3` hover, `--shadow-4`
  dropdowns/modals. Dark mode swaps to a 1px ring + soft black shadow (already
  handled by the token layer).
- **Blur/transparency**: only for (a) header bars and (b) the org-admin
  marketing site's glass cards. **Never on primary content cards.**

---

## 6. Interaction

- **Hover**: shift background to a tinted step — `--gray-a3` for neutral rows,
  `--accent-a3` for active/brand elements. Do not darken/lighten a solid fill.
  Interactive cards additionally lift 2px (`.ds-lift`).
- **Buttons hover**: `--accent-9` → `--accent-10` (one step down the scale).
- **Active/press**: no scale or shrink effect. Active nav items get a persistent
  2px left accent border plus a tinted background.
- **Focus rings**: 2px accent ring. Buttons/checkboxes/switches use the
  offset double ring
  `box-shadow: 0 0 0 2px var(--color-background), 0 0 0 4px var(--accent-8)`;
  text fields use a single 2px accent-alpha ring.
- **Motion**: 150–200ms ease on hover/active; 200ms transform for sidebar
  collapse and mobile drawer. `prefers-reduced-motion: reduce` is respected
  sitewide (already in the token layer). Slow ambient loops (15–30s) are allowed
  **only** on the org-admin marketing landing page.

### Gradients
Sanctioned in exactly two places:
1. Small accent-gradient chips — avatar fallbacks, quick-action icon tiles,
   brand mark squares: `linear-gradient(135deg, var(--accent-9), var(--accent-11))`
   (`.ds-accent-chip`).
2. The org-admin marketing hero's ambient background glows.

Never as a full-page or full-card background in a dashboard.

---

## 7. Component contracts

These prop signatures are enforced by the design system's adherence lint. Any
local implementation of these components must match exactly — no extra props.

| Component | Props |
|-----------|-------|
| `Button` | `children, variant, color, size, disabled, icon, style, onClick` — `variant: solid \| soft \| outline \| ghost`; `color: accent \| red \| green` |
| `Badge` | `children, color, variant, size, style` — `color: gray \| accent \| green \| amber \| red \| blue`; `variant: soft \| solid \| outline` |
| `Input` | `placeholder, value, onChange, type, size, variant, icon, error, style` — `variant: outline \| filled` |
| `Textarea` | `placeholder, value, onChange, rows, variant, error, disabled, style` — `variant: outline \| filled` |
| `Checkbox` | `checked, defaultChecked, onChange, label, disabled, size, style` |
| `Radio` | `checked, onChange, label, disabled, name, value, size, style` |
| `StyledCard` | `children, padding, hover, interactive, style, onClick` — `hover: default \| lift \| glow \| none` |
| `StatCard` | `title, value, icon, change, changeLabel, trend` — `trend: up \| down \| neutral` |
| `QuickActionCard` | `title, description, icon, href, onClick` |
| `EmptyState` | `icon, title, description, action, size` — `size: sm \| md \| lg` |
| `PageHeader` | `title, description, breadcrumbs, actions` |
| `SidebarNavItem` | `icon, label, active, badge, onClick` |
| `Toast` | `variant, title, description, icon, onClose` — `variant: success \| error \| warning \| info` |

Reference implementations live in the Design project under
`components/{core,data-display,navigation,feedback}/` — fetch with
`DesignSync get_file` when you need the exact markup.

---

## 8. Forms (including login / register / onboarding)

Every form control in every app — including auth screens — must:

- Use the component contracts in §7 (or a Radix Themes equivalent themed by the
  same tokens). No bespoke one-off input styling.
- Label with the micro uppercase label treatment (`.ds-label`).
- Use `--radius-3`/`--radius-4` corners, 1px `--gray-a5` hairline border,
  `--gray-a3` hover tint.
- Show errors via `--status-danger-*`, never a raw red hex.
- Use the single 2px accent-alpha focus ring on text fields.
- Render currency as `K 125,430` in `.ds-amount` (Fira Code, tabular).
- Keep **mobile money (MTN / Airtel / Zamtel) always visible** on any payment
  form — never behind a "more options" disclosure.

Admin and Organization Admin login/register are served by **Keycloak themes**,
not Next.js pages:
`docker-resources/keycloak/themes/myticketzm-admin` and
`docker-resources/keycloak/themes/myticketzm-organizer`.
Those FreeMarker templates + CSS must be brought to the same token vocabulary
(clean single card, light-primary, teal). The Next.js `/login` routes that
initiate the Keycloak redirect must match visually.

---

## 9. Iconography

**Iconoir** (`iconoir-react`) is the only icon set — every icon in every app.
No custom icon font, no SVG sprite sheet, no PNG icons, no emoji in product UI.

Icons are sized inline (14–24px) and colored via `currentColor` /
`var(--gray-9)` / `var(--accent-11)` so they inherit surrounding color.

---

## 10. Content & copy

- **Voice**: direct and benefit-led on marketing; plain and operational inside
  dashboards.
- **Person**: second person on marketing/onboarding; third person / system-log
  phrasing inside dashboards.
- **Casing**: sentence case everywhere except micro labels.
- **Currency**: always `K 125,430` — Kwacha symbol, space, tabular figures.
  Never `ZMW`, never `$`.
- **Status language**: humanize enums — `PENDING_REVIEW` → "Pending Review",
  `PUBLISHED` → "Live". Admin actions read "Approve" / "Reject" /
  "Request Changes".
- **No emoji** in shipped UI.
- **No logo file exists.** Render the brand as a plain wordmark. Never invent a
  mark.

---

## 11. Verification checklist

Run per app before declaring done:

```bash
# 1. No raw hex in app source (only design-tokens.css may hold hex)
grep -rEn "#[0-9a-fA-F]{3,8}\b" apps/<app>/src --include="*.tsx" --include="*.ts"

# 2. No Tailwind default palette classes
grep -rEn "\b(text|bg|border|from|to|via|ring|divide|outline)-(blue|emerald|indigo|purple|violet|green|red|gray|slate|zinc|amber|orange|teal|cyan|pink|rose|sky|yellow|neutral|stone)-[0-9]{2,3}" apps/<app>/src --include="*.tsx"

# 3. No banned left-border accent cards
grep -rEn "border-l-[248]|borderLeft" apps/<app>/src --include="*.tsx"

# 4. Typecheck + build
npx tsc -p apps/<app>/tsconfig.json --noEmit --declarationMap false
pnpm nx build <app>
```

Grep results must be empty and the build must be green.

### Two pre-existing tsconfig quirks — not yours to fix, just don't be surprised

1. **`--declarationMap false` is required.** `tsconfig.base.json` sets
   `declarationMap: true` while the app tsconfigs set `composite: false`, so a
   bare `npx tsc -p … --noEmit` dies with `TS5069` before typechecking anything.
   The flag is a workaround, not a fix.
2. **`.test.tsx` files are typechecked but `.test.ts` files are not.** The app
   `exclude` lists `src/**/*.spec.ts` and `src/**/*.test.ts` but omits the
   `.tsx` variants. `apps/organization-admin` therefore reports **17 pre-existing
   errors** in `__tests__/*.test.tsx` (unused imports, `await` outside `async`).
   These are committed and unrelated to design work — treat a typecheck as clean
   if the only errors are in test files, and confirm with
   `... | grep -v "__tests__\|\.test\.\|\.spec\."`.

`pnpm nx build <app>` runs its own typecheck over app source only, so it is the
more trustworthy gate of the two.
