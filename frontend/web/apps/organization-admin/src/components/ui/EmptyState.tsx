'use client';

/**
 * EmptyState Component
 *
 * Displays a placeholder when there's no data to show.
 * Provides visual feedback and optional call-to-action.
 */

import { ReactNode } from 'react';
import { Box, Flex, Text, Heading, Button } from '@radix-ui/themes';
import { Box as BoxIcon, Plus } from 'iconoir-react';
import Link from 'next/link';

// =============================================================================
// TYPES
// =============================================================================

interface EmptyStateAction {
  label: string;
  icon?: ReactNode;
  onClick?: () => void;
  href?: string;
}

interface EmptyStateProps {
  /** Icon to display */
  icon?: ReactNode;
  /** Main title */
  title: string;
  /** Description text */
  description?: string;
  /** Primary action button */
  action?: EmptyStateAction;
  /** Secondary action button */
  secondaryAction?: EmptyStateAction;
  /** Size variant */
  size?: 'sm' | 'md' | 'lg';
  /** Custom content below description */
  children?: ReactNode;
}

// =============================================================================
// COMPONENT
// =============================================================================

export function EmptyState({
  icon,
  title,
  description,
  action,
  secondaryAction,
  size = 'md',
  children,
}: EmptyStateProps) {
  // Size configurations
  const sizeConfig = {
    sm: {
      iconSize: 48,
      iconInner: 24,
      titleSize: '3' as const,
      padding: '24px',
    },
    md: {
      iconSize: 64,
      iconInner: 32,
      titleSize: '4' as const,
      padding: '40px',
    },
    lg: {
      iconSize: 80,
      iconInner: 40,
      titleSize: '5' as const,
      padding: '60px',
    },
  };

  const config = sizeConfig[size];

  const renderAction = (actionConfig: EmptyStateAction, isPrimary: boolean) => {
    const buttonProps = {
      size: (size === 'sm' ? '2' : '3') as '2' | '3',
      variant: isPrimary ? ('solid' as const) : ('outline' as const),
      style: isPrimary
        ? {
            background: 'linear-gradient(135deg, var(--brand-500) 0%, var(--brand-600) 100%)',
            cursor: 'pointer',
          }
        : {
            borderColor: 'rgba(148, 163, 184, 0.3)',
            color: 'var(--content-secondary)',
            cursor: 'pointer',
          },
    };

    if (actionConfig.href) {
      return (
        <Button {...buttonProps} asChild>
          <Link href={actionConfig.href}>
            {actionConfig.icon}
            {actionConfig.label}
          </Link>
        </Button>
      );
    }

    return (
      <Button {...buttonProps} onClick={actionConfig.onClick}>
        {actionConfig.icon}
        {actionConfig.label}
      </Button>
    );
  };

  return (
    <Flex
      direction="column"
      align="center"
      justify="center"
      style={{
        padding: config.padding,
        textAlign: 'center',
      }}
    >
      {/* Icon */}
      <Box
        style={{
          width: config.iconSize,
          height: config.iconSize,
          borderRadius: '50%',
          background: 'var(--surface-subtle)',
          border: '1px dashed var(--surface-border)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          marginBottom: '20px',
          color: 'var(--content-muted)',
        }}
      >
        {icon || <BoxIcon style={{ width: config.iconInner, height: config.iconInner }} />}
      </Box>

      {/* Title */}
      <Heading
        size={config.titleSize}
        mb="2"
        style={{ color: 'var(--content-primary)' }}
      >
        {title}
      </Heading>

      {/* Description */}
      {description && (
        <Text
          size="2"
          style={{
            color: 'var(--content-muted)',
            maxWidth: '400px',
            lineHeight: 1.5,
          }}
        >
          {description}
        </Text>
      )}

      {/* Custom content */}
      {children && <Box mt="4">{children}</Box>}

      {/* Actions */}
      {(action || secondaryAction) && (
        <Flex gap="3" mt="5">
          {action && renderAction(action, true)}
          {secondaryAction && renderAction(secondaryAction, false)}
        </Flex>
      )}
    </Flex>
  );
}

// =============================================================================
// PRESET EMPTY STATES
// =============================================================================

interface PresetEmptyStateProps {
  action?: EmptyStateAction;
  size?: 'sm' | 'md' | 'lg';
}

export function NoEventsEmptyState({ action, size = 'md' }: PresetEmptyStateProps) {
  return (
    <EmptyState
      icon={<BoxIcon style={{ width: 32, height: 32 }} />}
      title="No Events Yet"
      description="Create your first event to start selling tickets and managing attendees."
      action={action || {
        label: 'Create Event',
        icon: <Plus style={{ width: 18, height: 18, marginRight: 8 }} />,
        href: '/events/new',
      }}
      size={size}
    />
  );
}

export function NoTeamMembersEmptyState({ action, size = 'md' }: PresetEmptyStateProps) {
  return (
    <EmptyState
      icon={<BoxIcon style={{ width: 32, height: 32 }} />}
      title="No Team Members"
      description="Invite team members to help manage your events and organization."
      action={action || {
        label: 'Invite Member',
        icon: <Plus style={{ width: 18, height: 18, marginRight: 8 }} />,
        href: '/team/invite',
      }}
      size={size}
    />
  );
}

export function NoTransactionsEmptyState({ size = 'md' }: PresetEmptyStateProps) {
  return (
    <EmptyState
      icon={<BoxIcon style={{ width: 32, height: 32 }} />}
      title="No Transactions"
      description="Transactions will appear here once you start selling tickets."
      size={size}
    />
  );
}

export function NoSearchResultsEmptyState({ query, size = 'md' }: PresetEmptyStateProps & { query?: string }) {
  return (
    <EmptyState
      icon={<BoxIcon style={{ width: 32, height: 32 }} />}
      title="No Results Found"
      description={query ? `No results found for "${query}". Try adjusting your search.` : 'No results found. Try adjusting your filters.'}
      size={size}
    />
  );
}

export function NoPayoutsEmptyState({ action, size = 'md' }: PresetEmptyStateProps) {
  return (
    <EmptyState
      icon={<BoxIcon style={{ width: 32, height: 32 }} />}
      title="No Payouts Yet"
      description="Request a payout to transfer your earnings to your bank account."
      action={action || {
        label: 'Request Payout',
        icon: <Plus style={{ width: 18, height: 18, marginRight: 8 }} />,
        href: '/finance/payouts?action=new',
      }}
      size={size}
    />
  );
}

export function NoBankAccountsEmptyState({ action, size = 'md' }: PresetEmptyStateProps) {
  return (
    <EmptyState
      icon={<BoxIcon style={{ width: 32, height: 32 }} />}
      title="No Bank Accounts"
      description="Add a bank account to start receiving payouts from your ticket sales."
      action={action || {
        label: 'Add Bank Account',
        icon: <Plus style={{ width: 18, height: 18, marginRight: 8 }} />,
        href: '/finance/bank-accounts',
      }}
      size={size}
    />
  );
}

export function NoAttendeesEmptyState({ size = 'md' }: PresetEmptyStateProps) {
  return (
    <EmptyState
      icon={<BoxIcon style={{ width: 32, height: 32 }} />}
      title="No Attendees Yet"
      description="Attendees will appear here once tickets are sold for this event."
      size={size}
    />
  );
}

export function NoAnalyticsEmptyState({ size = 'md' }: PresetEmptyStateProps) {
  return (
    <EmptyState
      icon={<BoxIcon style={{ width: 32, height: 32 }} />}
      title="No Analytics Data"
      description="Analytics will be available once you start selling tickets."
      size={size}
    />
  );
}

export default EmptyState;
