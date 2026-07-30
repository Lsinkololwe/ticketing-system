/**
 * Reference Data Module
 *
 * One polymorphic lookup/catalog store for every business-editable list (mobile-money operators,
 * banks, currencies, event genres, KYB document types, reason codes, tax rates…).
 *
 * @example Public dropdown
 * ```tsx
 * import { useReferenceData } from '@pml.tickets/shared/api/admin/modules/reference-data';
 * const { items: banks } = useReferenceData('BANK');
 * ```
 *
 * @example Admin management
 * ```tsx
 * import {
 *   useReferenceTypes, useReferenceDataAdmin,
 *   useCreateReferenceData, useToggleReferenceDataActive,
 *   type ReferenceType,
 * } from '@pml.tickets/shared/api/admin/modules/reference-data';
 * ```
 */

export * from './reference-data.types';
export * from './reference-data.queries';
export * from './reference-data.mutations';
export * from './reference-data.hooks';
