'use client';

/**
 * Landing Page - Modern Premium Design
 *
 * Conversion-focused landing page with:
 * - Dynamic gradient backgrounds
 * - Glassmorphism effects
 * - Smooth animations
 * - Premium dark theme
 * - Mobile-first responsive
 */

import { useEffect, useRef, useState, useCallback } from 'react';
import { registerWithKeycloak, signInWithKeycloak } from '@/lib/auth/client';
import {
  Box,
  Flex,
  Heading,
  Button,
  Text,
} from '@radix-ui/themes';
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
// ANIMATED HOOKS
// =============================================================================

function useInView(threshold = 0.2) {
  const [isInView, setIsInView] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (ref.current) {
      const observer = new IntersectionObserver(
        ([entry]) => {
          if (entry.isIntersecting) {
            setIsInView(true);
          }
        },
        { threshold }
      );
      observer.observe(ref.current);
      return () => observer.disconnect();
    }
    return undefined;
  }, [threshold]);

  return { isInView, ref };
}

// =============================================================================
// HERO SECTION - Premium Dark with Aurora Effect
// =============================================================================

function HeroSection() {
  const [isStarting, setIsStarting] = useState(false);

  // Generate particle positions client-side only to avoid hydration mismatch
  const [particles, setParticles] = useState<{ left: number; delay: number; duration: number }[]>([]);

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
      // Go directly to Keycloak registration, then to business info form
      await registerWithKeycloak('/apply/business-info');
    } catch (err) {
      console.error('Failed to start registration:', err);
      setIsStarting(false);
    }
  }, []);

  return (
    <Box
      style={{
        position: 'relative',
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        overflow: 'hidden',
        background: '#0A0A0F',
      }}
    >
      {/* Aurora Gradient Background */}
      <Box className="aurora-bg" />

      {/* Animated Mesh Gradient */}
      <Box className="mesh-gradient" />

      {/* Floating Particles - rendered client-side only to avoid hydration mismatch */}
      <Box className="particles">
        {particles.map((particle, i) => (
          <Box
            key={i}
            className="particle"
            style={{
              left: `${particle.left}%`,
              animationDelay: `${particle.delay}s`,
              animationDuration: `${particle.duration}s`,
            }}
          />
        ))}
      </Box>

      {/* Grid Pattern */}
      <Box className="grid-pattern" />

      <Box
        style={{
          maxWidth: 1400,
          margin: '0 auto',
          padding: '140px 24px 100px',
          position: 'relative',
          width: '100%',
          zIndex: 10,
        }}
      >
        <Flex
          direction={{ initial: 'column', lg: 'row' }}
          gap="8"
          align="center"
        >
          {/* Left Content */}
          <Box style={{ flex: 1, maxWidth: 680 }}>
            {/* Announcement Badge */}
            <Box className="fade-in-up" style={{ animationDelay: '0ms' }}>
              <Flex align="center" gap="3" mb="5">
                <Box className="glow-badge">
                  <Sparks style={{ width: 16, height: 16, marginRight: 8 }} />
                  Event Ticketing Platform
                  <Box className="badge-shine" />
                </Box>
              </Flex>
            </Box>

            {/* Main Headline */}
            <Box className="fade-in-up" style={{ animationDelay: '100ms' }}>
              <Heading
                as="h1"
                style={{
                  fontSize: 'clamp(40px, 7vw, 80px)',
                  fontWeight: 800,
                  lineHeight: 1.0,
                  marginBottom: 28,
                  letterSpacing: '-0.03em',
                }}
              >
                <span className="text-gradient-white">
                  Sell Out Your
                </span>
                <br />
                <span className="text-gradient-brand">
                  Events Faster
                </span>
              </Heading>
            </Box>

            {/* Value Proposition */}
            <Box className="fade-in-up" style={{ animationDelay: '200ms' }}>
              <Text
                size="5"
                style={{
                  color: '#B8C5D3',
                  display: 'block',
                  marginBottom: 12,
                  lineHeight: 1.7,
                  maxWidth: 540,
                }}
              >
                The ticketing platform built for how Zambians actually buy.
              </Text>
              <Text
                size="4"
                style={{
                  color: '#7A8B9A',
                  display: 'block',
                  marginBottom: 40,
                  lineHeight: 1.7,
                  maxWidth: 500,
                }}
              >
                Accept <strong className="highlight-text">MTN MoMo, Airtel Money & Zamtel</strong> instantly.
                Get paid fast. Check-in attendees in seconds.
              </Text>
            </Box>

            {/* CTA Buttons */}
            <Box className="fade-in-up" style={{ animationDelay: '300ms' }}>
              <Flex gap="4" wrap="wrap" mb="6">
                <Button
                  size="4"
                  className="cta-primary"
                  onClick={handleGetStarted}
                  disabled={isStarting}
                  style={{ cursor: isStarting ? 'wait' : 'pointer' }}
                >
                  <span>{isStarting ? 'Redirecting...' : 'Get Started Free'}</span>
                  <ArrowRight style={{ width: 20, height: 20, marginLeft: 8 }} />
                  <Box className="btn-glow" />
                </Button>
                <a href="#features" style={{ textDecoration: 'none' }}>
                  <Button
                    size="4"
                    className="cta-secondary"
                  >
                    <Play style={{ width: 18, height: 18, marginRight: 8 }} />
                    See Features
                  </Button>
                </a>
              </Flex>
            </Box>

            {/* Trust Indicators */}
            <Box className="fade-in-up" style={{ animationDelay: '400ms' }}>
              <Flex gap="5" wrap="wrap" align="center">
                {[
                  'No monthly fees',
                  'Mobile money built-in',
                  'Fast payouts',
                ].map((item) => (
                  <Flex key={item} align="center" gap="2" className="trust-item">
                    <Box className="check-icon">
                      <CheckCircle style={{ width: 16, height: 16 }} />
                    </Box>
                    <Text size="2" style={{ color: '#8A9BAA' }}>{item}</Text>
                  </Flex>
                ))}
              </Flex>
            </Box>
          </Box>

          {/* Right - Dashboard Preview */}
          <Box
            className="fade-in-up dashboard-preview-container"
            style={{ flex: 1, display: 'flex', justifyContent: 'center', perspective: '1200px' }}
          >
            <Box className="dashboard-card">
              {/* Glow Effect */}
              <Box className="card-glow" />

              {/* Browser Chrome */}
              <Box className="browser-header">
                <Flex gap="2">
                  <Box className="browser-dot red" />
                  <Box className="browser-dot yellow" />
                  <Box className="browser-dot green" />
                </Flex>
                <Box className="browser-url">
                  <Text size="1" style={{ color: '#6B7280' }}>
                    dashboard.myticket.zm
                  </Text>
                </Box>
              </Box>

              {/* Dashboard Content */}
              <Box className="dashboard-content">
                {/* Header */}
                <Flex justify="between" align="center" mb="5">
                  <Box>
                    <Text size="1" style={{ color: '#6B7280', display: 'block' }}>
                      Your Dashboard
                    </Text>
                    <Text size="4" weight="bold" style={{ color: '#F8FAFC' }}>
                      Event Overview
                    </Text>
                  </Box>
                  <Box className="live-badge">
                    <Box className="live-dot" />
                    Live
                  </Box>
                </Flex>

                {/* Stats Grid */}
                <Box className="stats-grid">
                  <Box className="stat-card">
                    <Box className="stat-icon" style={{ background: 'rgba(16, 185, 129, 0.15)' }}>
                      <Calendar style={{ width: 18, height: 18, color: '#10B981' }} />
                    </Box>
                    <Box className="stat-value">2,847</Box>
                    <Text size="1" style={{ color: '#6B7280' }}>Tickets Sold</Text>
                  </Box>
                  <Box className="stat-card">
                    <Box className="stat-icon" style={{ background: 'rgba(20, 184, 166, 0.15)' }}>
                      <Wallet style={{ width: 18, height: 18, color: '#14B8A6' }} />
                    </Box>
                    <Box className="stat-value">K142,350</Box>
                    <Text size="1" style={{ color: '#6B7280' }}>Revenue</Text>
                  </Box>
                </Box>

                {/* Chart */}
                <Box className="chart-container">
                  <Flex justify="between" align="center" mb="3">
                    <Text size="1" weight="medium" style={{ color: '#9CA3AF' }}>
                      Sales Trends
                    </Text>
                    <Box className="realtime-badge">
                      <GraphUp style={{ width: 12, height: 12, marginRight: 4 }} />
                      Real-time
                    </Box>
                  </Flex>
                  <Flex gap="2" align="end" style={{ height: 80 }}>
                    {[25, 40, 35, 55, 70, 85, 95].map((height, i) => (
                      <Box
                        key={i}
                        className="chart-bar"
                        style={{
                          height: `${height}%`,
                          animationDelay: `${i * 0.1}s`,
                        }}
                      />
                    ))}
                  </Flex>
                </Box>
              </Box>
            </Box>
          </Box>
        </Flex>
      </Box>

      <style jsx global>{`
        /* Aurora Background */
        .aurora-bg {
          position: absolute;
          inset: 0;
          background:
            radial-gradient(ellipse 80% 50% at 20% 40%, rgba(16, 185, 129, 0.15), transparent 50%),
            radial-gradient(ellipse 60% 40% at 80% 60%, rgba(20, 184, 166, 0.12), transparent 50%),
            radial-gradient(ellipse 50% 30% at 50% 80%, rgba(59, 130, 246, 0.08), transparent 50%);
          animation: auroraShift 20s ease-in-out infinite;
        }

        @keyframes auroraShift {
          0%, 100% { opacity: 1; transform: scale(1); }
          50% { opacity: 0.8; transform: scale(1.1); }
        }

        /* Mesh Gradient */
        .mesh-gradient {
          position: absolute;
          inset: 0;
          background:
            radial-gradient(at 40% 20%, rgba(16, 185, 129, 0.08) 0px, transparent 50%),
            radial-gradient(at 80% 0%, rgba(20, 184, 166, 0.06) 0px, transparent 50%),
            radial-gradient(at 0% 50%, rgba(59, 130, 246, 0.05) 0px, transparent 50%),
            radial-gradient(at 80% 50%, rgba(16, 185, 129, 0.05) 0px, transparent 50%),
            radial-gradient(at 0% 100%, rgba(20, 184, 166, 0.08) 0px, transparent 50%);
          animation: meshFloat 30s ease-in-out infinite;
        }

        @keyframes meshFloat {
          0%, 100% { transform: translateY(0) rotate(0deg); }
          50% { transform: translateY(-20px) rotate(1deg); }
        }

        /* Particles */
        .particles {
          position: absolute;
          inset: 0;
          overflow: hidden;
          pointer-events: none;
        }

        .particle {
          position: absolute;
          width: 4px;
          height: 4px;
          background: rgba(16, 185, 129, 0.6);
          border-radius: 50%;
          bottom: -10px;
          animation: floatUp linear infinite;
          box-shadow: 0 0 10px rgba(16, 185, 129, 0.5);
        }

        @keyframes floatUp {
          0% { transform: translateY(0) scale(0); opacity: 0; }
          10% { opacity: 1; transform: scale(1); }
          90% { opacity: 1; }
          100% { transform: translateY(-100vh) scale(0.5); opacity: 0; }
        }

        /* Grid Pattern */
        .grid-pattern {
          position: absolute;
          inset: 0;
          background-image:
            linear-gradient(rgba(255, 255, 255, 0.02) 1px, transparent 1px),
            linear-gradient(90deg, rgba(255, 255, 255, 0.02) 1px, transparent 1px);
          background-size: 60px 60px;
          mask-image: radial-gradient(ellipse 80% 80% at 50% 50%, black, transparent);
          pointer-events: none;
        }

        /* Glow Badge */
        .glow-badge {
          display: inline-flex;
          align-items: center;
          padding: 12px 20px;
          background: linear-gradient(135deg, rgba(16, 185, 129, 0.15), rgba(20, 184, 166, 0.1));
          border: 1px solid rgba(16, 185, 129, 0.3);
          border-radius: 50px;
          color: #10B981;
          font-size: 14px;
          font-weight: 500;
          position: relative;
          overflow: hidden;
          backdrop-filter: blur(10px);
        }

        .badge-shine {
          position: absolute;
          top: 0;
          left: -100%;
          width: 100%;
          height: 100%;
          background: linear-gradient(90deg, transparent, rgba(255,255,255,0.2), transparent);
          animation: shine 3s infinite;
        }

        @keyframes shine {
          0% { left: -100%; }
          50%, 100% { left: 100%; }
        }

        /* Text Gradients */
        .text-gradient-white {
          color: #F8FAFC;
        }

        .text-gradient-brand {
          background: linear-gradient(135deg, #10B981 0%, #14B8A6 50%, #0EA5E9 100%);
          -webkit-background-clip: text;
          -webkit-text-fill-color: transparent;
          background-clip: text;
        }

        .highlight-text {
          color: #10B981;
          font-weight: 600;
        }

        /* CTA Buttons */
        .cta-primary {
          position: relative;
          background: linear-gradient(135deg, #10B981 0%, #059669 100%);
          padding: 20px 36px !important;
          font-size: 17px !important;
          font-weight: 600 !important;
          border-radius: 16px !important;
          border: none !important;
          cursor: pointer;
          overflow: hidden;
          transition: all 0.3s ease;
          box-shadow: 0 0 30px rgba(16, 185, 129, 0.4), 0 10px 40px rgba(16, 185, 129, 0.3);
        }

        .cta-primary:hover {
          transform: translateY(-3px);
          box-shadow: 0 0 50px rgba(16, 185, 129, 0.5), 0 20px 50px rgba(16, 185, 129, 0.4);
        }

        .btn-glow {
          position: absolute;
          inset: 0;
          background: linear-gradient(45deg, transparent 30%, rgba(255,255,255,0.3) 50%, transparent 70%);
          transform: translateX(-100%);
          transition: transform 0.6s ease;
        }

        .cta-primary:hover .btn-glow {
          transform: translateX(100%);
        }

        .cta-secondary {
          padding: 20px 28px !important;
          font-size: 17px !important;
          font-weight: 500 !important;
          border-radius: 16px !important;
          border: 1px solid rgba(255, 255, 255, 0.15) !important;
          background: rgba(255, 255, 255, 0.05) !important;
          color: #E5E7EB !important;
          cursor: pointer;
          transition: all 0.3s ease;
          backdrop-filter: blur(10px);
        }

        .cta-secondary:hover {
          background: rgba(255, 255, 255, 0.1) !important;
          border-color: rgba(255, 255, 255, 0.25) !important;
          transform: translateY(-2px);
        }

        /* Trust Items */
        .trust-item {
          opacity: 0.8;
          transition: opacity 0.2s ease;
        }

        .trust-item:hover {
          opacity: 1;
        }

        .check-icon {
          color: #10B981;
          display: flex;
        }

        /* Dashboard Card */
        .dashboard-card {
          position: relative;
          width: 100%;
          max-width: 480px;
          transform: rotateY(-8deg) rotateX(5deg);
          transform-style: preserve-3d;
          transition: transform 0.6s ease;
          border-radius: 20px;
          overflow: hidden;
          background: rgba(15, 23, 42, 0.8);
          border: 1px solid rgba(255, 255, 255, 0.1);
          backdrop-filter: blur(20px);
        }

        .dashboard-card:hover {
          transform: rotateY(0deg) rotateX(0deg) scale(1.02);
        }

        .card-glow {
          position: absolute;
          inset: -2px;
          background: linear-gradient(135deg, rgba(16, 185, 129, 0.3), rgba(20, 184, 166, 0.2), rgba(59, 130, 246, 0.2));
          border-radius: 22px;
          z-index: -1;
          filter: blur(20px);
          opacity: 0.6;
          animation: glowPulse 4s ease-in-out infinite;
        }

        @keyframes glowPulse {
          0%, 100% { opacity: 0.6; }
          50% { opacity: 0.9; }
        }

        .browser-header {
          display: flex;
          align-items: center;
          gap: 12px;
          padding: 14px 18px;
          background: rgba(17, 24, 39, 0.9);
          border-bottom: 1px solid rgba(255, 255, 255, 0.06);
        }

        .browser-dot {
          width: 12px;
          height: 12px;
          border-radius: 50%;
        }

        .browser-dot.red { background: #FF5F56; }
        .browser-dot.yellow { background: #FFBD2E; }
        .browser-dot.green { background: #27C93F; }

        .browser-url {
          flex: 1;
          background: rgba(255, 255, 255, 0.05);
          border-radius: 8px;
          padding: 8px 14px;
        }

        .dashboard-content {
          padding: 24px;
        }

        .live-badge {
          display: flex;
          align-items: center;
          gap: 6px;
          padding: 6px 12px;
          background: rgba(16, 185, 129, 0.15);
          border: 1px solid rgba(16, 185, 129, 0.3);
          border-radius: 20px;
          color: #10B981;
          font-size: 12px;
          font-weight: 500;
        }

        .live-dot {
          width: 6px;
          height: 6px;
          background: #10B981;
          border-radius: 50%;
          animation: pulse 2s infinite;
        }

        @keyframes pulse {
          0%, 100% { opacity: 1; transform: scale(1); }
          50% { opacity: 0.5; transform: scale(1.2); }
        }

        .stats-grid {
          display: grid;
          grid-template-columns: repeat(2, 1fr);
          gap: 12px;
          margin-bottom: 20px;
        }

        .stat-card {
          background: rgba(255, 255, 255, 0.03);
          border: 1px solid rgba(255, 255, 255, 0.06);
          border-radius: 14px;
          padding: 16px;
        }

        .stat-icon {
          width: 36px;
          height: 36px;
          border-radius: 10px;
          display: flex;
          align-items: center;
          justify-content: center;
          margin-bottom: 12px;
        }

        .stat-value {
          font-size: 22px;
          font-weight: 700;
          color: #F8FAFC;
          margin-bottom: 4px;
        }

        .chart-container {
          background: rgba(255, 255, 255, 0.03);
          border: 1px solid rgba(255, 255, 255, 0.06);
          border-radius: 14px;
          padding: 16px;
        }

        .realtime-badge {
          display: flex;
          align-items: center;
          padding: 4px 10px;
          background: rgba(16, 185, 129, 0.1);
          border-radius: 12px;
          color: #10B981;
          font-size: 11px;
          font-weight: 500;
        }

        .chart-bar {
          flex: 1;
          background: linear-gradient(180deg, #10B981 0%, rgba(16, 185, 129, 0.3) 100%);
          border-radius: 6px 6px 0 0;
          animation: growUp 0.8s ease-out both;
        }

        @keyframes growUp {
          from { transform: scaleY(0); transform-origin: bottom; }
          to { transform: scaleY(1); }
        }

        /* Animations */
        .fade-in-up {
          animation: fadeInUp 0.8s ease both;
        }

        @keyframes fadeInUp {
          from {
            opacity: 0;
            transform: translateY(30px);
          }
          to {
            opacity: 1;
            transform: translateY(0);
          }
        }

        /* Mobile Responsive */
        @media (max-width: 1024px) {
          .dashboard-preview-container {
            margin-top: 48px;
          }
        }

        @media (max-width: 768px) {
          .dashboard-preview-container {
            margin-top: 40px;
            width: 100%;
            max-width: 400px;
          }
          .dashboard-card {
            transform: none !important;
            max-width: 100%;
          }
          .particle {
            display: none;
          }
        }

        @media (max-width: 480px) {
          .dashboard-preview-container {
            margin-top: 32px;
            max-width: 100%;
          }
          .dashboard-card {
            border-radius: 16px !important;
          }
          .dashboard-content {
            padding: 16px !important;
          }
          .stats-grid {
            gap: 8px !important;
          }
          .stat-card {
            padding: 12px !important;
          }
          .stat-value {
            font-size: 18px !important;
          }
          .browser-header {
            padding: 10px 12px !important;
          }
          .cta-primary, .cta-secondary {
            width: 100%;
            justify-content: center;
          }
        }
      `}</style>
    </Box>
  );
}

// =============================================================================
// FEATURES SECTION
// =============================================================================

function FeaturesSection() {
  const { isInView, ref } = useInView(0.2);

  return (
    <Box
      id="features"
      ref={ref}
      style={{
        padding: '120px 0',
        background: 'linear-gradient(180deg, #0A0A0F 0%, #0D1117 50%, #101820 100%)',
        position: 'relative',
        scrollMarginTop: '100px',
      }}
    >
      <FeaturesContent isInView={isInView} />
    </Box>
  );
}

function FeaturesContent({ isInView }: { isInView: boolean }) {
  const features = [
    {
      icon: SmartphoneDevice,
      title: 'Mobile Money Payments',
      description: 'Accept MTN MoMo, Airtel Money, and Zamtel Kwacha. Your customers pay the way they prefer.',
      gradient: 'linear-gradient(135deg, #10B981, #059669)',
    },
    {
      icon: QrCode,
      title: 'QR Code Check-in',
      description: 'Scan tickets at the door with any smartphone. Works offline too.',
      gradient: 'linear-gradient(135deg, #14B8A6, #0D9488)',
    },
    {
      icon: StatsReport,
      title: 'Real-time Analytics',
      description: 'Track sales, attendance, and revenue as it happens. Make data-driven decisions.',
      gradient: 'linear-gradient(135deg, #0EA5E9, #0284C7)',
    },
    {
      icon: Group,
      title: 'Team Management',
      description: 'Add team members with different roles. Control who can do what.',
      gradient: 'linear-gradient(135deg, #8B5CF6, #7C3AED)',
    },
    {
      icon: Wallet,
      title: 'Fast Payouts',
      description: 'Get your money quickly. Direct to your bank account or mobile money.',
      gradient: 'linear-gradient(135deg, #F59E0B, #D97706)',
    },
    {
      icon: Shield,
      title: 'Secure & Reliable',
      description: 'PCI-compliant payments. Your data and your customers are protected.',
      gradient: 'linear-gradient(135deg, #EF4444, #DC2626)',
    },
  ];

  return (
    <>
      {/* Glassmorphism overlay effect */}
      <Box
        style={{
          position: 'absolute',
          top: '10%',
          left: '5%',
          width: '300px',
          height: '300px',
          background: 'radial-gradient(circle, rgba(16, 185, 129, 0.15) 0%, transparent 70%)',
          filter: 'blur(60px)',
          pointerEvents: 'none',
        }}
      />
      <Box
        style={{
          position: 'absolute',
          bottom: '20%',
          right: '10%',
          width: '250px',
          height: '250px',
          background: 'radial-gradient(circle, rgba(20, 184, 166, 0.12) 0%, transparent 70%)',
          filter: 'blur(50px)',
          pointerEvents: 'none',
        }}
      />

      <Box style={{ maxWidth: 1200, margin: '0 auto', padding: '0 24px', position: 'relative' }}>
        <Box style={{ textAlign: 'center', marginBottom: 64 }}>
          <Box className="section-badge">
            <Flash style={{ width: 14, height: 14, marginRight: 6 }} />
            Features
          </Box>
          <Heading
            as="h2"
            style={{
              fontSize: 'clamp(32px, 5vw, 52px)',
              fontWeight: 800,
              color: '#F8FAFC',
              marginBottom: 16,
              letterSpacing: '-0.02em',
            }}
          >
            Everything You Need to{' '}
            <span style={{ color: '#10B981' }}>Succeed</span>
          </Heading>
          <Text size="4" style={{ color: '#8A9BAA', maxWidth: 600, margin: '0 auto', lineHeight: 1.7 }}>
            From creating your event to getting paid, we handle the complexity so you can focus on your audience.
          </Text>
        </Box>

        <Box
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))',
            gap: 24,
          }}
        >
          {features.map((feature, index) => (
            <Box
              key={index}
              className="feature-card"
              style={{
                opacity: isInView ? 1 : 0,
                transform: isInView ? 'translateY(0)' : 'translateY(30px)',
                transition: `all 0.6s ease ${index * 0.1}s`,
              }}
            >
              <Box
                className="feature-icon"
                style={{ background: feature.gradient }}
              >
                <feature.icon style={{ width: 26, height: 26, color: 'white' }} />
              </Box>
              <Text
                size="4"
                weight="bold"
                style={{ color: '#F8FAFC', display: 'block', marginBottom: 8 }}
              >
                {feature.title}
              </Text>
              <Text size="2" style={{ color: '#8A9BAA', lineHeight: 1.7 }}>
                {feature.description}
              </Text>
            </Box>
          ))}
        </Box>
      </Box>

      <style jsx global>{`
        .section-badge {
          display: inline-flex;
          align-items: center;
          padding: 10px 18px;
          background: rgba(16, 185, 129, 0.1);
          border: 1px solid rgba(16, 185, 129, 0.2);
          border-radius: 30px;
          color: #10B981;
          font-size: 13px;
          font-weight: 500;
          margin-bottom: 20px;
        }

        .feature-card {
          background: rgba(255, 255, 255, 0.03);
          border: 1px solid rgba(255, 255, 255, 0.08);
          border-radius: 20px;
          padding: 28px;
          transition: all 0.4s ease;
          backdrop-filter: blur(10px);
          position: relative;
          overflow: hidden;
        }

        .feature-card::before {
          content: '';
          position: absolute;
          top: 0;
          left: 0;
          right: 0;
          height: 1px;
          background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.1), transparent);
        }

        .feature-card:hover {
          background: rgba(255, 255, 255, 0.06);
          border-color: rgba(16, 185, 129, 0.4);
          transform: translateY(-8px);
          box-shadow: 0 20px 40px rgba(0, 0, 0, 0.3), 0 0 60px rgba(16, 185, 129, 0.1);
        }

        .feature-icon {
          width: 56px;
          height: 56px;
          border-radius: 16px;
          display: flex;
          align-items: center;
          justify-content: center;
          margin-bottom: 20px;
          box-shadow: 0 10px 30px rgba(0, 0, 0, 0.3);
        }

        .glass-badge {
          backdrop-filter: blur(10px);
          background: rgba(16, 185, 129, 0.15) !important;
          border: 1px solid rgba(16, 185, 129, 0.25) !important;
        }

        @media (max-width: 640px) {
          .feature-card {
            grid-template-columns: 1fr !important;
          }
        }
      `}</style>
    </>
  );
}

// =============================================================================
// HOW IT WORKS SECTION
// =============================================================================

function HowItWorksSection() {
  const { isInView, ref } = useInView(0.2);

  const steps = [
    {
      number: '01',
      title: 'Create Your Event',
      description: 'Set up your event in minutes. Add ticket types, pricing, and all the details your attendees need.',
      icon: Calendar,
    },
    {
      number: '02',
      title: 'Share & Sell',
      description: 'Share your event page. Accept mobile money and card payments instantly.',
      icon: CreditCard,
    },
    {
      number: '03',
      title: 'Check-in Attendees',
      description: 'Scan QR codes at the door. Fast, reliable entry for any event size.',
      icon: QrCode,
    },
    {
      number: '04',
      title: 'Get Paid',
      description: 'Receive your earnings quickly. Track everything in your dashboard.',
      icon: Wallet,
    },
  ];

  return (
    <Box
      ref={ref}
      style={{
        padding: '120px 0',
        background: 'linear-gradient(135deg, #0F2922 0%, #0A1A14 50%, #0D1117 100%)',
        position: 'relative',
        overflow: 'hidden',
      }}
    >
      {/* Glassmorphism decorative elements */}
      <Box
        style={{
          position: 'absolute',
          top: '-100px',
          right: '-100px',
          width: '400px',
          height: '400px',
          background: 'radial-gradient(circle, rgba(16, 185, 129, 0.2) 0%, transparent 60%)',
          filter: 'blur(80px)',
          pointerEvents: 'none',
        }}
      />
      <Box
        style={{
          position: 'absolute',
          bottom: '-50px',
          left: '-50px',
          width: '300px',
          height: '300px',
          background: 'radial-gradient(circle, rgba(20, 184, 166, 0.15) 0%, transparent 60%)',
          filter: 'blur(60px)',
          pointerEvents: 'none',
        }}
      />

      <Box style={{ maxWidth: 1200, margin: '0 auto', padding: '0 24px', position: 'relative', zIndex: 1 }}>
        <Box style={{ textAlign: 'center', marginBottom: 64 }}>
          <Box className="section-badge glass-badge">
            <Clock style={{ width: 14, height: 14, marginRight: 6 }} />
            How It Works
          </Box>
          <Heading
            as="h2"
            style={{
              fontSize: 'clamp(32px, 5vw, 52px)',
              fontWeight: 800,
              color: '#F8FAFC',
              marginBottom: 16,
              letterSpacing: '-0.02em',
            }}
          >
            Simple Process,{' '}
            <span style={{ color: '#10B981' }}>Powerful Results</span>
          </Heading>
          <Text size="4" style={{ color: '#8A9BAA', maxWidth: 600, margin: '0 auto', lineHeight: 1.7 }}>
            Get started in minutes. No technical skills required.
          </Text>
        </Box>

        <Box
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
            gap: 32,
          }}
        >
          {steps.map((step, index) => (
            <Box
              key={index}
              style={{
                textAlign: 'center',
                opacity: isInView ? 1 : 0,
                transform: isInView ? 'translateY(0)' : 'translateY(30px)',
                transition: `all 0.6s ease ${index * 0.15}s`,
              }}
            >
              <Box className="step-icon-container">
                <Box className="step-icon">
                  <step.icon style={{ width: 32, height: 32, color: 'white' }} />
                </Box>
                <Box className="step-glow" />
              </Box>
              <Text
                size="1"
                weight="bold"
                style={{
                  color: '#10B981',
                  display: 'block',
                  marginBottom: 12,
                  letterSpacing: '0.15em',
                }}
              >
                STEP {step.number}
              </Text>
              <Text
                size="5"
                weight="bold"
                style={{ color: '#F8FAFC', display: 'block', marginBottom: 12 }}
              >
                {step.title}
              </Text>
              <Text size="2" style={{ color: '#8A9BAA', lineHeight: 1.7 }}>
                {step.description}
              </Text>
            </Box>
          ))}
        </Box>
      </Box>

      <style jsx global>{`
        .step-icon-container {
          position: relative;
          display: inline-flex;
          margin-bottom: 24px;
        }

        .step-icon {
          width: 80px;
          height: 80px;
          border-radius: 24px;
          background: linear-gradient(135deg, #10B981 0%, #059669 100%);
          display: flex;
          align-items: center;
          justify-content: center;
          position: relative;
          z-index: 1;
          box-shadow: 0 15px 35px rgba(16, 185, 129, 0.4);
          transition: all 0.3s ease;
        }

        .step-icon:hover {
          transform: scale(1.1) rotate(5deg);
          box-shadow: 0 20px 45px rgba(16, 185, 129, 0.5);
        }

        .step-glow {
          position: absolute;
          inset: -15px;
          background: linear-gradient(135deg, #10B981, #14B8A6);
          border-radius: 35px;
          filter: blur(30px);
          opacity: 0.5;
          z-index: 0;
          animation: stepPulse 3s ease-in-out infinite;
        }

        @keyframes stepPulse {
          0%, 100% { opacity: 0.5; transform: scale(1); }
          50% { opacity: 0.7; transform: scale(1.05); }
        }
      `}</style>
    </Box>
  );
}

// =============================================================================
// TRANSFORMATION SECTION
// =============================================================================

function TransformationSection() {
  const { isInView, ref } = useInView(0.3);

  const comparisons = [
    {
      before: { text: 'Chasing bank transfers', color: '#EF4444' },
      after: { text: 'Mobile money checkout in seconds', color: '#10B981' },
    },
    {
      before: { text: 'Waiting weeks for payouts', color: '#EF4444' },
      after: { text: 'Fast, reliable payments', color: '#10B981' },
    },
    {
      before: { text: 'Manual check-in chaos', color: '#EF4444' },
      after: { text: 'QR scan entry - quick & easy', color: '#10B981' },
    },
    {
      before: { text: 'Guessing who your audience is', color: '#EF4444' },
      after: { text: 'Real-time analytics dashboard', color: '#10B981' },
    },
  ];

  return (
    <Box
      ref={ref}
      style={{
        padding: '120px 0',
        background: 'linear-gradient(180deg, #0D1117 0%, #13111C 50%, #0A0A0F 100%)',
        position: 'relative',
        overflow: 'hidden',
      }}
    >
      {/* Subtle purple/blue accent for this section */}
      <Box
        style={{
          position: 'absolute',
          top: '50%',
          left: '50%',
          transform: 'translate(-50%, -50%)',
          width: '600px',
          height: '400px',
          background: 'radial-gradient(ellipse, rgba(139, 92, 246, 0.08) 0%, transparent 60%)',
          filter: 'blur(80px)',
          pointerEvents: 'none',
        }}
      />

      <Box style={{ maxWidth: 1200, margin: '0 auto', padding: '0 24px', position: 'relative', zIndex: 1 }}>
        <Box style={{ textAlign: 'center', marginBottom: 64 }}>
          <Box className="section-badge" style={{ background: 'linear-gradient(135deg, rgba(239, 68, 68, 0.1), rgba(16, 185, 129, 0.1))', backdropFilter: 'blur(10px)' }}>
            <Flash style={{ width: 14, height: 14, marginRight: 6 }} />
            The Difference
          </Box>
          <Heading
            as="h2"
            style={{
              fontSize: 'clamp(32px, 5vw, 52px)',
              fontWeight: 800,
              color: '#F8FAFC',
              marginBottom: 16,
              letterSpacing: '-0.02em',
            }}
          >
            From Frustration to{' '}
            <span style={{ color: '#10B981' }}>Success</span>
          </Heading>
          <Text size="4" style={{ color: '#8A9BAA', maxWidth: 600, margin: '0 auto', lineHeight: 1.7 }}>
            See what changes when you switch to MyTicket
          </Text>
        </Box>

        <Box
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
            gap: 20,
          }}
        >
          {comparisons.map((item, index) => (
            <Box
              key={index}
              className="comparison-card"
              style={{
                opacity: isInView ? 1 : 0,
                transform: isInView ? 'translateY(0)' : 'translateY(20px)',
                transition: `all 0.5s ease ${index * 0.1}s`,
              }}
            >
              {/* Before */}
              <Box className="comparison-before">
                <Flex align="center" gap="3">
                  <Box className="comparison-icon before">
                    <Xmark style={{ width: 16, height: 16 }} />
                  </Box>
                  <Text size="2" style={{ color: '#8A9BAA', textDecoration: 'line-through' }}>
                    {item.before.text}
                  </Text>
                </Flex>
              </Box>

              {/* Arrow */}
              <Flex justify="center" style={{ margin: '-10px 0', position: 'relative', zIndex: 1 }}>
                <Box className="comparison-arrow">
                  <NavArrowRight style={{ width: 12, height: 12, transform: 'rotate(90deg)' }} />
                </Box>
              </Flex>

              {/* After */}
              <Box className="comparison-after">
                <Flex align="center" gap="3">
                  <Box className="comparison-icon after">
                    <CheckCircle style={{ width: 16, height: 16 }} />
                  </Box>
                  <Text size="2" weight="medium" style={{ color: '#F8FAFC' }}>
                    {item.after.text}
                  </Text>
                </Flex>
              </Box>
            </Box>
          ))}
        </Box>
      </Box>

      <style jsx global>{`
        .comparison-card {
          background: rgba(255, 255, 255, 0.03);
          border: 1px solid rgba(255, 255, 255, 0.08);
          border-radius: 20px;
          overflow: hidden;
          transition: all 0.4s ease;
          backdrop-filter: blur(10px);
        }

        .comparison-card:hover {
          border-color: rgba(16, 185, 129, 0.4);
          transform: translateY(-5px);
          box-shadow: 0 15px 35px rgba(0, 0, 0, 0.25), 0 0 40px rgba(16, 185, 129, 0.08);
        }

        .comparison-before {
          padding: 18px 20px;
          background: rgba(239, 68, 68, 0.03);
          border-bottom: 1px dashed rgba(255, 255, 255, 0.06);
        }

        .comparison-after {
          padding: 18px 20px;
          background: rgba(16, 185, 129, 0.03);
        }

        .comparison-icon {
          width: 32px;
          height: 32px;
          border-radius: 10px;
          display: flex;
          align-items: center;
          justify-content: center;
        }

        .comparison-icon.before {
          background: rgba(239, 68, 68, 0.15);
          color: #EF4444;
        }

        .comparison-icon.after {
          background: rgba(16, 185, 129, 0.15);
          color: #10B981;
        }

        .comparison-arrow {
          width: 24px;
          height: 24px;
          border-radius: 50%;
          background: #0A0A0F;
          border: 1px solid rgba(255, 255, 255, 0.1);
          display: flex;
          align-items: center;
          justify-content: center;
          color: #10B981;
        }
      `}</style>
    </Box>
  );
}

// =============================================================================
// PRICING PREVIEW SECTION
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
    <Box
      id="pricing"
      style={{
        padding: '120px 0',
        background: '#0A0A0F',
        position: 'relative',
        overflow: 'hidden',
        scrollMarginTop: '100px',
      }}
    >
      {/* Glassmorphism background orbs */}
      <Box
        style={{
          position: 'absolute',
          top: '0',
          left: '30%',
          width: '500px',
          height: '500px',
          background: 'radial-gradient(circle, rgba(16, 185, 129, 0.1) 0%, transparent 50%)',
          filter: 'blur(100px)',
          pointerEvents: 'none',
        }}
      />
      <Box style={{ maxWidth: 800, margin: '0 auto', padding: '0 24px', textAlign: 'center' }}>
        <Box className="section-badge">
          <Wallet style={{ width: 14, height: 14, marginRight: 6 }} />
          Simple Pricing
        </Box>
        <Heading
          as="h2"
          style={{
            fontSize: 'clamp(28px, 4vw, 48px)',
            fontWeight: 800,
            color: '#F8FAFC',
            marginBottom: 16,
            letterSpacing: '-0.02em',
            lineHeight: 1.2,
          }}
        >
          No Monthly Fees.
          <br />
          <span style={{ color: '#10B981' }}>Pay Only When You Sell.</span>
        </Heading>
        <Text size="4" style={{ color: '#8A9BAA', marginBottom: 40, display: 'block', lineHeight: 1.7 }}>
          We only make money when you do. A small percentage of each ticket sale, that&apos;s it.
        </Text>

        <Box className="pricing-card">
          <Box className="pricing-glow" />
          <Flex direction="column" gap="4" align="center">
            <Flex gap="4" wrap="wrap" justify="center">
              {[
                'Unlimited events',
                'Mobile money included',
                'QR check-in app',
                'Team management',
              ].map((item) => (
                <Flex key={item} align="center" gap="2">
                  <Check style={{ width: 18, height: 18, color: '#10B981' }} />
                  <Text size="2" style={{ color: '#B8C5D3' }}>{item}</Text>
                </Flex>
              ))}
            </Flex>
            <Button
              size="3"
              className="pricing-cta"
              onClick={handleGetStarted}
              disabled={isStarting}
              style={{ cursor: isStarting ? 'wait' : 'pointer' }}
            >
              {isStarting ? 'Redirecting...' : 'Get Started Free'}
              <NavArrowRight style={{ width: 16, height: 16, marginLeft: 4 }} />
            </Button>
          </Flex>
        </Box>
      </Box>

      <style jsx global>{`
        .pricing-card {
          position: relative;
          padding: 48px;
          background: linear-gradient(135deg, rgba(255, 255, 255, 0.05) 0%, rgba(255, 255, 255, 0.02) 100%);
          border: 1px solid rgba(255, 255, 255, 0.1);
          border-radius: 28px;
          backdrop-filter: blur(20px);
          box-shadow: 0 25px 50px rgba(0, 0, 0, 0.25);
        }

        .pricing-card::before {
          content: '';
          position: absolute;
          top: 0;
          left: 20%;
          right: 20%;
          height: 1px;
          background: linear-gradient(90deg, transparent, rgba(16, 185, 129, 0.5), transparent);
        }

        .pricing-glow {
          position: absolute;
          inset: -2px;
          background: linear-gradient(135deg, rgba(16, 185, 129, 0.25), transparent 40%, transparent 60%, rgba(20, 184, 166, 0.25));
          border-radius: 30px;
          z-index: -1;
          filter: blur(40px);
          opacity: 0.6;
          animation: pricingGlow 4s ease-in-out infinite;
        }

        @keyframes pricingGlow {
          0%, 100% { opacity: 0.6; }
          50% { opacity: 0.9; }
        }

        .pricing-cta {
          background: transparent !important;
          color: #10B981 !important;
          border: none !important;
          cursor: pointer;
          transition: all 0.2s ease;
        }

        .pricing-cta:hover {
          transform: translateX(5px);
        }
      `}</style>
    </Box>
  );
}

// =============================================================================
// CTA SECTION
// =============================================================================

function CTASection() {
  const [isStarting, setIsStarting] = useState(false);
  const [isSigningIn, setIsSigningIn] = useState(false);

  const handleGetStarted = useCallback(async () => {
    try {
      setIsStarting(true);
      // Redirect to Keycloak registration, then to dashboard after registration
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
    <Box
      style={{
        padding: '120px 24px',
        background: 'linear-gradient(135deg, #059669 0%, #10B981 50%, #14B8A6 100%)',
        position: 'relative',
        overflow: 'hidden',
      }}
    >
      {/* Animated Background */}
      <Box className="cta-bg-pattern" />

      <Box style={{ maxWidth: 800, margin: '0 auto', textAlign: 'center', position: 'relative', zIndex: 1 }}>
        <Heading
          as="h2"
          style={{
            fontSize: 'clamp(28px, 5vw, 52px)',
            fontWeight: 800,
            color: 'white',
            marginBottom: 20,
            letterSpacing: '-0.02em',
            lineHeight: 1.2,
            textWrap: 'balance',
          }}
        >
          Ready to Sell Out Your Next Event?
        </Heading>
        <Text
          size="4"
          style={{
            color: 'rgba(255, 255, 255, 0.9)',
            marginBottom: 40,
            display: 'block',
            maxWidth: 600,
            margin: '0 auto 40px',
            lineHeight: 1.7,
          }}
        >
          Join event organizers across Zambia who are growing their audiences and getting paid faster.
        </Text>
        <Flex gap="4" justify="center" wrap="wrap">
          <Button
            size="4"
            className="cta-btn-primary"
            onClick={handleGetStarted}
            disabled={isStarting}
            style={{ cursor: isStarting ? 'wait' : 'pointer' }}
          >
            {isStarting ? 'Redirecting...' : 'Get Started Free'}
            <ArrowRight style={{ width: 20, height: 20, marginLeft: 8 }} />
          </Button>
          <Button
            size="4"
            className="cta-btn-secondary"
            onClick={handleSignIn}
            disabled={isSigningIn}
            style={{ cursor: isSigningIn ? 'wait' : 'pointer' }}
          >
            <User style={{ width: 18, height: 18, marginRight: 8 }} />
            {isSigningIn ? 'Redirecting...' : 'Sign In'}
          </Button>
        </Flex>
      </Box>

      <style jsx global>{`
        .cta-bg-pattern {
          position: absolute;
          inset: 0;
          background:
            radial-gradient(circle at 20% 80%, rgba(255, 255, 255, 0.15) 0%, transparent 40%),
            radial-gradient(circle at 80% 20%, rgba(255, 255, 255, 0.1) 0%, transparent 40%);
          animation: ctaFloat 20s ease-in-out infinite;
        }

        @keyframes ctaFloat {
          0%, 100% { transform: translateY(0); }
          50% { transform: translateY(-10px); }
        }

        .cta-btn-primary {
          background: white !important;
          color: #059669 !important;
          padding: 20px 36px !important;
          font-size: 17px !important;
          font-weight: 600 !important;
          border-radius: 16px !important;
          border: none !important;
          cursor: pointer;
          box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2);
          transition: all 0.3s ease;
        }

        .cta-btn-primary:hover {
          transform: translateY(-3px);
          box-shadow: 0 15px 50px rgba(0, 0, 0, 0.25);
        }

        .cta-btn-secondary {
          background: rgba(255, 255, 255, 0.15) !important;
          color: white !important;
          padding: 20px 28px !important;
          font-size: 17px !important;
          font-weight: 500 !important;
          border-radius: 16px !important;
          border: 1px solid rgba(255, 255, 255, 0.3) !important;
          cursor: pointer;
          transition: all 0.3s ease;
          backdrop-filter: blur(10px);
        }

        .cta-btn-secondary:hover {
          background: rgba(255, 255, 255, 0.25) !important;
          transform: translateY(-2px);
        }

        @media (max-width: 480px) {
          .cta-btn-primary, .cta-btn-secondary {
            width: 100%;
            justify-content: center;
          }
        }
      `}</style>
    </Box>
  );
}

// =============================================================================
// MAIN PAGE
// =============================================================================

export default function LandingPage() {
  return (
    <Box style={{ background: '#0A0A0F' }}>
      <HeroSection />
      <FeaturesSection />
      <HowItWorksSection />
      <TransformationSection />
      <PricingSection />
      <CTASection />
    </Box>
  );
}
