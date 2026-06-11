# Radix UI Compliance Standards

This document defines the compliance standards for using Radix UI and theming in the application.

## Table of Contents

- [Component Standards](#component-standards)
- [Theming Standards](#theming-standards)
- [Accessibility Standards](#accessibility-standards)
- [Responsive Design Standards](#responsive-design-standards)
- [Testing Standards](#testing-standards)

## Component Standards

### Rule 1: Use Radix UI Components, Not Raw HTML

All UI elements must use Radix UI primitives from `@radix-ui/themes`.

#### Interactive Elements

| Instead of | Use |
|-----------|-----|
| `<button>` | `<Button>` |
| `<input>` | `<TextField.Root>` |
| `<textarea>` | `<TextArea>` |
| `<select>` | `<Select.Root>` + `<Select.Trigger>` + `<Select.Content>` |
| `<a>` | `<Link>` |

Example:
```tsx
// ❌ Bad
<button onClick={handleClick}>Submit</button>

// ✅ Good
import { Button } from '@radix-ui/themes';
<Button onClick={handleClick}>Submit</Button>
```

#### Typography Elements

| Instead of | Use |
|-----------|-----|
| `<h1>` - `<h6>` | `<Heading>` with `as` prop |
| `<p>` | `<Text>` with `as="p"` |
| `<span>` | `<Text>` |
| `<strong>` | `<Text weight="bold">` |
| `<em>` | `<Em>` |

Example:
```tsx
// ❌ Bad
<h1 style={{ fontSize: '32px' }}>Title</h1>
<p>Paragraph text</p>

// ✅ Good
import { Heading, Text } from '@radix-ui/themes';
<Heading as="h1" size="8">Title</Heading>
<Text as="p">Paragraph text</Text>
```

#### Layout Elements

| Instead of | Use |
|-----------|-----|
| `<div>` with flexbox | `<Flex>` |
| `<div>` with grid | `<Grid>` |
| `<div>` generic container | `<Box>` |
| `<section>` | `<Card>` or `<Container>` |
| `<article>` | `<Card>` |

Example:
```tsx
// ❌ Bad
<div style={{ display: 'flex', gap: '16px', padding: '24px' }}>
  <div>Content</div>
</div>

// ✅ Good
import { Flex, Box } from '@radix-ui/themes';
<Flex gap="2" p="4">
  <Box>Content</Box>
</Flex>
```

### Rule 2: No Inline Styles

Inline styles are prohibited. Use Radix UI props instead.

#### Color

```tsx
// ❌ Bad
<div style={{ color: '#8B5CF6' }}>Text</div>
<div style={{ backgroundColor: 'rgb(139, 92, 246)' }}>Background</div>

// ✅ Good
import { Text, Box } from '@radix-ui/themes';
<Text color="violet">Text</Text>
<Box style={{ backgroundColor: 'var(--violet-9)' }}>Background</Box>
```

#### Spacing

```tsx
// ❌ Bad
<div style={{ padding: '24px', margin: '16px', gap: '8px' }}>Content</div>

// ✅ Good
import { Flex } from '@radix-ui/themes';
<Flex p="4" m="3" gap="2">Content</Flex>
```

#### Typography

```tsx
// ❌ Bad
<div style={{ fontSize: '18px', fontWeight: 'bold', lineHeight: '1.5' }}>Text</div>

// ✅ Good
import { Text } from '@radix-ui/themes';
<Text size="5" weight="bold">Text</Text>
```

## Theming Standards

### Color System

Use Radix UI color tokens. Do not hardcode colors.

#### Available Colors

Primary colors: `violet`, `blue`, `green`, `red`, `orange`, `yellow`
Neutral colors: `gray`, `mauve`, `slate`, `sage`, `olive`, `sand`

#### Color Scale

Each color has a scale from 1-12:
- 1-3: Backgrounds
- 4-6: Borders and separators
- 7-9: Solid colors and text
- 10-12: High contrast text

Example:
```tsx
import { Text, Box } from '@radix-ui/themes';

// Using color prop
<Text color="violet">Violet text</Text>
<Text color="gray" highContrast>High contrast gray</Text>

// Using CSS variables
<Box style={{ backgroundColor: 'var(--violet-3)' }}>Light violet background</Box>
<Text style={{ color: 'var(--violet-11)' }}>High contrast violet text</Text>
```

### Spacing Scale

Radix UI spacing scale: 1-9

| Scale | Value | Use Case |
|-------|-------|----------|
| 1 | 4px | Tight spacing (icons, badges) |
| 2 | 8px | Small spacing (button padding) |
| 3 | 12px | Medium spacing (card padding) |
| 4 | 16px | Standard spacing (section padding) |
| 5 | 24px | Large spacing (page margins) |
| 6 | 32px | XL spacing (hero sections) |
| 7 | 40px | XXL spacing (layout gaps) |
| 8 | 48px | XXXL spacing (page sections) |
| 9 | 64px | Huge spacing (landing pages) |

Example:
```tsx
import { Flex, Box } from '@radix-ui/themes';

<Flex gap="3" p="4">  {/* gap: 12px, padding: 16px */}
  <Box m="2">Item 1</Box>  {/* margin: 8px */}
  <Box m="5">Item 2</Box>  {/* margin: 24px */}
</Flex>
```

### Typography Scale

Radix UI size scale for text: 1-9

| Size | Font Size | Use Case |
|------|-----------|----------|
| 1 | 12px | Small labels, captions |
| 2 | 14px | Body text (default) |
| 3 | 16px | Emphasized body text |
| 4 | 18px | Small headings |
| 5 | 20px | Subheadings |
| 6 | 24px | H3 headings |
| 7 | 28px | H2 headings |
| 8 | 35px | H1 headings |
| 9 | 60px | Hero text |

Example:
```tsx
import { Heading, Text } from '@radix-ui/themes';

<Heading size="8">Page Title</Heading>
<Heading size="6">Section Title</Heading>
<Text size="3">Body text</Text>
<Text size="1" color="gray">Small caption</Text>
```

### Font Weights

| Weight | Value | Use Case |
|--------|-------|----------|
| light | 300 | Subtle text |
| regular | 400 | Body text (default) |
| medium | 500 | Emphasized text |
| bold | 700 | Headings, important text |

Example:
```tsx
import { Text, Heading } from '@radix-ui/themes';

<Text weight="regular">Normal text</Text>
<Text weight="medium">Medium emphasis</Text>
<Heading weight="bold">Bold heading</Heading>
```

## Accessibility Standards

### WCAG 2.1 AA Compliance (Minimum)

All components must meet WCAG 2.1 Level AA standards. Aim for AAA where possible.

#### 1. Color Contrast

| Level | Normal Text | Large Text |
|-------|------------|------------|
| AA | 4.5:1 | 3:1 |
| AAA | 7:1 | 4.5:1 |

Example:
```tsx
import { Text } from '@radix-ui/themes';

// ✅ Good - Uses highContrast for proper contrast
<Text color="gray" highContrast>High contrast text</Text>

// ⚠️ Warning - Low contrast, avoid for important text
<Text color="gray">Low contrast text</Text>
```

#### 2. Keyboard Navigation

All interactive elements must be keyboard accessible.

```tsx
// ✅ Good - Radix components have built-in keyboard support
import { Button, TextField } from '@radix-ui/themes';
<Button>Focusable</Button>
<TextField.Root />

// ❌ Bad - Div with click handler, not keyboard accessible
<div onClick={handleClick}>Click me</div>

// ✅ Good - Proper role and keyboard handlers
<div
  role="button"
  tabIndex={0}
  onClick={handleClick}
  onKeyDown={(e) => e.key === 'Enter' && handleClick()}
>
  Click me
</div>
```

#### 3. ARIA Labels

All interactive elements must have accessible names.

```tsx
import { IconButton, TextField } from '@radix-ui/themes';

// ✅ Good - Icon button with label
<IconButton aria-label="Delete item">
  <TrashIcon aria-hidden="true" />
</IconButton>

// ✅ Good - Form field with label
<label>
  Email
  <TextField.Root type="email" required />
</label>

// ✅ Good - Using aria-labelledby
<TextField.Root
  id="email"
  aria-labelledby="email-label"
  aria-describedby="email-error"
/>
<label id="email-label">Email</label>
<Text id="email-error" color="red">Invalid email</Text>
```

#### 4. Focus Indicators

All focusable elements must have visible focus indicators.

```tsx
// ✅ Good - Radix components have built-in focus styles
import { Button, TextField } from '@radix-ui/themes';
<Button>Has focus ring</Button>
<TextField.Root />

// Custom focus styles if needed
<Button className="custom-focus">
  Custom focus
</Button>

/* CSS */
.custom-focus:focus-visible {
  outline: 2px solid var(--focus-8);
  outline-offset: 2px;
}
```

#### 5. Touch Target Size

All interactive elements must be at least 44x44px (WCAG 2.1 AAA).

```tsx
import { Button, IconButton } from '@radix-ui/themes';

// ✅ Good - Minimum size 2 (32px) for mobile, size 3 (40px) recommended
<Button size="3">Large enough</Button>

// ⚠️ Warning - Size 1 may be too small for touch
<IconButton size="1" aria-label="Small icon">
  <Icon />
</IconButton>
```

#### 6. Semantic HTML

Use proper heading hierarchy and semantic elements.

```tsx
import { Heading } from '@radix-ui/themes';

// ✅ Good - Proper heading hierarchy
<Heading as="h1" size="8">Page Title</Heading>
<Heading as="h2" size="6">Section Title</Heading>
<Heading as="h3" size="4">Subsection Title</Heading>

// ❌ Bad - Skipped h2, jumped to h3
<Heading as="h1" size="8">Page Title</Heading>
<Heading as="h3" size="4">Subsection Title</Heading>
```

## Responsive Design Standards

### Breakpoints

| Breakpoint | Min Width | Use Case |
|-----------|-----------|----------|
| initial | 0px | Mobile (default) |
| xs | 520px | Large mobile |
| sm | 768px | Tablet |
| md | 1024px | Desktop |
| lg | 1280px | Large desktop |
| xl | 1640px | Extra large |

### Responsive Props

Use Radix UI responsive object syntax.

```tsx
import { Flex, Box, Heading } from '@radix-ui/themes';

// Responsive direction
<Flex
  direction={{ initial: 'column', md: 'row' }}
  gap={{ initial: '2', md: '4' }}
>
  <Box width={{ initial: '100%', md: '50%' }}>Column 1</Box>
  <Box width={{ initial: '100%', md: '50%' }}>Column 2</Box>
</Flex>

// Responsive typography
<Heading
  size={{ initial: '6', md: '8' }}
  weight={{ initial: 'medium', md: 'bold' }}
>
  Responsive Heading
</Heading>
```

### Mobile-First Approach

Always design for mobile first, then enhance for larger screens.

```tsx
// ✅ Good - Mobile first, enhanced for desktop
<Flex
  direction={{ initial: 'column', md: 'row' }}
  p={{ initial: '3', md: '5' }}
  gap={{ initial: '2', md: '4' }}
>
  <Box>Content</Box>
</Flex>

// ❌ Bad - Desktop first, broken on mobile
<Flex direction="row" p="5" gap="4">
  <Box>Content</Box>
</Flex>
```

### Touch-Friendly

Ensure interactive elements are large enough for touch on mobile.

```tsx
import { Button } from '@radix-ui/themes';

// ✅ Good - Size adapts to viewport
<Button size={{ initial: '3', md: '2' }}>
  Touch-friendly on mobile
</Button>
```

## Testing Standards

### Unit Tests

All components must have compliance tests.

```tsx
import { render } from '@testing-library/react';
import { Theme } from '@radix-ui/themes';
import { scanComponentForViolations } from '@/lib/__tests__/compliance';

describe('MyComponent', () => {
  it('should pass Radix UI compliance tests', () => {
    const violations = scanComponentForViolations(<MyComponent />);

    expect(violations.rawButtons).toBe(0);
    expect(violations.rawInputs).toBe(0);
    expect(violations.radixComponents).toBeGreaterThan(0);
  });
});
```

### E2E Tests

Pages must have visual regression tests.

```ts
import { test, expect } from '@playwright/test';

test('dashboard should match baseline', async ({ page }) => {
  await page.goto('/dashboard');
  await page.waitForLoadState('networkidle');

  await expect(page).toHaveScreenshot('dashboard.png', {
    fullPage: true,
    maxDiffPixelRatio: 0.02,
  });
});
```

### ESLint

All code must pass ESLint with radix-compliance rules.

```bash
pnpm lint:all
```

## Enforcement

These standards are enforced through:

1. **ESLint** - Catches violations during development
2. **Unit Tests** - Prevents violations from being committed
3. **E2E Tests** - Ensures visual consistency
4. **CI/CD** - Blocks PRs with violations from merging

## Resources

- [Radix UI Themes Documentation](https://www.radix-ui.com/themes/docs)
- [WCAG 2.1 Guidelines](https://www.w3.org/WAI/WCAG21/quickref/)
- [Compliance Testing Guide](./COMPLIANCE_TESTING.md)
- [ESLint Plugin README](../tools/eslint-plugin-radix-compliance/README.md)

## Updates

This document is updated as standards evolve. Last updated: 2026-06-07
