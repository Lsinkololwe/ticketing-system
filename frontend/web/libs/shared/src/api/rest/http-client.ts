/**
 * Base API Client
 *
 * Provides a base axios instance with token management interceptors
 * and error handling utilities for all API operations.
 *
 * Apps use the same-origin BFF client (`createBffApiClient`); the browser holds no tokens.
 */

import axios, { AxiosInstance, AxiosError, AxiosResponse } from 'axios';

// Backend API base URL
const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080';

/**
 * Redirect to login page on auth failure
 * Client-side only - safe to use in browser context
 */
function redirectToLogout(): void {
  if (typeof window !== 'undefined') {
    window.location.href = '/login';
  }
}

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
 * Axios client for the same-origin BFF (`/api/rest/*`). No token getters: the BFF attaches the
 * bearer server-side. Cookies travel same-origin only and every request carries the constant
 * `x-pml-csrf` header the BFF requires on unsafe methods. A 401 sends the user to `/login`.
 */
export const createBffApiClient = (baseURL = '/api/rest'): AxiosInstance => {
  const client = axios.create({
    baseURL,
    timeout: 30000,
    withCredentials: false, // same-origin requests send cookies regardless; never cross-origin
    headers: { 'Content-Type': 'application/json', 'x-pml-csrf': '1' },
  });
  client.interceptors.response.use(
    (response: AxiosResponse) => response,
    (error: AxiosError) => {
      if (error.response?.status === 401) redirectToLogout();
      return Promise.reject(error);
    }
  );
  return client;
};

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


