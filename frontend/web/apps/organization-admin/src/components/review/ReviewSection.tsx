'use client';

/**
 * ReviewSection Component
 *
 * Collapsible section for reviewing form data before submission.
 * Features:
 * - Icon and title header
 * - Edit button linking to edit page
 * - Consistent styling with the application flow
 *
 * @example
 * ```tsx
 * <ReviewSection
 *   title="Basic Information"
 *   icon={<Building style={{ width: 20, height: 20, color: 'var(--brand-500)' }} />}
 *   editLink="/apply/business-info"
 * >
 *   <ReviewField label="Organization Name" value={organization.name} />
 *   <ReviewField label="Email" value={organization.email} />
 * </ReviewSection>
 * ```
 */

import { ReactNode } from 'react';
import { useRouter } from 'next/navigation';
import { Flex, Text, Card, Button } from '@radix-ui/themes';
import { Edit } from 'iconoir-react';

// =============================================================================
// TYPES
// =============================================================================

export interface ReviewSectionProps {
  /** Section title */
  title: string;
  /** Icon displayed before the title */
  icon?: ReactNode;
  /** Link to edit page */
  editLink?: string;
  /** Callback when edit is clicked (alternative to editLink) */
  onEdit?: () => void;
  /** Whether to show the edit button */
  showEdit?: boolean;
  /** Section content */
  children: ReactNode;
  /** Custom styles */
  style?: React.CSSProperties;
}

// =============================================================================
// COMPONENT
// =============================================================================

export function ReviewSection({
  title,
  icon,
  editLink,
  onEdit,
  showEdit = true,
  children,
  style,
}: ReviewSectionProps) {
  const router = useRouter();

  const handleEdit = () => {
    if (onEdit) {
      onEdit();
    } else if (editLink) {
      router.push(editLink);
    }
  };

  const canEdit = showEdit && (editLink || onEdit);

  return (
    <Card className="card" style={{ marginBottom: '16px', ...style }}>
      <div style={{ padding: '24px' }}>
        <Flex justify="between" align="center" mb="4">
          <Flex align="center" gap="2">
            {icon}
            <Text size="3" weight="medium" style={{ color: 'var(--content-primary)' }}>
              {title}
            </Text>
          </Flex>

          {canEdit && (
            <Button
              variant="ghost"
              size="1"
              onClick={handleEdit}
              style={{ color: 'var(--brand-500)' }}
            >
              <Edit style={{ width: 14, height: 14, marginRight: 4 }} />
              Edit
            </Button>
          )}
        </Flex>

        {children}
      </div>
    </Card>
  );
}

export default ReviewSection;
