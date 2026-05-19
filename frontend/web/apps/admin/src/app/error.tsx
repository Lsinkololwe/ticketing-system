'use client';

import { useEffect } from 'react';
import { Box, Flex, Text, Heading, Button, Card, Callout } from '@radix-ui/themes';
import { WarningTriangle, Refresh, Home } from 'iconoir-react';

interface ErrorProps {
  error: Error & { digest?: string };
  reset: () => void;
}

export default function Error({ error, reset }: ErrorProps) {
  useEffect(() => {
    // Log error in development
    if (process.env.NODE_ENV !== 'production') {
      console.error('Route error:', error);
    }
  }, [error]);

  const handleGoHome = () => {
    if (typeof window !== 'undefined') {
      window.location.href = '/dashboard';
    }
  };

  return (
    <Flex
      align="center"
      justify="center"
      style={{ minHeight: '100vh', backgroundColor: 'var(--gray-2)', padding: 16 }}
    >
      <Card size="4" style={{ maxWidth: 600, width: '100%' }}>
        <Flex direction="column" align="center" gap="4" p="6">
          <Box
            p="4"
            style={{
              backgroundColor: 'var(--red-a3)',
              borderRadius: 'var(--radius-full)',
            }}
          >
            <WarningTriangle className="h-10 w-10" style={{ color: 'var(--red-11)' }} />
          </Box>

          <Heading size="5" align="center">Something went wrong</Heading>
          <Text size="3" color="gray" align="center">
            {error.message || 'An unexpected error occurred'}
          </Text>

          {/* Error details in development */}
          {process.env.NODE_ENV === 'development' && (
            <Callout.Root color="red" style={{ width: '100%' }}>
              <Callout.Icon>
                <WarningTriangle className="h-4 w-4" />
              </Callout.Icon>
              <Callout.Text>
                <Text size="2" weight="bold" style={{ display: 'block', marginBottom: 8 }}>
                  Error Details (Development Only):
                </Text>
                <Box
                  style={{
                    fontFamily: 'monospace',
                    fontSize: 12,
                    whiteSpace: 'pre-wrap',
                    wordBreak: 'break-all',
                  }}
                >
                  {error.name}: {error.message}
                </Box>
                {error.digest && (
                  <Text size="1" color="gray" style={{ display: 'block', marginTop: 8 }}>
                    Error ID: {error.digest}
                  </Text>
                )}
              </Callout.Text>
            </Callout.Root>
          )}

          {/* Action buttons */}
          <Flex gap="3" wrap="wrap" justify="center">
            <Button color="red" onClick={reset}>
              <Refresh className="h-4 w-4" />
              Try Again
            </Button>
            <Button variant="soft" onClick={handleGoHome}>
              <Home className="h-4 w-4" />
              Go to Dashboard
            </Button>
            <Button
              variant="soft"
              color="gray"
              onClick={() => {
                if (typeof window !== 'undefined') {
                  window.location.reload();
                }
              }}
            >
              <Refresh className="h-4 w-4" />
              Refresh Page
            </Button>
          </Flex>

          <Text size="2" color="gray" align="center">
            If this problem persists, please contact support.
          </Text>
        </Flex>
      </Card>
    </Flex>
  );
}
