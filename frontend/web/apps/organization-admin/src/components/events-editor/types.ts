import type { RefundPolicyOption, TabId } from './model';

/** Props shared by every editor tab. Values and errors come from the surrounding <Form>. */
export interface TabProps {
  /** Start/end are frozen (approved or live): use Reschedule. */
  dateLocked: boolean;
}

export interface ReferenceOptions {
  categories: Array<{ id: string; name: string }>;
  provinces: Array<{ id: string; name: string }>;
  cities: Array<{ id: string; name: string; province: string | null }>;
}

export type GoToTab = (tab: TabId) => void;

/** Platform numbers the editor shows; every field is null/empty while the rules cannot be read. */
export interface EditorRules {
  refundPolicies: RefundPolicyOption[];
  refundCutoffHours: number | null;
  holdMinutes: number | null;
  graceMinutes: number | null;
  maxPerBooking: number | null;
}

/** What the organizer can read of an event's review (timestamps and the reviewer comment). */
export interface ApprovalInfo {
  submittedAt: string | null;
  deadline: string | null;
  approvedAt: string | null;
  rejectedAt: string | null;
  publishAt: string | null;
  publishScheduled: boolean;
}
