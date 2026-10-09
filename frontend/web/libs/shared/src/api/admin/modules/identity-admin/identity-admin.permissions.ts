'use client';

/**
 * The permission catalogue (identity `permissions`) and who holds each permission (`rolePermissions`).
 *
 * Both are ADMIN-only reads of what the platform code defines; nothing here is typed out. "Held by" is
 * derived by asking identity what each role carries, so it cannot drift from the code that enforces it.
 */
import { useEffect, useMemo, useState } from 'react';
import { gql } from '@apollo/client';
import { useApolloClient, useQuery } from '@apollo/client/react';
import type {
  AdminPermissionCatalogueQuery,
  AdminRolePermissionsQuery,
  AdminRolePermissionsQueryVariables,
} from '../../../../types/graphql';

export const ADMIN_PERMISSION_CATALOGUE = gql`
  query AdminPermissionCatalogue {
    permissions {
      code
      module
      description
      scope
    }
  }
`;

export const ADMIN_ROLE_PERMISSIONS = gql`
  query AdminRolePermissions($role: String!) {
    rolePermissions(role: $role) {
      role
      scope
      permissions {
        code
      }
      switchable {
        code
      }
    }
  }
`;

export type CataloguePermission = AdminPermissionCatalogueQuery['permissions'][number];

export interface RoleToAsk {
  /** The role code identity knows (MANAGER, EVENT_ADMIN, FINANCE ...). */
  role: string;
  /** How to name it in the "held by" column. */
  label: string;
}

/** Roles that carry `code` always, and roles that carry it only where an organization owner switched it on. */
export function heldByText(code: string, carried: ReadonlyMap<string, { always: ReadonlySet<string>; switchable: ReadonlySet<string> }>, roles: readonly RoleToAsk[]): string {
  const always = roles.filter((r) => carried.get(r.role)?.always.has(code)).map((r) => r.label);
  const switchable = roles
    .filter((r) => !carried.get(r.role)?.always.has(code) && carried.get(r.role)?.switchable.has(code))
    .map((r) => `${r.label} (when enabled)`);
  const all = [...always, ...switchable];
  return all.length ? all.join(', ') : '—';
}

export function usePermissionCatalogue(roles: readonly RoleToAsk[]) {
  const client = useApolloClient();
  const catalogue = useQuery<AdminPermissionCatalogueQuery>(ADMIN_PERMISSION_CATALOGUE, { fetchPolicy: 'cache-first', errorPolicy: 'all' });
  const [carried, setCarried] = useState<Map<string, { always: Set<string>; switchable: Set<string> }>>(new Map());
  const [loadingRoles, setLoadingRoles] = useState(false);
  const key = roles.map((r) => r.role).join('|');

  useEffect(() => {
    if (roles.length === 0) return undefined;
    let cancelled = false;
    setLoadingRoles(true);
    void Promise.all(
      roles.map((r) =>
        client
          .query<AdminRolePermissionsQuery, AdminRolePermissionsQueryVariables>({
            query: ADMIN_ROLE_PERMISSIONS,
            variables: { role: r.role },
            fetchPolicy: 'cache-first',
            errorPolicy: 'all',
          })
          .then((res) => [r.role, res.data?.rolePermissions ?? null] as const)
          .catch(() => [r.role, null] as const),
      ),
    ).then((answers) => {
      if (cancelled) return;
      const next = new Map<string, { always: Set<string>; switchable: Set<string> }>();
      for (const [role, rp] of answers) {
        if (rp) next.set(role, { always: new Set(rp.permissions.map((p) => p.code)), switchable: new Set(rp.switchable.map((p) => p.code)) });
      }
      setCarried(next);
      setLoadingRoles(false);
    });
    return () => {
      cancelled = true;
    };
    // `key` stands for the role codes; the labels do not change what is asked.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [client, key]);

  const permissions = catalogue.data?.permissions ?? [];
  const rows = useMemo(
    () => permissions.map((p) => ({ ...p, heldBy: heldByText(p.code, carried, roles) })),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [permissions, carried, key],
  );
  return { rows, loading: catalogue.loading || loadingRoles, error: catalogue.error, refetch: catalogue.refetch };
}
