# Radix UI Compliance Testing Guide

This guide explains how to run and interpret the Radix UI compliance tests.

## Table of Contents

- [Overview](#overview)
- [Test Types](#test-types)
- [Running Tests Locally](#running-tests-locally)
- [Understanding Test Results](#understanding-test-results)
- [Fixing Violations](#fixing-violations)
- [Best Practices](#best-practices)

## Overview

The compliance testing suite ensures that all components:
- Use Radix UI primitives instead of raw HTML elements
- Follow theming standards with proper token usage
- Meet WCAG 2.1 AA/AAA accessibility requirements
- Maintain visual consistency across the application

## Test Types

### 1. React Testing Library Tests

Located in: `libs/shared/src/__tests__/compliance/`

These tests scan components for:
- Raw HTML elements (button, input, div, etc.)
- Inline style violations
- Hardcoded colors and spacing
- Missing accessibility attributes

### 2. Playwright E2E Tests

Located in: `apps/organization-admin/e2e/compliance/`

These tests verify:
- Visual consistency across pages
- Dark mode compatibility
- Responsive design at all breakpoints
- Interactive element behavior

### 3. ESLint Rules

Located in: `tools/eslint-plugin-radix-compliance/`

Custom ESLint rules that catch:
- Inline style props
- Raw HTML components
- Hardcoded color values
- Non-standard spacing/sizing

## Running Tests Locally

### Run All Compliance Tests

```bash
cd frontend/web

# Run unit tests
pnpm test libs/shared/src/__tests__/compliance --run

# Run E2E tests
pnpm e2e:org-admin apps/organization-admin/e2e/compliance

# Run ESLint
pnpm lint:all
```

### Run Specific Test Suites

```bash
# Radix UI component compliance
pnpm test libs/shared/src/__tests__/compliance/radix-ui-compliance.test.tsx --run

# Theme compliance
pnpm test libs/shared/src/__tests__/compliance/theme-compliance.test.tsx --run

# Accessibility compliance
pnpm test libs/shared/src/__tests__/compliance/accessibility-compliance.test.tsx --run

# E2E theme consistency
pnpm e2e:org-admin apps/organization-admin/e2e/compliance/theme-consistency.spec.ts

# E2E Radix components
pnpm e2e:org-admin apps/organization-admin/e2e/compliance/radix-components.spec.ts

# E2E responsive design
pnpm e2e:org-admin apps/organization-admin/e2e/compliance/responsive-design.spec.ts
```

### Run in Watch Mode

```bash
# Unit tests in watch mode
pnpm test libs/shared/src/__tests__/compliance --watch

# E2E tests in headed mode (see browser)
pnpm e2e:org-admin:headed apps/organization-admin/e2e/compliance
```

## Understanding Test Results

### Unit Test Output

```bash
✓ should not use raw HTML buttons (2ms)
✗ should not have inline color styles
  Expected: 0
  Received: 3
  Found 3 elements with inline color styles. Use Radix UI color tokens instead.
```

Interpretation:
- Passed: Component correctly uses Radix UI Button
- Failed: Component has 3 inline color styles that need to be replaced

### E2E Test Output

```bash
✓ should use Radix UI Button components
✗ should not have inline styles
  Found 5 elements with inline styles
```

Interpretation:
- Passed: All buttons are Radix components
- Failed: 5 elements have inline styles (check screenshots)

### ESLint Output

```bash
/src/components/MyComponent.tsx
  12:7  error  Avoid inline color styles. Use Radix UI color prop instead  radix-compliance/no-inline-styles
  15:9  warn   Use <Button> from @radix-ui/themes instead of raw <button>  radix-compliance/require-radix-components
```

Interpretation:
- Line 12: Inline color style (must fix)
- Line 15: Raw button element (should fix)

## Fixing Violations

### 1. Replace Raw HTML Elements

Before:
```tsx
<button onClick={handleClick}>Submit</button>
<input type="text" placeholder="Name" />
<div style={{ display: 'flex', gap: '16px' }}>Content</div>
```

After:
```tsx
import { Button, TextField, Flex } from '@radix-ui/themes';

<Button onClick={handleClick}>Submit</Button>
<TextField.Root placeholder="Name" />
<Flex gap="2">Content</Flex>
```

### 2. Remove Inline Styles

Before:
```tsx
<div style={{ padding: '24px', color: '#8B5CF6', fontSize: '18px' }}>
  Text content
</div>
```

After:
```tsx
import { Box, Text } from '@radix-ui/themes';

<Box p="4">
  <Text color="violet" size="5">Text content</Text>
</Box>
```

### 3. Use Radix Color Tokens

Before:
```tsx
<div style={{ color: '#8B5CF6' }}>Violet text</div>
<div style={{ backgroundColor: 'rgb(139, 92, 246)' }}>Violet background</div>
```

After:
```tsx
import { Text, Box } from '@radix-ui/themes';

<Text color="violet">Violet text</Text>
<Box style={{ backgroundColor: 'var(--violet-9)' }}>Violet background</Box>
```

### 4. Use Radix Spacing Scale

Before:
```tsx
<div style={{ padding: '24px', margin: '16px', gap: '8px' }}>Content</div>
```

After:
```tsx
import { Flex } from '@radix-ui/themes';

<Flex p="4" m="3" gap="2">Content</Flex>
```

Radix spacing scale: 1-9 (1 = 4px, 2 = 8px, 3 = 12px, 4 = 16px, etc.)

### 5. Add Accessibility Attributes

Before:
```tsx
<button>
  <svg>
    <path d="..." />
  </svg>
</button>
```

After:
```tsx
import { IconButton } from '@radix-ui/themes';

<IconButton aria-label="Delete item">
  <svg aria-hidden="true">
    <path d="..." />
  </svg>
</IconButton>
```

## Best Practices

### 1. Component Development Workflow

1. Always import Radix UI components first
2. Use TypeScript autocomplete to discover available props
3. Run ESLint as you code (`pnpm lint:all`)
4. Test component in isolation before integration
5. Run compliance tests before committing

### 2. Common Patterns

#### Form Fields

```tsx
import { TextField, TextArea, Select } from '@radix-ui/themes';

<form>
  <label>
    Name
    <TextField.Root placeholder="Enter name" />
  </label>

  <label>
    Description
    <TextArea placeholder="Enter description" />
  </label>

  <label>
    Category
    <Select.Root>
      <Select.Trigger />
      <Select.Content>
        <Select.Item value="1">Option 1</Select.Item>
      </Select.Content>
    </Select.Root>
  </label>
</form>
```

#### Cards and Layouts

```tsx
import { Card, Flex, Box, Heading, Text } from '@radix-ui/themes';

<Card>
  <Flex direction="column" gap="3">
    <Heading size="6">Card Title</Heading>
    <Text size="2" color="gray">Card description</Text>
    <Box>Card content</Box>
  </Flex>
</Card>
```

#### Responsive Design

```tsx
import { Flex, Box } from '@radix-ui/themes';

<Flex
  direction={{ initial: 'column', md: 'row' }}
  gap={{ initial: '2', md: '4' }}
>
  <Box width={{ initial: '100%', md: '50%' }}>Column 1</Box>
  <Box width={{ initial: '100%', md: '50%' }}>Column 2</Box>
</Flex>
```

### 3. Testing Checklist

Before committing:
- [ ] All ESLint violations fixed
- [ ] No inline styles in components
- [ ] No hardcoded colors or spacing
- [ ] All interactive elements have ARIA labels
- [ ] Components use Radix UI primitives
- [ ] Tests pass locally
- [ ] Visual regression tests updated (if UI changed)

### 4. Debugging Failed Tests

1. Read the error message carefully
2. Check the line number and file path
3. Look at the expected vs. received values
4. Run the specific test in watch mode
5. Use Playwright's trace viewer for E2E tests

```bash
# Debug specific test
pnpm test libs/shared/src/__tests__/compliance/radix-ui-compliance.test.tsx --watch

# Debug E2E with trace
pnpm e2e:org-admin:debug apps/organization-admin/e2e/compliance/theme-consistency.spec.ts
```

## CI/CD Integration

The compliance tests run automatically on every pull request:

1. ESLint checks run first (fastest)
2. Unit tests run in parallel
3. E2E tests run last (slowest)

If violations are found:
- PR comment is posted with details
- Workflow fails (must fix before merge)
- Links to documentation provided

## Resources

- [Radix UI Themes Documentation](https://www.radix-ui.com/themes/docs)
- [Compliance Standards](./COMPLIANCE_STANDARDS.md)
- [ESLint Plugin README](../tools/eslint-plugin-radix-compliance/README.md)
- [Radix UI Audit Report](../RADIX_UI_AUDIT_REPORT.md)

## Getting Help

If you're stuck:
1. Check the [Compliance Standards](./COMPLIANCE_STANDARDS.md) document
2. Review the [Radix UI Audit Report](../RADIX_UI_AUDIT_REPORT.md) for examples
3. Look at existing compliant components in the codebase
4. Ask in the team chat or create a discussion thread

## Appendix: Full Test Commands

```bash
# All unit tests
pnpm test libs/shared/src/__tests__/compliance --run

# All E2E tests
pnpm e2e:org-admin apps/organization-admin/e2e/compliance

# ESLint
pnpm lint:all

# Coverage report
pnpm test libs/shared/src/__tests__/compliance --run --coverage

# E2E with HTML report
pnpm e2e:org-admin apps/organization-admin/e2e/compliance --reporter=html
```
