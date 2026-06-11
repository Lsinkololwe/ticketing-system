# Radix UI Migration Guide

This guide provides step-by-step instructions for migrating components to full Radix UI compliance.

---

## Quick Reference: Component Mapping

### Layout Components

| HTML/Custom | Radix UI Equivalent | Notes |
|-------------|---------------------|-------|
| `<div>` | `<Box>` | Use for single-child containers |
| `<div style={{display: 'flex'}}>` | `<Flex>` | Flexbox with props |
| `<div style={{display: 'grid'}}>` | `<Grid>` | Grid with responsive columns |
| `<section>` | `<Section>` | Semantic section wrapper |
| `<Container>` | `<Container>` | Max-width centered container |

### Typography

| HTML/Custom | Radix UI Equivalent | Notes |
|-------------|---------------------|-------|
| `<h1>` - `<h6>` | `<Heading size="1-9">` | Semantic headings |
| `<p>`, `<span>` | `<Text>` | Body text |
| `<code>` | `<Code>` | Inline code |
| `<kbd>` | `<Kbd>` | Keyboard shortcuts |
| `<blockquote>` | `<Blockquote>` | Quotes |

### Form Components

| HTML/Custom | Radix UI Equivalent | Notes |
|-------------|---------------------|-------|
| `<input type="text">` | `<TextField.Root><TextField.Input /></TextField.Root>` | Text input |
| `<textarea>` | `<TextArea>` | Multi-line input |
| `<select>` | `<Select.Root>` | Dropdown select |
| `<input type="checkbox">` | `<Checkbox>` | Checkbox |
| `<input type="radio">` | `<RadioGroup.Root>` | Radio buttons |
| `<button>` | `<Button>` | Action button |
| `<form>` | Native `<form>` | Keep as-is |

### Feedback Components

| Component | Radix UI | Usage |
|-----------|----------|-------|
| Loading spinner | `<Spinner>` | Loading states |
| Alert/Toast | `<Callout>` | Inline messages |
| Badge | `<Badge>` | Status indicators |
| Progress bar | `<Progress>` | Progress indication |
| Skeleton | `<Skeleton>` | Loading placeholders |

### Overlay Components

| Component | Radix UI | Usage |
|-----------|----------|-------|
| Modal | `<Dialog.Root>` | Modal dialogs |
| Confirm dialog | `<AlertDialog.Root>` | Confirmation prompts |
| Dropdown menu | `<DropdownMenu.Root>` | Action menus |
| Context menu | `<ContextMenu.Root>` | Right-click menus |
| Popover | `<Popover.Root>` | Floating content |
| Tooltip | `<Tooltip>` | Hover hints |

---

## Migration Patterns

### Pattern 1: Replace `<div>` with `<Box>`

**Before:**
```typescript
<div style={{ padding: '24px', backgroundColor: '#1a1a1a' }}>
  <h1>Title</h1>
</div>
```

**After:**
```typescript
<Box p="6" style={{ backgroundColor: 'var(--gray-2)' }}>
  <Heading size="6">Title</Heading>
</Box>
```

**Changes:**
- `<div>` → `<Box>`
- Hardcoded `24px` → Radix spacing `p="6"`
- Hardcoded color → Radix token `var(--gray-2)`
- `<h1>` → `<Heading size="6">`

---

### Pattern 2: Replace Flex Containers

**Before:**
```typescript
<div
  style={{
    display: 'flex',
    flexDirection: 'column',
    gap: '16px',
    alignItems: 'center',
  }}
>
  {children}
</div>
```

**After:**
```typescript
<Flex direction="column" gap="4" align="center">
  {children}
</Flex>
```

**Benefits:**
- Cleaner, more declarative API
- Responsive props support
- Type-safe

---

### Pattern 3: Replace Grid Layouts

**Before:**
```typescript
<div
  style={{
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
    gap: '16px',
  }}
>
  {items.map(item => <Card key={item.id} {...item} />)}
</div>
```

**After:**
```typescript
<Grid columns={{ initial: '1', sm: '2', lg: '4' }} gap="4">
  {items.map(item => <Card key={item.id} {...item} />)}
</Grid>
```

**Benefits:**
- Responsive breakpoints built-in
- Simpler syntax
- Consistent with design system

---

### Pattern 4: Replace Hardcoded Colors

**Before:**
```typescript
<Box
  style={{
    backgroundColor: '#8B5CF6',
    color: '#FFFFFF',
    border: '1px solid rgba(139, 92, 246, 0.3)',
  }}
>
  Content
</Box>
```

**After:**
```typescript
<Box
  style={{
    backgroundColor: 'var(--violet-9)',
    color: 'var(--gray-1)',
    border: '1px solid var(--violet-a4)',
  }}
>
  Content
</Box>
```

**Color Token Guide:**
- Solid colors: `var(--color-1)` to `var(--color-12)` (1=lightest, 12=darkest)
- Alpha colors: `var(--color-a1)` to `var(--color-a12)` (for transparency)
- Text: `var(--gray-12)` (primary), `var(--gray-11)` (secondary)
- Backgrounds: `var(--gray-1)` (base), `var(--gray-2)` (elevated)

---

### Pattern 5: Replace Hardcoded Spacing

**Before:**
```typescript
<Box
  style={{
    padding: '32px',
    marginBottom: '24px',
    paddingLeft: '16px',
  }}
>
  Content
</Box>
```

**After:**
```typescript
<Box p="7" mb="6" pl="4">
  Content
</Box>
```

**Spacing Scale:**
```
0 = 0
1 = 4px
2 = 8px
3 = 12px
4 = 16px
5 = 24px
6 = 32px
7 = 40px
8 = 48px
9 = 64px
```

---

### Pattern 6: Replace Custom Buttons

**Before:**
```typescript
<button
  onClick={handleClick}
  style={{
    padding: '12px 24px',
    backgroundColor: '#10B981',
    color: 'white',
    border: 'none',
    borderRadius: '8px',
    cursor: 'pointer',
  }}
>
  Click Me
</button>
```

**After:**
```typescript
<Button
  onClick={handleClick}
  size="3"
  style={{
    background: 'linear-gradient(135deg, var(--accent-9) 0%, var(--accent-10) 100%)',
  }}
>
  Click Me
</Button>
```

**Button Variants:**
- `variant="solid"` - Filled button (default)
- `variant="soft"` - Subtle background
- `variant="outline"` - Outlined button
- `variant="ghost"` - Minimal button

---

### Pattern 7: Replace Form Inputs

**Before:**
```typescript
<input
  type="text"
  placeholder="Enter email"
  value={email}
  onChange={(e) => setEmail(e.target.value)}
  style={{
    padding: '12px',
    border: '1px solid #ccc',
    borderRadius: '6px',
  }}
/>
```

**After:**
```typescript
<TextField.Root>
  <TextField.Input
    placeholder="Enter email"
    value={email}
    onChange={(e) => setEmail(e.target.value)}
  />
</TextField.Root>
```

**TextField Props:**
- `size="1" | "2" | "3"` - Input size
- `variant="classic" | "surface" | "soft"` - Visual style
- `color="gray" | "red" | "green"` - Semantic color

---

### Pattern 8: Replace Typography

**Before:**
```typescript
<div>
  <h1 style={{ fontSize: '32px', fontWeight: 700, color: '#1a1a1a' }}>
    Welcome
  </h1>
  <p style={{ fontSize: '14px', color: '#666' }}>
    Description text
  </p>
</div>
```

**After:**
```typescript
<Box>
  <Heading size="7" weight="bold">
    Welcome
  </Heading>
  <Text size="2" color="gray">
    Description text
  </Text>
</Box>
```

**Typography Scale:**

**Heading sizes:** `1` (smallest) to `9` (largest)
- `size="9"` → 60px (hero)
- `size="7"` → 32px (page title)
- `size="5"` → 20px (section)
- `size="3"` → 16px (subsection)

**Text sizes:** `1` (smallest) to `9` (largest)
- `size="3"` → 15px (body)
- `size="2"` → 14px (small body)
- `size="1"` → 12px (caption)

---

### Pattern 9: Replace Custom Badges

**Before:**
```typescript
<span
  style={{
    padding: '4px 8px',
    backgroundColor: '#10B981',
    color: 'white',
    borderRadius: '4px',
    fontSize: '12px',
  }}
>
  Active
</span>
```

**After:**
```typescript
<Badge color="green" variant="soft" size="1">
  Active
</Badge>
```

**Badge Colors:**
- `color="green"` - Success states
- `color="red"` - Error states
- `color="orange"` - Warning states
- `color="blue"` - Info states
- `color="gray"` - Neutral states

**Badge Variants:**
- `variant="soft"` - Subtle background (default)
- `variant="solid"` - Solid color
- `variant="outline"` - Outlined

---

### Pattern 10: Replace Loading States

**Before:**
```typescript
{loading ? (
  <div style={{ textAlign: 'center', padding: '40px' }}>
    <div className="spinner" />
    <p>Loading...</p>
  </div>
) : (
  <Content />
)}
```

**After:**
```typescript
{loading ? (
  <Flex direction="column" align="center" p="8" gap="3">
    <Spinner size="3" />
    <Text color="gray">Loading...</Text>
  </Flex>
) : (
  <Content />
)}
```

---

## Advanced Patterns

### Pattern A: Responsive Design

**Before:**
```typescript
<div
  style={{
    padding: '16px',
    '@media (min-width: 768px)': {
      padding: '32px',
    },
  }}
>
  Content
</div>
```

**After:**
```typescript
<Box p={{ initial: '4', md: '7' }}>
  Content
</Box>
```

**Breakpoints:**
- `initial` - Mobile (default)
- `xs` - 520px
- `sm` - 768px
- `md` - 1024px
- `lg` - 1280px
- `xl` - 1640px

---

### Pattern B: Dark Mode Support

**Before:**
```typescript
<div
  style={{
    backgroundColor: isDarkMode ? '#1a1a1a' : '#ffffff',
    color: isDarkMode ? '#ffffff' : '#1a1a1a',
  }}
>
  Content
</div>
```

**After:**
```typescript
<Box
  style={{
    backgroundColor: 'var(--gray-1)',
    color: 'var(--gray-12)',
  }}
>
  Content
</Box>
```

**How It Works:**
- Radix theme tokens automatically adapt to dark mode
- No manual theme switching needed
- Consistent across all components

---

### Pattern C: Extract Complex Styles

For complex styling that can't be replaced with Radix props, extract to CSS:

**Before:**
```typescript
<div
  style={{
    background: 'linear-gradient(135deg, #8B5CF6 0%, #6366F1 100%)',
    boxShadow: '0 0 30px rgba(139, 92, 246, 0.4)',
    borderRadius: '12px',
    padding: '24px',
  }}
>
  Content
</div>
```

**After:**

**Component:**
```typescript
<Box className="brand-gradient-box" p="6">
  Content
</Box>
```

**CSS (global.css or module.css):**
```css
.brand-gradient-box {
  background: linear-gradient(135deg, var(--violet-9) 0%, var(--indigo-9) 100%);
  box-shadow: 0 0 30px var(--violet-a5);
  border-radius: var(--radius-3);
}
```

---

## Common Pitfalls

### ❌ Pitfall 1: Using `style` for everything

**Bad:**
```typescript
<Box style={{ padding: '24px', margin: '16px' }}>
```

**Good:**
```typescript
<Box p="6" m="4">
```

---

### ❌ Pitfall 2: Mixing units

**Bad:**
```typescript
<Box p="6" style={{ marginBottom: '20px' }}>
```

**Good:**
```typescript
<Box p="6" mb="5">
```

---

### ❌ Pitfall 3: Hardcoding breakpoints

**Bad:**
```typescript
<Box style={{ padding: window.innerWidth > 768 ? '32px' : '16px' }}>
```

**Good:**
```typescript
<Box p={{ initial: '4', md: '7' }}>
```

---

### ❌ Pitfall 4: Not using semantic tokens

**Bad:**
```typescript
<Text style={{ color: '#666666' }}>
```

**Good:**
```typescript
<Text color="gray">
```

---

## Migration Checklist

Use this checklist when refactoring a component:

### Pre-Migration
- [ ] Read the existing component code
- [ ] Identify all hardcoded values (colors, spacing, typography)
- [ ] Check for responsive design needs
- [ ] Document any custom styling that can't be replaced

### During Migration
- [ ] Replace HTML elements with Radix components
- [ ] Convert hardcoded spacing to Radix scale
- [ ] Replace hardcoded colors with theme tokens
- [ ] Update typography to use `<Heading>` and `<Text>`
- [ ] Add responsive props where needed
- [ ] Extract complex styles to CSS if needed

### Post-Migration
- [ ] Test in light mode
- [ ] Test in dark mode
- [ ] Test responsive breakpoints
- [ ] Verify accessibility (keyboard navigation, screen readers)
- [ ] Update component documentation
- [ ] Run linters and type checking

---

## Migration Example: Complete Component

### Before: Custom Card Component

```typescript
// CustomCard.tsx
interface CardProps {
  title: string;
  value: string;
  icon: React.ReactNode;
  trend: 'up' | 'down';
}

export function CustomCard({ title, value, icon, trend }: CardProps) {
  return (
    <div
      style={{
        padding: '24px',
        backgroundColor: '#1a1a1a',
        border: '1px solid #333',
        borderRadius: '12px',
      }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '16px' }}>
        <span style={{ fontSize: '14px', color: '#999' }}>{title}</span>
        <div
          style={{
            padding: '8px',
            backgroundColor: trend === 'up' ? '#10B981' : '#EF4444',
            borderRadius: '8px',
          }}
        >
          {icon}
        </div>
      </div>
      <h2 style={{ fontSize: '32px', fontWeight: 700, color: '#fff' }}>{value}</h2>
    </div>
  );
}
```

### After: Radix UI Compliant

```typescript
// StatCard.tsx
import { Box, Flex, Text, Heading, Badge } from '@radix-ui/themes';

interface StatCardProps {
  title: string;
  value: string;
  icon: React.ReactNode;
  trend: 'up' | 'down';
}

export function StatCard({ title, value, icon, trend }: StatCardProps) {
  return (
    <Box
      p="6"
      style={{
        backgroundColor: 'var(--gray-2)',
        border: '1px solid var(--gray-a4)',
        borderRadius: 'var(--radius-3)',
      }}
    >
      <Flex justify="between" mb="4">
        <Text size="2" color="gray">{title}</Text>
        <Box
          p="2"
          style={{
            backgroundColor: trend === 'up' ? 'var(--green-a3)' : 'var(--red-a3)',
            borderRadius: 'var(--radius-2)',
          }}
        >
          {icon}
        </Box>
      </Flex>
      <Heading size="7" weight="bold">{value}</Heading>
    </Box>
  );
}
```

**Changes Made:**
1. ✅ Replaced `<div>` with `<Box>`, `<Flex>`
2. ✅ Used Radix spacing props (`p="6"`, `mb="4"`)
3. ✅ Replaced hardcoded colors with theme tokens
4. ✅ Used `<Text>` and `<Heading>` for typography
5. ✅ Leveraged Radix color scale (`var(--green-a3)`)
6. ✅ Used Radix radius tokens (`var(--radius-3)`)

---

## Next Steps

After completing migration:

1. **Test thoroughly** - Light/dark mode, responsive breakpoints
2. **Update documentation** - Document any component-specific patterns
3. **Share learnings** - Contribute patterns to this guide
4. **Set up linting** - Prevent future violations

---

## Resources

- [Radix Themes Documentation](https://www.radix-ui.com/themes/docs)
- [Radix Color System](https://www.radix-ui.com/themes/docs/theme/color)
- [Radix Layout Components](https://www.radix-ui.com/themes/docs/components/flex)
- [Project Theme Tokens Reference](./THEME_TOKENS.md)
