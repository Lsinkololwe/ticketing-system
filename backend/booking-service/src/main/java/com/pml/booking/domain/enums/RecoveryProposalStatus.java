package com.pml.booking.domain.enums;

public enum RecoveryProposalStatus {
    PENDING,
    CONFIRMED,
    WITHDRAWN,
    /** Stored never; read from a pending proposal whose time has passed. */
    EXPIRED,
    /** Confirmed, but the action itself was refused; it applied nothing and must be proposed again. */
    FAILED
}
