package com.pml.shared.error;

/**
 * The eight families every refusal belongs to.
 *
 * <p>A client that has never heard of a particular code can still act correctly
 * on its classification — which is what keeps the frontend from needing a branch
 * per code, and what stops a newly added refusal rendering as a blank panel.</p>
 */
public enum ErrorClassification {

    /** The request is malformed. */
    BAD_REQUEST,

    /** No valid identity. */
    UNAUTHENTICATED,

    /**
     * Identity known, role insufficient, resource <b>within the caller's tenant</b>.
     *
     * <p>The tenant qualifier is the whole point. Returning this for a resource
     * outside the caller's tenant confirms that the resource exists, which turns
     * every error response into an enumeration oracle — so those return
     * {@code *_UNKNOWN} instead.</p>
     */
    PERMISSION_DENIED,

    /** Absent — or another tenant's, which must be indistinguishable. */
    NOT_FOUND,

    /** Well formed, but the world is not in the required state. */
    FAILED_PRECONDITION,

    /** A dependency is down, or the caller is throttled. */
    UNAVAILABLE,

    /** A defect. Never carries detail across the boundary. */
    INTERNAL,

    /** Never used deliberately; the fallback when nothing else fits. */
    UNKNOWN
}
