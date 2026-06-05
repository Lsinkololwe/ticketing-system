'use client';

/**
 * Placeholder Page Component
 *
 * Used for pages that are under development.
 * Shows a consistent, professional placeholder UI.
 */

import { Box, Flex, Heading, Text, Badge } from '@radix-ui/themes';
import { Hammer } from 'iconoir-react';
import { StyledCard } from './StyledCard';

interface PagePlaceholderProps {
  title: string;
  description?: string;
  icon?: React.ReactNode;
  comingSoon?: boolean;
}

export function PagePlaceholder({
  title,
  description,
  icon,
  comingSoon = true,
}: PagePlaceholderProps) {
  return (
    <Flex direction="column" gap="6">
      {/* Page Header */}
      <Flex justify="between" align="center">
        <Box>
          <Flex align="center" gap="3">
            <Heading size="6" weight="bold" style={{ color: 'var(--gray-12)' }}>
              {title}
            </Heading>
            {comingSoon && (
              <Badge color="violet" variant="soft" size="1">
                Coming Soon
              </Badge>
            )}
          </Flex>
          {description && (
            <Text size="2" color="gray" style={{ marginTop: '4px' }}>
              {description}
            </Text>
          )}
        </Box>
      </Flex>

      {/* Placeholder Content */}
      <StyledCard hover="none">
        <Flex
          direction="column"
          align="center"
          justify="center"
          gap="4"
          py="9"
          style={{ minHeight: '400px' }}
        >
          <Box
            style={{
              padding: '20px',
              borderRadius: '16px',
              background: 'linear-gradient(135deg, var(--violet-a3) 0%, var(--violet-a4) 100%)',
              border: '1px solid var(--violet-a5)',
            }}
          >
            {icon || (
              <Hammer
                style={{
                  width: 48,
                  height: 48,
                  color: 'var(--violet-11)',
                }}
              />
            )}
          </Box>
          <Flex direction="column" align="center" gap="2">
            <Heading size="4" weight="medium" style={{ color: 'var(--gray-12)' }}>
              {title}
            </Heading>
            <Text
              size="2"
              color="gray"
              align="center"
              style={{ maxWidth: '400px', lineHeight: 1.6 }}
            >
              This page is currently under development. Check back soon for the full implementation
              with data management and analytics.
            </Text>
          </Flex>
        </Flex>
      </StyledCard>
    </Flex>
  );
}

export default PagePlaceholder;
