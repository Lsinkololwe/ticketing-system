# eslint-plugin-radix-compliance

ESLint plugin to enforce Radix UI and theming standards in your React application.

## Installation

This is a local plugin, no installation needed. It's already part of your monorepo.

## Usage

Add the plugin to your ESLint configuration:

```javascript
// eslint.config.mjs
import radixCompliance from './tools/eslint-plugin-radix-compliance/lib/index.js';

export default [
  {
    plugins: {
      'radix-compliance': radixCompliance,
    },
    rules: {
      'radix-compliance/no-inline-styles': 'error',
      'radix-compliance/require-radix-components': 'warn',
      'radix-compliance/no-hardcoded-colors': 'error',
      'radix-compliance/use-theme-tokens': 'warn',
    },
  },
];
```

Or use the recommended configuration:

```javascript
export default [
  {
    ...radixCompliance.configs.recommended,
  },
];
```

## Rules

### `no-inline-styles`

Disallows inline style props. Encourages using Radix UI props instead.

Bad:

```jsx
<div style={{ padding: '24px', color: '#8B5CF6' }}>Content</div>
```

Good:

```jsx
<Box p="4" color="violet">Content</Box>
```

### `require-radix-components`

Requires using Radix UI components instead of raw HTML elements.

Bad:

```jsx
<button>Click me</button>
<input type="text" />
<div style={{ display: 'flex' }}>Layout</div>
```

Good:

```jsx
<Button>Click me</Button>
<TextField.Root />
<Flex>Layout</Flex>
```

### `no-hardcoded-colors`

Disallows hardcoded color values (hex, rgb, hsl, named colors).

Bad:

```jsx
<div style={{ color: '#8B5CF6' }}>Violet text</div>
<div style={{ backgroundColor: 'rgb(139, 92, 246)' }}>Violet background</div>
```

Good:

```jsx
<Text color="violet">Violet text</Text>
<Box style={{ backgroundColor: 'var(--violet-9)' }}>Violet background</Box>
```

### `use-theme-tokens`

Requires using Radix UI theme tokens for spacing, sizing, and typography.

Bad:

```jsx
<div style={{ fontSize: '18px', padding: '24px', margin: '16px' }}>Content</div>
```

Good:

```jsx
<Text size="5" p="4" m="3">Content</Text>
```

## Configuration Presets

### Recommended (Default)

Enables all rules with sensible defaults:
- `no-inline-styles`: error
- `require-radix-components`: warn
- `no-hardcoded-colors`: error
- `use-theme-tokens`: warn

### Strict

All rules as errors for maximum compliance:
- `no-inline-styles`: error
- `require-radix-components`: error
- `no-hardcoded-colors`: error
- `use-theme-tokens`: error

## Examples

### Before (Violations)

```jsx
function Dashboard() {
  return (
    <div style={{ display: 'flex', gap: '24px', padding: '32px' }}>
      <section style={{ backgroundColor: '#F3F4F6', borderRadius: '8px' }}>
        <h1 style={{ fontSize: '24px', fontWeight: 'bold', color: '#1F2937' }}>
          Dashboard
        </h1>
        <button style={{ padding: '12px 24px', backgroundColor: '#8B5CF6', color: 'white' }}>
          Click me
        </button>
        <input type="text" placeholder="Search..." style={{ padding: '8px' }} />
      </section>
    </div>
  );
}
```

### After (Compliant)

```jsx
import { Flex, Card, Heading, Button, TextField } from '@radix-ui/themes';

function Dashboard() {
  return (
    <Flex gap="4" p="5">
      <Card>
        <Heading size="8" weight="bold" color="gray">
          Dashboard
        </Heading>
        <Button size="3" color="violet">
          Click me
        </Button>
        <TextField.Root placeholder="Search..." size="2" />
      </Card>
    </Flex>
  );
}
```

## Integration with CI/CD

Add to your CI workflow:

```yaml
- name: Run ESLint
  run: pnpm lint:all
```

This will automatically check for Radix UI compliance violations in pull requests.

## License

MIT
