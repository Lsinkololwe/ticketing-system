'use client';

import React, { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import {
  Box,
  Flex,
  Text,
  Heading,
  Button,
  Container,
  Spinner,
  Separator,
} from '@radix-ui/themes';
import { Mail, SmartphoneDevice } from 'iconoir-react';
import { useKeycloak } from '@pml.tickets/shared';
import { BrandMark, MobileMoneyStrip } from '@/components/ui';

/** Quiet full-page state used while Keycloak initialises or redirects. */
function AuthPending({ message }: { message: string }) {
  return (
    <Flex
      align="center"
      justify="center"
      direction="column"
      gap="4"
      style={{ minHeight: '100vh', background: 'var(--gray-2)' }}
    >
      <Spinner size="3" />
      <Text size="2" color="gray">
        {message}
      </Text>
    </Flex>
  );
}

export default function AuthPage() {
  const router = useRouter();
  const { authenticated, initialized, login, loading } = useKeycloak();

  useEffect(() => {
    if (initialized && authenticated) {
      router.push('/');
    }
  }, [authenticated, initialized, router]);

  // Phone-first: this is a Zambian consumer app where the phone is the wallet.
  const handlePhoneLogin = () => login({ acr: { values: ['phone-otp'], essential: true } });
  const handleEmailLogin = () => login();

  if (!initialized || loading) return <AuthPending message="Getting things ready…" />;
  if (authenticated) return <AuthPending message="Signing you in…" />;

  return (
    <Flex
      align="center"
      justify="center"
      style={{ minHeight: '100vh', background: 'var(--gray-2)' }}
      p="4"
    >
      <Container size="1">
        {/* Clean single card — flat fill, hairline border, soft shadow.
            No gradient page wash, no gradient card header. */}
        <Box
          style={{
            background: 'var(--card-bg)',
            border: 'var(--card-border)',
            borderRadius: 'var(--card-radius-bento)',
            boxShadow: 'var(--card-shadow)',
            padding: 'var(--space-6)',
          }}
        >
          <Box mb="5">
            <BrandMark size="6" />
          </Box>

          <Heading size="6" className="font-display" mb="2">
            Sign in to get your tickets
          </Heading>
          <Text as="p" size="3" color="gray" mb="6">
            Your tickets live in your account, so they&apos;re on your phone and ready at the
            gate — even with no signal.
          </Text>

          <Flex direction="column" gap="3">
            <Button
              size="4"
              onClick={handlePhoneLogin}
              style={{ width: '100%' }}
              data-testid="auth-phone-login"
            >
              <SmartphoneDevice width={18} height={18} />
              Continue with phone
            </Button>

            <Flex align="center" gap="4" my="1">
              <Separator size="4" />
              <Text size="1" color="gray">
                or
              </Text>
              <Separator size="4" />
            </Flex>

            <Button
              size="4"
              variant="outline"
              onClick={handleEmailLogin}
              style={{ width: '100%' }}
              data-testid="auth-email-login"
            >
              <Mail width={18} height={18} />
              Continue with email
            </Button>
          </Flex>

          <Box mt="5" pt="5" style={{ borderTop: 'var(--hairline)' }}>
            <MobileMoneyStrip label="Pay with" />
          </Box>
        </Box>

        <Text as="p" size="1" color="gray" align="center" mt="5">
          We&apos;ll send you to our secure sign-in page. New here? Signing in creates your
          account.
        </Text>
      </Container>
    </Flex>
  );
}
