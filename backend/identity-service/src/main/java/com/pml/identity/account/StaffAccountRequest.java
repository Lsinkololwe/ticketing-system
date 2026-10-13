package com.pml.identity.account;

import com.pml.shared.constants.UserType;

/**
 * What a super administrator asked for when opening a staff account: who the person is, the one staff
 * role they are to hold, and who is asking. The first password is made by the system, never supplied.
 *
 * @param phoneNumber optional mobile number of the staff member
 * @param actorId     the administrator's id, recorded as the account's creator and on the role grant
 */
public record StaffAccountRequest(String email, String firstName, String lastName,
                                  String phoneNumber, UserType role, String actorId) {

    /** Personal details are never printed, so a request in a log line or an assertion message cannot leak them. */
    @Override
    public String toString() {
        return "StaffAccountRequest[role=" + role + ", actor=" + actorId + "]";
    }
}
