/**
 * Shapes of identity-service's contact GraphQL operations (CONTRACT.md section 14,
 * ET-IDN-004 R3/R5), as the browser sees them after the BFF whitelists them.
 */
export type ContactKind = "WHATSAPP" | "EMAIL";
export type ChangeKind = "ADD" | "CHANGE" | "REMOVE" | "PRIMARY";

export interface ContactView {
  id: string;
  type: ContactKind;
  valueMasked: string;
  verifiedAt: string | null;
  primary: boolean;
  createdAt?: string | null;
}

/** The one change waiting for its codes (or finishing), if any. Masked values only. */
export interface PendingChange {
  changeId: string;
  kind: ChangeKind;
  newContactMasked: string | null;
  expiresAt: string;
  /** The code sent to the current primary contact was already accepted. */
  currentContactVerified: boolean;
  attemptsRemaining: number;
}

export interface MyContactsView {
  contacts: ContactView[];
  pendingChange: PendingChange | null;
}

export interface CodeChallenge {
  challengeId: string;
  contactType: ContactKind;
  maskedContact: string;
  expiresInSeconds: number;
  resendAfterSeconds: number;
}

export interface ChangeChallenges {
  changeId: string;
  /** Code sent to the new contact. */
  newContact: CodeChallenge;
  /** Code sent to the current primary contact. */
  currentContact: CodeChallenge;
  expiresAt: string;
}

/** COMPLETED: done. APPLYING: codes were right, finishing in the background; ask myContacts again. */
export type OpStatus = "COMPLETED" | "APPLYING";
export interface OpResult {
  changeId: string;
  kind: ChangeKind;
  status: OpStatus;
}
