'use client';

import { Component, ReactNode } from 'react';
import { Flex, Text, Button, Card, Heading } from '@radix-ui/themes';

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
          style={{ minHeight: '100vh', padding: '24px' }}
        >
          <Card size="3" style={{ maxWidth: '500px', width: '100%' }}>
            <Flex direction="column" gap="4">
              <Heading size="5" color="red">
                Something went wrong
              </Heading>
              <Text color="gray" size="2">
                {this.state.error?.message || 'An unexpected error occurred'}
              </Text>
              <Flex gap="2">
                <Button onClick={this.handleReset} variant="soft">
                  Try again
                </Button>
                <Button
                  onClick={() => window.location.reload()}
                  variant="outline"
                >
                  Reload page
                </Button>
              </Flex>
            </Flex>
          </Card>
        </Flex>
      );
    }

    return this.props.children;
  }
}
