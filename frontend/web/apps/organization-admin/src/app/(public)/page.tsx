'use client';

/**
 * Organizer landing page.
 *
 * This is the ONE sanctioned exception to the flat, light dashboard language:
 * a premium near-black canvas with slow ambient aurora/mesh loops, a faint grid
 * overlay, floating particles and glassmorphism cards.
 *
 * Every color, radius, blur and loop duration comes from the `--marketing-*`
 * token layer in `app/global.css`; there are no color literals in this file.
 * The ambient loops run 15-30s so they read as atmosphere rather than motion,
 * and `prefers-reduced-motion: reduce` freezes them all at a resting frame.
 *
 * Copy: second person, benefit-led, sentence case. The dashboard's operational
 * third-person voice deliberately does not apply here.
 */

import { useEffect, useRef, useState, useCallback } from 'react';
import { registerWithKeycloak, signInWithKeycloak } from '@/lib/auth/client';
import { Box, Flex, Heading, Button, Text } from '@radix-ui/themes';
import {
  Calendar,
  StatsReport,
  Wallet,
  SmartphoneDevice,
  QrCode,
  NavArrowRight,
  Check,
  GraphUp,
  Shield,
  Play,
  Group,
  Flash,
  Xmark,
  CheckCircle,
  ArrowRight,
  Clock,
  CreditCard,
  User,
  Sparks,
} from 'iconoir-react';

// =============================================================================
// SCROLL REVEAL
// =============================================================================

function useInView(threshold = 0.2) {
  const [isInView, setIsInView] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!ref.current) return undefined;

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) setIsInView(true);
      },
      { threshold }
    );
    observer.observe(ref.current);
    return () => observer.disconnect();
  }, [threshold]);

  return { isInView, ref };
}

// =============================================================================
// HERO
// =============================================================================

function HeroSection() {
  const [isStarting, setIsStarting] = useState(false);

  // Particles are generated client-side only — a server-rendered random
  // position would mismatch on hydration.
  const [particles, setParticles] = useState<
    { left: number; delay: number; duration: number }[]
  >([]);

  useEffect(() => {
    setParticles(
      Array.from({ length: 20 }, () => ({
        left: Math.random() * 100,
        delay: Math.random() * 5,
        duration: 15 + Math.random() * 10,
      }))
    );
  }, []);

  const handleGetStarted = useCallback(async () => {
    try {
      setIsStarting(true);
      await registerWithKeycloak('/apply/business-info');
    } catch (err) {
      console.error('Failed to start registration:', err);
      setIsStarting(false);
    }
  }, []);

  return (
    <Box className="mkt-hero">
      <Box className="mkt-aurora" aria-hidden="true" />
      <Box className="mkt-mesh" aria-hidden="true" />

      <Box className="mkt-particles" aria-hidden="true">
        {particles.map((particle, i) => (
          <Box
            key={i}
            className="mkt-particle"
            style={{
              left: `${particle.left}%`,
              animationDelay: `${particle.delay}s`,
              animationDuration: `${particle.duration}s`,
            }}
          />
        ))}
      </Box>

      <Box className="mkt-grid" aria-hidden="true" />

      <Box
        className="mkt-container mkt-container-wide"
        style={{ padding: '140px 24px 100px', zIndex: 10 }}
      >
        <Flex direction={{ initial: 'column', lg: 'row' }} gap="8" align="center">
          <Box style={{ flex: 1, maxWidth: 680 }}>
            <Box className="mkt-fade-up">
              <Flex align="center" gap="3" mb="5">
                <Box className="mkt-badge mkt-badge-hero">
                  <Sparks width={16} height={16} style={{ marginRight: 8 }} />
                  Event ticketing for Zambia
                  <Box className="mkt-badge-shine" aria-hidden="true" />
                </Box>
              </Flex>
            </Box>

            <Box className="mkt-fade-up" style={{ animationDelay: '100ms' }}>
              <Heading as="h1" className="mkt-display">
                <span className="mkt-fg">Sell out your</span>
                <br />
                <span className="mkt-accent-text">events faster</span>
              </Heading>
            </Box>

            <Box className="mkt-fade-up" style={{ animationDelay: '200ms' }}>
              <Text
                size="5"
                className="mkt-lead"
                style={{ display: 'block', marginBottom: 12, maxWidth: 540 }}
              >
                The ticketing platform built for how Zambians actually buy.
              </Text>
              <Text
                size="4"
                className="mkt-body-faint"
                style={{ display: 'block', marginBottom: 40, maxWidth: 500 }}
              >
                Take <strong className="mkt-strong">MTN MoMo, Airtel Money and Zamtel Kwacha</strong>{' '}
                on every ticket. Get paid fast. Scan people in at the door in seconds.
              </Text>
            </Box>

            <Box className="mkt-fade-up" style={{ animationDelay: '300ms' }}>
              <Flex gap="4" wrap="wrap" mb="6">
                <Button
                  data-testid="hero-get-started"
                  size="4"
                  className="mkt-cta"
                  onClick={handleGetStarted}
                  disabled={isStarting}
                  style={{ cursor: isStarting ? 'wait' : 'pointer' }}
                >
                  <span>{isStarting ? 'Redirecting…' : 'Start selling free'}</span>
                  <ArrowRight width={20} height={20} style={{ marginLeft: 8 }} />
                  <Box className="mkt-cta-sheen" aria-hidden="true" />
                </Button>
                <a href="#features" style={{ textDecoration: 'none' }}>
                  <Button data-testid="hero-see-features" size="4" className="mkt-cta-secondary">
                    <Play width={18} height={18} style={{ marginRight: 8 }} />
                    See what you get
                  </Button>
                </a>
              </Flex>
            </Box>

            <Box className="mkt-fade-up" style={{ animationDelay: '400ms' }}>
              <Flex gap="5" wrap="wrap" align="center">
                {['No monthly fee', 'Mobile money built in', 'Fast payouts'].map((item) => (
                  <Flex key={item} align="center" gap="2">
                    <Box
                      aria-hidden="true"
                      style={{ display: 'flex', color: 'var(--marketing-accent)' }}
                    >
                      <CheckCircle width={16} height={16} />
                    </Box>
                    <Text size="2" className="mkt-body">
                      {item}
                    </Text>
                  </Flex>
                ))}
              </Flex>
            </Box>
          </Box>

          {/* Dashboard preview — the hero's thesis: this is what you get. */}
          <Box
            className="mkt-fade-up mkt-preview-wrap"
            style={{
              flex: 1,
              display: 'flex',
              justifyContent: 'center',
              perspective: '1200px',
            }}
          >
            <Box className="mkt-preview">
              <Box className="mkt-preview-glow" aria-hidden="true" />

              <Box className="mkt-preview-chrome">
                <Box className="mkt-preview-url">
                  <Text size="1" className="mkt-body">
                    dashboard.myticket.zm
                  </Text>
                </Box>
              </Box>

              <Box className="mkt-preview-body">
                <Flex justify="between" align="center" mb="5">
                  <Box>
                    <Text as="p" size="1" className="mkt-body">
                      Your dashboard
                    </Text>
                    <Text as="p" size="4" weight="bold" className="mkt-fg">
                      Event overview
                    </Text>
                  </Box>
                  <Box className="mkt-preview-live">
                    <Box className="mkt-preview-live-dot" aria-hidden="true" />
                    Live
                  </Box>
                </Flex>

                <Box className="mkt-preview-stats">
                  <Box className="mkt-preview-tile">
                    <Box className="mkt-preview-tile-icon mkt-preview-tile-icon-accent" aria-hidden="true">
                      <Calendar width={18} height={18} />
                    </Box>
                    <Box className="mkt-preview-value ds-amount">2,847</Box>
                    <Text size="1" className="mkt-body">
                      Tickets sold
                    </Text>
                  </Box>
                  {/* Revenue is money — jade, not brand teal. */}
                  <Box className="mkt-preview-tile">
                    <Box className="mkt-preview-tile-icon mkt-preview-tile-icon-money" aria-hidden="true">
                      <Wallet width={18} height={18} />
                    </Box>
                    <Box className="mkt-preview-value ds-amount">K 142,350</Box>
                    <Text size="1" className="mkt-body">
                      Revenue
                    </Text>
                  </Box>
                </Box>

                <Box className="mkt-preview-chart">
                  <Flex justify="between" align="center" mb="3">
                    <Text size="1" weight="medium" className="mkt-body">
                      Sales trend
                    </Text>
                    <Box className="mkt-preview-realtime">
                      <GraphUp width={12} height={12} style={{ marginRight: 4 }} />
                      Real time
                    </Box>
                  </Flex>
                  <Flex gap="2" align="end" style={{ height: 80 }}>
                    {[25, 40, 35, 55, 70, 85, 95].map((height, i) => (
                      <Box
                        key={i}
                        className="mkt-preview-bar"
                        style={{ height: `${height}%`, animationDelay: `${i * 0.1}s` }}
                      />
                    ))}
                  </Flex>
                </Box>
              </Box>
            </Box>
          </Box>
        </Flex>
      </Box>
    </Box>
  );
}

// =============================================================================
// FEATURES
// =============================================================================

const FEATURES = [
  {
    icon: SmartphoneDevice,
    title: 'Mobile money on every ticket',
    description:
      'MTN MoMo, Airtel Money and Zamtel Kwacha are always on the checkout — never behind a "more options" link.',
  },
  {
    icon: QrCode,
    title: 'Scan people in with a phone',
    description: 'Check attendees in at the door with any smartphone. It keeps working offline.',
  },
  {
    icon: StatsReport,
    title: 'Watch sales as they happen',
    description: 'See what is selling, who is coming and what you have earned, updated live.',
  },
  {
    icon: Group,
    title: 'Bring your team in',
    description: 'Add colleagues with the right role so they can help without seeing your finances.',
  },
  {
    icon: Wallet,
    title: 'Get your money quickly',
    description: 'Send your balance straight to a bank account or a mobile money wallet.',
  },
  {
    icon: Shield,
    title: 'Payments you can trust',
    description: 'PCI-compliant processing. Your money and your buyers stay protected.',
  },
];

function FeaturesSection() {
  const { isInView, ref } = useInView(0.2);

  return (
    <Box id="features" ref={ref} className="mkt-section mkt-section-features">
      <Box
        className="mkt-orb"
        aria-hidden="true"
        style={{
          top: '10%',
          left: '5%',
          width: 300,
          height: 300,
          background: 'radial-gradient(circle, var(--marketing-glow-primary) 0%, transparent 70%)',
          filter: 'blur(60px)',
        }}
      />
      <Box
        className="mkt-orb"
        aria-hidden="true"
        style={{
          bottom: '20%',
          right: '10%',
          width: 250,
          height: 250,
          background: 'radial-gradient(circle, var(--marketing-glow-secondary) 0%, transparent 70%)',
          filter: 'blur(50px)',
        }}
      />

      <Box className="mkt-container">
        <Box style={{ textAlign: 'center', marginBottom: 64 }}>
          <Box className="mkt-badge">
            <Flash width={14} height={14} style={{ marginRight: 6 }} />
            What you get
          </Box>
          <Heading as="h2" className="mkt-h2">
            Everything you need to <span className="mkt-accent-word">run the night</span>
          </Heading>
          <Text
            size="4"
            className="mkt-body"
            style={{ display: 'block', maxWidth: 600, margin: '0 auto' }}
          >
            From putting an event up to getting paid out, the plumbing is handled so you can
            concentrate on the people in the room.
          </Text>
        </Box>

        <Box
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))',
            gap: 24,
          }}
        >
          {FEATURES.map((feature, index) => (
            <Box
              key={feature.title}
              className="mkt-card mkt-reveal"
              data-visible={isInView}
              style={{ transitionDelay: `${index * 100}ms` }}
            >
              <Box
                className="mkt-card-icon"
                aria-hidden="true"
                style={{
                  background:
                    'linear-gradient(135deg, var(--marketing-accent), var(--marketing-accent-deep))',
                }}
              >
                <feature.icon width={26} height={26} />
              </Box>
              <Text as="p" size="4" weight="bold" className="mkt-fg" style={{ marginBottom: 8 }}>
                {feature.title}
              </Text>
              <Text as="p" size="2" className="mkt-body">
                {feature.description}
              </Text>
            </Box>
          ))}
        </Box>
      </Box>
    </Box>
  );
}

// =============================================================================
// HOW IT WORKS
// The numbering here is real: these four steps happen in this order, every
// time. That is the only reason they carry step numbers.
// =============================================================================

const STEPS = [
  {
    number: '01',
    title: 'Put your event up',
    description: 'Add the date, the venue and your ticket types. It takes a few minutes.',
    icon: Calendar,
  },
  {
    number: '02',
    title: 'Share the link',
    description: 'Send your event page anywhere. Buyers pay with mobile money or card.',
    icon: CreditCard,
  },
  {
    number: '03',
    title: 'Scan them in',
    description: 'Check attendees in at the door with a phone. Fast enough for any queue.',
    icon: QrCode,
  },
  {
    number: '04',
    title: 'Take your money out',
    description: 'Request a payout to your bank or mobile money wallet whenever you want it.',
    icon: Wallet,
  },
];

function HowItWorksSection() {
  const { isInView, ref } = useInView(0.2);

  return (
    <Box ref={ref} className="mkt-section mkt-section-steps">
      <Box
        className="mkt-orb"
        aria-hidden="true"
        style={{
          top: -100,
          right: -100,
          width: 400,
          height: 400,
          background: 'radial-gradient(circle, var(--marketing-glow-primary) 0%, transparent 60%)',
          filter: 'blur(80px)',
        }}
      />

      <Box className="mkt-container">
        <Box style={{ textAlign: 'center', marginBottom: 64 }}>
          <Box className="mkt-badge">
            <Clock width={14} height={14} style={{ marginRight: 6 }} />
            How it works
          </Box>
          <Heading as="h2" className="mkt-h2">
            Four steps, <span className="mkt-accent-word">start to payout</span>
          </Heading>
          <Text
            size="4"
            className="mkt-body"
            style={{ display: 'block', maxWidth: 600, margin: '0 auto' }}
          >
            You can be selling tickets today. Nothing technical required.
          </Text>
        </Box>

        <Box
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
            gap: 32,
          }}
        >
          {STEPS.map((step, index) => (
            <Box
              key={step.number}
              className="mkt-reveal"
              data-visible={isInView}
              style={{ textAlign: 'center', transitionDelay: `${index * 150}ms` }}
            >
              <Box className="mkt-step-icon-wrap">
                <Box className="mkt-step-icon" aria-hidden="true">
                  <step.icon width={32} height={32} />
                </Box>
                <Box className="mkt-step-glow" aria-hidden="true" />
              </Box>
              <Text
                as="p"
                className="ds-label ds-amount"
                style={{ color: 'var(--marketing-accent)', marginBottom: 12 }}
              >
                Step {step.number}
              </Text>
              <Text as="p" size="5" weight="bold" className="mkt-fg" style={{ marginBottom: 12 }}>
                {step.title}
              </Text>
              <Text as="p" size="2" className="mkt-body">
                {step.description}
              </Text>
            </Box>
          ))}
        </Box>
      </Box>
    </Box>
  );
}

// =============================================================================
// BEFORE / AFTER
// =============================================================================

const COMPARISONS = [
  { before: 'Chasing bank transfers', after: 'Mobile money checkout in seconds' },
  { before: 'Waiting weeks to see your money', after: 'Payouts you request when you need them' },
  { before: 'A clipboard at the door', after: 'A phone that scans a ticket in a second' },
  { before: 'Guessing who your audience is', after: 'Sales and attendance you can actually read' },
];

function TransformationSection() {
  const { isInView, ref } = useInView(0.3);

  return (
    <Box ref={ref} className="mkt-section mkt-section-compare">
      <Box
        className="mkt-orb"
        aria-hidden="true"
        style={{
          top: '50%',
          left: '50%',
          transform: 'translate(-50%, -50%)',
          width: 600,
          height: 400,
          borderRadius: 0,
          background: 'radial-gradient(ellipse, var(--marketing-glow-cool) 0%, transparent 60%)',
          filter: 'blur(80px)',
        }}
      />

      <Box className="mkt-container">
        <Box style={{ textAlign: 'center', marginBottom: 64 }}>
          <Box className="mkt-badge">
            <Flash width={14} height={14} style={{ marginRight: 6 }} />
            The difference
          </Box>
          <Heading as="h2" className="mkt-h2">
            What changes when you <span className="mkt-accent-word">switch</span>
          </Heading>
          <Text
            size="4"
            className="mkt-body"
            style={{ display: 'block', maxWidth: 600, margin: '0 auto' }}
          >
            The four things organizers tell us they stopped worrying about.
          </Text>
        </Box>

        <Box
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
            gap: 20,
          }}
        >
          {COMPARISONS.map((item, index) => (
            <Box
              key={item.before}
              className="mkt-compare mkt-reveal"
              data-visible={isInView}
              style={{ transitionDelay: `${index * 100}ms` }}
            >
              <Box className="mkt-compare-before">
                <Flex align="center" gap="3">
                  <Box className="mkt-compare-icon mkt-compare-icon-before" aria-hidden="true">
                    <Xmark width={16} height={16} />
                  </Box>
                  <Text size="2" className="mkt-body" style={{ textDecoration: 'line-through' }}>
                    {item.before}
                  </Text>
                </Flex>
              </Box>

              <Flex justify="center" style={{ margin: '-10px 0', position: 'relative', zIndex: 1 }}>
                <Box className="mkt-compare-arrow" aria-hidden="true">
                  <NavArrowRight width={12} height={12} style={{ transform: 'rotate(90deg)' }} />
                </Box>
              </Flex>

              <Box className="mkt-compare-after">
                <Flex align="center" gap="3">
                  <Box className="mkt-compare-icon mkt-compare-icon-after" aria-hidden="true">
                    <CheckCircle width={16} height={16} />
                  </Box>
                  <Text size="2" weight="medium" className="mkt-fg">
                    {item.after}
                  </Text>
                </Flex>
              </Box>
            </Box>
          ))}
        </Box>
      </Box>
    </Box>
  );
}

// =============================================================================
// PRICING
// =============================================================================

function PricingSection() {
  const [isStarting, setIsStarting] = useState(false);

  const handleGetStarted = useCallback(async () => {
    try {
      setIsStarting(true);
      await registerWithKeycloak('/apply/business-info');
    } catch (err) {
      console.error('Failed to start registration:', err);
      setIsStarting(false);
    }
  }, []);

  return (
    <Box id="pricing" className="mkt-section mkt-section-pricing">
      <Box
        className="mkt-orb"
        aria-hidden="true"
        style={{
          top: 0,
          left: '30%',
          width: 500,
          height: 500,
          background: 'radial-gradient(circle, var(--marketing-glow-soft) 0%, transparent 50%)',
          filter: 'blur(100px)',
        }}
      />

      <Box className="mkt-container mkt-container-narrow">
        <Box className="mkt-badge">
          <Wallet width={14} height={14} style={{ marginRight: 6 }} />
          Pricing
        </Box>
        <Heading as="h2" className="mkt-h2" style={{ lineHeight: 1.2 }}>
          No monthly fee.
          <br />
          <span className="mkt-accent-word">You pay when you sell.</span>
        </Heading>
        <Text
          as="p"
          size="4"
          className="mkt-body"
          style={{ marginBottom: 40, display: 'block' }}
        >
          We take a small share of each ticket sold. That is the whole arrangement — nothing up
          front, nothing monthly.
        </Text>

        <Box className="mkt-pricing-card">
          <Box className="mkt-pricing-glow" aria-hidden="true" />
          <Flex direction="column" gap="4" align="center">
            <Flex gap="4" wrap="wrap" justify="center">
              {[
                'Unlimited events',
                'Mobile money included',
                'Check-in app',
                'Team accounts',
              ].map((item) => (
                <Flex key={item} align="center" gap="2">
                  <Check
                    width={18}
                    height={18}
                    aria-hidden="true"
                    style={{ color: 'var(--marketing-accent)' }}
                  />
                  <Text size="2" className="mkt-lead">
                    {item}
                  </Text>
                </Flex>
              ))}
            </Flex>
            <Button
              data-testid="pricing-get-started"
              size="3"
              className="mkt-cta-plain"
              onClick={handleGetStarted}
              disabled={isStarting}
              style={{ cursor: isStarting ? 'wait' : 'pointer' }}
            >
              {isStarting ? 'Redirecting…' : 'Start selling free'}
              <NavArrowRight width={16} height={16} style={{ marginLeft: 4 }} />
            </Button>
          </Flex>
        </Box>
      </Box>
    </Box>
  );
}

// =============================================================================
// CLOSING CTA
// =============================================================================

function CTASection() {
  const [isStarting, setIsStarting] = useState(false);
  const [isSigningIn, setIsSigningIn] = useState(false);

  const handleGetStarted = useCallback(async () => {
    try {
      setIsStarting(true);
      await registerWithKeycloak('/dashboard');
    } catch (err) {
      console.error('Failed to start registration:', err);
      setIsStarting(false);
    }
  }, []);

  const handleSignIn = useCallback(async () => {
    try {
      setIsSigningIn(true);
      await signInWithKeycloak('/dashboard');
    } catch (err) {
      console.error('Failed to sign in:', err);
      setIsSigningIn(false);
    }
  }, []);

  return (
    <Box className="mkt-section-cta">
      <Box className="mkt-cta-pattern" aria-hidden="true" />

      <Box
        className="mkt-container mkt-container-narrow"
        style={{ maxWidth: 800, padding: 0 }}
      >
        <Heading
          as="h2"
          className="mkt-fg"
          style={{
            fontSize: 'clamp(28px, 5vw, 52px)',
            fontWeight: 'var(--weight-extrabold)',
            marginBottom: 20,
            letterSpacing: '-0.02em',
            lineHeight: 1.2,
            textWrap: 'balance',
          }}
        >
          Ready to sell out your next event?
        </Heading>
        <Text
          as="p"
          size="4"
          className="mkt-lead"
          style={{ maxWidth: 600, margin: '0 auto 40px' }}
        >
          Join the organizers across Zambia growing their audiences and getting paid faster.
        </Text>
        <Flex gap="4" justify="center" wrap="wrap">
          <Button
            data-testid="cta-get-started"
            size="4"
            className="mkt-cta-invert"
            onClick={handleGetStarted}
            disabled={isStarting}
            style={{ cursor: isStarting ? 'wait' : 'pointer' }}
          >
            {isStarting ? 'Redirecting…' : 'Start selling free'}
            <ArrowRight width={20} height={20} style={{ marginLeft: 8 }} />
          </Button>
          <Button
            data-testid="cta-sign-in"
            size="4"
            className="mkt-cta-onbrand"
            onClick={handleSignIn}
            disabled={isSigningIn}
            style={{ cursor: isSigningIn ? 'wait' : 'pointer' }}
          >
            <User width={18} height={18} style={{ marginRight: 8 }} />
            {isSigningIn ? 'Redirecting…' : 'Sign in'}
          </Button>
        </Flex>
      </Box>
    </Box>
  );
}

// =============================================================================
// PAGE
// =============================================================================

export default function LandingPage() {
  return (
    <Box className="mkt-page">
      <HeroSection />
      <FeaturesSection />
      <HowItWorksSection />
      <TransformationSection />
      <PricingSection />
      <CTASection />
    </Box>
  );
}
