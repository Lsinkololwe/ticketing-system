'use client';

import { Component, type ErrorInfo, type ReactNode } from 'react';
import { Banner, Button } from '@pml.tickets/shared/components/m3';

interface Props {
  children: ReactNode;
  fallback?: ReactNode;
}
interface State {
  error: Error | null;
}

/** Catches render errors and shows the designed error state with a retry. */
export class ErrorBoundary extends Component<Props, State> {
  override state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  override componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('ErrorBoundary caught an error:', error, info);
  }

  override render() {
    if (!this.state.error) return this.props.children;
    if (this.props.fallback) return this.props.fallback;
    return (
      <main className="m3-main">
        <Banner
          tone="error"
          title="Something went wrong"
          actions={
            <Button size="sm" variant="tonal" onClick={() => this.setState({ error: null })}>
              Try again
            </Button>
          }
        >
          {this.state.error.message || 'An unexpected error occurred.'}
        </Banner>
      </main>
    );
  }
}
