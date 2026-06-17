'use client';

/**
 * Step Indicator Component
 *
 * Minimal, clean progress indicator for multi-step wizard.
 *
 * RADIX UI THEMES COMPLIANT:
 * - Uses Radix Theme tokens (--accent-*, --gray-*)
 * - Uses Radix component props where possible
 * - Clean, modern design with inline labels
 *
 * Features:
 * - Horizontal inline design with step numbers and titles
 * - Current step highlighted with accent color
 * - Completed steps shown with checkmark
 * - Click to navigate between completed steps
 * - Mobile-responsive progress bar view
 */

import { Box, Flex, Text } from '@radix-ui/themes';
import { Check } from 'iconoir-react';

// =============================================================================
// TYPES
// =============================================================================

export interface Step {
  id: string;
  title: string;
  description?: string;
}

interface StepIndicatorProps {
  steps: Step[];
  currentStep: number;
  onStepClick?: (step: number) => void;
  allowNavigation?: boolean;
}

// =============================================================================
// COMPONENT
// =============================================================================

export function StepIndicator({
  steps,
  currentStep,
  onStepClick,
  allowNavigation = true,
}: StepIndicatorProps) {
  const handleStepClick = (index: number) => {
    if (allowNavigation && index < currentStep && onStepClick) {
      onStepClick(index);
    }
  };

  return (
    <Box mb="6">
      {/* Desktop View - Inline horizontal design */}
      <Flex
        className="step-indicator-desktop"
        align="center"
        justify="center"
        gap="0"
      >
        {steps.map((step, index) => {
          const isCompleted = index < currentStep;
          const isCurrent = index === currentStep;
          const isClickable = allowNavigation && isCompleted;
          const isLast = index === steps.length - 1;

          return (
            <Flex key={step.id} align="center">
              {/* Step Item */}
              <Flex
                align="center"
                gap="2"
                onClick={() => handleStepClick(index)}
                className={`step-item ${isClickable ? 'clickable' : ''}`}
                role={isClickable ? 'button' : undefined}
                tabIndex={isClickable ? 0 : undefined}
                aria-label={isClickable ? `Go to step ${index + 1}: ${step.title}` : undefined}
                aria-current={isCurrent ? 'step' : undefined}
                onKeyDown={(e) => {
                  if (isClickable && (e.key === 'Enter' || e.key === ' ')) {
                    e.preventDefault();
                    handleStepClick(index);
                  }
                }}
              >
                {/* Step Circle */}
                <Flex
                  align="center"
                  justify="center"
                  className={`step-circle ${isCurrent ? 'current' : ''} ${isCompleted ? 'completed' : ''}`}
                >
                  {isCompleted ? (
                    <Check width={14} height={14} strokeWidth={2.5} />
                  ) : (
                    <Text size="1" weight="medium">
                      {index + 1}
                    </Text>
                  )}
                </Flex>

                {/* Step Title */}
                <Text
                  size="2"
                  weight={isCurrent ? 'medium' : 'regular'}
                  className={`step-title ${isCurrent ? 'current' : ''} ${isCompleted ? 'completed' : ''}`}
                >
                  {step.title}
                </Text>
              </Flex>

              {/* Connector Line */}
              {!isLast && (
                <Box
                  className={`step-connector ${isCompleted ? 'completed' : ''}`}
                  mx="4"
                />
              )}
            </Flex>
          );
        })}
      </Flex>

      {/* Mobile View - Progress bar with current step */}
      <Box className="step-indicator-mobile">
        <Flex align="center" justify="between" mb="2">
          <Flex align="center" gap="2">
            <Flex
              align="center"
              justify="center"
              className="step-circle current mobile"
            >
              <Text size="1" weight="medium">
                {currentStep + 1}
              </Text>
            </Flex>
            <Text size="2" weight="medium" highContrast>
              {steps[currentStep]?.title}
            </Text>
          </Flex>
          <Text size="1" color="gray">
            {currentStep + 1} of {steps.length}
          </Text>
        </Flex>

        {/* Progress Bar */}
        <Box className="progress-track">
          <Box
            className="progress-bar"
            style={{
              width: `${((currentStep + 1) / steps.length) * 100}%`,
            }}
          />
        </Box>
      </Box>

      {/* Styles */}
      <style jsx global>{`
        /* Desktop view */
        .step-indicator-desktop {
          display: flex;
        }

        /* Mobile view - hidden by default */
        .step-indicator-mobile {
          display: none;
        }

        /* Step item container */
        .step-item {
          padding: 6px 8px;
          border-radius: var(--radius-2);
          transition: background 150ms ease;
        }

        .step-item.clickable {
          cursor: pointer;
        }

        .step-item.clickable:hover {
          background: var(--gray-a3);
        }

        .step-item.clickable:focus-visible {
          outline: none;
          box-shadow: 0 0 0 2px var(--accent-8);
        }

        /* Step circle - smaller, cleaner */
        .step-circle {
          width: 24px;
          height: 24px;
          border-radius: 50%;
          background: var(--gray-a3);
          color: var(--gray-10);
          flex-shrink: 0;
          transition: all 150ms ease;
        }

        .step-circle.current {
          background: var(--accent-9);
          color: var(--accent-contrast);
        }

        .step-circle.completed {
          background: var(--accent-a4);
          color: var(--accent-11);
        }

        .step-circle.mobile {
          width: 28px;
          height: 28px;
        }

        /* Step title */
        .step-title {
          color: var(--gray-10);
          white-space: nowrap;
        }

        .step-title.current {
          color: var(--gray-12);
        }

        .step-title.completed {
          color: var(--gray-11);
        }

        /* Connector line */
        .step-connector {
          width: 40px;
          height: 2px;
          background: var(--gray-6);
          border-radius: 1px;
          transition: background 150ms ease;
        }

        .step-connector.completed {
          background: var(--accent-9);
        }

        /* Progress track (mobile) */
        .progress-track {
          height: 3px;
          border-radius: 2px;
          background: var(--gray-a4);
          overflow: hidden;
        }

        /* Progress bar (mobile) */
        .progress-bar {
          height: 100%;
          border-radius: 2px;
          background: var(--accent-9);
          transition: width 300ms ease;
        }

        /* Responsive - show mobile view on small screens */
        @media (max-width: 540px) {
          .step-indicator-desktop {
            display: none !important;
          }
          .step-indicator-mobile {
            display: block !important;
          }
        }

        /* Reduced motion */
        @media (prefers-reduced-motion: reduce) {
          .step-connector,
          .step-circle,
          .step-item,
          .progress-bar {
            transition: none !important;
          }
        }
      `}</style>
    </Box>
  );
}

export default StepIndicator;
