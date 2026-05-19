'use client';

import React, { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import {
  Box,
  Card,
  Flex,
  Text,
  Heading,
  Button,
  Container,
  Spinner,
  Separator,
} from '@radix-ui/themes';
import { Phone, Mail } from 'iconoir-react';
import { useKeycloak } from '@pml.tickets/shared';

export default function AuthPage() {
  const router = useRouter();
  const { authenticated, initialized, login, loading } = useKeycloak();

  useEffect(() => {
    if (initialized && authenticated) {
      router.push('/dashboard');
    }
  }, [authenticated, initialized, router]);

  const handleEmailLogin = () => {
    login();
  };

  const handlePhoneLogin = () => {
    login({ acr: { values: ['phone-otp'], essential: true } });
  };

  if (!initialized || loading) {
    return (
      <Box
        style={{
          minHeight: '100vh',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          background: 'linear-gradient(135deg, var(--accent-4) 0%, var(--accent-6) 100%)',
        }}
      >
        <Flex direction="column" align="center" gap="4">
          <Spinner size="3" />
          <Text style={{ color: 'white' }}>Initializing...</Text>
        </Flex>
      </Box>
    );
  }

  if (authenticated) {
    return (
      <Box
        style={{
          minHeight: '100vh',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          background: 'linear-gradient(135deg, var(--accent-4) 0%, var(--accent-6) 100%)',
        }}
      >
        <Flex direction="column" align="center" gap="4">
          <Spinner size="3" />
          <Text style={{ color: 'white' }}>Redirecting to dashboard...</Text>
        </Flex>
      </Box>
    );
  }

  return (
    <Box
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'linear-gradient(135deg, var(--accent-4) 0%, var(--accent-6) 100%)',
      }}
      p="4"
    >
      <Container size="1">
        <Card size="4">
          {/* Header */}
          <Box
            p="5"
            style={{
              background: 'linear-gradient(135deg, var(--accent-9) 0%, var(--accent-10) 100%)',
              borderRadius: 'var(--radius-4) var(--radius-4) 0 0',
              margin: '-24px -24px 24px -24px',
            }}
          >
            <Heading size="6" align="center" style={{ color: 'white' }}>
              Welcome to Event Ticketing
            </Heading>
            <Text size="2" align="center" style={{ color: 'rgba(255,255,255,0.9)', marginTop: 8, display: 'block' }}>
              Your gateway to amazing events in Zambia
            </Text>
          </Box>

          <Flex direction="column" gap="5" p="2">
            <Heading size="5" align="center">
              Sign In to Continue
            </Heading>

            <Text size="2" color="gray" align="center">
              Choose your preferred sign-in method below.
            </Text>

            <Flex direction="column" gap="3">
              <Button size="3" onClick={handleEmailLogin}>
                <Mail className="h-5 w-5" />
                Sign In with Email
              </Button>

              <Flex align="center" gap="4">
                <Separator size="4" />
                <Text size="1" color="gray">or</Text>
                <Separator size="4" />
              </Flex>

              <Button size="3" variant="soft" color="green" onClick={handlePhoneLogin}>
                <Phone className="h-5 w-5" />
                Sign In with Phone
              </Button>
            </Flex>

            <Text size="1" color="gray" align="center">
              You will be redirected to our secure login page
            </Text>

            <Separator size="4" />

            <Text size="2" color="gray" align="center">
              Don&apos;t have an account?{' '}
              <Text
                as="span"
                onClick={handleEmailLogin}
                style={{ color: 'var(--accent-11)', cursor: 'pointer' }}
              >
                Sign up here
              </Text>
            </Text>
          </Flex>
        </Card>

        <Text size="1" color="gray" align="center" mt="5" style={{ color: 'rgba(255,255,255,0.7)' }}>
          By signing in, you agree to our Terms of Service and Privacy Policy
        </Text>
      </Container>
    </Box>
  );
}
