'use client';

/**
 * Public Layout - Pro Max Design
 *
 * Premium layout for public marketing pages with:
 * - Floating glassmorphic header
 * - Responsive navigation
 * - Polished footer with social links
 */

import { useState, useEffect, useCallback } from 'react';
import { Box, Flex, Text, Button, IconButton } from '@radix-ui/themes';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { registerWithKeycloak } from '@/lib/auth/client';
import {
  Calendar,
  NavArrowRight,
  Menu,
  Xmark,
  Twitter,
  Instagram,
  Linkedin,
  Mail,
  Phone,
  MapPin,
} from 'iconoir-react';

// =============================================================================
// PUBLIC HEADER
// =============================================================================

function PublicHeader() {
  const [scrolled, setScrolled] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [isStarting, setIsStarting] = useState(false);
  const pathname = usePathname();

  const handleGetStarted = useCallback(async () => {
    try {
      setIsStarting(true);
      setMobileMenuOpen(false);
      await registerWithKeycloak('/apply/business-info');
    } catch (err) {
      console.error('Failed to start registration:', err);
      setIsStarting(false);
    }
  }, []);

  useEffect(() => {
    const handleScroll = () => {
      setScrolled(window.scrollY > 20);
    };
    window.addEventListener('scroll', handleScroll);
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  const navLinks = [
    { href: '/#features', label: 'Features' },
    { href: '/#pricing', label: 'Pricing' },
    { href: '/login', label: 'Sign In' },
  ];

  return (
    <>
      <header
        style={{
          position: 'fixed',
          top: scrolled ? 12 : 16,
          left: '50%',
          transform: 'translateX(-50%)',
          width: scrolled ? 'calc(100% - 32px)' : 'calc(100% - 48px)',
          maxWidth: scrolled ? 1200 : 1400,
          height: 64,
          background: scrolled
            ? 'rgba(9, 9, 11, 0.85)'
            : 'rgba(9, 9, 11, 0.6)',
          backdropFilter: 'blur(20px)',
          WebkitBackdropFilter: 'blur(20px)',
          border: `1px solid ${scrolled ? 'rgba(255, 255, 255, 0.1)' : 'rgba(255, 255, 255, 0.05)'}`,
          borderRadius: 16,
          zIndex: 100,
          transition: 'all 0.3s ease',
        }}
      >
        <Flex
          justify="between"
          align="center"
          style={{
            padding: '0 24px',
            height: '100%',
          }}
        >
          {/* Logo */}
          <Link href="/" style={{ textDecoration: 'none' }}>
            <Flex align="center" gap="3" className="logo-hover">
              <Box
                style={{
                  width: 40,
                  height: 40,
                  borderRadius: 12,
                  background: 'linear-gradient(135deg, var(--brand-500) 0%, var(--brand-600) 100%)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  boxShadow: '0 4px 12px rgba(16, 185, 129, 0.3)',
                }}
              >
                <Calendar style={{ width: 22, height: 22, color: 'white' }} />
              </Box>
              <Box>
                <Text
                  size="3"
                  weight="bold"
                  style={{ color: '#F8FAFC', display: 'block', lineHeight: 1.2 }}
                >
                  MyTicket Zambia
                </Text>
              </Box>
            </Flex>
          </Link>

          {/* Desktop Navigation */}
          <Flex
            align="center"
            gap="6"
            style={{
              display: 'none',
            }}
            className="desktop-nav"
          >
            {navLinks.map((link) => (
              <Link key={link.href} href={link.href} style={{ textDecoration: 'none' }}>
                <Text
                  size="2"
                  weight={pathname === link.href ? 'medium' : 'regular'}
                  className="nav-link"
                  style={{
                    color: pathname === link.href
                      ? '#F8FAFC'
                      : '#CBD5E1',
                    transition: 'color 0.2s ease',
                  }}
                >
                  {link.label}
                </Text>
              </Link>
            ))}
            <Button
              size="2"
              className="cta-button"
              onClick={handleGetStarted}
              disabled={isStarting}
              style={{
                background: 'linear-gradient(135deg, var(--brand-500) 0%, var(--brand-600) 100%)',
                padding: '8px 20px',
                borderRadius: 10,
                cursor: isStarting ? 'wait' : 'pointer',
                boxShadow: '0 4px 12px rgba(16, 185, 129, 0.25)',
                opacity: isStarting ? 0.7 : 1,
              }}
            >
              {isStarting ? 'Redirecting...' : 'Get Started'}
              <NavArrowRight style={{ width: 16, height: 16, marginLeft: 4 }} />
            </Button>
          </Flex>

          {/* Mobile Menu Button */}
          <IconButton
            variant="ghost"
            className="mobile-menu-btn"
            onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
            aria-label={mobileMenuOpen ? 'Close menu' : 'Open menu'}
            aria-expanded={mobileMenuOpen}
            style={{
              display: 'flex',
              cursor: 'pointer',
            }}
          >
            {mobileMenuOpen ? (
              <Xmark style={{ width: 24, height: 24 }} aria-hidden="true" />
            ) : (
              <Menu style={{ width: 24, height: 24 }} aria-hidden="true" />
            )}
          </IconButton>
        </Flex>
      </header>

      {/* Mobile Menu */}
      {mobileMenuOpen && (
        <Box
          className="mobile-menu"
          style={{
            position: 'fixed',
            top: 88,
            left: 16,
            right: 16,
            background: 'rgba(9, 9, 11, 0.95)',
            backdropFilter: 'blur(20px)',
            border: '1px solid rgba(255, 255, 255, 0.1)',
            borderRadius: 16,
            padding: 24,
            zIndex: 99,
            animation: 'slideDown 0.2s ease',
          }}
        >
          <Flex direction="column" gap="4">
            {navLinks.map((link) => (
              <Link
                key={link.href}
                href={link.href}
                style={{ textDecoration: 'none' }}
                onClick={() => setMobileMenuOpen(false)}
              >
                <Text
                  size="3"
                  style={{
                    color: pathname === link.href
                      ? '#10B981'
                      : '#CBD5E1',
                    display: 'block',
                    padding: '8px 0',
                  }}
                >
                  {link.label}
                </Text>
              </Link>
            ))}
            <Box mt="2">
              <Button
                size="3"
                onClick={handleGetStarted}
                disabled={isStarting}
                style={{
                  width: '100%',
                  background: 'linear-gradient(135deg, var(--brand-500) 0%, var(--brand-600) 100%)',
                  cursor: isStarting ? 'wait' : 'pointer',
                  opacity: isStarting ? 0.7 : 1,
                }}
              >
                {isStarting ? 'Redirecting...' : 'Get Started Free'}
                <NavArrowRight style={{ width: 18, height: 18, marginLeft: 6 }} />
              </Button>
            </Box>
          </Flex>
        </Box>
      )}

      <style jsx global>{`
        @keyframes slideDown {
          from {
            opacity: 0;
            transform: translateY(-10px);
          }
          to {
            opacity: 1;
            transform: translateY(0);
          }
        }

        .nav-link:hover {
          color: #FFFFFF !important;
        }

        .cta-button:hover {
          transform: translateY(-1px);
          box-shadow: 0 6px 16px rgba(16, 185, 129, 0.35) !important;
        }

        .logo-hover {
          transition: transform 0.2s ease;
        }

        .logo-hover:hover {
          transform: scale(1.02);
        }

        /* Desktop nav visibility */
        @media (min-width: 768px) {
          .desktop-nav {
            display: flex !important;
          }
          .mobile-menu-btn {
            display: none !important;
          }
          .mobile-menu {
            display: none !important;
          }
        }
      `}</style>
    </>
  );
}

// =============================================================================
// PUBLIC FOOTER
// =============================================================================

function PublicFooter() {
  const currentYear = new Date().getFullYear();

  const footerLinks = {
    product: [
      { label: 'Features', href: '/features' },
      { label: 'Pricing', href: '/pricing' },
      { label: 'Check-In App', href: '/check-in' },
      { label: 'API Docs', href: '/docs' },
    ],
    company: [
      { label: 'About Us', href: '/about' },
      { label: 'Contact', href: '/contact' },
      { label: 'Careers', href: '/careers' },
      { label: 'Press Kit', href: '/press' },
    ],
    legal: [
      { label: 'Terms of Service', href: '/terms' },
      { label: 'Privacy Policy', href: '/privacy' },
      { label: 'Cookie Policy', href: '/cookies' },
    ],
    support: [
      { label: 'Help Center', href: '/help' },
      { label: 'FAQs', href: '/faqs' },
      { label: 'Status', href: '/status' },
    ],
  };

  return (
    <footer
      style={{
        background: 'var(--surface-default)',
        borderTop: '1px solid var(--surface-border)',
      }}
    >
      {/* Main Footer */}
      <Box
        style={{
          maxWidth: 1200,
          margin: '0 auto',
          padding: '64px 24px 48px',
        }}
      >
        <Flex
          direction={{ initial: 'column', md: 'row' }}
          justify="between"
          gap="8"
        >
          {/* Brand Column */}
          <Box style={{ maxWidth: 320, flex: '0 0 auto' }}>
            <Flex align="center" gap="3" mb="4">
              <Box
                style={{
                  width: 44,
                  height: 44,
                  borderRadius: 12,
                  background: 'linear-gradient(135deg, var(--brand-500) 0%, var(--brand-600) 100%)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <Calendar style={{ width: 24, height: 24, color: 'white' }} />
              </Box>
              <Box>
                <Text size="4" weight="bold" style={{ color: 'var(--content-primary)', display: 'block' }}>
                  MyTicket Zambia
                </Text>
              </Box>
            </Flex>
            <Text
              size="2"
              style={{
                color: 'var(--content-muted)',
                lineHeight: 1.7,
                display: 'block',
                marginBottom: 24,
              }}
            >
              The complete event management platform for organizers in Zambia.
              Accept mobile money, sell tickets, and manage check-ins with ease.
            </Text>

            {/* Contact Info */}
            <Flex direction="column" gap="2" mb="5">
              <Flex align="center" gap="2">
                <Mail style={{ width: 16, height: 16, color: 'var(--content-muted)' }} />
                <Text size="2" style={{ color: 'var(--content-secondary)' }}>
                  hello@myticket.zm
                </Text>
              </Flex>
              <Flex align="center" gap="2">
                <Phone style={{ width: 16, height: 16, color: 'var(--content-muted)' }} />
                <Text size="2" style={{ color: 'var(--content-secondary)' }}>
                  +260 97 XXX XXXX
                </Text>
              </Flex>
              <Flex align="center" gap="2">
                <MapPin style={{ width: 16, height: 16, color: 'var(--content-muted)' }} />
                <Text size="2" style={{ color: 'var(--content-secondary)' }}>
                  Lusaka, Zambia
                </Text>
              </Flex>
            </Flex>

            {/* Social Links */}
            <Flex gap="3">
              {[
                { icon: Twitter, href: '#', label: 'Twitter' },
                { icon: Instagram, href: '#', label: 'Instagram' },
                { icon: Linkedin, href: '#', label: 'LinkedIn' },
              ].map((social) => (
                <a
                  key={social.label}
                  href={social.href}
                  aria-label={social.label}
                  style={{
                    width: 40,
                    height: 40,
                    borderRadius: 10,
                    background: 'var(--surface-subtle)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    color: 'var(--content-muted)',
                    transition: 'all 0.2s ease',
                    cursor: 'pointer',
                  }}
                  className="social-link"
                >
                  <social.icon style={{ width: 20, height: 20 }} aria-hidden="true" />
                </a>
              ))}
            </Flex>
          </Box>

          {/* Link Columns */}
          <Flex gap="8" wrap="wrap" style={{ flex: 1, justifyContent: 'flex-end' }}>
            {Object.entries(footerLinks).map(([category, links]) => (
              <Box key={category} style={{ minWidth: 140 }}>
                <Text
                  size="2"
                  weight="medium"
                  style={{
                    color: 'var(--content-primary)',
                    display: 'block',
                    marginBottom: 16,
                    textTransform: 'capitalize',
                  }}
                >
                  {category}
                </Text>
                <Flex direction="column" gap="3">
                  {links.map((link) => (
                    <Link
                      key={link.href}
                      href={link.href}
                      style={{ textDecoration: 'none' }}
                    >
                      <Text
                        size="2"
                        className="footer-link"
                        style={{
                          color: 'var(--content-muted)',
                          transition: 'color 0.2s ease',
                        }}
                      >
                        {link.label}
                      </Text>
                    </Link>
                  ))}
                </Flex>
              </Box>
            ))}
          </Flex>
        </Flex>
      </Box>

      {/* Bottom Bar */}
      <Box
        style={{
          borderTop: '1px solid var(--surface-border)',
        }}
      >
        <Flex
          justify="between"
          align="center"
          wrap="wrap"
          gap="4"
          style={{
            maxWidth: 1200,
            margin: '0 auto',
            padding: '20px 24px',
          }}
        >
          <Text size="1" style={{ color: 'var(--content-muted)' }}>
            © {currentYear} MyTicket Zambia. All rights reserved.
          </Text>
          <Flex align="center" gap="4">
            <Text size="1" style={{ color: 'var(--content-muted)' }}>
              Made with ❤️ in Zambia
            </Text>
            <Box style={{ width: 1, height: 12, background: 'var(--surface-border)' }} />
            <Text size="1" style={{ color: 'var(--content-muted)' }}>
              ZICTA Registered
            </Text>
          </Flex>
        </Flex>
      </Box>

      <style jsx global>{`
        .footer-link:hover {
          color: var(--brand-500) !important;
        }

        .social-link:hover {
          background: var(--brand-500) !important;
          color: white !important;
          transform: translateY(-2px);
        }
      `}</style>
    </footer>
  );
}

// =============================================================================
// LAYOUT
// =============================================================================

export default function PublicLayout({ children }: { children: React.ReactNode }) {
  return (
    <Box
      style={{
        minHeight: '100vh',
        display: 'flex',
        flexDirection: 'column',
        background: 'var(--surface-default)',
      }}
    >
      <PublicHeader />
      <main
        style={{
          flex: 1,
          paddingTop: 0, // Hero extends to top
        }}
      >
        {children}
      </main>
      <PublicFooter />
    </Box>
  );
}
