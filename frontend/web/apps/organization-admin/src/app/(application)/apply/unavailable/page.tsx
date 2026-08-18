'use client';

/**
 * Application Unavailable
 *
 * Where the onboarding guard sends a user whose organization status could not be
 * established — backend unreachable, access-token refresh failed, or a status
 * value this build does not recognise.
 *
 * ## Why this page exists
 *
 * The alternative — and what the app did before — is to treat "I could not find
 * out" as "there is nothing to find" and render the setup wizard. For a user who
 * has already submitted an application and is waiting on a reviewer, that is the
 * worst possible answer: it tells them their work is gone and invites them to
 * start again. A retry screen that admits uncertainty is strictly better than a
 * confident wrong answer.
 *
 * Deliberately offers no path into the application form. The only actions are
 * retry and contact support.
 */

import { useCallback, useState, useTransition } from 'react';
import { useRouter } from 'next/navigation';
import { Box, Flex, Text, Heading, Button, Card } from '@radix-ui/themes';
import { WarningTriangle, Refresh, Mail } from 'iconoir-react';

export default function ApplicationUnavailablePage() {
  const router = useRouter();
  const [isPending, startTransition] = useTransition();
  const [attempts, setAttempts] = useState(0);

  const retry = useCallback(() => {
    setAttempts((n) => n + 1);
    // `refresh()` re-runs the server layout, which re-resolves the onboarding
    // state. If the backend has recovered the guard redirects onward from there.
    startTransition(() => router.refresh());
  }, [router]);

  return (
    <Flex
      role="main"
      direction="column"
      align="center"
      justify="center"
      minHeight="calc(100vh - 132px)"
      px={{ initial: '4', sm: '6' }}
      data-testid="application-unavailable"
    >
      <Card size="3" style={{ maxWidth: '480px', width: '100%', textAlign: 'center' }}>
        <Flex direction="column" align="center" gap="4" p="4">
          <Flex
            align="center"
            justify="center"
            width="56px"
            height="56px"
            aria-hidden="true"
            style={{
              borderRadius: 'var(--radius-5)',
              backgroundColor: 'var(--amber-a3)',
              color: 'var(--amber-11)',
            }}
          >
            <WarningTriangle width={26} height={26} strokeWidth={1.5} />
          </Flex>

          <Box>
            <Heading as="h1" size="5" weight="bold" highContrast mb="2">
              We couldn&apos;t load your application
            </Heading>
            <Text as="p" size="2" color="gray">
              This is a problem on our side, not with your application. Nothing you
              submitted has been lost. Try again in a moment.
            </Text>
          </Box>

          {/* Announced so screen-reader users learn the retry did something, since
              a successful retry navigates away and a failed one looks identical. */}
          <Box aria-live="polite" role="status">
            {attempts > 0 && !isPending && (
              <Text as="p" size="1" color="gray">
                Still unavailable. Last checked just now.
              </Text>
            )}
          </Box>

          <Flex gap="3" mt="2" wrap="wrap" justify="center">
            <Button
              size="3"
              variant="solid"
              color="teal"
              onClick={retry}
              loading={isPending}
              data-testid="retry-button"
            >
              <Refresh width={16} height={16} aria-hidden="true" />
              Try again
            </Button>
            <Button
              size="3"
              variant="outline"
              color="gray"
              asChild
              data-testid="contact-support-button"
            >
              <a href="mailto:support@myticket.zm">
                <Mail width={16} height={16} aria-hidden="true" />
                Contact support
              </a>
            </Button>
          </Flex>
        </Flex>
      </Card>
    </Flex>
  );
}
