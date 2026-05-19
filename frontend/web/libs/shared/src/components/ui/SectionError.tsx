'use client';

/**
 * Professional Error Components for Sections
 *
 * These components provide elegant error handling per section/card,
 * allowing other parts of the UI to continue functioning.
 */

import { memo } from 'react';
import { Box, Flex, Text, Button, Card, Callout } from '@radix-ui/themes';

interface SectionErrorProps {
  /** Error message to display */
  message?: string;
  /** Detailed error for debugging (shown in dev mode) */
  error?: Error | string | null;
  /** Callback for retry action */
  onRetry?: () => void;
  /** Whether a retry is in progress */
  isRetrying?: boolean;
  /** Variant of error display */
  variant?: 'inline' | 'card' | 'callout';
  /** Size of the error display */
  size?: 'small' | 'medium' | 'large';
  /** Title for the error (optional) */
  title?: string;
}

/**
 * Inline error indicator - minimal, fits within existing UI flow
 */
export const SectionErrorInline = memo(function SectionErrorInline({
  message = 'Failed to load data',
  onRetry,
  isRetrying,
}: Pick<SectionErrorProps, 'message' | 'onRetry' | 'isRetrying'>) {
  return (
    <Flex align="center" gap="2" py="2">
      <Box
        style={{
          width: 6,
          height: 6,
          borderRadius: '50%',
          backgroundColor: 'var(--red-9)',
          flexShrink: 0,
        }}
      />
      <Text size="2" color="red" style={{ flex: 1 }}>
        {message}
      </Text>
      {onRetry && (
        <Button
          variant="ghost"
          color="red"
          size="1"
          onClick={onRetry}
          disabled={isRetrying}
        >
          {isRetrying ? 'Retrying...' : 'Retry'}
        </Button>
      )}
    </Flex>
  );
});

/**
 * Card-based error display - for sections that need more prominence
 */
export const SectionErrorCard = memo(function SectionErrorCard({
  message = 'Failed to load data',
  title,
  error,
  onRetry,
  isRetrying,
  size = 'medium',
}: SectionErrorProps) {
  const padding = size === 'small' ? '3' : size === 'large' ? '5' : '4';

  return (
    <Card
      size="2"
      style={{
        backgroundColor: 'var(--red-a2)',
        border: '1px solid var(--red-6)',
      }}
    >
      <Flex direction="column" gap="3" p={padding}>
        <Flex align="center" gap="3">
          <Box
            p="2"
            style={{
              backgroundColor: 'var(--red-a4)',
              borderRadius: 'var(--radius-2)',
            }}
          >
            <svg
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="var(--red-11)"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
              <line x1="12" y1="9" x2="12" y2="13" />
              <line x1="12" y1="17" x2="12.01" y2="17" />
            </svg>
          </Box>
          <Box style={{ flex: 1 }}>
            {title && (
              <Text size="2" weight="bold" style={{ display: 'block', color: 'var(--red-11)' }}>
                {title}
              </Text>
            )}
            <Text size="2" color="red">
              {message}
            </Text>
          </Box>
          {onRetry && (
            <Button
              variant="soft"
              color="red"
              size="1"
              onClick={onRetry}
              disabled={isRetrying}
            >
              {isRetrying ? 'Retrying...' : 'Retry'}
            </Button>
          )}
        </Flex>

        {/* Show error details in development */}
        {process.env.NODE_ENV === 'development' && error && (
          <Box
            p="2"
            style={{
              backgroundColor: 'var(--red-a3)',
              borderRadius: 'var(--radius-2)',
              fontFamily: 'monospace',
              fontSize: '11px',
              overflow: 'auto',
              maxHeight: 100,
            }}
          >
            <Text size="1" color="red">
              {typeof error === 'string' ? error : error.message}
            </Text>
          </Box>
        )}
      </Flex>
    </Card>
  );
});

/**
 * Callout-based error display - uses Radix Themes Callout
 */
export const SectionErrorCallout = memo(function SectionErrorCallout({
  message = 'Failed to load data',
  onRetry,
  isRetrying,
}: Pick<SectionErrorProps, 'message' | 'onRetry' | 'isRetrying'>) {
  return (
    <Callout.Root color="red" size="1" role="alert">
      <Callout.Icon>
        <svg
          width="16"
          height="16"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          <circle cx="12" cy="12" r="10" />
          <line x1="12" y1="8" x2="12" y2="12" />
          <line x1="12" y1="16" x2="12.01" y2="16" />
        </svg>
      </Callout.Icon>
      <Callout.Text>
        <Flex justify="between" align="center" gap="3">
          <Text>{message}</Text>
          {onRetry && (
            <Button
              variant="ghost"
              color="red"
              size="1"
              onClick={onRetry}
              disabled={isRetrying}
            >
              {isRetrying ? 'Retrying...' : 'Retry'}
            </Button>
          )}
        </Flex>
      </Callout.Text>
    </Callout.Root>
  );
});

/**
 * Main SectionError component - automatically selects variant
 */
export const SectionError = memo(function SectionError({
  variant = 'card',
  ...props
}: SectionErrorProps) {
  switch (variant) {
    case 'inline':
      return <SectionErrorInline {...props} />;
    case 'callout':
      return <SectionErrorCallout {...props} />;
    case 'card':
    default:
      return <SectionErrorCard {...props} />;
  }
});

/**
 * Empty state component for sections with no data
 */
export const SectionEmpty = memo(function SectionEmpty({
  message = 'No data available',
  icon,
}: {
  message?: string;
  icon?: React.ReactNode;
}) {
  return (
    <Flex direction="column" align="center" justify="center" py="6" gap="3">
      {icon && (
        <Box style={{ color: 'var(--gray-9)' }}>{icon}</Box>
      )}
      <Text size="2" color="gray">
        {message}
      </Text>
    </Flex>
  );
});

export default SectionError;
