'use client';

/**
 * EmptyState — MyTicketZM design system data-display primitive.
 *
 * Contract (spec §7): `icon, title, description, action, size`
 *   size: sm | md | lg
 *
 * Copy rule: an empty screen is an invitation to act, not a mood. Each preset
 * below names what will appear here and gives one concrete next step, in plain
 * operational third person.
 */

import type { ReactNode } from 'react';
import { Box, Flex, Text, Heading, Button } from '@radix-ui/themes';
import {
  Calendar,
  Group,
  Wallet,
  Bank,
  Search,
  StatsReport,
  Plus,
  Archive,
} from 'iconoir-react';
import Link from 'next/link';

export type EmptyStateSize = 'sm' | 'md' | 'lg';

export interface EmptyStateAction {
  label: string;
  icon?: ReactNode;
  onClick?: () => void;
  href?: string;
}

export interface EmptyStateProps {
  icon?: ReactNode;
  title: string;
  description?: string;
  action?: EmptyStateAction;
  size?: EmptyStateSize;
}

const SIZES: Record<EmptyStateSize, { chip: number; glyph: number; heading: '3' | '4' | '5'; padding: string }> = {
  sm: { chip: 48, glyph: 22, heading: '3', padding: 'var(--space-5)' },
  md: { chip: 64, glyph: 28, heading: '4', padding: 'var(--space-7)' },
  lg: { chip: 80, glyph: 34, heading: '5', padding: 'var(--space-9)' },
};

export function EmptyState({ icon, title, description, action, size = 'md' }: EmptyStateProps) {
  const config = SIZES[size];

  return (
    <Flex
      direction="column"
      align="center"
      justify="center"
      style={{ padding: config.padding, textAlign: 'center' }}
    >
      <Box
        aria-hidden="true"
        style={{
          width: config.chip,
          height: config.chip,
          marginBottom: 'var(--space-5)',
          borderRadius: '50%',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          background: 'var(--gray-a3)',
          border: '1px dashed var(--gray-a6)',
          color: 'var(--gray-9)',
        }}
      >
        {icon || <Archive width={config.glyph} height={config.glyph} />}
      </Box>

      <Heading size={config.heading} mb="2" style={{ color: 'var(--gray-12)' }}>
        {title}
      </Heading>

      {description && (
        <Text
          size="2"
          style={{ color: 'var(--gray-10)', maxWidth: 400, lineHeight: 'var(--text-3-line)' }}
        >
          {description}
        </Text>
      )}

      {action && (
        <Box mt="5">
          {action.href ? (
            <Button
              data-testid="empty-state-action"
              size={size === 'sm' ? '2' : '3'}
              color="teal"
              style={{ cursor: 'pointer' }}
              asChild
            >
              <Link href={action.href}>
                {action.icon}
                {action.label}
              </Link>
            </Button>
          ) : (
            <Button
              data-testid="empty-state-action"
              size={size === 'sm' ? '2' : '3'}
              color="teal"
              style={{ cursor: 'pointer' }}
              onClick={action.onClick}
            >
              {action.icon}
              {action.label}
            </Button>
          )}
        </Box>
      )}
    </Flex>
  );
}

// =============================================================================
// PRESETS
// Each preset is a thin wrapper — it only ever passes contract props through.
// =============================================================================

interface PresetEmptyStateProps {
  action?: EmptyStateAction;
  size?: EmptyStateSize;
}

const addIcon = <Plus width={18} height={18} />;

export function NoEventsEmptyState({ action, size = 'md' }: PresetEmptyStateProps) {
  return (
    <EmptyState
      icon={<Calendar width={28} height={28} />}
      title="No events yet"
      description="Create an event to start selling tickets and tracking attendance."
      action={action || { label: 'Create event', icon: addIcon, href: '/events/new' }}
      size={size}
    />
  );
}

export function NoTeamMembersEmptyState({ action, size = 'md' }: PresetEmptyStateProps) {
  return (
    <EmptyState
      icon={<Group width={28} height={28} />}
      title="No team members yet"
      description="Invite colleagues to help run events, scan tickets and manage payouts."
      action={action || { label: 'Invite member', icon: addIcon, href: '/team/invite' }}
      size={size}
    />
  );
}

export function NoTransactionsEmptyState({ size = 'md' }: PresetEmptyStateProps) {
  return (
    <EmptyState
      icon={<Wallet width={28} height={28} />}
      title="No transactions yet"
      description="Ticket sales, refunds and fees appear here as soon as the first ticket sells."
      size={size}
    />
  );
}

export function NoSearchResultsEmptyState({
  query,
  size = 'md',
}: PresetEmptyStateProps & { query?: string }) {
  return (
    <EmptyState
      icon={<Search width={28} height={28} />}
      title="No matches"
      description={
        query
          ? `Nothing matches "${query}". Try a shorter search or clear the filters.`
          : 'Nothing matches the current filters. Try clearing one of them.'
      }
      size={size}
    />
  );
}

export function NoPayoutsEmptyState({ action, size = 'md' }: PresetEmptyStateProps) {
  return (
    <EmptyState
      icon={<Wallet width={28} height={28} />}
      title="No payouts yet"
      description="Request a payout to move your available balance to a bank account or mobile money wallet."
      action={action || { label: 'Request payout', icon: addIcon, href: '/finance/payouts?action=new' }}
      size={size}
    />
  );
}

export function NoBankAccountsEmptyState({ action, size = 'md' }: PresetEmptyStateProps) {
  return (
    <EmptyState
      icon={<Bank width={28} height={28} />}
      title="No payout destinations"
      description="Add a bank account or mobile money wallet so earnings have somewhere to land."
      action={action || { label: 'Add account', icon: addIcon, href: '/finance/bank-accounts' }}
      size={size}
    />
  );
}

export function NoAttendeesEmptyState({ size = 'md' }: PresetEmptyStateProps) {
  return (
    <EmptyState
      icon={<Group width={28} height={28} />}
      title="No attendees yet"
      description="Attendees appear here as tickets are sold for this event."
      size={size}
    />
  );
}

export function NoAnalyticsEmptyState({ size = 'md' }: PresetEmptyStateProps) {
  return (
    <EmptyState
      icon={<StatsReport width={28} height={28} />}
      title="No data to chart yet"
      description="Sales, views and conversion figures appear once the first ticket sells."
      size={size}
    />
  );
}

export default EmptyState;
