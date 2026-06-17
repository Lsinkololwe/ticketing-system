import 'server-only';
import type { Session as BetterAuthSession, User as BetterAuthUser } from 'better-auth/types';

/**
 * Better Auth session response from auth.api.getSession()
 * Contains both session data and user data
 */
export interface Session {
  readonly session: BetterAuthSession;
  readonly user: BetterAuthUser;
}

/**
 * User type alias for convenience
 */
export type User = BetterAuthUser;

/**
 * Organization application status enumeration.
 * Represents the current state of an organization's registration process.
 */
export enum OrganizationStatusEnum {
  /** No application started */
  NOT_STARTED = 'NOT_STARTED',
  /** Application draft in progress */
  DRAFT = 'DRAFT',
  /** Application in progress */
  IN_PROGRESS = 'IN_PROGRESS',
  /** Application submitted and pending review */
  PENDING_REVIEW = 'PENDING_REVIEW',
  /** Application approved, organization is active */
  APPROVED = 'APPROVED',
  /** Organization is active */
  ACTIVE = 'ACTIVE',
  /** Application needs changes */
  CHANGES_REQUESTED = 'CHANGES_REQUESTED',
  /** Application rejected */
  REJECTED = 'REJECTED',
  /** Organization suspended */
  SUSPENDED = 'SUSPENDED',
}

/**
 * Organization status information for routing decisions.
 * Includes computed boolean properties for easy conditional logic.
 */
export interface OrganizationStatus {
  /** Whether the user has an organization */
  readonly hasOrganization: boolean;
  /** Organization ID if exists */
  readonly id: string | null;
  /** Organization name if exists */
  readonly name: string | null;
  /** Raw status string from backend */
  readonly status: string | null;
  /** Whether organization is approved/active */
  readonly isApproved: boolean;
  /** Whether pending admin review */
  readonly isPendingReview: boolean;
  /** Whether changes were requested */
  readonly needsChanges: boolean;
  /** Whether application was rejected */
  readonly isRejected: boolean;
  /** Whether application is in draft state */
  readonly isDraft: boolean;
}

/**
 * GraphQL query response for organization status.
 */
export interface OrganizationStatusResponse {
  readonly myOrganizationStatus: OrganizationStatus;
}

/**
 * Route configuration for organization application flow.
 * Maps application statuses to their corresponding route paths.
 */
export interface RouteMapping {
  readonly [key: string]: string;
}

/**
 * Extended session with optional organization information.
 */
export interface SessionWithOrganization extends Session {
  readonly organization?: {
    readonly id: string;
    readonly status: OrganizationStatusEnum;
  };
}

/**
 * Keycloak token response structure.
 */
export interface KeycloakTokenResponse {
  readonly access_token: string;
  readonly expires_in: number;
  readonly refresh_expires_in: number;
  readonly refresh_token?: string;
  readonly token_type: string;
  readonly id_token?: string;
  readonly session_state?: string;
  readonly scope: string;
}

/**
 * Result of a session verification attempt.
 */
export interface SessionVerificationResult {
  readonly session: Session;
  readonly isValid: boolean;
}

/**
 * Options for session operations.
 */
export interface SessionOptions {
  readonly redirectOnError?: boolean;
  readonly errorPath?: string;
}
