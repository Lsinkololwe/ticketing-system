package com.pml.identity.account;

/** What a contact change does (ET-IDN-004 R3, R5). */
public enum ContactChangeKind {
    /** A new verified contact joins the account. */
    ADD,
    /** One contact is replaced by another of the same type; the old one is released. */
    CHANGE,
    /** A contact is released; the last verified one cannot be. */
    REMOVE,
    /** Another verified contact becomes the primary one. */
    PRIMARY
}
