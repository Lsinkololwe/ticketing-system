'use client';

/**
 * Dashboard Layout Content (Client Component)
 *
 * Handles the interactive dashboard UI:
 * - Collapsible sidebar navigation
 * - Sticky header with user menu
 * - Responsive design (mobile-first)
 * - Role-based navigation filtering
 *
 * Security Note:
 * This component assumes authentication has already been verified
 * by the server-side layout. It receives session data as props.
 */

import { useState, useCallback, useEffect, ReactNode } from 'react';
import { Box, Callout, Text } from '@radix-ui/themes';
import { Clock } from 'iconoir-react';
import { Sidebar } from '@/components/layout/Sidebar';
import { Header } from '@/components/layout/Header';

// =============================================================================
// CONSTANTS
// =============================================================================

/** Fixed chrome per spec §4: 280px sidebar, drawer under 1024px. */
const SIDEBAR_WIDTH = 280;
const SIDEBAR_COLLAPSED_WIDTH = 72;
const MOBILE_BREAKPOINT = 1024;

// =============================================================================
// TYPES
// =============================================================================

interface DashboardLayoutContentProps {
  children: ReactNode;
  /**
   * Renders the read-only "under review" preview banner. Backend-derived
   * (organization is PENDING_REVIEW / can access dashboard but not yet approved).
   */
  previewMode?: boolean;
}

// =============================================================================
// COMPONENT
// =============================================================================

export function DashboardLayoutContent({ children, previewMode = false }: DashboardLayoutContentProps) {
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false);
  const [isMobile, setIsMobile] = useState(false);

  // Detect mobile viewport
  useEffect(() => {
    const checkMobile = () => {
      const mobile = window.innerWidth < MOBILE_BREAKPOINT;
      setIsMobile(mobile);
      if (mobile) {
        setSidebarCollapsed(false);
        setMobileSidebarOpen(false);
      }
    };

    checkMobile();
    window.addEventListener('resize', checkMobile);
    return () => window.removeEventListener('resize', checkMobile);
  }, []);

  // Handle sidebar toggle
  const handleSidebarToggle = useCallback(() => {
    if (isMobile) {
      setMobileSidebarOpen((prev) => !prev);
    } else {
      setSidebarCollapsed((prev) => !prev);
    }
  }, [isMobile]);

  // Close mobile sidebar
  const handleMobileSidebarClose = useCallback(() => {
    setMobileSidebarOpen(false);
  }, []);

  // Calculate main content margin
  const mainMarginLeft = isMobile
    ? 0
    : sidebarCollapsed
      ? SIDEBAR_COLLAPSED_WIDTH
      : SIDEBAR_WIDTH;

  return (
    <Box
      style={{
        minHeight: '100vh',
        backgroundColor: 'var(--color-background)',
      }}
    >
      {/* Drawer scrim (under 1024px only) */}
      {isMobile && mobileSidebarOpen && (
        <Box
          className="ds-sidebar-scrim"
          onClick={handleMobileSidebarClose}
          aria-hidden="true"
        />
      )}

      {/* Sidebar */}
      <Sidebar
        collapsed={sidebarCollapsed}
        onToggle={handleSidebarToggle}
        mobileOpen={mobileSidebarOpen}
        onMobileClose={handleMobileSidebarClose}
        isMobile={isMobile}
      />

      {/* Main Content Area */}
      <Box
        style={{
          marginLeft: mainMarginLeft,
          transition: 'margin-left 300ms ease',
          minHeight: '100vh',
          display: 'flex',
          flexDirection: 'column',
        }}
      >
        {/* Header */}
        <Header
          onMenuClick={handleSidebarToggle}
          showMenuButton={isMobile}
        />

        {/* Page Content */}
        <Box
          asChild
          px={{ initial: '4', sm: '6', lg: '8' }}
          py={{ initial: '4', sm: '6' }}
          style={{
            flex: 1,
            maxWidth: '1400px',
            width: '100%',
            margin: '0 auto',
          }}
        >
          <main>
            {previewMode && (
              <Callout.Root color="blue" variant="soft" size="2" mb="5" role="status">
                <Callout.Icon>
                  <Clock width={18} height={18} />
                </Callout.Icon>
                <Box>
                  <Text as="p" size="2" weight="medium" style={{ color: 'var(--gray-12)' }}>
                    Application under review — preview mode
                  </Text>
                  <Text as="p" size="2" style={{ color: 'var(--gray-11)', marginTop: 2 }}>
                    Explore your dashboard and prepare draft events now. Publishing events and
                    requesting payouts unlock automatically once your organization is approved.
                  </Text>
                </Box>
              </Callout.Root>
            )}
            {children}
          </main>
        </Box>
      </Box>
    </Box>
  );
}
