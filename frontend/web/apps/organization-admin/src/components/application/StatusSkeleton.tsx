/**
 * Status Page Skeleton
 *
 * Loading skeleton for the organization application status page.
 * Matches the exact structure of the StatusPage.
 *
 * @example
 * ```tsx
 * import { StatusSkeleton } from '@/components/application';
 *
 * if (loading) {
 *   return <StatusSkeleton />;
 * }
 * ```
 */

import { Box, Card, Flex } from '@radix-ui/themes';
import {
  SkeletonText,
  SkeletonHeading,
  SkeletonButton,
  SkeletonIcon,
} from '@/components/skeletons';

export function StatusSkeleton() {
  return (
    <Box>
      {/* Status Banner Skeleton */}
      <Card
        mb="4"
        style={{
          background: 'var(--gray-a2)',
          border: '1px solid var(--gray-a5)',
          borderRadius: 'var(--radius-lg)',
        }}
      >
        <Box p="4">
          <Flex align="start" gap="3">
            {/* Status Icon */}
            <Box
              className="skeleton"
              style={{
                width: 40,
                height: 40,
                borderRadius: 'var(--radius-md)',
                flexShrink: 0,
              }}
            />
            <Box style={{ flex: 1, minWidth: 0 }}>
              <SkeletonHeading width="140px" style={{ marginBottom: '8px' }} />
              <SkeletonText width="320px" />
            </Box>
          </Flex>

          {/* Action Buttons Skeleton */}
          <Flex gap="2" mt="4" wrap="wrap">
            <SkeletonButton width="140px" height="32px" />
            <SkeletonButton width="120px" height="32px" />
          </Flex>
        </Box>
      </Card>

      {/* Organization Summary Card Skeleton */}
      <Card variant="surface" mb="4">
        <Box p="4">
          <Flex align="center" gap="2" mb="3">
            <SkeletonIcon width="16px" height="16px" />
            <SkeletonText width="120px" />
          </Flex>

          <Flex gap="2" py="1" mb="1">
            <SkeletonText size="sm" width="50px" />
            <SkeletonText size="sm" width="150px" />
          </Flex>
          <Flex gap="2" py="1" mb="1">
            <SkeletonText size="sm" width="40px" />
            <SkeletonText size="sm" width="80px" />
          </Flex>
          <Flex gap="2" py="1">
            <SkeletonText size="sm" width="45px" />
            <SkeletonText size="sm" width="180px" />
          </Flex>
        </Box>
      </Card>

      {/* Timeline Card Skeleton */}
      <Card variant="surface">
        <Box p="4">
          <Flex align="center" gap="2" mb="4">
            <SkeletonIcon width="16px" height="16px" />
            <SkeletonText width="80px" />
          </Flex>

          {/* Timeline Items */}
          {[1, 2, 3].map((i) => (
            <Flex key={i} gap="3" mb="3">
              <Box
                className="skeleton"
                style={{
                  width: 24,
                  height: 24,
                  borderRadius: '50%',
                  flexShrink: 0,
                }}
              />
              <Box style={{ flex: 1 }}>
                <SkeletonText width={`${100 + i * 20}px`} style={{ marginBottom: 4 }} />
                <SkeletonText size="sm" width={`${80 + i * 15}px`} />
              </Box>
            </Flex>
          ))}
        </Box>
      </Card>
    </Box>
  );
}

export default StatusSkeleton;
