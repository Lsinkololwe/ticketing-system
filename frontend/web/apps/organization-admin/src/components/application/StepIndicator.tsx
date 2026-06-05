'use client';

/**
 * Step Indicator Component
 *
 * Visual progress indicator for multi-step application wizard.
 * Features:
 * - Shows current step with visual highlight
 * - Completed steps marked with checkmark
 * - Click to navigate between completed steps
 * - Mobile-responsive (shows only current step on small screens)
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
    // Only allow clicking on completed steps
    if (allowNavigation && index < currentStep && onStepClick) {
      onStepClick(index);
    }
  };

  return (
    <Box mb="8">
      {/* Desktop View */}
      <Flex
        className="step-indicator-desktop"
        align="center"
        justify="center"
        gap="0"
        style={{
          position: 'relative',
        }}
      >
        {steps.map((step, index) => {
          const isCompleted = index < currentStep;
          const isCurrent = index === currentStep;
          const isClickable = allowNavigation && isCompleted;

          return (
            <Flex key={step.id} align="center" style={{ position: 'relative' }}>
              {/* Connector Line (before step, except first) */}
              {index > 0 && (
                <Box
                  style={{
                    width: 60,
                    height: 2,
                    background: isCompleted
                      ? 'linear-gradient(90deg, #10B981 0%, #14B8A6 100%)'
                      : 'rgba(148, 163, 184, 0.2)',
                    transition: 'background 300ms ease',
                  }}
                />
              )}

              {/* Step Circle */}
              <Flex
                align="center"
                justify="center"
                onClick={() => handleStepClick(index)}
                style={{
                  width: 40,
                  height: 40,
                  borderRadius: '50%',
                  background: isCurrent
                    ? 'linear-gradient(135deg, #10B981 0%, #14B8A6 100%)'
                    : isCompleted
                      ? 'rgba(16, 185, 129, 0.2)'
                      : 'rgba(30, 41, 59, 0.8)',
                  border: isCurrent
                    ? 'none'
                    : isCompleted
                      ? '2px solid #10B981'
                      : '2px solid rgba(148, 163, 184, 0.3)',
                  cursor: isClickable ? 'pointer' : 'default',
                  transition: 'all 300ms ease',
                  boxShadow: isCurrent ? '0 0 20px rgba(16, 185, 129, 0.4)' : 'none',
                  position: 'relative',
                  zIndex: 1,
                }}
              >
                {isCompleted ? (
                  <Check style={{ width: 18, height: 18, color: '#10B981' }} />
                ) : (
                  <Text
                    size="2"
                    weight="bold"
                    style={{
                      color: isCurrent ? 'white' : '#94A3B8',
                    }}
                  >
                    {index + 1}
                  </Text>
                )}
              </Flex>

              {/* Step Label (positioned below) */}
              <Box
                style={{
                  position: 'absolute',
                  top: '52px',
                  left: index > 0 ? '60px' : '0',
                  transform: 'translateX(-50%)',
                  textAlign: 'center',
                  width: '100px',
                }}
              >
                <Text
                  size="1"
                  weight={isCurrent ? 'bold' : 'regular'}
                  style={{
                    color: isCurrent ? '#F8FAFC' : isCompleted ? '#10B981' : '#94A3B8',
                    display: 'block',
                    whiteSpace: 'nowrap',
                  }}
                >
                  {step.title}
                </Text>
              </Box>
            </Flex>
          );
        })}
      </Flex>

      {/* Mobile View - Current Step Only */}
      <Box className="step-indicator-mobile" style={{ display: 'none' }}>
        <Flex align="center" justify="center" gap="3" mb="2">
          <Box
            style={{
              width: 36,
              height: 36,
              borderRadius: '50%',
              background: 'linear-gradient(135deg, #10B981 0%, #14B8A6 100%)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              boxShadow: '0 0 15px rgba(16, 185, 129, 0.3)',
            }}
          >
            <Text size="2" weight="bold" style={{ color: 'white' }}>
              {currentStep + 1}
            </Text>
          </Box>
          <Box>
            <Text size="1" style={{ color: '#94A3B8', display: 'block' }}>
              Step {currentStep + 1} of {steps.length}
            </Text>
            <Text size="2" weight="medium" style={{ color: '#F8FAFC' }}>
              {steps[currentStep]?.title}
            </Text>
          </Box>
        </Flex>

        {/* Progress Bar */}
        <Box
          style={{
            height: 4,
            borderRadius: 2,
            background: 'rgba(148, 163, 184, 0.2)',
            overflow: 'hidden',
          }}
        >
          <Box
            style={{
              width: `${((currentStep + 1) / steps.length) * 100}%`,
              height: '100%',
              borderRadius: 2,
              background: 'linear-gradient(90deg, #10B981 0%, #14B8A6 100%)',
              transition: 'width 300ms ease',
            }}
          />
        </Box>
      </Box>

      {/* Responsive Styles */}
      <style jsx global>{`
        @media (max-width: 768px) {
          .step-indicator-desktop {
            display: none !important;
          }
          .step-indicator-mobile {
            display: block !important;
          }
        }
      `}</style>
    </Box>
  );
}

export default StepIndicator;
