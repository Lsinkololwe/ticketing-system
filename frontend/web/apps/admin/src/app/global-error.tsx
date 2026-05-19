'use client';

import { useEffect } from 'react';
import { WarningTriangle, Refresh } from 'iconoir-react';

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

  // Note: global-error.tsx cannot use Radix Theme wrapper since it replaces the entire document
  // Using inline styles for this critical error page
  return (
    <html lang="en">
      <body>
        <div style={{
          minHeight: '100vh',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          backgroundColor: '#f9fafb',
          padding: 16,
          fontFamily: 'system-ui, -apple-system, sans-serif',
        }}>
          <div style={{
            width: '100%',
            maxWidth: 600,
            backgroundColor: 'white',
            borderRadius: 12,
            boxShadow: '0 4px 6px -1px rgba(0,0,0,0.1), 0 2px 4px -1px rgba(0,0,0,0.06)',
            border: '1px solid #fecaca',
            padding: 32,
          }}>
            <div style={{ textAlign: 'center', marginBottom: 24 }}>
              <div style={{
                width: 64,
                height: 64,
                backgroundColor: '#fee2e2',
                borderRadius: '50%',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                margin: '0 auto 16px',
              }}>
                <WarningTriangle style={{ width: 40, height: 40, color: '#dc2626' }} />
              </div>
              <h1 style={{ fontSize: 24, fontWeight: 600, color: '#dc2626', margin: '0 0 8px' }}>
                Critical Error
              </h1>
              <p style={{ fontSize: 16, color: '#6b7280', margin: '0 0 8px' }}>
                {error.message || 'An unexpected error occurred'}
              </p>
              <p style={{ fontSize: 14, color: '#6b7280', margin: 0 }}>
                A critical error occurred in the application. Please refresh the page.
              </p>
            </div>

            {/* Error details in development */}
            {process.env.NODE_ENV === 'development' && (
              <div style={{
                backgroundColor: '#fef2f2',
                border: '1px solid #fecaca',
                borderRadius: 8,
                padding: 16,
                marginBottom: 24,
              }}>
                <p style={{ fontSize: 14, fontWeight: 600, color: '#dc2626', margin: '0 0 8px' }}>
                  Error Details (Development Only):
                </p>
                <pre style={{
                  whiteSpace: 'pre-wrap',
                  wordBreak: 'break-all',
                  fontSize: 12,
                  color: '#b91c1c',
                  fontFamily: 'monospace',
                  margin: 0,
                }}>
                  {error.name}: {error.message}
                </pre>
                {error.digest && (
                  <p style={{ fontSize: 12, color: '#6b7280', marginTop: 8 }}>
                    Error ID: {error.digest}
                  </p>
                )}
              </div>
            )}

            {/* Action buttons */}
            <div style={{
              display: 'flex',
              flexDirection: 'column',
              gap: 12,
              alignItems: 'center',
            }}>
              <button
                onClick={reset}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  gap: 8,
                  backgroundColor: '#dc2626',
                  color: 'white',
                  border: 'none',
                  borderRadius: 8,
                  padding: '10px 20px',
                  fontSize: 14,
                  fontWeight: 500,
                  cursor: 'pointer',
                }}
              >
                <Refresh style={{ width: 16, height: 16 }} />
                Try Again
              </button>
              <button
                onClick={() => {
                  if (typeof window !== 'undefined') {
                    window.location.reload();
                  }
                }}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  gap: 8,
                  backgroundColor: 'white',
                  color: '#dc2626',
                  border: '1px solid #dc2626',
                  borderRadius: 8,
                  padding: '10px 20px',
                  fontSize: 14,
                  fontWeight: 500,
                  cursor: 'pointer',
                }}
              >
                <Refresh style={{ width: 16, height: 16 }} />
                Refresh Page
              </button>
            </div>

            <p style={{ fontSize: 14, color: '#6b7280', textAlign: 'center', marginTop: 24 }}>
              If this problem persists, please contact support.
            </p>
          </div>
        </div>
      </body>
    </html>
  );
}
