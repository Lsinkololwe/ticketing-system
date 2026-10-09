package com.pml.identity.service;

import com.pml.identity.web.graphql.dto.platform.PlatformRules;
import com.pml.identity.web.graphql.dto.platform.PlatformRulesApproval;
import com.pml.identity.web.graphql.dto.platform.PublicPlatformRules;
import com.pml.identity.web.graphql.dto.platform.RulesRefundPolicy;
import com.pml.identity.web.graphql.dto.platform.RulesRefundTier;
import com.pml.shared.config.model.PlatformRulesSection;
import com.pml.shared.config.model.PlatformRulesView;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the settings document's read model into the shape the organizer and buyer apps receive.
 * Pure, so the conversions (fraction to percentage, policy order, which commission applies) are
 * layer-1 tests.
 */
public final class PlatformRulesAssembler {

    private PlatformRulesAssembler() {
    }

    /**
     * @param organizationFraction the caller's organization's own commission as a fraction, or null
     *                             when they have none (or no organization): the default applies
     * @param updatedByName        the administrator's display name, never an email or id
     */
    public static PlatformRules assemble(PlatformRulesView view, Double organizationFraction, String updatedByName) {
        PlatformRulesSection rules = view.getRules();
        List<RulesRefundPolicy> policies = policies(rules);
        double defaultPercent = OrganizationRules.percentOf(view.getCommissionRate()) == null
                ? 0.0 : OrganizationRules.percentOf(view.getCommissionRate());
        Double applies = organizationFraction != null
                ? OrganizationRules.percentOf(organizationFraction) : defaultPercent;
        return new PlatformRules(
                rules.getVersion(), view.getUpdatedAt(), updatedByName, rules.getCurrency(),
                defaultPercent, applies, view.getMinimumPayout(),
                rules.getReservationHoldMinutes(), rules.getReservationGraceMinutes(),
                rules.getEscrowHoldDays(), rules.getRefundCutoffHours(),
                rules.getMaxTicketsPerBooking(), rules.getRescheduleLimit(), policies,
                new PlatformRulesApproval(view.getApprovalSlaHours(), view.getApprovalWarningThresholdHours(),
                        view.isAutoEscalationEnabled(), view.getEscalationDelayHours(),
                        view.isRequireCommentsOnRejection(), view.isRequireCommentsOnChangesRequested(),
                        view.isAllowSelfApproval()));
    }

    /** The signed-out subset: buyer-facing limits and refund policies only. */
    public static PublicPlatformRules assemblePublic(PlatformRulesView view) {
        PlatformRulesSection rules = view.getRules();
        return new PublicPlatformRules(
                rules.getVersion(), view.getUpdatedAt(), rules.getCurrency(),
                rules.getReservationHoldMinutes(), rules.getReservationGraceMinutes(),
                rules.getMaxTicketsPerBooking(), rules.getRefundCutoffHours(),
                rules.getRescheduleLimit(), policies(rules));
    }

    private static List<RulesRefundPolicy> policies(PlatformRulesSection rules) {
        List<RulesRefundPolicy> policies = new ArrayList<>();
        for (String code : PlatformRulesSection.POLICY_CODES) {
            var policy = rules.getRefundPolicies() == null ? null : rules.getRefundPolicies().get(code);
            if (policy != null) {
                policies.add(new RulesRefundPolicy(code, policy.getLabel(), policy.getSummary(),
                        policy.getRules().stream()
                                .sorted(java.util.Comparator.comparingInt(
                                        com.pml.shared.config.model.RefundTier::getDaysBefore).reversed())
                                .map(t -> new RulesRefundTier(t.getDaysBefore(), t.getPercent())).toList()));
            }
        }
        return policies;
    }
}
