'use client';

/**
 * ReviewField Component
 *
 * Displays a labeled value for review purposes.
 * Shows a dash for empty/null values.
 *
 * @example
 * ```tsx
 * <ReviewField label="Organization Name" value={organization.name} />
 * <ReviewField label="Website" value={organization.website} emptyText="Not provided" />
 * ```
 */

import { ReactNode } from 'react';
import { Box, Text } from '@radix-ui/themes';

// =============================================================================
// TYPES
// =============================================================================

export interface ReviewFieldProps {
  /** Field label */
  label: string;
  /** Field value (string, number, or null/undefined) */
  value: string | number | null | undefined;
  /** Text to show when value is empty (default: '-') */
  emptyText?: string;
  /** Custom renderer for the value */
  renderValue?: (value: string | number | null | undefined) => ReactNode;
  /** Custom styles */
  style?: React.CSSProperties;
}

// =============================================================================
// COMPONENT
// =============================================================================

export function ReviewField({
  label,
  value,
  emptyText = '-',
  renderValue,
  style,
}: ReviewFieldProps) {
  const displayValue = value != null && value !== '' ? value : emptyText;
  const hasValue = value != null && value !== '';

  return (
    <Box style={{ marginBottom: '12px', ...style }}>
      <Text
        size="1"
        style={{
          color: 'var(--content-tertiary)',
          display: 'block',
          marginBottom: '4px',
        }}
      >
        {label}
      </Text>
      <Text
        size="2"
        style={{
          color: hasValue ? 'var(--content-primary)' : 'var(--content-muted)',
          fontStyle: hasValue ? 'normal' : 'italic',
        }}
      >
        {renderValue ? renderValue(value) : displayValue}
      </Text>
    </Box>
  );
}

// =============================================================================
// REVIEW FIELD GRID
// =============================================================================

export interface ReviewFieldGridProps {
  /** Minimum width of each column (default: 200px) */
  minColumnWidth?: string;
  /** Gap between items (default: 16px) */
  gap?: string;
  /** Children (ReviewField components) */
  children: ReactNode;
  /** Custom styles */
  style?: React.CSSProperties;
}

/**
 * ReviewFieldGrid Component
 *
 * Responsive grid layout for ReviewField components.
 */
export function ReviewFieldGrid({
  minColumnWidth = '200px',
  gap = '16px',
  children,
  style,
}: ReviewFieldGridProps) {
  return (
    <Box
      style={{
        display: 'grid',
        gridTemplateColumns: `repeat(auto-fit, minmax(${minColumnWidth}, 1fr))`,
        gap,
        ...style,
      }}
    >
      {children}
    </Box>
  );
}

export default ReviewField;
