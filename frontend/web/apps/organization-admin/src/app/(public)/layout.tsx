'use client';

/**
 * Public (marketing) layout.
 *
 * Floating glass header over the dark marketing canvas, then a footer that
 * returns to the standard light/dark product surface — the handoff point
 * between the marketing voice and the product voice.
 *
 * All colors resolve through the `--marketing-*` and DS token layers declared
 * in `app/global.css`; no literals live in this file. The wordmark is plain
 * text — no logo file exists and none is invented.
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
    { href: '/login', label: 'Sign in' },
  ];

  return (
    <>
      <header className="mkt-nav" data-scrolled={scrolled}>
        <Flex justify="between" align="center" style={{ padding: '0 24px', height: '100%' }}>
          {/* Plain wordmark — no logo file exists, so none is invented. */}
          <Link href="/" style={{ textDecoration: 'none' }}>
            <Flex align="center" gap="3">
              <Box className="mkt-mark" aria-hidden="true">
                <Calendar width={22} height={22} />
              </Box>
              <Text size="3" weight="bold" className="mkt-fg" style={{ lineHeight: 1.2 }}>
                MyTicket Zambia
              </Text>
            </Flex>
          </Link>

          <Flex align="center" gap="6" className="mkt-nav-desktop" style={{ display: 'none' }}>
            {navLinks.map((link) => (
              <Link key={link.href} href={link.href} style={{ textDecoration: 'none' }}>
                <Text
                  size="2"
                  weight={pathname === link.href ? 'medium' : 'regular'}
                  className="mkt-nav-link"
                  data-active={pathname === link.href}
                >
                  {link.label}
                </Text>
              </Link>
            ))}
            <Button
              data-testid="public-nav-get-started"
              size="2"
              className="mkt-nav-cta"
              onClick={handleGetStarted}
              disabled={isStarting}
              style={{ cursor: isStarting ? 'wait' : 'pointer', opacity: isStarting ? 0.7 : 1 }}
            >
              {isStarting ? 'Redirecting…' : 'Get started'}
              <NavArrowRight width={16} height={16} style={{ marginLeft: 4 }} />
            </Button>
          </Flex>

          <IconButton
            variant="ghost"
            className="mkt-nav-mobile-btn"
            onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
            aria-label={mobileMenuOpen ? 'Close menu' : 'Open menu'}
            aria-expanded={mobileMenuOpen}
            style={{ display: 'flex', cursor: 'pointer', color: 'var(--marketing-fg)' }}
          >
            {mobileMenuOpen ? (
              <Xmark width={24} height={24} aria-hidden="true" />
            ) : (
              <Menu width={24} height={24} aria-hidden="true" />
            )}
          </IconButton>
        </Flex>
      </header>

      {mobileMenuOpen && (
        <Box className="mkt-mobile-menu">
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
                  className="mkt-nav-link"
                  data-active={pathname === link.href}
                  style={{ display: 'block', padding: '8px 0' }}
                >
                  {link.label}
                </Text>
              </Link>
            ))}
            <Box mt="2">
              <Button
                data-testid="public-nav-get-started-mobile"
                size="3"
                className="mkt-nav-cta"
                onClick={handleGetStarted}
                disabled={isStarting}
                style={{
                  width: '100%',
                  cursor: isStarting ? 'wait' : 'pointer',
                  opacity: isStarting ? 0.7 : 1,
                }}
              >
                {isStarting ? 'Redirecting…' : 'Start selling free'}
                <NavArrowRight width={18} height={18} style={{ marginLeft: 6 }} />
              </Button>
            </Box>
          </Flex>
        </Box>
      )}
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
      { label: 'Check-in app', href: '/check-in' },
      { label: 'API docs', href: '/docs' },
    ],
    company: [
      { label: 'About us', href: '/about' },
      { label: 'Contact', href: '/contact' },
      { label: 'Careers', href: '/careers' },
      { label: 'Press kit', href: '/press' },
    ],
    legal: [
      { label: 'Terms of service', href: '/terms' },
      { label: 'Privacy policy', href: '/privacy' },
      { label: 'Cookie policy', href: '/cookies' },
    ],
    support: [
      { label: 'Help centre', href: '/help' },
      { label: 'FAQs', href: '/faqs' },
      { label: 'Service status', href: '/status' },
    ],
  };

  return (
    <footer
      style={{
        background: 'var(--color-background)',
        borderTop: '1px solid var(--gray-a5)',
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
                aria-hidden="true"
                className="ds-accent-chip"
                style={{
                  width: 44,
                  height: 44,
                  borderRadius: 'var(--radius-4)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <Calendar width={24} height={24} />
              </Box>
              <Text size="4" weight="bold" style={{ color: 'var(--gray-12)', display: 'block' }}>
                MyTicket Zambia
              </Text>
            </Flex>
            <Text
              size="2"
              style={{
                color: 'var(--gray-10)',
                lineHeight: 1.7,
                display: 'block',
                marginBottom: 24,
              }}
            >
              Event ticketing for organizers in Zambia. Take mobile money, sell tickets and scan
              people in at the door.
            </Text>

            {/* Contact */}
            <Flex direction="column" gap="2" mb="5">
              <Flex align="center" gap="2">
                <Mail width={16} height={16} style={{ color: 'var(--gray-9)' }} aria-hidden="true" />
                <Text size="2" style={{ color: 'var(--gray-11)' }}>
                  hello@myticket.zm
                </Text>
              </Flex>
              <Flex align="center" gap="2">
                <Phone width={16} height={16} style={{ color: 'var(--gray-9)' }} aria-hidden="true" />
                <Text size="2" className="ds-amount" style={{ color: 'var(--gray-11)' }}>
                  +260 97 XXX XXXX
                </Text>
              </Flex>
              <Flex align="center" gap="2">
                <MapPin width={16} height={16} style={{ color: 'var(--gray-9)' }} aria-hidden="true" />
                <Text size="2" style={{ color: 'var(--gray-11)' }}>
                  Lusaka, Zambia
                </Text>
              </Flex>
            </Flex>

            {/* Social */}
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
                  className="mkt-social"
                >
                  <social.icon width={20} height={20} aria-hidden="true" />
                </a>
              ))}
            </Flex>
          </Box>

          {/* Link Columns */}
          <Flex gap="8" wrap="wrap" style={{ flex: 1, justifyContent: 'flex-end' }}>
            {Object.entries(footerLinks).map(([category, links]) => (
              <Box key={category} style={{ minWidth: 140 }}>
                {/* Micro uppercase label — the one sanctioned ALL-CAPS use. */}
                <Text as="p" className="ds-label" style={{ marginBottom: 16 }}>
                  {category}
                </Text>
                <Flex direction="column" gap="3">
                  {links.map((link) => (
                    <Link key={link.href} href={link.href} style={{ textDecoration: 'none' }}>
                      <Text size="2" className="mkt-footer-link">
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

      {/* Bottom bar */}
      <Box style={{ borderTop: '1px solid var(--gray-a5)' }}>
        <Flex
          justify="between"
          align="center"
          wrap="wrap"
          gap="4"
          style={{ maxWidth: 1200, margin: '0 auto', padding: '20px 24px' }}
        >
          <Text size="1" style={{ color: 'var(--gray-10)' }}>
            © {currentYear} MyTicket Zambia. All rights reserved.
          </Text>
          <Flex align="center" gap="4">
            {/* No emoji in shipped UI. */}
            <Text size="1" style={{ color: 'var(--gray-10)' }}>
              Built in Zambia
            </Text>
            <Box style={{ width: 1, height: 12, background: 'var(--gray-a5)' }} />
            <Text size="1" style={{ color: 'var(--gray-10)' }}>
              ZICTA registered
            </Text>
          </Flex>
        </Flex>
      </Box>
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
        background: 'var(--color-background)',
      }}
    >
      <PublicHeader />
      {/* No top padding — the hero runs under the floating glass header. */}
      <main style={{ flex: 1 }}>{children}</main>
      <PublicFooter />
    </Box>
  );
}
