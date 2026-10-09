package com.pml.booking.domain;

import com.pml.booking.domain.enums.RecoveryAction;
import com.pml.booking.domain.model.RecoveryProposal;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;

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

    /** Whether the held authorities satisfy the role an action needs; ADMIN covers FINANCE, SUPER_ADMIN covers all. */
    public static boolean holdsRole(Collection<String> authorities, RecoveryAction action) {
        Set<String> held = Set.copyOf(authorities);
        if (held.contains("ROLE_SUPER_ADMIN")) {
            return true;
        }
        return switch (action.requiredRole()) {
            case "SUPER_ADMIN" -> false;
            case "FINANCE" -> held.contains("ROLE_FINANCE") || held.contains("ROLE_ADMIN");
            default -> held.contains("ROLE_" + action.requiredRole());
        };
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
