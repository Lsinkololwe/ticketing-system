'use client';

/**
 * Dashboard Layout
 *
 * Protected layout for authenticated organizers with approved status.
 * Features:
 * - Collapsible sidebar navigation
 * - Sticky header with user menu
 * - Responsive design (mobile-first)
 * - Role-based navigation filtering
 *
 * Security:
 * - ProtectedRoute wrapper for client-side auth guard
 * - Server-side protection via middleware.ts
 */

import { useState, useCallback, useEffect, ReactNode } from 'react';
import { Box } from '@radix-ui/themes';
import { Sidebar } from '@/components/layout/Sidebar';
import { Header } from '@/components/layout/Header';
import { ProtectedRoute } from '@/components/auth/ProtectedRoute';

// =============================================================================
// CONSTANTS
// =============================================================================

const SIDEBAR_WIDTH = 280;
const SIDEBAR_COLLAPSED_WIDTH = 72;
const MOBILE_BREAKPOINT = 1024;

// =============================================================================
// TYPES
// =============================================================================

interface DashboardLayoutProps {
  children: ReactNode;
}

// =============================================================================
// DASHBOARD LAYOUT CONTENT
// =============================================================================

function DashboardLayoutContent({ children }: DashboardLayoutProps) {
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
        backgroundColor: 'var(--dashboard-bg)',
      }}
    >
      {/* Mobile Overlay */}
      {isMobile && mobileSidebarOpen && (
        <Box
          onClick={handleMobileSidebarClose}
          style={{
            position: 'fixed',
            inset: 0,
            backgroundColor: 'rgba(0, 0, 0, 0.5)',
            zIndex: 40,
            transition: 'opacity 200ms ease',
          }}
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
          <main>{children}</main>
        </Box>
      </Box>

      {/* Global Dashboard Styles */}
      <style jsx global>{`
        /* Dashboard CSS Variables */
        :root {
          --dashboard-bg: var(--surface-base);
          --dashboard-header-bg: rgba(255, 255, 255, 0.8);
          --dashboard-header-blur: 12px;
          --dashboard-header-border: var(--surface-border);
          --dashboard-sidebar-bg: #0F172A;
          --dashboard-sidebar-border: rgba(148, 163, 184, 0.1);
        }

        .dark {
          --dashboard-bg: #0A0E17;
          --dashboard-header-bg: rgba(15, 23, 42, 0.8);
          --dashboard-sidebar-bg: #0A0E17;
        }

        /* Hide mobile elements on desktop */
        @media (min-width: 1024px) {
          .hidden-desktop {
            display: none !important;
          }
        }

        /* Hide desktop elements on mobile */
        @media (max-width: 1023px) {
          .hidden-mobile {
            display: none !important;
          }
        }

        /* Smooth scrolling */
        html {
          scroll-behavior: smooth;
        }

        /* Custom scrollbar for dashboard */
        .dashboard-scroll::-webkit-scrollbar {
          width: 6px;
          height: 6px;
        }

        .dashboard-scroll::-webkit-scrollbar-track {
          background: transparent;
        }

        .dashboard-scroll::-webkit-scrollbar-thumb {
          background: var(--surface-border);
          border-radius: 3px;
        }

        .dashboard-scroll::-webkit-scrollbar-thumb:hover {
          background: var(--content-muted);
        }
      `}</style>
    </Box>
  );
}

// =============================================================================
// EXPORT
// =============================================================================

export default function DashboardLayout({ children }: DashboardLayoutProps) {
  return (
    <ProtectedRoute>
      <DashboardLayoutContent>{children}</DashboardLayoutContent>
    </ProtectedRoute>
  );
}
