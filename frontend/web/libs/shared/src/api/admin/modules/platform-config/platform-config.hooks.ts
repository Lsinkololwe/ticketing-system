'use client';

import type { UpdatePlatformConfigurationInput as GqlUpdatePlatformConfigurationInput, PlatformConfigurationQuery, UpdatePlatformConfigurationMutation, UpdatePlatformConfigurationMutationVariables } from '../../../../types/graphql';
import { useMutation, useQuery } from '@apollo/client/react';
import { PLATFORM_CONFIGURATION, UPDATE_PLATFORM_CONFIGURATION } from './platform-config.queries';

export type ApprovalNotificationChannel = string;

export interface PlatformRefundPolicyRow {
  code: string;
  label: string;
  summary: string;
  rules: Array<{ daysBefore: number; percent: number }>;
}

export interface PlatformConfigurationRow {
  id: string;
  approvalSlaHours: number;
  approvalWarningThresholdHours: number;
  autoEscalationEnabled: boolean;
  escalationDelayHours: number;
  escalationRecipientRole: string;
  escalationReminderIntervalHours: number;
  maxEscalationReminders: number;
  organizerNotificationChannel: ApprovalNotificationChannel;
  adminNotificationChannel: ApprovalNotificationChannel;
  sendSlaWarningNotifications: boolean;
  sendEscalationNotifications: boolean;
  requireCommentsOnRejection: boolean;
  requireCommentsOnChangesRequested: boolean;
  allowSelfApproval: boolean;
  commissionDefault: number | null;
  minimumPayout: number | string | null;
  currency: string;
  reservationHoldMinutes: number;
  reservationGraceMinutes: number;
  escrowHoldDays: number;
  refundCutoffHours: number;
  maxTicketsPerBooking: number;
  rescheduleLimit: number;
  refundPolicies: PlatformRefundPolicyRow[];
  version: number;
  updatedAt: string;
  updatedBy: string;
}

export type UpdatePlatformConfigurationInput = Partial<Omit<PlatformConfigurationRow, 'id' | 'version' | 'updatedAt' | 'updatedBy'>>;

export function usePlatformConfiguration() {
  const { data, loading, error, refetch } = useQuery<PlatformConfigurationQuery>(
    PLATFORM_CONFIGURATION,
    { fetchPolicy: 'cache-and-network', errorPolicy: 'all' },
  );
  return { config: (data?.platformConfiguration ?? null) as PlatformConfigurationRow | null, loading, error, refetch };
}

export function useUpdatePlatformConfiguration() {
  const [mutate, { loading, error }] = useMutation<UpdatePlatformConfigurationMutation, UpdatePlatformConfigurationMutationVariables>(UPDATE_PLATFORM_CONFIGURATION, { errorPolicy: 'all' });
  const update = async (input: UpdatePlatformConfigurationInput) => {
    const res = await mutate({ variables: { input: input as GqlUpdatePlatformConfigurationInput } });
    if (res.error || !res.data) throw res.error ?? new Error('Update failed');
    return res.data.updatePlatformConfiguration;
  };
  return { update, loading, error };
}
