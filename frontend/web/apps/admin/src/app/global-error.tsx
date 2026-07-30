'use client';

/**
 * Global error boundary.
 *
 * This route replaces the root layout entirely, so nothing from
 * `app/layout.tsx` is in scope — not the stylesheets, not <Providers>, not the
 * Radix <Theme>. The previous version worked around that with 17 hardcoded
 * hex values, which meant the one screen a user sees when everything else has
 * failed was also the one screen off-brand and stuck in light mode.
 *
 * Instead we re-establish the minimum: import both stylesheets and mount our
 * own <Theme> with the Admin brand context. Every colour then resolves through
 * the same tokens as the rest of the portal, and no hex is needed.
 *
 * `data-brand="admin"` is repeated on <html> here for the same reason.
 */

import '@radix-ui/themes/styles.css';
import './global.css';

import { useEffect } from 'react';
import { Box, Flex, Heading, Text, Theme } from '@radix-ui/themes';
import { Refresh, WarningTriangle } from 'iconoir-react';
import { Button } from '@/components/ui';

interface GlobalErrorProps {
  error: Error & { digest?: string };
  reset: () => void;
}

export default function GlobalError({ error, reset }: GlobalErrorProps) {
  useEffect(() => {
    if (process.env.NODE_ENV !== 'production') {
      console.error('Global error:', error);
    }
  }, [error]);

  return (
    <html lang="en" data-brand="admin" suppressHydrationWarning>
      <body>
        <Theme
          accentColor="teal"
          grayColor="slate"
          radius="medium"
          scaling="100%"
          panelBackground="solid"
          appearance="light"
        >
          <Flex
            align="center"
            justify="center"
            p="4"
            style={{
              minHeight: '100vh',
              background: 'var(--color-background)',
            }}
          >
            <Box
              p="6"
              style={{
                width: '100%',
                maxWidth: 600,
                background: 'var(--card-bg)',
                border: 'var(--card-border)',
                borderRadius: 'var(--card-radius-bento)',
                boxShadow: 'var(--shadow-4)',
              }}
            >
              <Flex direction="column" align="center" gap="3" mb="5">
                <Flex
                  align="center"
                  justify="center"
                  style={{
                    width: 56,
                    height: 56,
                    borderRadius: 'var(--radius-5)',
                    background: 'var(--status-danger-a3)',
                    color: 'var(--status-danger-11)',
                  }}
                >
                  <WarningTriangle style={{ width: 28, height: 28 }} />
                </Flex>

                <Heading
                  size="6"
                  align="center"
                  style={{ color: 'var(--gray-12)' }}
                >
                  The admin portal stopped responding
                </Heading>

                <Text size="2" align="center" style={{ color: 'var(--gray-11)' }}>
                  {error.message ||
                    'An unexpected error interrupted the page before it could load.'}
                </Text>

                <Text size="2" align="center" style={{ color: 'var(--gray-11)' }}>
                  Retrying usually clears it. If it does not, reload the page.
                </Text>
              </Flex>

              {process.env.NODE_ENV === 'development' && (
                <Box
                  p="4"
                  mb="5"
                  style={{
                    background: 'var(--status-danger-a3)',
                    border: '1px solid var(--status-danger-9)',
                    borderRadius: 'var(--card-radius)',
                  }}
                >
                  <Text
                    className="ds-label"
                    as="p"
                    style={{ color: 'var(--status-danger-11)' }}
                  >
                    Error details — development only
                  </Text>
                  <Box
                    asChild
                    mt="2"
                    style={{
                      whiteSpace: 'pre-wrap',
                      wordBreak: 'break-all',
                      fontFamily: 'var(--font-mono)',
                      fontSize: 'var(--text-1-size)',
                      color: 'var(--status-danger-11)',
                      margin: 0,
                    }}
                  >
                    <pre>
                      {error.name}: {error.message}
                    </pre>
                  </Box>
                  {error.digest && (
                    <Text
                      size="1"
                      mt="2"
                      className="ds-amount"
                      style={{ display: 'block', color: 'var(--gray-11)' }}
                    >
                      Error ID: {error.digest}
                    </Text>
                  )}
                </Box>
              )}

              <Flex direction="column" align="center" gap="3">
                <Button
                  color="red"
                  data-testid="global-error-retry-button"
                  icon={<Refresh style={{ width: 16, height: 16 }} />}
                  onClick={reset}
                >
                  Try again
                </Button>

                <Button
                  variant="outline"
                  color="red"
                  data-testid="global-error-reload-button"
                  icon={<Refresh style={{ width: 16, height: 16 }} />}
                  onClick={() => {
                    if (typeof window !== 'undefined') {
                      window.location.reload();
                    }
                  }}
                >
                  Reload the page
                </Button>
              </Flex>

              <Text
                size="1"
                align="center"
                mt="5"
                style={{ display: 'block', color: 'var(--gray-11)' }}
              >
                If this keeps happening, contact support with the error ID above.
              </Text>
            </Box>
          </Flex>
        </Theme>
      </body>
    </html>
  );
}
