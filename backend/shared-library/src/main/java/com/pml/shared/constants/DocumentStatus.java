package com.pml.shared.constants;

/**
 * Status of a verification document.
 */
/*
 * Lives in shared-library so catalog-service's reference-data bootstrapper can
 * reflect over it. Catalog owns the administrator-editable status list and
 * cannot depend on the service that uses the enum.
 */
public enum DocumentStatus {
    /**
     * Document uploaded, awaiting review
     */
    PENDING,

    /**
     * Document verified and approved
     */
    APPROVED,

    /**
     * Document rejected
     */
    REJECTED,

    /**
     * Document has expired
     */
    EXPIRED
}
