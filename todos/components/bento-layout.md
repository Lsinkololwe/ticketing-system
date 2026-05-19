# Bento Layout Design System

## Overview

The bento layout is a modern grid-based design pattern inspired by Apple's design language, creating visually interesting dashboard layouts with cards of varying sizes.

## Grid Configuration

### Base Grid
```tsx
<Grid
  columns={{ initial: "1", sm: "2", md: "3", lg: "4" }}
  gap="4"
  style={{ gridAutoRows: 'minmax(120px, auto)' }}
>
  {children}
</Grid>
```

### Breakpoints
| Breakpoint | Columns | Min Width |
|------------|---------|-----------|
| initial    | 1       | 0px       |
| sm         | 2       | 640px     |
| md         | 3       | 768px     |
| lg         | 4       | 1024px    |
| xl         | 4       | 1280px    |

## Card Sizes

### Standard Sizes

#### Small Card (1x1)
```tsx
<BentoCard>
  {/* 1 column, 1 row - perfect for single stats */}
</BentoCard>
```

#### Wide Card (2x1)
```tsx
<BentoCard span={{ col: 2 }}>
  {/* 2 columns, 1 row - good for charts, lists */}
</BentoCard>
```

#### Tall Card (1x2)
```tsx
<BentoCard span={{ row: 2 }}>
  {/* 1 column, 2 rows - activity feeds, leaderboards */}
</BentoCard>
```

#### Large Card (2x2)
```tsx
<BentoCard span={{ col: 2, row: 2 }}>
  {/* 2 columns, 2 rows - main feature cards, charts */}
</BentoCard>
```

#### Full Width Card (4x1)
```tsx
<BentoCard span={{ col: 4 }}>
  {/* Full width - data tables, timelines */}
</BentoCard>
```

## Component Implementation

### BentoGrid Component
```tsx
'use client';

import { Grid, Box } from '@radix-ui/themes';
import { ReactNode } from 'react';

interface BentoGridProps {
  children: ReactNode;
  columns?: { initial?: string; sm?: string; md?: string; lg?: string };
  gap?: '1' | '2' | '3' | '4' | '5';
}

export function BentoGrid({
  children,
  columns = { initial: "1", sm: "2", md: "3", lg: "4" },
  gap = "4"
}: BentoGridProps) {
  return (
    <Grid
      columns={columns}
      gap={gap}
      style={{ gridAutoRows: 'minmax(120px, auto)' }}
    >
      {children}
    </Grid>
  );
}
```

### BentoCard Component
```tsx
'use client';

import { Card, Box } from '@radix-ui/themes';
import { ReactNode, CSSProperties } from 'react';

interface BentoCardProps {
  children: ReactNode;
  span?: {
    col?: 1 | 2 | 3 | 4;
    row?: 1 | 2 | 3;
  };
  variant?: 'surface' | 'classic' | 'ghost';
  padding?: '0' | '1' | '2' | '3' | '4' | '5';
  className?: string;
}

export function BentoCard({
  children,
  span,
  variant = 'surface',
  padding = '4',
  className
}: BentoCardProps) {
  const style: CSSProperties = {};

  if (span?.col) {
    style.gridColumn = `span ${span.col}`;
  }
  if (span?.row) {
    style.gridRow = `span ${span.row}`;
  }

  return (
    <Card variant={variant} style={style} className={className}>
      <Box p={padding} style={{ height: '100%' }}>
        {children}
      </Box>
    </Card>
  );
}
```

### StatCard Component
```tsx
'use client';

import { Card, Flex, Text, Heading, Box } from '@radix-ui/themes';
import { ReactNode } from 'react';
import { TrendingUp, TrendingDown } from 'lucide-react';

interface StatCardProps {
  title: string;
  value: string | number;
  change?: {
    value: number;
    trend: 'up' | 'down';
  };
  icon?: ReactNode;
  color?: 'blue' | 'green' | 'red' | 'orange' | 'purple' | 'gray';
  description?: string;
}

export function StatCard({
  title,
  value,
  change,
  icon,
  color = 'blue',
  description
}: StatCardProps) {
  return (
    <Card>
      <Flex direction="column" gap="2" p="4" style={{ height: '100%' }}>
        <Flex justify="between" align="start">
          <Text size="2" color="gray" weight="medium">
            {title}
          </Text>
          {icon && (
            <Box
              p="2"
              style={{
                backgroundColor: `var(--${color}-a3)`,
                borderRadius: 'var(--radius-2)',
                color: `var(--${color}-11)`,
              }}
            >
              {icon}
            </Box>
          )}
        </Flex>

        <Heading size="6" style={{ fontVariantNumeric: 'tabular-nums' }}>
          {value}
        </Heading>

        {change && (
          <Flex align="center" gap="1">
            {change.trend === 'up' ? (
              <TrendingUp size={14} style={{ color: 'var(--green-11)' }} />
            ) : (
              <TrendingDown size={14} style={{ color: 'var(--red-11)' }} />
            )}
            <Text
              size="1"
              color={change.trend === 'up' ? 'green' : 'red'}
              weight="medium"
            >
              {change.value}%
            </Text>
            <Text size="1" color="gray">
              vs last period
            </Text>
          </Flex>
        )}

        {description && (
          <Text size="1" color="gray">
            {description}
          </Text>
        )}
      </Flex>
    </Card>
  );
}
```

## Layout Examples

### Dashboard Overview
```tsx
<BentoGrid>
  {/* Large chart - top left */}
  <BentoCard span={{ col: 2, row: 2 }}>
    <RevenueChart />
  </BentoCard>

  {/* Stats - top right column */}
  <StatCard title="Revenue" value="$12,450" color="green" />
  <StatCard title="Bookings" value="156" color="blue" />

  {/* Activity feed - right side tall */}
  <BentoCard span={{ row: 2 }}>
    <ActivityFeed />
  </BentoCard>

  {/* Quick actions */}
  <BentoCard>
    <QuickActions />
  </BentoCard>

  {/* Wide card - upcoming events */}
  <BentoCard span={{ col: 2 }}>
    <UpcomingEvents />
  </BentoCard>

  {/* Full width table */}
  <BentoCard span={{ col: 4 }}>
    <RecentBookings />
  </BentoCard>
</BentoGrid>
```

### Visual Layout
```
┌────────────────┬────────┬────────┐
│                │  Stat  │  Stat  │
│    Chart       ├────────┼────────┤
│    (2x2)       │ Quick  │Activity│
│                │Actions │  (1x2) │
├────────┬───────┴────────┤        │
│        │    Upcoming    │        │
│  Stat  │    Events      │        │
│        │     (2x1)      │        │
├────────┴────────────────┴────────┤
│          Recent Bookings (4x1)     │
└──────────────────────────────────┘
```

## Color System

### Accent Colors
| Color  | Usage |
|--------|-------|
| blue   | Primary actions, info, navigation |
| green  | Success, revenue, positive trends |
| red    | Errors, refunds, negative trends |
| orange | Warnings, pending states |
| purple | Events, creativity, premium |
| gray   | Neutral, disabled states |

### Background Patterns
```tsx
// Light tinted background
backgroundColor: `var(--${color}-a3)`

// Icon/accent color
color: `var(--${color}-11)`

// Softer text color
color: `var(--${color}-9)`
```

## Responsive Behavior

### Mobile (< 640px)
- All cards stack vertically (1 column)
- Wide cards take full width
- Tall cards maintain aspect ratio
- Remove horizontal scrolling

### Tablet (640px - 1024px)
- 2-column grid
- Large cards span full width
- Maintain card minimum heights

### Desktop (> 1024px)
- Full 4-column grid
- Cards span as designed
- Maximum content width: 1440px

## Animation Guidelines

### Card Hover
```css
.bento-card {
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.bento-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-4);
}
```

### Loading States
```tsx
<BentoCard>
  <Skeleton height="100%" />
</BentoCard>
```

### Enter Animations
```tsx
// Staggered fade-in for cards
const variants = {
  hidden: { opacity: 0, y: 20 },
  visible: (i: number) => ({
    opacity: 1,
    y: 0,
    transition: { delay: i * 0.1 }
  })
};
```

## Best Practices

1. **Visual Hierarchy**: Place most important content in larger cards
2. **Balance**: Distribute card sizes evenly to avoid visual weight on one side
3. **Whitespace**: Use consistent gap spacing (16px recommended)
4. **Content Density**: Don't overcrowd cards - one primary message per card
5. **Mobile First**: Design layouts that collapse gracefully
6. **Performance**: Lazy load charts and complex visualizations
7. **Accessibility**: Ensure sufficient color contrast, keyboard navigation
