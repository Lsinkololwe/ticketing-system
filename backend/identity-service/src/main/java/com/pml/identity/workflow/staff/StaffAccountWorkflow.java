package com.pml.identity.workflow.staff;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * Opens a platform staff account, so that the person exists in the login system and in the platform's
 * records together or not at all.
 *
 * <p>Addressed as {@code staff-create/{staffKey}} - the keyed hash of the person's sign-in address, never
 * the address - and started with conflict policy {@code USE_EXISTING}: two requests for the same person
 * (a double click, or a retry after the first answer was lost) meet one execution and so one account.</p>
 *
 * <pre>
 *   claim (record, PROVISIONING, with the role) -> createInKeycloak (unable to sign in) -> link (record takes
 *   the Keycloak id) -> grantRole -> activate (sign-in opened, ACTIVE) -> notify
 * </pre>
 *
 * <p>A transient failure is retried; a step that stays refused or unreachable past its attempts is undone
 * in reverse, the Keycloak user this process made and then the record, and the request is refused with
 * the reason. The input holds the sealed request, never a readable address, name or password, and no
 * step's result carries the temporary password either: it is applied inside the Keycloak step and the
 * only copy that leaves the process is in the response of the call that asked for the account.</p>
 */
@WorkflowInterface
public interface StaffAccountWorkflow {

    @WorkflowMethod
    Created run(Request request);

    @QueryMethod
    Progress progress();

    /**
     * @param creationId the opaque id derived from the person's address key, for the search attributes
     * @param sealed     the encrypted request: address, names, number, role, administrator, temporary password
     * @param attemptId  a random id of the call that started this execution, not a secret; the result
     *                   hands it back so a call can tell whether its own password is the one that was applied
     */
    record Request(String creationId, String sealed, String attemptId) {
        @Override
        public String toString() {
            return "Request[" + creationId + "]";
        }
    }

    /**
     * The id of the finished account, and the attempt whose password was applied: null when the account
     * was already finished before this execution did anything, so no password was issued by it. The
     * password itself is never part of a result.
     */
    record Created(String accountId, String attemptId) {
    }

    /** {@code step}: the last step that completed. */
    record Progress(String accountId, String step) {
    }
}
