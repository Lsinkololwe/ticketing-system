/**
 * Skeleton Components
 *
 * Reusable skeleton loading primitives for consistent loading states.
 * Uses CSS classes defined in global.css with shimmer animation.
 *
 * RADIX UI THEMES COMPLIANT:
 * - Uses Radix color tokens (--gray-a3, --gray-a5)
 * - Respects prefers-reduced-motion
 *
 * @example
 * ```tsx
 * import { Skeleton, SkeletonText, SkeletonInput, SkeletonCard } from '@/components/skeletons';
 *
 * // Basic usage
 * <Skeleton width="200px" height="40px" />
 *
 * // Text skeleton
 * <SkeletonText width="150px" />
 *
 * // Form input skeleton
 * <SkeletonInput />
 *
 * // Card with skeleton content
 * <SkeletonCard>
 *   <SkeletonText width="100px" />
 *   <SkeletonInput />
 * </SkeletonCard>
 * ```
 */

import { Box, Card, Flex, Grid } from '@radix-ui/themes';
import type { ReactNode, CSSProperties } from 'react';

// =============================================================================
// TYPES
// =============================================================================

interface SkeletonBaseProps {
  /** Width of the skeleton (CSS value) */
  width?: string;
  /** Height of the skeleton (CSS value) */
  height?: string;
  /** Additional CSS class names */
  className?: string;
  /** Additional inline styles */
  style?: CSSProperties;
}

interface SkeletonTextProps extends SkeletonBaseProps {
  /** Text size variant */
  size?: 'sm' | 'md' | 'lg';
}

interface SkeletonCardProps {
  /** Card content (skeleton elements) */
  children: ReactNode;
  /** Margin bottom (Radix spacing) */
  mb?: '1' | '2' | '3' | '4' | '5' | '6';
}

interface SkeletonFormFieldProps {
  /** Label width */
  labelWidth?: string;
  /** Whether to show helper text skeleton */
  showHelper?: boolean;
}

interface SkeletonFormRowProps {
  /** Number of columns */
  columns?: 1 | 2 | 3;
  /** Children skeleton fields */
  children: ReactNode;
}

// =============================================================================
// BASE SKELETON
// =============================================================================

/**
 * Base skeleton component - flexible building block
 */
export function Skeleton({
  width,
  height,
  className = '',
  style,
}: SkeletonBaseProps) {
  return (
    <Box
      className={`skeleton ${className}`}
      style={{ width, height, ...style }}
    />
  );
}

// =============================================================================
// TEXT SKELETONS
// =============================================================================

/**
 * Text skeleton - for text content placeholders
 */
export function SkeletonText({
  width = '100%',
  size = 'md',
  className = '',
  style,
}: SkeletonTextProps) {
  const sizeClass = {
    sm: 'skeleton-text-sm',
    md: 'skeleton-text',
    lg: 'skeleton-text-lg',
  }[size];

  return (
    <Box
      className={`skeleton ${sizeClass} ${className}`}
      style={{ width, ...style }}
    />
  );
}

/**
 * Heading skeleton - for heading placeholders
 */
export function SkeletonHeading({
  width = '200px',
  className = '',
  style,
}: SkeletonBaseProps) {
  return (
    <Box
      className={`skeleton skeleton-heading ${className}`}
      style={{ width, ...style }}
    />
  );
}

// =============================================================================
// FORM SKELETONS
// =============================================================================

/**
 * Input skeleton - for form input placeholders
 */
export function SkeletonInput({
  width = '100%',
  className = '',
  style,
}: SkeletonBaseProps) {
  return (
    <Box
      className={`skeleton skeleton-input ${className}`}
      style={{ width, ...style }}
    />
  );
}

/**
 * TextArea skeleton - for multiline input placeholders
 */
export function SkeletonTextArea({
  width = '100%',
  height = '100px',
  className = '',
  style,
}: SkeletonBaseProps) {
  return (
    <Box
      className={`skeleton skeleton-textarea ${className}`}
      style={{ width, height, ...style }}
    />
  );
}

/**
 * Complete form field skeleton - label + input
 */
export function SkeletonFormField({
  labelWidth = '100px',
  showHelper = false,
}: SkeletonFormFieldProps) {
  return (
    <Box>
      <SkeletonText size="sm" width={labelWidth} style={{ marginBottom: '8px' }} />
      <SkeletonInput />
      {showHelper && (
        <SkeletonText size="sm" width="180px" style={{ marginTop: '4px' }} />
      )}
    </Box>
  );
}

/**
 * TextArea form field skeleton - label + textarea
 */
export function SkeletonTextAreaField({
  labelWidth = '100px',
  showHelper = false,
}: SkeletonFormFieldProps) {
  return (
    <Box>
      <SkeletonText size="sm" width={labelWidth} style={{ marginBottom: '8px' }} />
      <SkeletonTextArea />
      {showHelper && (
        <SkeletonText size="sm" width="180px" style={{ marginTop: '4px' }} />
      )}
    </Box>
  );
}

/**
 * Form row skeleton - responsive grid of fields
 */
export function SkeletonFormRow({ columns = 2, children }: SkeletonFormRowProps) {
  const colConfig = {
    1: { initial: '1' as const },
    2: { initial: '1' as const, sm: '2' as const },
    3: { initial: '1' as const, sm: '3' as const },
  };

  return (
    <Grid columns={colConfig[columns]} gap="4">
      {children}
    </Grid>
  );
}

// =============================================================================
// BUTTON SKELETONS
// =============================================================================

/**
 * Button skeleton - for button placeholders
 */
export function SkeletonButton({
  width = '100px',
  className = '',
  style,
}: SkeletonBaseProps) {
  return (
    <Box
      className={`skeleton skeleton-button ${className}`}
      style={{ width, ...style }}
    />
  );
}

// =============================================================================
// ICON SKELETONS
// =============================================================================

/**
 * Icon skeleton - circular placeholder for icons
 */
export function SkeletonIcon({
  width = '20px',
  height = '20px',
  className = '',
  style,
}: SkeletonBaseProps) {
  return (
    <Box
      className={`skeleton skeleton-icon ${className}`}
      style={{ width, height, ...style }}
    />
  );
}

/**
 * Avatar skeleton - larger circular placeholder
 */
export function SkeletonAvatar({
  width = '40px',
  height = '40px',
  className = '',
  style,
}: SkeletonBaseProps) {
  return (
    <Box
      className={`skeleton skeleton-circle ${className}`}
      style={{ width, height, ...style }}
    />
  );
}

// =============================================================================
// CARD SKELETONS
// =============================================================================

/**
 * Card skeleton wrapper - matches Radix Card styling
 */
export function SkeletonCard({ children, mb = '6' }: SkeletonCardProps) {
  return (
    <Card mb={mb} variant="surface">
      <Box p="5">{children}</Box>
    </Card>
  );
}

/**
 * Card header skeleton - icon + title pattern
 */
export function SkeletonCardHeader({ titleWidth = '140px' }: { titleWidth?: string }) {
  return (
    <Flex align="center" gap="2" mb="4">
      <SkeletonIcon />
      <SkeletonText width={titleWidth} />
    </Flex>
  );
}

// =============================================================================
// PAGE SKELETONS
// =============================================================================

/**
 * Page header skeleton - title + description
 */
export function SkeletonPageHeader({
  titleWidth = '260px',
  descriptionWidth = '420px',
}: {
  titleWidth?: string;
  descriptionWidth?: string;
}) {
  return (
    <Box mb="6">
      <SkeletonHeading width={titleWidth} style={{ marginBottom: '8px' }} />
      <SkeletonText width={descriptionWidth} />
    </Box>
  );
}

/**
 * Navigation buttons skeleton - back + continue pattern
 */
export function SkeletonNavButtons({
  backWidth = '100px',
  continueWidth = '180px',
}: {
  backWidth?: string;
  continueWidth?: string;
}) {
  return (
    <Flex justify="between" align="center" gap="3" mt="6">
      <SkeletonButton width={backWidth} />
      <SkeletonButton width={continueWidth} />
    </Flex>
  );
}

export default Skeleton;
