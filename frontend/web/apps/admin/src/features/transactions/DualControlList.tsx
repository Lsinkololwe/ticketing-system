'use client';

import { useState } from 'react';
import { Button, ErrorState, List, ListItem, Skeleton, StatusPill, useSnackbar } from '@pml.tickets/shared/components/m3';
import {
  canConfirmProposal,
  useDualControlActions,
  type RecoveryProposalRow,
} from '@pml.tickets/shared/api/admin/modules/payments-ops';
import { ReasonDialog } from '@/components/console/ReasonDialog';
import { useStaff } from '@/components/console/StaffContext';
import { formatDateTime, humanize, money } from '@/lib/format';
import { needText } from '@/lib/permissions';
import { useStepUp } from '@/lib/useStepUp';

export interface DualControlListProps {
  proposals: RecoveryProposalRow[];
  loading?: boolean;
  error?: Error;
  onRetry?: () => void;
  emptyText?: string;
}

/**
 * Recovery actions waiting for a second approver. The person who proposed an action (the maker) never
 * sees a Confirm button for it: they can only withdraw it. Confirming is a step-up action.
 */
export function DualControlList({ proposals, loading, error, onRetry, emptyText = 'Nothing is waiting for a second approver.' }: DualControlListProps) {
  const { id: staffId, can } = useStaff();
  const { guard } = useStepUp();
  const snackbar = useSnackbar();
  const actions = useDualControlActions();
  const [confirming, setConfirming] = useState<RecoveryProposalRow | null>(null);

  if (error && proposals.length === 0) return <ErrorState error={error} onRetry={onRetry} />;
  if (loading && proposals.length === 0) return <Skeleton />;
  if (proposals.length === 0) return <p className="adm-note">{emptyText}</p>;

  const confirm = async (reason: string) => {
    if (!confirming) return;
    try {
      await guard(() => actions.confirm(confirming.id, reason));
      snackbar.show('Action confirmed');
      setConfirming(null);
    } catch (e) {
      snackbar.show((e as Error).message || 'Could not confirm the action');
    }
  };
  const withdraw = async (p: RecoveryProposalRow) => {
    try {
      await actions.withdraw(p.id);
      snackbar.show('Proposal withdrawn');
    } catch (e) {
      snackbar.show((e as Error).message || 'Could not withdraw the proposal');
    }
  };

  return (
    <>
      <List aria-label="Dual control queue">
        {proposals.map((p) => {
          const maker = p.proposedById === staffId;
          const allowed = can('secondApprove') && canConfirmProposal(p, staffId);
          return (
            <ListItem
              key={p.id}
              headline={`${humanize(p.action)}${p.amount ? ` · ${money(p.amount)}` : ''}`}
              support={`${p.proposalReason} · proposed ${formatDateTime(p.proposedAt)}${maker ? ' by you' : ''} · expires ${formatDateTime(p.expiresAt)}`}
              trailing={
                <>
                  <StatusPill status={p.status} />
                  {p.status === 'PENDING' ? (
                    maker ? (
                      <>
                        <span className="m3-muted">A second person must confirm.</span>
                        <Button variant="text" size="sm" onClick={() => void withdraw(p)}>
                          Withdraw
                        </Button>
                      </>
                    ) : allowed ? (
                      <Button variant="tonal" size="sm" onClick={() => setConfirming(p)}>
                        Confirm
                      </Button>
                    ) : (
                      <span className="m3-muted">{can('secondApprove') ? 'You cannot confirm this.' : needText('secondApprove')}</span>
                    )
                  ) : null}
                </>
              }
            />
          );
        })}
      </List>
      <ReasonDialog
        open={confirming !== null}
        title="Confirm this action?"
        body={confirming ? `${humanize(confirming.action)} proposed for ${confirming.subjectIds.length} ${humanize(confirming.subjectType).toLowerCase()}. This runs it now.` : undefined}
        confirmLabel="Confirm action"
        loading={actions.busy}
        reasonLabel="Why are you confirming it?"
        onClose={() => setConfirming(null)}
        onConfirm={(r) => void confirm(r)}
      />
    </>
  );
}
