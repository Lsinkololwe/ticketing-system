'use client';

import { Flex, Text } from '@radix-ui/themes';

/**
 * MobileMoneyStrip — always-visible MTN / Airtel / Zamtel affordance.
 *
 * DS rule: "Mobile money (MTN, Airtel, Zamtel) is a core, always-visible
 * payment method — never hide it behind 'more options'." Reused on event
 * cards, the hero, and the checkout so the customer always sees it.
 *
 * Each provider is a colored dot (brand hue via --momo-*) plus its name.
 */
const PROVIDERS = [
  { name: 'MTN', dot: 'var(--momo-mtn)' },
  { name: 'Airtel', dot: 'var(--momo-airtel)' },
  { name: 'Zamtel', dot: 'var(--momo-zamtel)' },
] as const;

export interface MobileMoneyStripProps {
  label?: string;
  /** Render on a dark/gradient ground (inverts the label color). */
  inverse?: boolean;
  className?: string;
}

export function MobileMoneyStrip({
  label = 'Pay with mobile money',
  inverse = false,
  className,
}: MobileMoneyStripProps) {
  return (
    <Flex align="center" gap="3" wrap="wrap" className={className}>
      {label && (
        <Text
          size="1"
          className="ds-label"
          style={inverse ? { color: 'var(--on-scrim-muted)' } : undefined}
        >
          {label}
        </Text>
      )}
      <Flex align="center" gap="3">
        {PROVIDERS.map((p) => (
          <span
            key={p.name}
            className="ds-momo-chip"
            style={inverse ? { color: 'var(--on-scrim)' } : undefined}
          >
            <span className="ds-momo-dot" style={{ background: p.dot }} aria-hidden="true" />
            {p.name}
          </span>
        ))}
      </Flex>
    </Flex>
  );
}

export default MobileMoneyStrip;
