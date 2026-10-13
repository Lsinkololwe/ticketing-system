package com.pml.booking.domain;

import com.pml.booking.domain.enums.RecoveryAction;
import com.pml.booking.domain.model.RecoveryProposal;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import com.pml.shared.security.tenancy.StaffRole;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Who may propose and confirm what, and when. Pure, so the maker-checker rule is a unit test and not
 * a convention.
 */
public final class DualControlRules {

    /** A proposal that nobody confirms in this long applies nothing and must be made again. */
    public static final Duration PROPOSAL_TTL = Duration.ofHours(2);

    public static final int MIN_REASON_LENGTH = 20;

    private DualControlRules() {
    }

    /**
     * Whether the held authorities satisfy the role an action needs; ADMIN covers FINANCE, SUPER_ADMIN covers all.
     *
     * <p>This is separation of duties, not a data reach: it is asked of every proposal in a queue and
     * of every row's "can I confirm this" flag, so it is a plain comparison of role names, held in
     * the shared {@link StaffRole} rank rule and not recorded per comparison. What is recorded is the
     * act itself, when a proposal is made and when it is confirmed.
     */
    public static boolean holdsRole(Collection<String> authorities, RecoveryAction action) {
        return StaffRole.covers(Set.copyOf(authorities), action.requiredRole());
    }

    /** Null when {@code reason} is a real justification; otherwise the refusal. */
    public static DomainRefusal checkReason(String reason, String what) {
        if (reason == null || reason.trim().length() < MIN_REASON_LENGTH) {
            return new TranslatedRefusal(ErrorCode.COMMAND_NOT_WELL_FORMED,
                    what + " needs a reason of at least " + MIN_REASON_LENGTH + " characters");
        }
        return null;
    }

    /**
     * Null when {@code confirmer} may confirm {@code proposal} at {@code now}; otherwise why not.
     * The order is the order of disclosure: who you are first, then the state of the proposal.
     */
    public static DomainRefusal checkConfirmation(RecoveryProposal proposal, String confirmer,
                                                  Collection<String> authorities, Instant now) {
        if (confirmer == null || confirmer.equals(proposal.getProposedById())) {
            return new TranslatedRefusal(ErrorCode.ACTOR_NOT_PERMITTED,
                    "a second person must confirm; you cannot confirm your own proposal");
        }
        if (!holdsRole(authorities, proposal.getAction())) {
            return new TranslatedRefusal(ErrorCode.ACTOR_NOT_PERMITTED,
                    "confirming " + proposal.getAction() + " needs the " + proposal.getAction().requiredRole() + " role");
        }
        var status = proposal.statusAt(now);
        if (status != com.pml.booking.domain.enums.RecoveryProposalStatus.PENDING) {
            return new TranslatedRefusal(ErrorCode.TRANSACTION_NOT_RECOVERABLE,
                    "the proposal is " + status, Map.of("currentStatus", status.name()));
        }
        return null;
    }
}
