/**
 * Application Flow Components Index
 *
 * Components and constants for the organization application wizard.
 *
 * @example
 * ```tsx
 * import {
 *   StepIndicator,
 *   BusinessInfoSkeleton,
 *   APPLICATION_STEPS,
 *   PROVINCE_OPTIONS,
 *   PROVINCE_LABELS,
 *   ORGANIZATION_TYPE_OPTIONS,
 *   ORGANIZATION_TYPE_LABELS,
 * } from '@/components/application';
 * ```
 */

// Step indicator component
export { StepIndicator } from './StepIndicator';
export type { Step } from './StepIndicator';

// Wizard container component
export { WizardContainer } from './WizardContainer';

// Skeleton components
export { BusinessInfoSkeleton } from './BusinessInfoSkeleton';
export { ReviewSkeleton } from './ReviewSkeleton';
export { StatusSkeleton } from './StatusSkeleton';
export { WelcomeSkeleton } from './WelcomeSkeleton';

// Application constants
export {
  APPLICATION_STEPS,
  PROVINCE_OPTIONS,
  PROVINCE_LABELS,
  ORGANIZATION_TYPE_OPTIONS,
  ORGANIZATION_TYPE_LABELS,
  BUSINESS_TYPE_OPTIONS,
  BUSINESS_TYPE_LABELS,
} from './constants';
export type { ApplicationStep, SelectOption } from './constants';
