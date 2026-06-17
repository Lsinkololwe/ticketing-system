'use client';

/**
 * Wizard Container Component
 *
 * Production-grade wizard layout for multi-step application flow.
 *
 * Features:
 * - Clean, focused design without distractions
 * - Automatic step tracking and progress indication
 * - Responsive layout with mobile optimization
 * - Consistent padding and spacing
 * - No fluff content - pure task completion focus
 *
 * Design Principles:
 * - Minimal: Only show what's necessary for the current step
 * - Clear: Progress indication is always visible
 * - Focused: Remove all distractions from task completion
 */

import { Box, Container } from '@radix-ui/themes';
import { StepIndicator, type Step } from './StepIndicator';

// =============================================================================
// TYPES
// =============================================================================

interface WizardContainerProps {
  children: React.ReactNode;
  steps: Step[];
  currentStep: number;
  onStepClick?: (step: number) => void;
  allowNavigation?: boolean;
  maxWidth?: '520px' | '640px' | '768px' | '896px' | '1024px';
}

// =============================================================================
// COMPONENT
// =============================================================================

export function WizardContainer({
  children,
  steps,
  currentStep,
  onStepClick,
  allowNavigation = true,
  maxWidth = '768px',
}: WizardContainerProps) {
  return (
    <Box
      style={{
        minHeight: '100vh',
        background: 'linear-gradient(180deg, var(--gradient-brand-start) 0%, transparent 240px)',
        padding: '40px 24px',
      }}
    >
      <Container
        size="3"
        style={{
          maxWidth,
          margin: '0 auto',
        }}
      >
        {/* Step Indicator */}
        <StepIndicator
          steps={steps}
          currentStep={currentStep}
          onStepClick={onStepClick}
          allowNavigation={allowNavigation}
        />

        {/* Main Content */}
        <Box
          style={{
            background: 'var(--surface-primary)',
            borderRadius: 'var(--radius-2xl)',
            padding: '32px',
            boxShadow: 'var(--shadow-lg)',
            border: '1px solid var(--surface-border)',
          }}
        >
          {children}
        </Box>
      </Container>
    </Box>
  );
}

export default WizardContainer;
