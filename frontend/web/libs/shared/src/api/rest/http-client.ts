/**
 * Base API Client
 *
 * Provides a base axios instance with token management interceptors
 * and error handling utilities for all API operations.
 *
 * The token getter function allows integration with different token storage mechanisms.
 * For apps using Keycloak, pass the getToken function from useKeycloak() hook.
 */

import axios, { AxiosInstance, AxiosError, InternalAxiosRequestConfig, AxiosResponse } from 'axios';
import { redirectToLogout } from '../../auth/server/token-service';

// Backend API base URL
const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080';

/**
 * API Error type
 */
export interface ApiError {
  code: string;
  message: string;
  details?: unknown;
  timestamp: string;
}

/**
 * Type for synchronous token getter function
 */
export type TokenGetter = () => { token: string } | null;

/**
 * Type for async token getter function (recommended)
 */
export type AsyncTokenGetter = () => Promise<string | null>;

/**
 * Create base axios instance with default configuration
 *
 * @param tokenGetter - Optional synchronous function to get the current token
 * @param asyncTokenGetter - Optional async function to get token string (recommended)
 */
export const createApiClient = (
  tokenGetter?: TokenGetter,
  asyncTokenGetter?: AsyncTokenGetter
): AxiosInstance => {
  const client = axios.create({
    baseURL: API_BASE_URL,
    timeout: 30000, // 30 seconds timeout for admin operations
    headers: {
      'Content-Type': 'application/json',
    },
  });

  // Request interceptor to add auth token with validation
  client.interceptors.request.use(
    async (config: InternalAxiosRequestConfig) => {
      try {
        let token: string | null = null;
        if (asyncTokenGetter) {
          token = await asyncTokenGetter();
        } else if (tokenGetter) {
          token = tokenGetter()?.token ?? null;
        }
        // If no token getter provided and no token found, redirect to logout
        if (!token) {
          redirectToLogout();
          return Promise.reject(new Error('No valid token available'));
        }
        if (config.headers) {
          config.headers.Authorization = `Bearer ${token}`;
        }
        return config;
      } catch (error) {
        redirectToLogout();
        return Promise.reject(error);
      }
    },
    (error) => {
      return Promise.reject(error);
    }
  );

  // Response interceptor for error handling
  client.interceptors.response.use(
    (response: AxiosResponse) => {
      return response;
    },
    (error: AxiosError) => {
      // Handle common error scenarios
      if (error.response?.status === 401) {
        // Unauthorized - clear auth data
        redirectToLogout();
      } else if (error.response?.status === 403) {
        // Forbidden - user doesn't have permission
        const errorData = error.response?.data as { message?: string } | undefined;
        console.warn('Access forbidden:', errorData?.message || 'Insufficient permissions');
      }
      
      return Promise.reject(error);
    }
  );

  return client;
};

/**
 * Default API client instance
 * NOTE: For authenticated requests, you must provide an asyncTokenGetter.
 * Example with Keycloak:
 * ```ts
 * const { getToken } = useKeycloak();
 * const client = createApiClient(undefined, getToken);
 * ```
 */
export const apiClient = createApiClient();

/**
 * Convert axios error to ApiError
 */
export const toApiError = (error: unknown): ApiError => {
  if (axios.isAxiosError(error)) {
    const axiosError = error as AxiosError<{ message?: string; error?: string; code?: string }>;
    return {
      code: axiosError.response?.data?.code || `HTTP_${axiosError.response?.status || 'UNKNOWN'}`,
      message: axiosError.response?.data?.message || 
               axiosError.response?.data?.error || 
               axiosError.message || 
               'An error occurred',
      details: axiosError.response?.data,
      timestamp: new Date().toISOString(),
    };
  }
  
  if (error instanceof Error) {
    return {
      code: 'UNKNOWN_ERROR',
      message: error.message,
      timestamp: new Date().toISOString(),
    };
  }
  
  return {
    code: 'UNKNOWN_ERROR',
    message: 'An unexpected error occurred',
    timestamp: new Date().toISOString(),
  };
};

/**
 * Handle API response and convert to ApiResponse format
 */
export const handleApiResponse = <T>(response: AxiosResponse<T>): T => {
  return response.data;
};

/**
 * Handle API error and convert to ApiResponse format
 */
export const handleApiError = <T>(error: unknown): { success: false; error: ApiError } => {
  const apiError = toApiError(error);
  return {
    success: false,
    error: apiError,
  };
};

export { API_BASE_URL };


