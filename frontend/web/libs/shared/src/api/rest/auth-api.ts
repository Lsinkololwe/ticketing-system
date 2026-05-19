/**
 * REST API Authentication Client
 *
 * Provides direct REST API calls to backend authentication endpoints.
 * Used for legacy REST-based authentication flows.
 *
 * NOTE: For OAuth2/OIDC authentication, use Keycloak via the KeycloakProvider.
 * This module is kept for backward compatibility with REST endpoints.
 */

import axios, { AxiosResponse } from 'axios';
import type {
  LoginRequest,
  LoginResponse,
  RegistrationRequest,
  RegistrationResponse,
  AuthResult,
  User
} from './types/auth';
import { RestUserType } from './types/rest-api-types';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080';

/**
 * Create REST API client instance
 */
const restApiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
});

/**
 * Map backend role string to RestUserType enum
 * Matches backend UserType enum from com.pml.event_ticketing.constants.UserType
 */
const mapRoleToUserType = (role: string): RestUserType => {
  switch (role) {
    case 'SYSTEM_ADMIN':
      return RestUserType.SYSTEM_ADMIN;
    case 'SUPER_ADMIN':
      return RestUserType.SUPER_ADMIN;
    case 'EVENT_ORGANIZER':
      return RestUserType.EVENT_ORGANIZER;
    case 'EVENT_ATTENDEE':
      return RestUserType.EVENT_ATTENDEE;
    case 'FINANCE_ADMIN':
      return RestUserType.FINANCE_ADMIN;
    case 'OPERATIONS_ADMIN':
      return RestUserType.OPERATIONS_ADMIN;
    default:
      return RestUserType.EVENT_ATTENDEE;
  }
};

/**
 * Map LoginResponse to User interface
 */
const mapLoginResponseToUser = (response: LoginResponse): User => {
  return {
    id: response.userId,
    email: response.email || response.username,
    username: response.username,
    firstName: response.firstName,
    lastName: response.lastName,
    phoneNumber: response.phoneNumber,
    userType: mapRoleToUserType(response.role),
    isActive: true,
    emailVerified: response.emailVerified ?? false,
    phoneVerified: response.phoneVerified ?? false,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  };
};

/**
 * User login (non-admin users)
 * POST /api/auth/login
 */
export const login = async (username: string, password: string): Promise<AuthResult> => {
  try {
    const loginRequest: LoginRequest = { username, password };
    
    const response: AxiosResponse<LoginResponse> = await restApiClient.post(
      '/api/auth/login',
      loginRequest
    );

    const data = response.data;
    
    // Check for pending approval
    if (data.pendingApproval) {
      return {
        success: false,
        message: data.message || 'Account pending approval',
      };
    }

    const user = mapLoginResponseToUser(data);
    
    return {
      success: true,
      message: data.message || 'Login successful',
      user,
      token: data.token,
    };
  } catch (error) {
    if (axios.isAxiosError(error)) {
      const errorMessage = error.response?.data?.message || 
                          error.response?.data?.error || 
                          `Login failed with status ${error.response?.status}`;
      return {
        success: false,
        message: errorMessage,
      };
    }
    
    return {
      success: false,
      message: error instanceof Error ? error.message : 'Login failed. Please try again.',
    };
  }
};

/**
 * Admin login
 * POST /api/auth/admin/login
 * Note: Backend admin login response only includes: token, userId, role, username
 * Missing: email, firstName, lastName, phoneNumber, emailVerified, phoneVerified
 */
export const adminLogin = async (username: string, password: string): Promise<AuthResult> => {
  try {
    const loginRequest: LoginRequest = { username, password };
    
    const response: AxiosResponse<LoginResponse> = await restApiClient.post(
      '/api/auth/admin/login',
      loginRequest
    );

    const data = response.data;
    
    // Admin login response may not have all fields, so we construct user with available data
    const user = mapLoginResponseToUser({
      ...data,
      // Use username as email fallback if email not provided
      email: data.email || data.username,
      // Set defaults for missing fields
      firstName: data.firstName || '',
      lastName: data.lastName || '',
      emailVerified: data.emailVerified ?? false,
      phoneVerified: data.phoneVerified ?? false,
    });
    
    return {
      success: true,
      message: data.message || 'Login successful',
      user,
      token: data.token,
    };
  } catch (error) {
    if (axios.isAxiosError(error)) {
      const errorMessage = error.response?.data?.message || 
                          error.response?.data?.error || 
                          `Login failed with status ${error.response?.status}`;
      return {
        success: false,
        message: errorMessage,
      };
    }
    
    return {
      success: false,
      message: error instanceof Error ? error.message : 'Login failed. Please try again.',
    };
  }
};

/**
 * User registration
 * POST /api/auth/register
 * Note: Backend returns ResponseEntity<String> with "User registered successfully"
 */
export const register = async (request: RegistrationRequest): Promise<AuthResult> => {
  try {
    const response: AxiosResponse<string | RegistrationResponse> = await restApiClient.post(
      '/api/auth/register',
      request
    );

    // Backend returns a plain string "User registered successfully" on success
    // or error response with JSON on failure
    if (typeof response.data === 'string') {
      return {
        success: true,
        message: response.data,
      };
    }

    const data = response.data as RegistrationResponse;
    
    return {
      success: data.success ?? true,
      message: data.message || 'Registration successful',
    };
  } catch (error) {
    if (axios.isAxiosError(error)) {
      // Handle 409 Conflict (organizer already exists and pending)
      if (error.response?.status === 409) {
        const errorMessage = typeof error.response.data === 'string' 
          ? error.response.data 
          : error.response.data?.message || 'Registration already pending';
        return {
          success: false,
          message: errorMessage,
        };
      }
      
      const errorMessage = error.response?.data?.message || 
                          error.response?.data?.error || 
                          (typeof error.response?.data === 'string' ? error.response.data : undefined) ||
                          `Registration failed with status ${error.response?.status}`;
      return {
        success: false,
        message: errorMessage,
      };
    }
    
    return {
      success: false,
      message: error instanceof Error ? error.message : 'Registration failed. Please try again.',
    };
  }
};

/**
 * Logout
 * POST /api/auth/logout
 */
export const logout = async (token: string): Promise<boolean> => {
  try {
    const response = await restApiClient.post(
      '/api/auth/logout',
      {},
      {
        headers: {
          'Authorization': `Bearer ${token}`,
        },
      }
    );

    return response.status === 200;
  } catch (error) {
    console.error('Logout error:', error);
    return false;
  }
};


