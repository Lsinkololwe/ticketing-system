'use client';

import { Text } from '@radix-ui/themes';
import type { ComponentProps } from 'react';

type RadixTextSize = ComponentProps<typeof Text>['size'];

/**
 * BrandMark — the MyTicketZM wordmark.
 *
 * DS: "No logo file exists yet — use the plain wordmark, never invent a mark."
 * So this is plain type: "MyTicket" in the foreground, "ZM" in the iris brand
 * color, set in the display face. No icon square, no invented glyph.
 */
export interface BrandMarkProps {
  size?: RadixTextSize;
  /** Render on a dark/gradient ground. */
  inverse?: boolean;
  className?: string;
}

export function BrandMark({ size = '5', inverse = false, className }: BrandMarkProps) {
  return (
    <Text
      size={size}
      weight="bold"
      className={['font-display', className].filter(Boolean).join(' ')}
      style={{
        letterSpacing: '-0.02em',
        color: inverse ? 'var(--on-scrim)' : 'var(--gray-12)',
        lineHeight: 1,
      }}
    >
      MyTicket
      <span style={{ color: inverse ? 'var(--iris-4)' : 'var(--color-secondary-text)' }}>ZM</span>
    </Text>
  );
}

export default BrandMark;
