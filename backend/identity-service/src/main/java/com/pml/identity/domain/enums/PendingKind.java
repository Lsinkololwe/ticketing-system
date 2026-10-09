package com.pml.identity.domain.enums;

/** Transient marker for a multi-step change in flight on an account (CONTRACT 2). */
public enum PendingKind {
    MERGING,
    CHANGING,
    DELETION_REQUESTED
}
