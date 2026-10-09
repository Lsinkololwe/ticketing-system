'use client';

import { Component, type ErrorInfo, type ReactNode } from 'react';
import { Button } from '@pml.tickets/shared/components/m3';
import { AuthLayout } from '@pml.tickets/shared/layouts';

interface Props {
  children: ReactNode;
  fallback?: ReactNode;
}
interface State {
  hasError: boolean;
}

export class ErrorBoundary extends Component<Props, State> {
  override state: State = { hasError: false };

  static getDerivedStateFromError(): State {
    return { hasError: true };
  }

  override componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('ErrorBoundary caught an error:', error, info);
  }

  override render() {
    if (!this.state.hasError) return this.props.children;
    if (this.props.fallback) return this.props.fallback;
    return (
      <AuthLayout product="MyTicketZM" console="Platform admin" title="This section could not load" description="Something went wrong while showing this page. Your data is safe.">
        <Button variant="filled" onClick={() => this.setState({ hasError: false })}>
          Try again
        </Button>
      </AuthLayout>
    );
  }
}
