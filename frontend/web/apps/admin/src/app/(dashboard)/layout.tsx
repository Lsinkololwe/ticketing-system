'use client';

/**
 * Dashboard Layout with Themed Backgrounds
 *
 * Visual hierarchy through layered backgrounds:
 * - Sidebar: Deep gradient with accent border
 * - Header: Glassmorphism with backdrop blur
 * - Main: Subtle pattern with ambient glow
 *
 * Responsive design:
 * - Collapsible sidebar on mobile/tablet
 * - Persistent sidebar on desktop (1024px+)
 */

import { ReactNode, useState, useEffect, createContext } from 'react';
import { Box, Flex } from '@radix-ui/themes';
import { Sidebar } from '../../components/layout/Sidebar';
import { Header } from '../../components/layout/Header';
import { ProtectedRoute } from '@/components/auth/ProtectedRoute';

// =============================================================================
// SIDEBAR CONTEXT
// =============================================================================

interface SidebarContextType {
  isOpen: boolean;
  setIsOpen: (open: boolean) => void;
  isMobile: boolean;
}

const SidebarContext = createContext<SidebarContextType>({
  isOpen: false,
  setIsOpen: () => {},
  isMobile: false,
});
// =============================================================================
// LAYOUT COMPONENT
// =============================================================================

interface DashboardLayoutProps {
  children: ReactNode;
}

export default function DashboardLayout({ children }: DashboardLayoutProps) {
  const [isOpen, setIsOpen] = useState(false);
  const [isMobile, setIsMobile] = useState(false);

  // Handle responsive breakpoints
  useEffect(() => {
    const checkMobile = () => {
      const mobile = window.innerWidth < 1024;
      setIsMobile(mobile);
      if (mobile) {
        setIsOpen(false);
      }
    };

    checkMobile();
    window.addEventListener('resize', checkMobile);
    return () => window.removeEventListener('resize', checkMobile);
  }, []);

  return (
    <ProtectedRoute roles={['ADMIN', 'SUPER_ADMIN', 'FINANCE']}>
      <SidebarContext.Provider value={{ isOpen, setIsOpen, isMobile }}>
        {/* Root container with main background */}
        <Box
          className="dashboard-root"
          style={{
            minHeight: '100vh',
            background: 'var(--dashboard-main-bg)',
          }}
        >
          {/* Ambient glow overlay */}
          <Box
            className="dashboard-glow"
            style={{
              position: 'fixed',
              inset: 0,
              background: 'var(--dashboard-accent-glow)',
              pointerEvents: 'none',
              zIndex: 0,
            }}
          />

          {/* Pattern overlay */}
          <Box
            className="dashboard-pattern"
            style={{
              position: 'fixed',
              inset: 0,
              backgroundImage: 'var(--dashboard-main-pattern)',
              backgroundRepeat: 'repeat',
              pointerEvents: 'none',
              zIndex: 0,
            }}
          />

          <Flex style={{ position: 'relative', zIndex: 1 }}>
            {/* Backdrop for mobile sidebar */}
            {isMobile && isOpen && (
              <Box
                onClick={() => setIsOpen(false)}
                className="sidebar-backdrop"
                style={{
                  position: 'fixed',
                  inset: 0,
                  backgroundColor: 'rgba(0, 0, 0, 0.6)',
                  backdropFilter: 'blur(4px)',
                  zIndex: 40,
                  transition: 'opacity 200ms ease',
                }}
              />
            )}

            {/* Sidebar with themed background */}
            <Sidebar isOpen={isOpen} isMobile={isMobile} onClose={() => setIsOpen(false)} />

            {/* Main Content Area */}
            <Box
              className="dashboard-main"
              style={{
                flex: 1,
                marginLeft: isMobile ? 0 : '280px',
                minHeight: '100vh',
                transition: 'margin-left 200ms ease',
                position: 'relative',
              }}
            >
              {/* Header with glassmorphism */}
              <Header onMenuClick={() => setIsOpen(!isOpen)} showMenuButton={isMobile} />

              {/* Page Content */}
              <Box
                asChild
                px={{ initial: '4', sm: '6' }}
                py="5"
                style={{
                  minHeight: 'calc(100vh - 64px)',
                }}
              >
                <main>{children}</main>
              </Box>
            </Box>
          </Flex>
        </Box>

        {/* Dark mode mesh gradient (only in dark mode) */}
        <style jsx global>{`
          .dark .dashboard-root::before {
            content: '';
            position: fixed;
            inset: 0;
            background: var(--dashboard-mesh-gradient);
            pointer-events: none;
            z-index: 0;
          }
        `}</style>
      </SidebarContext.Provider>
    </ProtectedRoute>
  );
}
