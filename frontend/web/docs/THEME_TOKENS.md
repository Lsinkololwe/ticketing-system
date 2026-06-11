# Theme Tokens Reference

Complete reference for Radix UI theme tokens used in the Event Ticketing System.

---

## Color System

### Overview

Radix provides a comprehensive color system with:
- **13 color scales** (gray, red, green, blue, etc.)
- **12 steps per scale** (1=lightest, 12=darkest)
- **Automatic dark mode** adaptation
- **Alpha variants** for transparency

---

## Base Color Scales

### Gray Scale (Primary Text & Backgrounds)

| Token | Light Mode | Dark Mode | Usage |
|-------|------------|-----------|-------|
| `var(--gray-1)` | #fcfcfc | #111111 | App background |
| `var(--gray-2)` | #f9f9f9 | #191919 | Subtle background |
| `var(--gray-3)` | #f0f0f0 | #222222 | UI element background |
| `var(--gray-4)` | #e8e8e8 | #2a2a2a | Hovered UI element |
| `var(--gray-5)` | #e0e0e0 | #313131 | Active/selected element |
| `var(--gray-6)` | #d9d9d9 | #3a3a3a | Subtle borders/separators |
| `var(--gray-7)` | #cecece | #484848 | UI element border |
| `var(--gray-8)` | #bbbbbb | #606060 | Hovered border |
| `var(--gray-9)` | #8d8d8d | #6e6e6e | Solid backgrounds |
| `var(--gray-10)` | #838383 | #7b7b7b | Hovered solid backgrounds |
| `var(--gray-11)` | #646464 | #b4b4b4 | Secondary text |
| `var(--gray-12)` | #202020 | #eeeeee | Primary text |

**Alpha Variants:**
- `var(--gray-a1)` to `var(--gray-a12)` - Transparent versions
- Use for overlays, glassmorphism, subtle backgrounds

---

### Semantic Color Scales

#### Success (Green)

| Token | Light Mode | Dark Mode | Usage |
|-------|------------|-----------|-------|
| `var(--green-9)` | #30a46c | #46a758 | Success indicators, positive actions |
| `var(--green-11)` | #297c3b | #5bb98c | Success text |
| `var(--green-a3)` | rgba(0,200,80,0.12) | rgba(0,230,100,0.15) | Success backgrounds |

**Example Usage:**
```typescript
// Success button
<Button style={{ backgroundColor: 'var(--green-9)', color: 'white' }}>
  Approve
</Button>

// Success message
<Callout color="green">
  <Text>Operation successful!</Text>
</Callout>

// Subtle success background
<Box style={{ backgroundColor: 'var(--green-a2)' }}>
  Success state
</Box>
```

---

#### Error (Red)

| Token | Light Mode | Dark Mode | Usage |
|-------|------------|-----------|-------|
| `var(--red-9)` | #e5484d | #e54d2e | Error indicators, destructive actions |
| `var(--red-11)` | #cd2b31 | #ff6369 | Error text |
| `var(--red-a3)` | rgba(255,0,0,0.12) | rgba(255,50,50,0.15) | Error backgrounds |

**Example Usage:**
```typescript
// Delete button
<Button color="red" variant="solid">
  Delete Event
</Button>

// Error message
<Callout color="red">
  <Text>An error occurred</Text>
</Callout>

// Form field error
<TextField.Root color="red">
  <TextField.Input />
</TextField.Root>
```

---

#### Warning (Orange)

| Token | Light Mode | Dark Mode | Usage |
|-------|------------|-----------|-------|
| `var(--orange-9)` | #f76808 | #f76b15 | Warning indicators |
| `var(--orange-11)` | #cc4e00 | #ff8b3e | Warning text |
| `var(--orange-a3)` | rgba(255,100,0,0.12) | rgba(255,120,20,0.15) | Warning backgrounds |

**Example Usage:**
```typescript
// Warning badge
<Badge color="orange">Pending Review</Badge>

// Warning alert
<Callout color="orange">
  <Text>3 payout requests need attention</Text>
</Callout>
```

---

#### Info (Blue)

| Token | Light Mode | Dark Mode | Usage |
|-------|------------|-----------|-------|
| `var(--blue-9)` | #0090ff | #0090ff | Info indicators |
| `var(--blue-11)` | #0069bc | #3b9eff | Info text |
| `var(--blue-a3)` | rgba(0,144,255,0.12) | rgba(0,144,255,0.15) | Info backgrounds |

**Example Usage:**
```typescript
// Info callout
<Callout color="blue">
  <Text>5 events starting within 24 hours</Text>
</Callout>

// Info badge
<Badge color="blue">Draft</Badge>
```

---

### Brand Colors

#### Violet (Admin Portal)

| Token | Light Mode | Dark Mode | Usage |
|-------|------------|-----------|-------|
| `var(--violet-9)` | #8b5cf6 | #8b5cf6 | Primary brand color |
| `var(--violet-10)` | #8445f7 | #8e4ec6 | Hovered brand color |
| `var(--violet-11)` | #7938d4 | #a78bfa | Brand text |
| `var(--violet-a3)` | rgba(139,92,246,0.12) | rgba(139,92,246,0.15) | Subtle brand backgrounds |
| `var(--violet-a5)` | rgba(139,92,246,0.20) | rgba(139,92,246,0.25) | Brand borders |

**Example Usage:**
```typescript
// Primary button (Admin)
<Button
  style={{
    background: 'linear-gradient(135deg, var(--violet-9) 0%, var(--indigo-9) 100%)',
  }}
>
  Create Event
</Button>

// Active sidebar item
<Box
  style={{
    backgroundColor: 'var(--violet-a3)',
    borderLeft: '2px solid var(--violet-9)',
    color: 'var(--violet-11)',
  }}
>
  Dashboard
</Box>
```

---

#### Accent (Organization Portal - Emerald/Teal)

**Note:** Use `accent` color in Organization Admin for brand differentiation.

| Token | Light Mode | Dark Mode | Usage |
|-------|------------|-----------|-------|
| `var(--accent-9)` | #10b981 | #10b981 | Primary brand color |
| `var(--accent-10)` | #059669 | #14b8a6 | Hovered brand color |
| `var(--accent-11)` | #047857 | #2dd4bf | Brand text |
| `var(--accent-a3)` | rgba(16,185,129,0.12) | rgba(16,185,129,0.15) | Subtle brand backgrounds |

**Custom Brand Tokens:**

For consistency, define these in your theme:

```css
/* theme/brand-colors.css */
:root {
  --brand-primary: var(--accent-9);
  --brand-primary-hover: var(--accent-10);
  --brand-primary-text: var(--accent-11);
  --brand-primary-subtle: var(--accent-a3);
}
```

**Example Usage:**
```typescript
// Primary button (Organization)
<Button
  style={{
    background: 'linear-gradient(135deg, var(--accent-9) 0%, var(--accent-10) 100%)',
  }}
>
  Create Event
</Button>

// Active sidebar item
<Box
  style={{
    backgroundColor: 'var(--accent-a3)',
    borderLeft: '2px solid var(--accent-9)',
    color: 'var(--accent-11)',
  }}
>
  Dashboard
</Box>
```

---

## Spacing Scale

### Base Spacing Tokens

| Token | Value | Usage |
|-------|-------|-------|
| `0` | 0 | No spacing |
| `1` | 4px | Tight spacing, icons, badges |
| `2` | 8px | Small gaps between related items |
| `3` | 12px | Default gaps, compact layouts |
| `4` | 16px | Standard spacing, card padding (compact) |
| `5` | 24px | Medium spacing, sections |
| `6` | 32px | Large spacing, card padding (default) |
| `7` | 40px | XL spacing, page sections |
| `8` | 48px | 2XL spacing, major sections |
| `9` | 64px | 3XL spacing, hero sections |

### Spacing Props

**Padding:**
- `p` - All sides
- `px` - Horizontal (left + right)
- `py` - Vertical (top + bottom)
- `pt`, `pr`, `pb`, `pl` - Individual sides

**Margin:**
- `m` - All sides
- `mx` - Horizontal (left + right)
- `my` - Vertical (top + bottom)
- `mt`, `mr`, `mb`, `ml` - Individual sides

**Example Usage:**
```typescript
<Box p="6" mb="4" px="8">     // padding: 32px, margin-bottom: 16px, padding-left/right: 48px
<Flex gap="3">                 // gap: 12px
<Grid gap="4">                 // gap: 16px
```

---

## Typography Scale

### Heading Sizes

| Size | Font Size | Line Height | Usage |
|------|-----------|-------------|-------|
| `1` | 12px | 16px | Tiny heading |
| `2` | 14px | 20px | Small heading |
| `3` | 16px | 24px | Subsection heading |
| `4` | 18px | 26px | Section heading |
| `5` | 20px | 28px | Page subsection |
| `6` | 24px | 30px | Page title |
| `7` | 32px | 36px | Large page title |
| `8` | 40px | 44px | Hero title |
| `9` | 60px | 60px | Display title |

**Example Usage:**
```typescript
<Heading size="6">Page Title</Heading>
<Heading size="4">Section Title</Heading>
<Heading size="3">Subsection</Heading>
```

---

### Text Sizes

| Size | Font Size | Line Height | Usage |
|------|-----------|-------------|-------|
| `1` | 12px | 16px | Caption, helper text |
| `2` | 14px | 20px | Small body text |
| `3` | 15px | 24px | Default body text |
| `4` | 16px | 24px | Emphasized body text |
| `5` | 18px | 26px | Large body text |
| `6` | 20px | 28px | XL body text |
| `7` | 24px | 30px | 2XL body text |
| `8` | 32px | 36px | 3XL body text |
| `9` | 40px | 44px | 4XL body text |

**Example Usage:**
```typescript
<Text size="3">Default body text</Text>
<Text size="2" color="gray">Secondary text</Text>
<Text size="1">Caption</Text>
```

---

### Font Weights

| Prop | CSS Value | Usage |
|------|-----------|-------|
| `weight="light"` | 300 | Light headings |
| `weight="regular"` | 400 | Body text (default) |
| `weight="medium"` | 500 | Emphasized text |
| `weight="bold"` | 700 | Headings, labels |

**Example Usage:**
```typescript
<Heading weight="bold">Bold Heading</Heading>
<Text weight="medium">Emphasized text</Text>
<Text weight="regular">Normal text</Text>
```

---

## Radius (Border Radius)

| Token | Value | Usage |
|-------|-------|-------|
| `var(--radius-1)` | 3px | Small elements |
| `var(--radius-2)` | 4px | Buttons, badges |
| `var(--radius-3)` | 6px | Cards, inputs |
| `var(--radius-4)` | 8px | Large cards |
| `var(--radius-5)` | 12px | Prominent elements |
| `var(--radius-6)` | 16px | Hero elements |
| `var(--radius-full)` | 9999px | Circles, pills |

**Example Usage:**
```typescript
<Box style={{ borderRadius: 'var(--radius-3)' }}>
  Card content
</Box>

<Button style={{ borderRadius: 'var(--radius-2)' }}>
  Rounded button
</Button>

<Box style={{ borderRadius: 'var(--radius-full)' }}>
  Avatar/circle
</Box>
```

---

## Shadows (Elevation)

| Token | Value | Usage |
|-------|-------|-------|
| `var(--shadow-1)` | 0 1px 2px rgba(0,0,0,0.05) | Subtle elevation |
| `var(--shadow-2)` | 0 2px 8px rgba(0,0,0,0.05) | Cards |
| `var(--shadow-3)` | 0 4px 16px rgba(0,0,0,0.08) | Dropdowns |
| `var(--shadow-4)` | 0 8px 24px rgba(0,0,0,0.1) | Modals |
| `var(--shadow-5)` | 0 16px 48px rgba(0,0,0,0.12) | Large modals |
| `var(--shadow-6)` | 0 24px 64px rgba(0,0,0,0.15) | Hero elements |

**Example Usage:**
```typescript
<Box style={{ boxShadow: 'var(--shadow-2)' }}>
  Elevated card
</Box>

<Dialog.Content style={{ boxShadow: 'var(--shadow-4)' }}>
  Modal content
</Dialog.Content>
```

---

## Responsive Breakpoints

| Breakpoint | Min Width | Usage |
|------------|-----------|-------|
| `initial` | 0 | Mobile-first (default) |
| `xs` | 520px | Large phones |
| `sm` | 768px | Tablets |
| `md` | 1024px | Small laptops |
| `lg` | 1280px | Desktops |
| `xl` | 1640px | Large desktops |

**Example Usage:**
```typescript
<Box p={{ initial: '4', md: '7', lg: '9' }}>
  Responsive padding
</Box>

<Grid columns={{ initial: '1', sm: '2', lg: '4' }}>
  Responsive grid
</Grid>

<Heading size={{ initial: '6', md: '7', lg: '8' }}>
  Responsive heading
</Heading>
```

---

## Z-Index Layers

Recommended z-index values for consistent layering:

| Layer | Z-Index | Usage |
|-------|---------|-------|
| Base | 0 | Default content |
| Sticky | 10 | Sticky headers |
| Dropdown | 20 | Dropdown menus |
| Sidebar | 30 | Fixed sidebar |
| Header | 40 | Fixed header |
| Overlay | 50 | Modal overlays |
| Modal | 60 | Modal dialogs |
| Tooltip | 70 | Tooltips |
| Notification | 80 | Toast notifications |

**Example Usage:**
```typescript
<Box style={{ position: 'fixed', zIndex: 40 }}>
  Fixed header
</Box>

<Dialog.Overlay style={{ zIndex: 50 }} />
<Dialog.Content style={{ zIndex: 60 }} />
```

---

## Dark Mode

### Automatic Adaptation

Radix theme tokens automatically adapt to dark mode:

```typescript
// This works in both light and dark mode
<Box style={{ backgroundColor: 'var(--gray-2)', color: 'var(--gray-12)' }}>
  Content
</Box>
```

**Light mode:** Background is near-white, text is near-black
**Dark mode:** Background is near-black, text is near-white

### Manual Dark Mode Overrides

If you need to override dark mode styles:

```css
.my-component {
  background-color: var(--gray-2);
}

.dark .my-component {
  background-color: var(--gray-3); /* Slightly lighter in dark mode */
}
```

---

## Custom Theme Extensions

### Adding Custom Colors

For brand-specific colors not in Radix's scale:

```css
/* theme/custom-tokens.css */
:root {
  --brand-emerald: #10b981;
  --brand-violet: #8b5cf6;
  --brand-gold: #f59e0b;
}

.dark {
  --brand-emerald: #14b8a6;
  --brand-violet: #a78bfa;
  --brand-gold: #fbbf24;
}
```

**Usage:**
```typescript
<Box style={{ backgroundColor: 'var(--brand-emerald)' }}>
  Custom brand color
</Box>
```

---

### Adding Custom Spacing

For one-off spacing needs:

```css
/* theme/custom-tokens.css */
:root {
  --space-10: 80px;
  --space-12: 128px;
}
```

---

## Migration Cheat Sheet

### Common Replacements

```typescript
// Colors
'#8B5CF6' → 'var(--violet-9)'
'#10B981' → 'var(--accent-9)'
'#FFFFFF' → 'var(--gray-1)'
'#000000' → 'var(--gray-12)'

// Spacing
'padding: 24px' → 'p="6"'
'margin: 16px' → 'm="4"'
'gap: 12px' → 'gap="3"'

// Typography
'fontSize: 32px' → '<Heading size="7">'
'fontSize: 14px' → '<Text size="2">'
'fontWeight: 700' → 'weight="bold"'

// Borders
'border: 1px solid #ccc' → 'border: 1px solid var(--gray-a4)'
'borderRadius: 8px' → 'borderRadius: var(--radius-3)'

// Shadows
'boxShadow: 0 2px 8px rgba(0,0,0,0.1)' → 'boxShadow: var(--shadow-2)'
```

---

## Best Practices

1. **Always use theme tokens** instead of hardcoded values
2. **Prefer Radix props** (`p="6"`) over inline styles (`style={{ padding: '24px' }}`)
3. **Use semantic colors** (`color="gray"`) over token references when available
4. **Leverage responsive props** for mobile-first design
5. **Test in both light and dark mode** to ensure proper adaptation
6. **Document custom tokens** if you need to extend the theme

---

## Resources

- [Radix Themes Playground](https://themes.radix-ui.com/playground)
- [Radix Color Scales](https://www.radix-ui.com/colors)
- [Project Audit Report](../RADIX_UI_AUDIT_REPORT.md)
- [Migration Guide](./RADIX_MIGRATION_GUIDE.md)
