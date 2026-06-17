/**
 * Business Info Form Skeleton
 *
 * Loading skeleton for the organization business information form.
 * Matches the exact structure of the BusinessInfoPage form.
 *
 * @example
 * ```tsx
 * import { BusinessInfoSkeleton } from '@/components/application';
 *
 * if (loading) {
 *   return <BusinessInfoSkeleton />;
 * }
 * ```
 */

import { Box, Grid } from '@radix-ui/themes';
import {
  SkeletonCard,
  SkeletonCardHeader,
  SkeletonFormField,
  SkeletonTextAreaField,
  SkeletonPageHeader,
  SkeletonNavButtons,
} from '@/components/skeletons';
import { StepIndicator } from './StepIndicator';
import { APPLICATION_STEPS } from './constants';

export function BusinessInfoSkeleton() {
  return (
    <Box>
      {/* Step Indicator - actual component, not skeleton */}
      <StepIndicator steps={APPLICATION_STEPS} currentStep={0} allowNavigation={false} />

      {/* Page Header Skeleton */}
      <SkeletonPageHeader
        titleWidth="260px"
        descriptionWidth="420px"
      />

      {/* Basic Information Card */}
      <SkeletonCard>
        <SkeletonCardHeader titleWidth="140px" />
        <Grid columns={{ initial: '1', sm: '2' }} gap="4">
          <SkeletonFormField labelWidth="120px" />
          <SkeletonFormField labelWidth="120px" />
          <Box gridColumn={{ initial: '1', sm: '1 / -1' }}>
            <SkeletonFormField labelWidth="80px" showHelper />
          </Box>
          <Box gridColumn={{ initial: '1', sm: '1 / -1' }}>
            <SkeletonTextAreaField labelWidth="160px" showHelper />
          </Box>
        </Grid>
      </SkeletonCard>

      {/* Contact Information Card */}
      <SkeletonCard>
        <SkeletonCardHeader titleWidth="160px" />
        <Grid columns={{ initial: '1', sm: '2' }} gap="4">
          <SkeletonFormField labelWidth="100px" />
          <SkeletonFormField labelWidth="100px" />
          <SkeletonFormField labelWidth="60px" showHelper />
        </Grid>
      </SkeletonCard>

      {/* Location Card */}
      <SkeletonCard>
        <SkeletonCardHeader titleWidth="80px" />
        <Grid columns={{ initial: '1', sm: '3' }} gap="4">
          <SkeletonFormField labelWidth="40px" />
          <SkeletonFormField labelWidth="60px" />
          <SkeletonFormField labelWidth="60px" />
        </Grid>
      </SkeletonCard>

      {/* Social Links Card */}
      <SkeletonCard>
        <SkeletonCardHeader titleWidth="180px" />
        <Grid columns={{ initial: '1', sm: '3' }} gap="4">
          <SkeletonFormField labelWidth="60px" showHelper />
          <SkeletonFormField labelWidth="70px" showHelper />
          <SkeletonFormField labelWidth="80px" showHelper />
        </Grid>
      </SkeletonCard>

      {/* Navigation Buttons */}
      <SkeletonNavButtons
        backWidth="100px"
        continueWidth="180px"
      />
    </Box>
  );
}

export default BusinessInfoSkeleton;
