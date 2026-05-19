// REST API Authentication Types
// These types match the backend REST API (AuthController.java) structure

import { RestUserType } from './rest-api-types';

/**
 * Login Request DTO
 * Matches backend LoginDto.java
 */
export interface LoginRequest {
  username: string;
  password: string;
}

/**
 * Frontend Login Input (for forms)
 * Uses email/username interchangeably for UX
 */
export interface LoginInput {
  email: string; // Maps to username in LoginRequest
  password: string;
  rememberMe?: boolean;
}

/**
 * Login Response from REST API
 * Matches backend AuthController.java response structure
 */
export interface LoginResponse {
  message?: string;
  token: string;
  userId: string;
  username: string;
  email?: string;
  phoneNumber?: string;
  firstName?: string;
  lastName?: string;
  role: string; // UserType enum value as string
  roles?: string[]; // Array of role strings
  emailVerified?: boolean;
  phoneVerified?: boolean;
  pendingApproval?: boolean;
}

/**
 * User Registration Request DTO
 * Matches backend UserRegistrationDto.java
 */
export interface RegistrationRequest {
  firstName: string;
  lastName: string;
  email: string;
  phoneNumber: string;
  userType?: RestUserType;
  password?: string;
  confirmPassword?: string;
  companyName?: string;
  taxId?: string;
  businessPhone?: string;
  businessEmail?: string;
}

/**
 * Frontend Registration Input (for forms)
 * Uses RestUserType and includes UI-specific fields
 */
export interface RegisterInput {
  firstName: string;
  lastName: string;
  email: string;
  phoneNumber: string;
  userType: RestUserType;
  password: string;
  confirmPassword: string;
  companyName?: string;
  taxId?: string;
  businessPhone?: string;
  businessEmail?: string;
  acceptTerms?: boolean;
  acceptPrivacy?: boolean;
  marketingConsent?: boolean;
}

/**
 * User Registration Response
 */
export interface RegistrationResponse {
  success: boolean;
  message: string;
  userId?: string;
}

/**
 * User Registration Progress DTO
 * Matches backend UserRegistrationDto.java progress tracking fields
 */
export interface UserRegistrationProgress {
  userId?: string;
  progressPercentage: number;
  stepData?: Record<string, unknown>;
  lastUpdated?: string;
  nextStep?: string;
  canProceed: boolean;
  validationErrors: string[];
}

/**
 * Auth Result Type
 * Result of authentication operations
 */
export interface AuthResult {
  success: boolean;
  message: string;
  user?: User;
  token?: string;
}

/**
 * User Interface
 * Represents a user from the REST API
 * 
 * Matches backend User model fields returned from REST endpoints.
 * Note: Some backend fields (password, otpCode, etc.) are never returned in API responses.
 */
export interface User {
  id: string; // Backend User.id
  email: string; // Backend User.email
  username: string; // Backend User.username
  firstName?: string; // Backend User.firstName
  lastName?: string; // Backend User.lastName
  phoneNumber?: string; // Backend User.phoneNumber
  userType: RestUserType; // Backend User.userType (UserType enum)
  profilePicture?: string; // May be in GraphQL schema but not in REST User model
  isActive: boolean; // Backend User.isActive
  enabled?: boolean; // Backend User.enabled (Spring Security) - may not be in all responses
  emailVerified: boolean; // Backend User.emailVerified
  phoneVerified: boolean; // Backend User.phoneVerified
  lastLoginAt?: string; // Backend User.lastLoginAt (LocalDateTime -> ISO string)
  createdAt: string; // Backend User.createdAt (LocalDateTime -> ISO string)
  updatedAt: string; // Backend User.updatedAt (LocalDateTime -> ISO string)
  
  // Business fields for EVENT_ORGANIZER users
  companyName?: string; // Backend User.companyName
  taxId?: string; // Backend User.taxId
  businessPhone?: string; // Backend User.businessPhone
  businessEmail?: string; // Backend User.businessEmail
  organizerApprovalStatus?: string; // Backend User.organizerApprovalStatus (OrganizerApprovalStatus enum -> string)
  organizerApprovalNote?: string; // Backend User.organizerApprovalNote
  
  // Optional audit fields (may not be in all responses)
  createdBy?: string; // Backend User.createdBy
  updatedBy?: string; // Backend User.updatedBy
}
