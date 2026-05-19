/**
 * Admin API Client
 */
import { AxiosInstance } from 'axios';
import { createApiClient, handleApiResponse, handleApiError, TokenGetter, AsyncTokenGetter } from './http-client';
import {
  UserRegistrationDto,
  UserResponse,
  TransactionHealthSummary,
  WebhookDeliveryStats,
  TransactionTrends,
  PerformanceMetrics,
  ReconciliationResponse,
  BootstrapStatus,
  PendingReconciliationsResponse,
} from './types/admin-api';

export class AdminApiClient {
  private client: AxiosInstance;

  /**
   * @param tokenGetter - Optional synchronous token getter
   * @param asyncTokenGetter - Optional async token getter (recommended)
   */
  constructor(tokenGetter?: TokenGetter, asyncTokenGetter?: AsyncTokenGetter) {
    this.client = createApiClient(tokenGetter, asyncTokenGetter);
  }

  async createAdmin(dto: UserRegistrationDto): Promise<UserResponse> {
    try {
      const response = await this.client.post<UserResponse>(
        '/api/admin/users/create-admin',
        dto
      );
      return handleApiResponse(response);
    } catch (error) {
      throw handleApiError(error).error;
    }
  }

  async createSuperAdmin(dto: UserRegistrationDto): Promise<UserResponse> {
    try {
      const response = await this.client.post<UserResponse>(
        '/api/admin/users/create-super-admin',
        dto
      );
      return handleApiResponse(response);
    } catch (error) {
      throw handleApiError(error).error;
    }
  }

  async createOrganizer(dto: UserRegistrationDto): Promise<UserResponse> {
    try {
      const response = await this.client.post<UserResponse>(
        '/api/admin/users/create-organizer',
        dto
      );
      return handleApiResponse(response);
    } catch (error) {
      throw handleApiError(error).error;
    }
  }

  async createAttendee(dto: UserRegistrationDto): Promise<UserResponse> {
    try {
      const response = await this.client.post<UserResponse>(
        '/api/admin/users/create-attendee',
        dto
      );
      return handleApiResponse(response);
    } catch (error) {
      throw handleApiError(error).error;
    }
  }

  async approveOrganizer(id: string, note?: string): Promise<UserResponse> {
    try {
      const params = note ? { note } : {};
      const response = await this.client.post<UserResponse>(
        `/api/admin/users/organizers/${id}/approve`,
        null,
        { params }
      );
      return handleApiResponse(response);
    } catch (error) {
      throw handleApiError(error).error;
    }
  }

  async rejectOrganizer(id: string, note?: string): Promise<UserResponse> {
    try {
      const params = note ? { note } : {};
      const response = await this.client.post<UserResponse>(
        `/api/admin/users/organizers/${id}/reject`,
        null,
        { params }
      );
      return handleApiResponse(response);
    } catch (error) {
      throw handleApiError(error).error;
    }
  }

  async getTransactionHealthSummary(): Promise<TransactionHealthSummary> {
    try {
      const response = await this.client.get<TransactionHealthSummary>(
        '/api/admin/monitoring/health/summary'
      );
      return handleApiResponse(response);
    } catch (error) {
      throw handleApiError(error).error;
    }
  }

  async getWebhookDeliveryStats(): Promise<WebhookDeliveryStats> {
    try {
      const response = await this.client.get<WebhookDeliveryStats>(
        '/api/admin/monitoring/webhooks/stats'
      );
      return handleApiResponse(response);
    } catch (error) {
      throw handleApiError(error).error;
    }
  }

  async getTransactionTrends(hours: number = 24): Promise<TransactionTrends> {
    try {
      const response = await this.client.get<TransactionTrends>(
        '/api/admin/monitoring/trends',
        { params: { hours } }
      );
      return handleApiResponse(response);
    } catch (error) {
      throw handleApiError(error).error;
    }
  }

  async getPerformanceMetrics(): Promise<PerformanceMetrics> {
    try {
      const response = await this.client.get<PerformanceMetrics>(
        '/api/admin/monitoring/performance/metrics'
      );
      return handleApiResponse(response);
    } catch (error) {
      throw handleApiError(error).error;
    }
  }

  async reconcileTransaction(transactionId: string): Promise<ReconciliationResponse> {
    try {
      const response = await this.client.post<ReconciliationResponse>(
        `/api/admin/reconciliation/transaction/${transactionId}`
      );
      return handleApiResponse(response);
    } catch (error) {
      throw handleApiError(error).error;
    }
  }

  async reconcileAllTransactions(): Promise<ReconciliationResponse> {
    try {
      const response = await this.client.post<ReconciliationResponse>(
        '/api/admin/reconciliation/all'
      );
      return handleApiResponse(response);
    } catch (error) {
      throw handleApiError(error).error;
    }
  }

  async getPendingReconciliations(): Promise<PendingReconciliationsResponse> {
    try {
      const response = await this.client.get<PendingReconciliationsResponse>(
        '/api/admin/reconciliation/pending'
      );
      return handleApiResponse(response);
    } catch (error) {
      throw handleApiError(error).error;
    }
  }

  async getBootstrapStatus(): Promise<BootstrapStatus> {
    try {
      const response = await this.client.get<BootstrapStatus>(
        '/api/bootstrap/status'
      );
      return handleApiResponse(response);
    } catch (error) {
      throw handleApiError(error).error;
    }
  }

  async createFirstAdmin(dto: UserRegistrationDto): Promise<UserResponse> {
    try {
      const response = await this.client.post<UserResponse>(
        '/api/bootstrap/first-admin',
        dto
      );
      return handleApiResponse(response);
    } catch (error) {
      throw handleApiError(error).error;
    }
  }

  async createFirstSuperAdmin(dto: UserRegistrationDto): Promise<UserResponse> {
    try {
      const response = await this.client.post<UserResponse>(
        '/api/bootstrap/first-super-admin',
        dto
      );
      return handleApiResponse(response);
    } catch (error) {
      throw handleApiError(error).error;
    }
  }
}

export const adminApi = new AdminApiClient();

export const createAdminApiClient = (tokenGetter?: TokenGetter, asyncTokenGetter?: AsyncTokenGetter): AdminApiClient => {
  return new AdminApiClient(tokenGetter, asyncTokenGetter);
};


