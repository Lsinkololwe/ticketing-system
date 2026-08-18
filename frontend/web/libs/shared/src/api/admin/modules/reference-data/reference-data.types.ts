/**
 * Reference Data Type Definitions (Admin)
 *
 * Re-exports the generated GraphQL types. Nothing here is hand-authored.
 *
 * <h2>Why this file was rewritten</h2>
 * It used to declare `ReferenceType` as a hand-written union of seventeen
 * members — every taxonomy type, and not one of the eleven workflow types. The
 * admin app therefore could not name `PAYOUT_STATUS` or `TICKET_STATUS` at all,
 * so the configurable-status feature the backend had built was structurally
 * unreachable from the UI. `ReferenceData` was likewise missing `semantic` and
 * `allowedTransitions`, the two fields that make a status configurable rather
 * than merely listed.
 *
 * None of that was visible as an error. A hand-authored type that omits members
 * still compiles; it just silently narrows what the app can express, and the
 * gap only shows up as a feature that appears not to exist.
 *
 * @see backend/catalog-service/src/main/resources/graphql/schema.graphqls
 * @see libs/shared/src/types/graphql/index.ts
 */

// ==========================================
// Re-export GraphQL Types
// ==========================================

export type {
  ReferenceData,
  ReferenceType,
  ReferenceTypeInfo,
  ReferenceDataOffsetPage,
  ReferenceDataMutationResponse,
  CreateReferenceDataInput,
  UpdateReferenceDataInput,
  DeleteMutationResponse,
  WorkflowSemantic,
} from '../../../../types/graphql';

import type { ReferenceData, WorkflowSemantic } from '../../../../types/graphql';

// ==========================================
// UI Helpers
// ==========================================

/** Radix color for a reference-data row's active state. */
export function getActiveColor(isActive: boolean): string {
  return isActive ? 'green' : 'gray';
}

/** Convert a list of reference rows to SearchableSelect options. */
export function toSelectOptions(
  items: ReferenceData[]
): { value: string; label: string }[] {
  return items.map((item) => ({ value: item.code, label: item.name }));
}

/**
 * The six meanings a status may carry.
 *
 * Derived from the generated `WorkflowSemantic` union rather than typed out, so
 * a seventh meaning added to the backend appears in the picker on the next
 * codegen instead of being quietly unofferable.
 */
export const WORKFLOW_SEMANTICS: WorkflowSemantic[] = [
  'INITIAL',
  'PENDING',
  'IN_PROGRESS',
  'SUCCEEDED',
  'FAILED',
  'CANCELLED',
];

/**
 * What each meaning does to a record, in the words an administrator needs.
 *
 * The picker is the one moment where choosing wrong is invisible and expensive:
 * a payout status filed as SUCCEEDED when it should be PENDING stops appearing
 * in the queue somebody works through.
 */
export const SEMANTIC_HELP: Record<WorkflowSemantic, string> = {
  INITIAL: 'Just created. Nothing has happened to it yet.',
  PENDING: 'Waiting on a person — it appears in someone’s queue.',
  IN_PROGRESS: 'In flight, waiting on a system rather than a person.',
  SUCCEEDED: 'Completed successfully. Counts as a finished record.',
  FAILED: 'Ended badly. Reporting counts it as a loss.',
  CANCELLED: 'Ended deliberately. Not a failure, not a success.',
};

/** Radix colour per meaning, so the same status reads the same on every screen. */
export const SEMANTIC_COLOR: Record<WorkflowSemantic, string> = {
  INITIAL: 'gray',
  PENDING: 'amber',
  IN_PROGRESS: 'blue',
  SUCCEEDED: 'green',
  FAILED: 'red',
  CANCELLED: 'gray',
};

/**
 * Types whose transitions are fixed by a state machine in code.
 *
 * Mirrors `ReferenceType.isCodeOwnedMachine()`. The backend refuses transition
 * edits for these; the UI hides the editor and says why, so an administrator
 * meets the explanation before the refusal rather than after it.
 *
 * - TICKET_STATUS — `TicketStateMachine`, ET-TKT-002 §4
 * - RESERVATION_STATUS — `ReservationStateMachine`, ET-TKT-001 R6
 * - EVENT_STATUS — the event lifecycle table, ET-CAT-001
 */
export const CODE_OWNED_MACHINES = [
  'TICKET_STATUS',
  'RESERVATION_STATUS',
  'EVENT_STATUS',
] as const;
