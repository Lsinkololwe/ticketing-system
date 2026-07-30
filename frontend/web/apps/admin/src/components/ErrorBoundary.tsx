'use client';

import { Component, ReactNode } from 'react';
import { Box, Flex, Text, Button, Heading } from '@radix-ui/themes';

interface Props {
  children: ReactNode;
  fallback?: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

export class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  override componentDidCatch(error: Error, errorInfo: React.ErrorInfo) {
    console.error('ErrorBoundary caught an error:', error, errorInfo);
  }

  handleReset = () => {
    this.setState({ hasError: false, error: null });
  };

  override render() {
    if (this.state.hasError) {
      if (this.props.fallback) {
        return this.props.fallback;
      }

      return (
        <Flex
          align="center"
          justify="center"
          style={{ minHeight: '100vh', padding: 'var(--space-5)' }}
        >
          <Box
            p="5"
            style={{
              maxWidth: '500px',
              width: '100%',
              background: 'var(--card-bg)',
              border: 'var(--card-border)',
              borderRadius: 'var(--card-radius-bento)',
              boxShadow: 'var(--card-shadow)',
            }}
          >
            <Flex direction="column" gap="4">
              <Heading size="5" style={{ color: 'var(--gray-12)' }}>
                This section could not load
              </Heading>
              <Text size="2" style={{ color: 'var(--gray-11)' }}>
                {this.state.error?.message ||
                  'An unexpected error interrupted this part of the page.'}
              </Text>
              <Flex gap="2">
                <Button data-testid="error-boundary-retry" onClick={this.handleReset} variant="soft">
                  Try again
                </Button>
                <Button
                  data-testid="error-boundary-reload"
                  onClick={() => window.location.reload()}
                  variant="outline"
                >
                  Reload page
                </Button>
              </Flex>
            </Flex>
          </Box>
        </Flex>
      );
    }

    return this.props.children;
  }
}
