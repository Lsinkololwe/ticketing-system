"use client";

import { useStepUp } from "@/lib/useStepUp";
import { useEffect, useMemo, useState } from "react";
import { useFormState } from "react-hook-form";
import type { z } from "zod";
import { Form } from "@pml.tickets/shared/forms/Form";
import { useZodForm } from "@pml.tickets/shared/forms/useZodForm";
import {
  SelectRHF,
  SwitchRHF,
  TextFieldRHF,
} from "@pml.tickets/shared/forms/fields";
import {
  Banner,
  Button,
  Card,
  CardHeader,
  ConfirmDialog,
  EmptyState,
  ErrorState,
  FormCell,
  FormGrid,
  KeyValue,
  SaveBar,
  Skeleton,
  SplitLayout,
  useSnackbar,
} from "@pml.tickets/shared/components/m3";
import {
  usePlatformConfiguration,
  useUpdatePlatformConfiguration,
  type PlatformConfigurationRow,
} from "@pml.tickets/shared/api/admin/modules/platform-config";
import { useStaff } from "@/components/console";
import { useReferenceOptions } from "@pml.tickets/shared/api/graphql/shared/reference";
import type { ApprovalNotificationChannel, UserType } from "@pml.tickets/shared/types/graphql";
import { formatDateTime, money } from "@/lib/format";
import { enumOptions } from "@/lib/enumLabels";
import { RefundPolicyFields } from "./RefundPolicyFields";
import { PermissionCatalogue } from "./PermissionCatalogue";
import { useRouter } from "next/navigation";
import {
  APPROVAL_DEFAULTS,
  APPROVAL_LABELS,
  approvalSchema,
  changedKeys,
  draftOf,
  type ApprovalDraft,
} from "./approval";

/** The saved value stays selectable while the list loads or when the platform no longer lists it. */
const withSaved = (options: Array<{ value: string; label: string }>, saved: string) =>
  options.some((o) => o.value === saved) ? options : [{ value: saved, label: saved }, ...options];

const NUMBER_FIELDS: Array<[keyof ApprovalDraft, string, string?]> = [
  ["approvalSlaHours", "Approval target (hours)"],
  ["approvalWarningThresholdHours", "Warn when this many hours are left"],
  ["escalationDelayHours", "Escalate after (hours)"],
  ["escalationReminderIntervalHours", "Reminder interval (hours)"],
  ["maxEscalationReminders", "Maximum reminders"],
];

const TIMING_FIELDS: Array<[keyof ApprovalDraft, string]> = [
  ["reservationHoldMinutes", "Reservation hold (minutes)"],
  ["reservationGraceMinutes", "Payment grace period (minutes)"],
  ["escrowHoldDays", "Escrow hold after the event (days)"],
  ["refundCutoffHours", "Refund cutoff before the event (hours)"],
  ["maxTicketsPerBooking", "Tickets per booking (maximum)"],
  ["rescheduleLimit", "Reschedules allowed per event"],
];

/**
 * Who an unattended escalation is sent to. The configuration stores a plain string, so the policy of which
 * staff roles may receive one lives here, typed from the generated staff role union.
 */
const RECIPIENTS: Record<Extract<UserType, 'FINANCE_LEAD' | 'ADMIN' | 'SUPER_ADMIN'>, string> = {
  FINANCE_LEAD: "Finance lead",
  ADMIN: "Admin",
  SUPER_ADMIN: "Super admin",
};

/** The one channel value that is not a channel: both. A contract value of `ApprovalNotificationChannel`. */
const BOTH_CHANNELS: ApprovalNotificationChannel = "BOTH";
const DIRECT_CHANNELS: ReadonlyArray<ApprovalNotificationChannel> = ["EMAIL", "IN_APP"];

const TOGGLES: Array<[keyof ApprovalDraft, string, string]> = [
  [
    "autoEscalationEnabled",
    "Escalate automatically",
    "When the target passes, notify the recipient without waiting for a person.",
  ],
  [
    "sendSlaWarningNotifications",
    "Send SLA warnings",
    "Tell the reviewer before the target passes.",
  ],
  ["sendEscalationNotifications", "Send escalation notifications", ""],
  ["requireCommentsOnRejection", "Require comments when rejecting", ""],
  [
    "requireCommentsOnChangesRequested",
    "Require comments when requesting changes",
    "",
  ],
];

/** Platform rules: approvals and SLA, money, holds and cutoffs, refund policies and the organizer preview, all stored by the backend. */
export function RulesTab() {
  const { config, loading, error, refetch } = usePlatformConfiguration();
  if (!config && loading) {
    return (
      <div
        className="m3-stack"
        aria-busy="true"
        aria-label="Loading platform rules"
      >
        <Skeleton shape="block" />
        <Skeleton shape="block" />
      </div>
    );
  }
  if (!config) {
    return error ? (
      <ErrorState error={error} onRetry={() => void refetch()} />
    ) : (
      <EmptyState
        title="Platform rules are not available"
        description="The configuration could not be loaded."
      />
    );
  }
  return <RulesForm config={config} />;
}

function RulesForm({ config }: { config: PlatformConfigurationRow }) {
  const router = useRouter();
  const { can } = useStaff();
  const { show } = useSnackbar();
  const { guard } = useStepUp();
  const { update, loading: saving } = useUpdatePlatformConfiguration();
  const [pending, setPending] = useState<ApprovalDraft | null>(null);
  const [confirmReset, setConfirmReset] = useState(false);
  // Email and in-app are platform channels (reference data); "both" is a contract value of the setting.
  const channelRows = useReferenceOptions("NOTIFICATION_CHANNEL");
  const channelOptions = useMemo(
    () => [
      ...channelRows.options.filter((o) => DIRECT_CHANNELS.includes(o.value as ApprovalNotificationChannel)).map((o) => ({ value: o.value, label: o.label })),
      ...(channelRows.ready ? [{ value: BOTH_CHANNELS, label: "Email and in-app" }] : []),
    ],
    [channelRows.options, channelRows.ready],
  );

  const saved = useMemo(() => draftOf(config), [config]);
  const form = useZodForm(approvalSchema, { defaultValues: saved });
  useEffect(() => {
    form.reset(saved);
  }, [form, saved]);

  const { dirtyFields } = useFormState({ control: form.control });
  const dirtyCount = Object.keys(dirtyFields).length;
  const [sla, explain] = form.watch([
    "approvalSlaHours",
    "requireCommentsOnChangesRequested",
  ]);
  const live = form.watch();
  const slaOk = Number.isFinite(sla);

  const edit = can("cfgEdit");
  const selfApproval = can("cfgHolds");
  const changes = pending ? changedKeys(pending, saved) : [];

  const save = async () => {
    if (!pending) return;
    try {
      await guard(() =>
        update(Object.fromEntries(changes.map((k) => [k, pending[k]]))),
      );
      form.reset(pending);
      show({
        message:
          "Configuration saved. Organizers now see the updated approval rules.",
      });
    } catch {
      show({
        message: "Could not save the configuration. Try again.",
        tone: "error",
      });
    }
    setPending(null);
  };

  const reset = async () => {
    try {
      await guard(() => update({ ...APPROVAL_DEFAULTS }));
      form.reset(APPROVAL_DEFAULTS);
      show({ message: "Platform rules reset to defaults" });
    } catch {
      show({
        message: "Could not reset the configuration. Try again.",
        tone: "error",
      });
    }
    setConfirmReset(false);
  };

  const preview = (
    <Card
      as="section"
      variant="filled"
      aria-label="What the organizer will see"
    >
      <CardHeader
        title="What organizers will see"
        subtitle="Live preview of your draft. It updates as you type."
      />
      <KeyValue
        items={[
          { label: "Commission", value: Number.isFinite(live.commissionDefault) ? `${live.commissionDefault}% of each ticket sale` : "–" },
          { label: "Payout minimum", value: Number.isFinite(live.minimumPayout) ? money(live.minimumPayout) : "–" },
          { label: "Escrow", value: `Held for ${live.escrowHoldDays ?? "–"} days after the event` },
          {
            label: "Approval",
            value: slaOk
              ? `Typical review time: ${sla} hours.${explain ? " Reviewers always explain requested changes." : ""}`
              : "–",
          },
          { label: "Buyers", value: `Hold a ticket for ${live.reservationHoldMinutes ?? "–"} minutes, up to ${live.maxTicketsPerBooking ?? "–"} per booking` },
          { label: "Rescheduling", value: `Up to ${live.rescheduleLimit ?? "–"} reschedules per event` },
          { label: "Refund policies on offer", value: (live.refundPolicies ?? []).map((p) => `${p.label}: ${p.summary}`).join(" · ") || "None" },
        ]}
      />
    </Card>
  );

  const aside = (
    <>
      {preview}
      <Card as="section" aria-label="Market and payments">
        <CardHeader
          title="Market and payments"
          subtitle="Fixed for this launch."
        />
        <KeyValue
          items={[
            { label: "Currency", value: "Zambian Kwacha (K)" },
            { label: "Country", value: "Zambia" },
            { label: "Payment provider", value: "PawaPay only" },
            {
              label: "Buyer methods",
              value: "MTN Mobile Money, Airtel Money, Zamtel Kwacha",
            },
          ]}
        />
      </Card>
      <Card as="section" aria-label="Reference data">
        <CardHeader
          title="Reference data"
          subtitle="Shared with organizers and buyers. Managed on their own pages."
        />
        <div className="m3-stack">
          <Button
            variant="outlined"
            onClick={() => router.push("/events/categories")}
          >
            Event categories
          </Button>
          <Button
            variant="outlined"
            onClick={() => router.push("/events/locations")}
          >
            Provinces and cities
          </Button>
          <Button
            variant="outlined"
            onClick={() => router.push("/config/refdata")}
          >
            Banks, KYB types, reasons
          </Button>
        </div>
      </Card>
    </>
  );

  const main = (
    <>
      <Banner
        tone="info"
        actions={
          edit ? (
            <Button
              variant="text"
              danger
              size="sm"
              onClick={() => setConfirmReset(true)}
            >
              Reset to defaults
            </Button>
          ) : undefined
        }
      >
        <strong>Versioning:</strong> last saved by{" "}
        {config.updatedBy || "unknown"} on {formatDateTime(config.updatedAt)}.
        Saving publishes the approval rules to the organizer and buyer apps.
      </Banner>

      <Card as="section" aria-label="Approvals and SLA">
        <CardHeader
          title="Approvals and SLA"
          subtitle="How long reviewers have and what happens when a submission waits."
        />
        <FormGrid>
          {NUMBER_FIELDS.map(([k, label]) => (
            <FormCell key={k} span={6}>
              <TextFieldRHF
                name={k}
                label={label}
                type="number"
                inputMode="numeric"
              />
            </FormCell>
          ))}
          <FormCell span={6}>
            <SelectRHF
              name="escalationRecipientRole"
              label="Escalation goes to"
              options={[
                ...(saved.escalationRecipientRole in RECIPIENTS
                  ? []
                  : [
                      {
                        value: saved.escalationRecipientRole,
                        label: saved.escalationRecipientRole,
                      },
                    ]),
                ...enumOptions(RECIPIENTS),
              ]}
            />
          </FormCell>
          <FormCell span={6}>
            <SelectRHF
              name="organizerNotificationChannel"
              label="Organizer notification channel"
              disabled={!channelRows.ready}
              helperText={channelRows.error ? "The channel list could not be loaded." : undefined}
              options={withSaved(channelOptions, saved.organizerNotificationChannel)}
            />
          </FormCell>
          <FormCell span={6}>
            <SelectRHF
              name="adminNotificationChannel"
              label="Admin notification channel"
              disabled={!channelRows.ready}
              helperText={channelRows.error ? "The channel list could not be loaded." : undefined}
              options={withSaved(channelOptions, saved.adminNotificationChannel)}
            />
          </FormCell>
        </FormGrid>
        {TOGGLES.map(([k, label, hint]) => (
          <SwitchRHF
            key={String(k)}
            name={k}
            label={label}
            hint={hint || undefined}
          />
        ))}
        <SwitchRHF
          name="allowSelfApproval"
          label="Allow self-approval"
          hint="Off keeps a second pair of eyes on every decision."
          disabled={!selfApproval}
        />
        {!selfApproval ? (
          <p className="m3-muted">
            Only super admins can change self-approval.
          </p>
        ) : null}
        <p className="m3-muted">
          Organizer impact: organizers see a typical review time of{" "}
          {slaOk ? sla : "–"} hours when they submit an event. Changes apply to
          submissions from now on.
        </p>
      </Card>

      <Card as="section" aria-label="Money">
        <CardHeader
          title="Money"
          subtitle="Commission and payout rules. Commission changes apply to new sales only."
        />
        <FormGrid>
          <FormCell span={6}>
            <TextFieldRHF name="commissionDefault" label="Default commission (%)" type="number" inputMode="decimal" />
          </FormCell>
          <FormCell span={6}>
            <TextFieldRHF name="minimumPayout" label="Minimum payout (K)" type="number" inputMode="decimal" />
          </FormCell>
        </FormGrid>
      </Card>

      <Card as="section" aria-label="Holds and cutoffs">
        <CardHeader
          title="Holds and cutoffs"
          subtitle="Timing rules for reservations, escrow and refunds."
        />
        <FormGrid>
          {TIMING_FIELDS.map(([k, label]) => (
            <FormCell key={k} span={6}>
              <TextFieldRHF name={k} label={label} type="number" inputMode="numeric" disabled={!selfApproval} />
            </FormCell>
          ))}
        </FormGrid>
        {!selfApproval ? <p className="m3-muted">Only super admins can change timing rules.</p> : null}
      </Card>

      <RefundPolicyFields disabled={!edit} />
    </>
  );

  return (
    <div className="m3-stack">
      <Form<z.input<typeof approvalSchema>, ApprovalDraft>
        form={form}
        disabled={!edit}
        aria-label="Platform rules"
        onSubmit={(values: ApprovalDraft) => setPending(values)}
      >
        <SplitLayout
          main={main}
          aside={aside}
          asideLabel="Organizer preview and shared lists"
        />
        <SaveBar
          hidden={dirtyCount === 0}
          message={`${dirtyCount} unsaved change${dirtyCount === 1 ? "" : "s"}`}
          saving={saving}
          saveLabel="Save configuration"
          onSave={() =>
            void form.handleSubmit((v: ApprovalDraft) => setPending(v))()
          }
          onDiscard={() => {
            form.reset(saved);
            show({ message: "Changes discarded" });
          }}
        />
      </Form>
      <PermissionCatalogue />
      <ConfirmDialog
        open={pending !== null}
        onClose={() => setPending(null)}
        onConfirm={() => void save()}
        title="Save platform configuration?"
        description={`${changes.length} setting${changes.length === 1 ? "" : "s"} change: ${changes
          .map((k) => APPROVAL_LABELS[k])
          .join(
            ", ",
          )}. New rules apply to future activity only and are published to the organizer app.`}
        confirmLabel="Save configuration"
        loading={saving}
      />
      <ConfirmDialog
        open={confirmReset}
        onClose={() => setConfirmReset(false)}
        onConfirm={() => void reset()}
        title="Reset platform rules to defaults?"
        description="Every platform rule returns to the launch defaults, including the 48-hour approval target. Organizers pick this up straight away."
        confirmLabel="Reset to defaults"
        danger
        loading={saving}
      />
    </div>
  );
}
