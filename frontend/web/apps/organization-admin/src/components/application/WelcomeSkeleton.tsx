/**
 * Welcome Page Skeleton
 *
 * Loading skeleton for the welcome/onboarding page.
 * Matches the centered layout and feature list structure.
 *
 * @example
 * ```tsx
 * import { WelcomeSkeleton } from '@/components/application';
 *
 * if (loading) {
 *   return <WelcomeSkeleton />;
 * }
 * ```
 */

import { Box, Card, Flex } from '@radix-ui/themes';
import {
  Skeleton,
  SkeletonText,
  SkeletonHeading,
  SkeletonButton,
  SkeletonIcon,
} from '@/components/skeletons';

/** Skeleton for a feature item card */
function SkeletonFeatureItem() {
  return (
    <Card variant="surface" size="1">
      <Flex align="center" gap="3" p="1">
        <Skeleton
          width="32px"
          height="32px"
          style={{ borderRadius: 'var(--radius-2)', flexShrink: 0 }}
        />
        <Box style={{ flex: 1 }}>
          <Flex align="center" gap="1">
            <SkeletonText width="100px" />
            <SkeletonText size="sm" width="140px" />
          </Flex>
        </Box>
      </Flex>
    </Card>
  );
}

export function WelcomeSkeleton() {
  return (
    <Flex
      direction="column"
      align="center"
      justify="center"
      px={{ initial: '4', sm: '6' }}
      py={{ initial: '6', sm: '8' }}
      minHeight="calc(100vh - 132px)"
    >
      {/* Content Container */}
      <Flex
        direction="column"
        align="center"
        width="100%"
        maxWidth="480px"
      >
        {/* Hero Icon Skeleton */}
        <Box
          mb="5"
          className="skeleton"
          style={{
            width: 72,
            height: 72,
            borderRadius: '50%',
          }}
        />

        {/* Welcome Heading Skeleton */}
        <Box mb="2">
          <SkeletonHeading width="200px" />
        </Box>

        {/* Subheading Skeleton */}
        <Box mb="6" style={{ textAlign: 'center', width: '100%' }}>
          <SkeletonText width="100%" style={{ marginBottom: 6 }} />
          <SkeletonText width="85%" style={{ margin: '0 auto' }} />
        </Box>

        {/* Feature List Skeleton */}
        <Flex direction="column" gap="2" mb="6" width="100%">
          <SkeletonFeatureItem />
          <SkeletonFeatureItem />
          <SkeletonFeatureItem />
        </Flex>

        {/* CTA Button Skeleton */}
        <SkeletonButton width="200px" height="44px" />

        {/* Time Estimate Skeleton */}
        <Flex align="center" gap="2" mt="3">
          <SkeletonIcon width="14px" height="14px" />
          <SkeletonText size="sm" width="120px" />
        </Flex>
      </Flex>
    </Flex>
  );
}

export default WelcomeSkeleton;
