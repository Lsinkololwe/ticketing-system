'use client';

/**
 * Organization Context Provider
 *
 * Provides organization data and role-based permissions throughout the dashboard.
 *
 * Features:
 * - Fetches and caches organization data
 * - Provides current user's role in the organization
 * - Permission helpers for role-based access control
 * - Loading and error states
 *
 * Usage:
 * ```tsx
 * const { organization, role, can } = useOrganization();
 *
 * if (can('manageTeam')) {
 *   // Show team management UI
 * }
 * ```
 */

import {
  createContext,
  useContext,
  useState,
  useEffect,
  useCallback,
  useMemo,
  ReactNode,
} from 'react';
import { useSession } from '@/lib/auth/client';

// =============================================================================
// TYPES
// =============================================================================

export type OrganizationRole = 'OWNER' | 'ADMIN' | 'MANAGER' | 'MARKETER' | 'CONTRIBUTOR';

export type OrganizerProfileStatus =
  | 'DRAFT'
  | 'PENDING_DOCUMENTS'
  | 'PENDING_REVIEW'
  | 'APPROVED'
  | 'REJECTED'
  | 'CHANGES_REQUESTED';

export interface Organization {
  id: string;
  name: string;
  slug: string;
  logoUrl?: string;
  description?: string;
  website?: string;
  email?: string;
  phone?: string;
  address?: {
    line1: string;
    line2?: string;
    city: string;
    province: string;
    postalCode?: string;
    country: string;
  };
  settings?: {
    timezone: string;
    currency: string;
    language: string;
    notificationsEnabled: boolean;
  };
  createdAt: string;
  updatedAt: string;
}

export interface OrganizerProfile {
  id: string;
  status: OrganizerProfileStatus;
  companyName: string;
  businessType: string;
  submittedAt?: string;
  approvedAt?: string;
  reviewNotes?: string;
}

export interface OrganizationMember {
  id: string;
  userId: string;
  name: string;
  email: string;
  role: OrganizationRole;
  joinedAt: string;
}

/**
 * Permission capabilities based on role
 */
export type Permission =
  | 'manageTeam'
  | 'createEvents'
  | 'publishEvents'
  | 'viewFinancials'
  | 'requestPayouts'
  | 'viewAnalytics'
  | 'checkIn'
  | 'manageSettings';

/**
 * Role-permission matrix
 */
const ROLE_PERMISSIONS: Record<OrganizationRole, Permission[]> = {
  OWNER: [
    'manageTeam',
    'createEvents',
    'publishEvents',
    'viewFinancials',
    'requestPayouts',
    'viewAnalytics',
    'checkIn',
    'manageSettings',
  ],
  ADMIN: [
    'manageTeam',
    'createEvents',
    'publishEvents',
    'viewFinancials',
    'requestPayouts',
    'viewAnalytics',
    'checkIn',
    'manageSettings',
  ],
  MANAGER: [
    'createEvents',
    'publishEvents',
    'viewFinancials',
    'viewAnalytics',
    'checkIn',
  ],
  MARKETER: ['viewAnalytics', 'checkIn'],
  CONTRIBUTOR: ['checkIn'],
};

interface OrganizationContextValue {
  // Organization data
  organization: Organization | null;
  organizerProfile: OrganizerProfile | null;
  members: OrganizationMember[];

  // Current user's role
  role: OrganizationRole | null;
  isOwner: boolean;
  isAdmin: boolean;

  // Status checks
  isApproved: boolean;
  isPending: boolean;
  isLoading: boolean;
  error: string | null;

  // Permission helpers
  can: (permission: Permission) => boolean;
  canAny: (permissions: Permission[]) => boolean;
  canAll: (permissions: Permission[]) => boolean;

  // Actions
  refreshOrganization: () => Promise<void>;
  setOrganization: (org: Organization) => void;
}

// =============================================================================
// CONTEXT
// =============================================================================

const OrganizationContext = createContext<OrganizationContextValue | null>(null);

// =============================================================================
// PROVIDER
// =============================================================================

interface OrganizationProviderProps {
  children: ReactNode;
}

export function OrganizationProvider({ children }: OrganizationProviderProps) {
  const { data: session } = useSession();

  const [organization, setOrganization] = useState<Organization | null>(null);
  const [organizerProfile, setOrganizerProfile] = useState<OrganizerProfile | null>(null);
  const [members, setMembers] = useState<OrganizationMember[]>([]);
  const [role, setRole] = useState<OrganizationRole | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Fetch organization data
  const fetchOrganization = useCallback(async () => {
    if (!session?.user) {
      setIsLoading(false);
      return;
    }

    setIsLoading(true);
    setError(null);

    try {
      // TODO: Replace with actual GraphQL queries
      // const { data } = await apolloClient.query({
      //   query: MY_ORGANIZATION_QUERY,
      // });

      // For now, simulate API response with mock data
      await new Promise((resolve) => setTimeout(resolve, 500));

      // Mock data - replace with actual API response
      const mockOrganizerProfile: OrganizerProfile = {
        id: 'profile-1',
        status: 'APPROVED',
        companyName: 'My Organization',
        businessType: 'LIMITED_COMPANY',
        submittedAt: new Date().toISOString(),
        approvedAt: new Date().toISOString(),
      };

      const mockOrganization: Organization = {
        id: 'org-1',
        name: 'My Organization',
        slug: 'my-organization',
        email: 'contact@myorg.com',
        phone: '+260 97X XXX XXX',
        settings: {
          timezone: 'Africa/Lusaka',
          currency: 'ZMW',
          language: 'en',
          notificationsEnabled: true,
        },
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };

      const mockMembers: OrganizationMember[] = [
        {
          id: 'member-1',
          userId: session.user.id || 'user-1',
          name: session.user.name || 'User',
          email: session.user.email || '',
          role: 'OWNER',
          joinedAt: new Date().toISOString(),
        },
      ];

      setOrganizerProfile(mockOrganizerProfile);
      setOrganization(mockOrganization);
      setMembers(mockMembers);

      // Set current user's role
      const currentMember = mockMembers.find(
        (m) => m.userId === session.user?.id || m.email === session.user?.email
      );
      setRole(currentMember?.role || 'OWNER');
    } catch (err) {
      console.error('Failed to fetch organization:', err);
      setError('Failed to load organization data');
    } finally {
      setIsLoading(false);
    }
  }, [session]);

  // Initial fetch
  useEffect(() => {
    fetchOrganization();
  }, [fetchOrganization]);

  // Permission checker
  const can = useCallback(
    (permission: Permission): boolean => {
      if (!role) return false;
      return ROLE_PERMISSIONS[role].includes(permission);
    },
    [role]
  );

  const canAny = useCallback(
    (permissions: Permission[]): boolean => {
      return permissions.some(can);
    },
    [can]
  );

  const canAll = useCallback(
    (permissions: Permission[]): boolean => {
      return permissions.every(can);
    },
    [can]
  );

  // Computed values
  const value = useMemo<OrganizationContextValue>(
    () => ({
      organization,
      organizerProfile,
      members,
      role,
      isOwner: role === 'OWNER',
      isAdmin: role === 'OWNER' || role === 'ADMIN',
      isApproved: organizerProfile?.status === 'APPROVED',
      isPending:
        organizerProfile?.status === 'PENDING_REVIEW' ||
        organizerProfile?.status === 'PENDING_DOCUMENTS',
      isLoading,
      error,
      can,
      canAny,
      canAll,
      refreshOrganization: fetchOrganization,
      setOrganization,
    }),
    [
      organization,
      organizerProfile,
      members,
      role,
      isLoading,
      error,
      can,
      canAny,
      canAll,
      fetchOrganization,
    ]
  );

  return (
    <OrganizationContext.Provider value={value}>
      {children}
    </OrganizationContext.Provider>
  );
}

// =============================================================================
// HOOKS
// =============================================================================

/**
 * Hook to access organization context
 */
export function useOrganization(): OrganizationContextValue {
  const context = useContext(OrganizationContext);
  if (!context) {
    throw new Error('useOrganization must be used within an OrganizationProvider');
  }
  return context;
}

/**
 * Hook to check if user has a specific permission
 */
export function usePermission(permission: Permission): boolean {
  const { can } = useOrganization();
  return can(permission);
}

/**
 * Hook to check if user has any of the specified permissions
 */
export function useAnyPermission(permissions: Permission[]): boolean {
  const { canAny } = useOrganization();
  return canAny(permissions);
}

/**
 * Hook to get the current user's role
 */
export function useRole(): OrganizationRole | null {
  const { role } = useOrganization();
  return role;
}

/**
 * Hook to check organizer approval status
 */
export function useApprovalStatus(): {
  isApproved: boolean;
  isPending: boolean;
  status: OrganizerProfileStatus | null;
} {
  const { organizerProfile, isApproved, isPending } = useOrganization();
  return {
    isApproved,
    isPending,
    status: organizerProfile?.status || null,
  };
}

export default OrganizationContext;
