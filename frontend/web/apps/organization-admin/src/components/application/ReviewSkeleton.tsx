/**
 * Review Page Skeleton
 *
 * Loading skeleton for the organization application review page.
 * Matches the exact structure of the ReviewPage.
 *
 * @example
 * ```tsx
 * import { ReviewSkeleton } from '@/components/application';
 *
 * if (loading) {
 *   return <ReviewSkeleton />;
 * }
 * ```
 */

import { Box, Flex, Grid } from '@radix-ui/themes';
import {
  SkeletonCard,
  SkeletonText,
  SkeletonHeading,
  SkeletonButton,
  SkeletonIcon,
} from '@/components/skeletons';
import { StepIndicator } from './StepIndicator';
import { APPLICATION_STEPS } from './constants';

// =============================================================================
// SKELETON SUBCOMPONENTS
// =============================================================================

/** Skeleton for a review field (label + value) */
function SkeletonField({ labelWidth = '100px', valueWidth = '150px' }: {
  labelWidth?: string;
  valueWidth?: string;
}) {
  return (
    <Flex gap="2" py="1">
      <Box style={{ minWidth: 100, flexShrink: 0 }}>
        <SkeletonText size="sm" width={labelWidth} />
      </Box>
      <SkeletonText size="sm" width={valueWidth} />
    </Flex>
  );
}

/** Skeleton for a review section with icon and title */
function SkeletonSection({ titleWidth = '100px', children }: {
  titleWidth?: string;
  children: React.ReactNode;
}) {
  return (
    <Box mb="4">
      <Flex justify="between" align="center" mb="2">
        <Flex align="center" gap="2">
          <SkeletonIcon width="16px" height="16px" />
          <SkeletonText width={titleWidth} />
        </Flex>
        <SkeletonButton width="40px" height="24px" />
      </Flex>
      <Box pl="5">{children}</Box>
    </Box>
  );
}

/** Skeleton for checkbox agreement items */
function SkeletonCheckboxItem() {
  return (
    <Flex align="start" gap="3" py="2">
      <Box className="skeleton" style={{ width: 16, height: 16, borderRadius: 4, flexShrink: 0, marginTop: 2 }} />
      <Box style={{ flex: 1 }}>
        <SkeletonText width="280px" />
      </Box>
    </Flex>
  );
}

// =============================================================================
// MAIN COMPONENT
// =============================================================================

export function ReviewSkeleton() {
  return (
    <Box>
      {/* Step Indicator - actual component */}
      <StepIndicator steps={APPLICATION_STEPS} currentStep={1} allowNavigation={false} />

      {/* Header with Edit button */}
      <Flex justify="between" align="start" mb="5">
        <Box>
          <SkeletonHeading width="180px" style={{ marginBottom: '4px' }} />
          <SkeletonText width="260px" />
        </Box>
        <SkeletonButton width="70px" height="32px" />
      </Flex>

      {/* Review Card */}
      <SkeletonCard mb="4">
        {/* Organization Section */}
        <SkeletonSection titleWidth="100px">
          <SkeletonField labelWidth="50px" valueWidth="180px" />
          <SkeletonField labelWidth="40px" valueWidth="120px" />
          <SkeletonField labelWidth="55px" valueWidth="200px" />
          <Box py="2">
            <SkeletonText size="sm" width="70px" style={{ marginBottom: 4 }} />
            <SkeletonText width="100%" />
            <SkeletonText width="80%" style={{ marginTop: 4 }} />
          </Box>
        </SkeletonSection>

        {/* Contact Section */}
        <SkeletonSection titleWidth="120px">
          <SkeletonField labelWidth="45px" valueWidth="180px" />
          <SkeletonField labelWidth="50px" valueWidth="130px" />
          <SkeletonField labelWidth="55px" valueWidth="160px" />
        </SkeletonSection>

        {/* Location Section */}
        <SkeletonSection titleWidth="70px">
          <SkeletonField labelWidth="35px" valueWidth="100px" />
          <SkeletonField labelWidth="60px" valueWidth="80px" />
          <SkeletonField labelWidth="55px" valueWidth="70px" />
        </SkeletonSection>

        {/* Social Section */}
        <SkeletonSection titleWidth="100px">
          <SkeletonField labelWidth="65px" valueWidth="200px" />
          <SkeletonField labelWidth="70px" valueWidth="180px" />
          <SkeletonField labelWidth="60px" valueWidth="190px" />
        </SkeletonSection>
      </SkeletonCard>

      {/* Terms Card */}
      <SkeletonCard mb="4">
        <Flex align="center" gap="2" mb="3">
          <SkeletonIcon width="16px" height="16px" />
          <SkeletonText width="180px" />
        </Flex>
        <Box pl="1">
          <SkeletonCheckboxItem />
          <SkeletonCheckboxItem />
        </Box>
      </SkeletonCard>

      {/* Navigation Buttons */}
      <Grid columns="2" gap="3" mt="6">
        <SkeletonButton width="100%" height="40px" />
        <SkeletonButton width="100%" height="40px" />
      </Grid>
    </Box>
  );
}

export default ReviewSkeleton;
