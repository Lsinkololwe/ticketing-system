'use client';

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { FileUploadResponse } from './types/rest-api-types';
import { getAuthHeader } from '../../auth/server/token-service';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080/api';

export const restQueryKeys = {
  auth: { all: ['auth'] as const, currentUser: () => ['auth', 'currentUser'] as const },
  files: { all: ['files'] as const, list: () => ['files', 'list'] as const, detail: (id: string) => ['files', 'detail', id] as const },
  health: { all: ['health'] as const, status: () => ['health', 'status'] as const, info: () => ['health', 'info'] as const },
  admin: {
    all: ['admin'] as const,
    monitoring: { all: ['admin', 'monitoring'] as const, healthSummary: () => ['admin', 'monitoring', 'healthSummary'] as const, webhookStats: () => ['admin', 'monitoring', 'webhookStats'] as const },
    reconciliation: { all: ['admin', 'reconciliation'] as const, transaction: (id: string) => ['admin', 'reconciliation', 'transaction', id] as const },
  },
  bootstrap: { all: ['bootstrap'] as const, status: () => ['bootstrap', 'status'] as const },
};

// NOTE: useCurrentUser hook is provided by '@pml.tickets/shared/api/graphql/users'
// which uses GraphQL/Apollo Client for proper implementation

export const useFileUpload = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (file: File) => {
      const headers = await getAuthHeader();
      const formData = new FormData();
      formData.append('file', file);
      const response = await fetch(`${API_BASE_URL}/files/upload`, { method: 'POST', headers, body: formData });
      if (!response.ok) throw new Error('File upload failed');
      return response.json() as Promise<FileUploadResponse>;
    },
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: restQueryKeys.files.list() }); },
  });
};

export const useFileDownload = (fileId: string) => {
  return useQuery({
    queryKey: restQueryKeys.files.detail(fileId),
    queryFn: async () => {
      const headers = await getAuthHeader();
      const response = await fetch(`${API_BASE_URL}/files/${fileId}`, { headers });
      if (!response.ok) throw new Error('File download failed');
      return response.blob();
    },
    enabled: !!fileId,
  });
};

export const useHealthCheck = () => {
  return useQuery({
    queryKey: restQueryKeys.health.status(),
    queryFn: async () => {
      const response = await fetch(`${API_BASE_URL}/health`);
      if (!response.ok) throw new Error('Health check failed');
      return response.json() as Promise<{ status: string; timestamp: string; service: string; version: string }>;
    },
    refetchInterval: 30000,
  });
};

export const useHealthInfo = () => {
  return useQuery({
    queryKey: restQueryKeys.health.info(),
    queryFn: async () => {
      const response = await fetch(`${API_BASE_URL}/health/info`);
      if (!response.ok) throw new Error('Health info failed');
      return response.json() as Promise<{ status: string; timestamp: string; service: string; version: string }>;
    },
  });
};

export const useMonitoringHealthSummary = () => {
  return useQuery({
    queryKey: restQueryKeys.admin.monitoring.healthSummary(),
    queryFn: async () => {
      const headers = await getAuthHeader();
      const response = await fetch(`${API_BASE_URL}/admin/monitoring/health/summary`, { headers });
      if (!response.ok) throw new Error('Failed to fetch monitoring health summary');
      return response.json() as Promise<{ totalTransactions: number; pendingTransactions: number; failedTransactions: number; successRate: number; averageProcessingTime: number; lastUpdated: string; }>;
    },
    enabled: true,
    refetchInterval: 60000,
  });
};

export const useWebhookStats = () => {
  return useQuery({
    queryKey: restQueryKeys.admin.monitoring.webhookStats(),
    queryFn: async () => {
      const headers = await getAuthHeader();
      const response = await fetch(`${API_BASE_URL}/admin/monitoring/webhooks/stats`, { headers });
      if (!response.ok) throw new Error('Failed to fetch webhook stats');
      return response.json();
    },
    enabled: true,
  });
};

export const useReconcileTransaction = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (transactionId: string) => {
      const headers = await getAuthHeader({ 'Content-Type': 'application/json' });
      const response = await fetch(`${API_BASE_URL}/admin/reconciliation/transaction/${transactionId}`, { method: 'POST', headers });
      if (!response.ok) throw new Error('Transaction reconciliation failed');
      return response.json();
    },
    onSuccess: (data, transactionId) => {
      queryClient.invalidateQueries({ queryKey: restQueryKeys.admin.reconciliation.transaction(transactionId) });
      queryClient.invalidateQueries({ queryKey: restQueryKeys.admin.monitoring.healthSummary() });
    },
  });
};

export const useBootstrapStatus = () => {
  return useQuery({
    queryKey: restQueryKeys.bootstrap.status(),
    queryFn: async () => {
      const response = await fetch(`${API_BASE_URL}/bootstrap/status`);
      if (!response.ok) throw new Error('Failed to fetch bootstrap status');
      return response.json() as Promise<{ hasAdmin: boolean; hasSuper: boolean }>;
    },
  });
};

export const useCreateFirstAdmin = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (adminData: { email: string; password: string; firstName: string; lastName: string; }) => {
      const response = await fetch(`${API_BASE_URL}/bootstrap/first-admin`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(adminData) });
      if (!response.ok) throw new Error('Failed to create first admin');
      return response.json();
    },
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: restQueryKeys.bootstrap.status() }); },
  });
};

// Centralized token helper imported from shared service

