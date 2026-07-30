'use client';

/**
 * Toast — MyTicketZM Design System §7.
 *
 * Contract props (exact, no extras):
 *   variant, title, description, icon, onClose
 *
 * variant: success | error | warning | info
 *
 * Tinted surface + matching text tone from the `--status-*` roles, with a
 * default Iconoir icon per variant so the meaning does not rest on colour
 * alone. Money is never a toast variant — jade stays out of this palette.
 *
 * Errors explain what happened and what to do; they do not apologise.
 */

import type { ReactNode } from 'react';
import { Box, Flex, IconButton, Text } from '@radix-ui/themes';
import {
  CheckCircle,
  InfoCircle,
  WarningTriangle,
  Xmark,
  XmarkCircle,
} from 'iconoir-react';

export type ToastVariant = 'success' | 'error' | 'warning' | 'info';

export interface ToastProps {
  variant?: ToastVariant;
  title: string;
  description?: string;
  /** Overrides the default icon for the variant. Iconoir only. */
  icon?: ReactNode;
  onClose?: () => void;
}

const VARIANTS: Record<
  ToastVariant,
  { surface: string; text: string; solid: string; Icon: typeof CheckCircle }
> = {
  success: {
    surface: 'var(--status-success-a3)',
    text: 'var(--status-success-11)',
    solid: 'var(--status-success-9)',
    Icon: CheckCircle,
  },
  error: {
    surface: 'var(--status-danger-a3)',
    text: 'var(--status-danger-11)',
    solid: 'var(--status-danger-9)',
    Icon: XmarkCircle,
  },
  warning: {
    surface: 'var(--status-warning-a3)',
    text: 'var(--status-warning-11)',
    solid: 'var(--status-warning-9)',
    Icon: WarningTriangle,
  },
  info: {
    surface: 'var(--status-info-a3)',
    text: 'var(--status-info-11)',
    solid: 'var(--status-info-9)',
    Icon: InfoCircle,
  },
};

export function Toast({
  variant = 'info',
  title,
  description,
  icon,
  onClose,
}: ToastProps) {
  const v = VARIANTS[variant];

  return (
    <Flex
      align="start"
      gap="3"
      p="3"
      role={variant === 'error' ? 'alert' : 'status'}
      style={{
        background: v.surface,
        border: `1px solid ${v.solid}`,
        borderRadius: 'var(--card-radius)',
        // Width is the container's business — stacked in a toast viewport it
        // gets the viewport's width; inline in a panel it fills the panel.
        width: '100%',
      }}
    >
      <Box style={{ display: 'flex', color: v.text, flexShrink: 0 }}>
        {icon ?? <v.Icon style={{ width: 18, height: 18 }} />}
      </Box>

      <Flex direction="column" gap="1" style={{ flex: 1, minWidth: 0 }}>
        <Text size="2" weight="medium" style={{ color: v.text }}>
          {title}
        </Text>
        {description ? (
          <Text size="1" style={{ color: 'var(--gray-11)' }}>
            {description}
          </Text>
        ) : null}
      </Flex>

      {onClose ? (
        <IconButton
          variant="ghost"
          size="1"
          color="gray"
          aria-label="Dismiss notification"
          onClick={onClose}
          style={{ flexShrink: 0 }}
        >
          <Xmark style={{ width: 14, height: 14 }} />
        </IconButton>
      ) : null}
    </Flex>
  );
}

export default Toast;
