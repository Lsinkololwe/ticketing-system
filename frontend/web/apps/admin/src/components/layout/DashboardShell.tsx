'use client';

/**
 * Dashboard Shell (Client)
 *
 * The interactive chrome of the admin dashboard: responsive sidebar state,
 * header, and themed background layers. Rendered by the server-side
 * (dashboard)/layout.tsx ONLY after the session + role guard passes, so this
 * component assumes the user is already authorized.
 */

import { ReactNode, useState, useEffect, createContext } from 'react';
import { Box, Flex } from '@radix-ui/themes';
import { Sidebar } from './Sidebar';
import { Header } from './Header';

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
// SHELL COMPONENT
// =============================================================================

interface DashboardShellProps {
  children: ReactNode;
}

export function DashboardShell({ children }: DashboardShellProps) {
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
    <SidebarContext.Provider value={{ isOpen, setIsOpen, isMobile }}>
      {/* Root container. The canvas is a flat --color-background: the design
          system bans gradients as full-page dashboard backgrounds, so the
          ambient accent glow and dark-mode mesh gradient that used to sit here
          are gone. Depth comes from the card shadows instead. */}
      <Box
        className="dashboard-root"
        style={{
          minHeight: '100vh',
          background: 'var(--dashboard-main-bg)',
        }}
      >
        <Flex style={{ position: 'relative', zIndex: 1 }}>
          {/* Overlay-drawer backdrop, under 1024px. */}
          {isMobile && isOpen && (
            <Box
              onClick={() => setIsOpen(false)}
              className="sidebar-backdrop"
              aria-hidden="true"
              style={{
                position: 'fixed',
                inset: 0,
                backgroundColor: 'var(--color-overlay)',
                zIndex: 40,
                transition:
                  'opacity var(--transition-default) var(--ease-standard)',
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
              // A flex item defaults to min-width:auto, which refuses to shrink
              // below its content's intrinsic width. Without this the main
              // column measured 695px inside a 390px viewport and the whole
              // dashboard — every page, not just this one — scrolled sideways
              // on a phone. Wide content is meant to scroll inside its own
              // container; this is what lets it.
              minWidth: 0,
              marginLeft: isMobile ? 0 : '250px',
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
                minHeight: 'calc(100vh - 56px)',
              }}
            >
              <main>{children}</main>
            </Box>
          </Box>
        </Flex>
      </Box>
    </SidebarContext.Provider>
  );
}

export default DashboardShell;
