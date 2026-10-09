'use client';

import { useMemo, useState } from 'react';
import {
  BulkBar,
  Button,
  Card,
  CardHeader,
  ConfirmDialog,
  DataTable,
  EmptyState,
  ErrorState,
  StatusPill,
  useSnackbar,
} from '@pml.tickets/shared/components/m3';
import {
  useApproveVerificationDocument,
  useApprovalOrganizationDocuments,
  useRejectVerificationDocument,
  type ApprovalDocument,
} from '@pml.tickets/shared/api/admin/modules';
import { RowActions } from '@/components/console/RowActions';
import { FilterBar } from '@/components/console/FilterBar';
import { ReasonDialog } from '@/components/console/ReasonDialog';
import { useStaff } from '@/components/console/StaffContext';
import { needText } from '@/lib/permissions';
import { formatDateTime, humanize } from '@/lib/format';
import { enumOptions, DOCUMENT_STATUS_LABELS } from '@/lib/enumLabels';
import { useReferenceOptions } from '@pml.tickets/shared/api/graphql/shared/reference';
import { useClientPaging } from './useClientPaging';

interface DocRow extends ApprovalDocument {
  orgId: string;
  orgName: string;
}

const fileSize = (n: number | null) => (n == null ? '' : n >= 1_048_576 ? `${(n / 1_048_576).toFixed(1)} MB` : `${Math.max(1, Math.round(n / 1024))} KB`);

/** Approvals / Documents tab: verification document queue with approve / reject / bulk approve. */
export function DocumentVerification() {
  // Document types are the platform's KYB list, not a list of this screen.
  const docTypes = useReferenceOptions('KYB_DOCUMENT_TYPE');
  const snackbar = useSnackbar();
  const canDecide = useStaff().can('decide');
  const { organizations, loading, error, refetch } = useApprovalOrganizationDocuments();
  const { approve } = useApproveVerificationDocument();
  const { reject } = useRejectVerificationDocument();

  const [query, setQuery] = useState('');
  const [filters, setFilters] = useState<Record<string, string>>({});
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [rejecting, setRejecting] = useState<DocRow | null>(null);
  const [bulk, setBulk] = useState<DocRow[] | null>(null);
  const [busy, setBusy] = useState(false);

  const all: DocRow[] = useMemo(
    () =>
      organizations.flatMap((o) => (o.verificationDocuments ?? []).map((d) => ({ ...d, orgId: o.id, orgName: o.name }))).sort((a, b) =>
        (a.status === 'PENDING' ? 0 : 1) - (b.status === 'PENDING' ? 0 : 1) || (a.uploadedAt < b.uploadedAt ? -1 : 1),
      ),
    [organizations],
  );
  const rows = useMemo(() => {
    const q = query.trim().toLowerCase();
    return all
      .filter((d) => !q || `${d.fileName ?? ''} ${d.orgName} ${d.documentType}`.toLowerCase().includes(q))
      .filter((d) => !filters.status || filters.status === 'all' || d.status === filters.status)
      .filter((d) => !filters.type || filters.type === 'all' || d.documentType === filters.type);
  }, [all, query, filters]);
  const paging = useClientPaging(rows);

  const act = async (fn: () => Promise<unknown>, message: string) => {
    setBusy(true);
    try {
      if (!(await fn())) throw new Error('The server did not confirm that. Try again.');
      await refetch();
      snackbar.show(message);
      return true;
    } catch (e) {
      snackbar.show({ message: e instanceof Error ? e.message : 'That did not go through. Try again.', tone: 'error' });
      return false;
    } finally {
      setBusy(false);
    }
  };

  const approveBulk = async () => {
    if (!bulk) return;
    setBusy(true);
    let done = 0;
    for (const d of bulk) {
      try {
        if (await approve(d.id)) done += 1;
      } catch {
        /* counted as not approved */
      }
    }
    await refetch();
    setBusy(false);
    setBulk(null);
    setSelected(new Set());
    snackbar.show(done ? `${done} document${done === 1 ? '' : 's'} approved` : { message: 'No documents were approved.', tone: 'error' });
  };

  return (
    <>
      <Card>
        <CardHeader title="Verification documents" subtitle="ID documents, business licenses and tax certificates uploaded by organizers." />
        <FilterBar
          searchLabel="Search documents"
          query={query}
          onQuery={(q) => { setQuery(q); paging.reset(); }}
          filters={[
            { id: 'status', label: 'Status', options: enumOptions(DOCUMENT_STATUS_LABELS) },
            { id: 'type', label: 'Type', options: docTypes.options.map((o) => ({ value: o.value, label: o.label })) },
          ]}
          values={filters}
          onFilter={(id, v) => { setFilters((f) => ({ ...f, [id]: v })); paging.reset(); }}
          onClear={() => { setQuery(''); setFilters({}); paging.reset(); }}
        />
        <BulkBar count={selected.size}>
          <Button
            variant="tonal"
            size="sm"
            disabled={!canDecide}
            onClick={() => setBulk(all.filter((d) => selected.has(d.id) && d.status === 'PENDING'))}
          >
            Approve selected
          </Button>
          <Button variant="text" size="sm" onClick={() => setSelected(new Set())}>Clear selection</Button>
          {!canDecide ? <span>{needText('decide')}</span> : null}
        </BulkBar>
        <DataTable
          caption="Verification documents"
          rows={paging.slice}
          getRowId={(d) => d.id}
          loading={loading && organizations.length === 0}
          error={error && organizations.length === 0 ? <ErrorState error={error} onRetry={() => void refetch()} /> : undefined}
          empty={<EmptyState icon="inbox" title="No documents to verify." />}
          selectable
          selectedIds={selected}
          onSelectionChange={setSelected}
          pagination={paging.pagination}
          columns={[
            { id: 'doc', header: 'Document', rowHeader: true, cell: (d) => (<><b>{docTypes.labelOf(d.documentType)}</b><br /><span className="m3-muted">{[d.fileName, fileSize(d.fileSize)].filter(Boolean).join(' · ')}</span></>) },
            { id: 'org', header: 'Organization', cell: (d) => d.orgName },
            { id: 'at', header: 'Uploaded', cell: (d) => formatDateTime(d.uploadedAt) },
            {
              id: 'status',
              header: 'Status',
              cell: (d) => (
                <>
                  <StatusPill status={d.status}>{humanize(d.status)}</StatusPill>
                  {d.rejectionReason ? (<><br /><span className="m3-muted">{d.rejectionReason}</span></>) : null}
                </>
              ),
            },
          ]}
          rowActions={(d) =>
            d.status === 'PENDING' && canDecide ? (
              <RowActions
                name={`${docTypes.labelOf(d.documentType)} ${d.id}`}
                primary={{ label: 'Approve', disabled: busy, onSelect: () => void act(() => approve(d.id), `${docTypes.labelOf(d.documentType)} approved`) }}
                items={[{ id: 'reject', label: 'Reject…', danger: true, disabled: busy, onSelect: () => setRejecting(d) }]}
              />
            ) : null
          }
        />
      </Card>

      <ReasonDialog
        open={!!rejecting}
        title={`Reject ${rejecting ? docTypes.labelOf(rejecting.documentType).toLowerCase() : 'document'}?`}
        body="The organizer is told why and can upload a new file."
        confirmLabel="Reject document"
        danger
        loading={busy}
        onClose={() => setRejecting(null)}
        onConfirm={async (reason) => {
          const d = rejecting;
          if (d && (await act(() => reject(d.id, reason), `${docTypes.labelOf(d.documentType)} rejected`))) setRejecting(null);
        }}
      />
      <ConfirmDialog
        open={!!bulk}
        onClose={() => setBulk(null)}
        title={`Approve ${bulk?.length ?? 0} document${bulk?.length === 1 ? '' : 's'}?`}
        description="Only pending documents are approved."
        confirmLabel="Approve"
        loading={busy}
        onConfirm={() => void approveBulk()}
      />
    </>
  );
}
