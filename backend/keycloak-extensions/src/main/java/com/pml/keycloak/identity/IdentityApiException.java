package com.pml.keycloak.identity;

/** The identity-service answered with a non-2xx status. Never carries the request or response body. */
public class IdentityApiException extends RuntimeException {

    private final int httpStatus;
    private final transient Dto.Problem problem;

    public IdentityApiException(int httpStatus, Dto.Problem problem) {
        super("identity-service answered " + httpStatus
                + (problem != null && problem.errorCode() != null ? " " + problem.errorCode() : ""));
        this.httpStatus = httpStatus;
        this.problem = problem;
    }

    public int httpStatus() {
        return httpStatus;
    }

    /** May be null when the body was not a problem document. */
    public Dto.Problem problem() {
        return problem;
    }

    public String errorCode() {
        return problem == null ? null : problem.errorCode();
    }

    /** 5xx and 429 are worth retrying for fire-and-forget calls. */
    public boolean retryable() {
        return httpStatus >= 500 || httpStatus == 429 || httpStatus == 408;
    }
}
