package com.pml.catalog.service;

import com.pml.catalog.domain.model.PlatformConfiguration;
import com.pml.catalog.web.graphql.dto.UpdatePlatformConfigurationInput;
import com.pml.shared.config.model.PlatformPaymentDefaults;
import com.pml.shared.config.model.PlatformRulesSection;
import com.pml.shared.config.model.RefundPolicyDefinition;
import com.pml.shared.config.model.RefundTier;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies the runtime-rule part of an {@link UpdatePlatformConfigurationInput} to the settings
 * document, enforcing the bounds of ET-ADM-002-R1 before anything is written.
 *
 * <p>Pure: no I/O, so every bound is a layer-1 test. A value outside its bounds refuses with
 * {@code CONFIGURATION_VALUE_INVALID} carrying the {@code constraint} that failed; the document is
 * left untouched because validation finishes before the first assignment.
 *
 * <p>Commission is expressed to administrators as a percentage (5 = 5%) and stored as the fraction
 * the rest of the platform uses (0.05).
 */
public final class PlatformRulesUpdater {

    static final double MAX_COMMISSION_PERCENT = 50.0;
    static final BigDecimal MAX_MINIMUM_PAYOUT = new BigDecimal("100000");

    private PlatformRulesUpdater() {
    }

    /** Validates {@code input} and, only if every value is acceptable, writes it onto {@code config}. */
    public static void apply(PlatformConfiguration config, UpdatePlatformConfigurationInput input) {
        PlatformRulesSection rules = config.getRules() != null
                ? config.getRules() : PlatformRulesSection.defaults();

        // Validate everything first; assign after. A half-applied update is worse than a refused one.
        if (input.getCommissionDefault() != null) {
            BigDecimal percent = input.getCommissionDefault();
            require(percent.signum() >= 0 && percent.compareTo(BigDecimal.valueOf(MAX_COMMISSION_PERCENT)) <= 0, "commissionDefault in [0, 50]");
        }
        if (input.getMinimumPayout() != null) {
            require(input.getMinimumPayout().signum() >= 0
                    && input.getMinimumPayout().compareTo(MAX_MINIMUM_PAYOUT) <= 0, "minimumPayout in [0, 100000]");
        }
        range(input.getReservationHoldMinutes(), 1, 120, "reservationHoldMinutes in [1, 120]");
        range(input.getReservationGraceMinutes(), 0, 60, "reservationGraceMinutes in [0, 60]");
        range(input.getEscrowHoldDays(), 0, 90, "escrowHoldDays in [0, 90]");
        range(input.getRefundCutoffHours(), 0, 720, "refundCutoffHours in [0, 720]");
        range(input.getMaxTicketsPerBooking(), 1, 50, "maxTicketsPerBooking in [1, 50]");
        range(input.getRescheduleLimit(), 0, 20, "rescheduleLimit in [0, 20]");
        range(input.getApprovalSlaHours(), 1, 720, "approvalSlaHours in [1, 720]");
        range(input.getApprovalWarningThresholdHours(), 0, 720, "approvalWarningThresholdHours in [0, 720]");
        range(input.getEscalationDelayHours(), 0, 720, "escalationDelayHours in [0, 720]");
        if (input.getCurrency() != null) {
            require(input.getCurrency().matches("^[A-Z]{3}$"), "currency is an ISO-4217 code");
        }
        int sla = input.getApprovalSlaHours() != null ? input.getApprovalSlaHours() : config.getApprovalSlaHours();
        int warn = input.getApprovalWarningThresholdHours() != null
                ? input.getApprovalWarningThresholdHours() : config.getApprovalWarningThresholdHours();
        require(warn <= sla, "approvalWarningThresholdHours <= approvalSlaHours");
        Map<String, RefundPolicyDefinition> policies = input.getRefundPolicies() == null
                ? null : validatedPolicies(rules, input.getRefundPolicies());

        // All good: assign.
        if (input.getCommissionDefault() != null || input.getMinimumPayout() != null) {
            PlatformPaymentDefaults payment = config.getPayment() != null
                    ? config.getPayment() : PlatformPaymentDefaults.builder().build();
            if (input.getCommissionDefault() != null) {
                payment.setCommissionRate(input.getCommissionDefault()
                        .movePointLeft(2).doubleValue());
            }
            if (input.getMinimumPayout() != null) {
                payment.setMinimumPayoutAmount(com.pml.shared.constants.Money.round(input.getMinimumPayout()));
            }
            config.setPayment(payment);
        }
        if (input.getReservationHoldMinutes() != null) rules.setReservationHoldMinutes(input.getReservationHoldMinutes());
        if (input.getReservationGraceMinutes() != null) rules.setReservationGraceMinutes(input.getReservationGraceMinutes());
        if (input.getEscrowHoldDays() != null) rules.setEscrowHoldDays(input.getEscrowHoldDays());
        if (input.getRefundCutoffHours() != null) rules.setRefundCutoffHours(input.getRefundCutoffHours());
        if (input.getMaxTicketsPerBooking() != null) rules.setMaxTicketsPerBooking(input.getMaxTicketsPerBooking());
        if (input.getRescheduleLimit() != null) rules.setRescheduleLimit(input.getRescheduleLimit());
        if (input.getCurrency() != null) rules.setCurrency(input.getCurrency());
        if (policies != null) rules.setRefundPolicies(policies);
        rules.setVersion(rules.getVersion() + 1);
        config.setRules(rules);
    }

    private static Map<String, RefundPolicyDefinition> validatedPolicies(
            PlatformRulesSection current, List<UpdatePlatformConfigurationInput.RefundPolicyInput> inputs) {
        Map<String, RefundPolicyDefinition> merged = new LinkedHashMap<>(
                current.getRefundPolicies() == null ? Map.of() : current.getRefundPolicies());
        for (var in : inputs) {
            require(in.getCode() != null && PlatformRulesSection.POLICY_CODES.contains(in.getCode()),
                    "refund policy code is one of " + PlatformRulesSection.POLICY_CODES);
            RefundPolicyDefinition existing = merged.get(in.getCode());
            String label = in.getLabel() != null ? in.getLabel() : existing != null ? existing.getLabel() : null;
            String summary = in.getSummary() != null ? in.getSummary() : existing != null ? existing.getSummary() : null;
            require(label != null && !label.isBlank() && label.length() <= 40, "refund policy label 1..40 characters");
            require(summary != null && !summary.isBlank() && summary.length() <= 200,
                    "refund policy summary 1..200 characters");
            List<RefundTier> tiers = in.getRules() != null
                    ? in.getRules().stream().map(r -> new RefundTier(r.getDaysBefore(), r.getPercent())).toList()
                    : existing != null ? existing.getRules() : List.of();
            validateTiers(in.getCode(), tiers);
            merged.put(in.getCode(), RefundPolicyDefinition.builder()
                    .label(label.trim()).summary(summary.trim()).rules(new ArrayList<>(tiers)).build());
        }
        for (String code : PlatformRulesSection.POLICY_CODES) {
            require(merged.containsKey(code), "refund policy " + code + " is defined");
        }
        require(merged.get("NO_REFUNDS").getRules().isEmpty(), "NO_REFUNDS has no refund tiers");
        return merged;
    }

    /** Percent in [0,100], days in [0,365] and distinct; a later tier never refunds more than an earlier one. */
    static void validateTiers(String code, List<RefundTier> tiers) {
        List<RefundTier> ordered = tiers.stream()
                .sorted(Comparator.comparingInt(RefundTier::getDaysBefore).reversed()).toList();
        int previousPercent = 100;
        Integer previousDays = null;
        for (RefundTier tier : ordered) {
            require(tier.getDaysBefore() >= 0 && tier.getDaysBefore() <= 365,
                    code + " tier daysBefore in [0, 365]");
            require(tier.getPercent() >= 0 && tier.getPercent() <= 100, code + " tier percent in [0, 100]");
            require(previousDays == null || previousDays != tier.getDaysBefore(),
                    code + " tiers have distinct daysBefore");
            require(tier.getPercent() <= previousPercent,
                    code + " refund never increases closer to the event");
            previousPercent = tier.getPercent();
            previousDays = tier.getDaysBefore();
        }
    }

    private static void range(Integer value, int min, int max, String constraint) {
        if (value != null) {
            require(value >= min && value <= max, constraint);
        }
    }

    private static void require(boolean ok, String constraint) {
        if (!ok) {
            throw new TranslatedRefusal(ErrorCode.CONFIGURATION_VALUE_INVALID,
                    "platform configuration value out of bounds: " + constraint,
                    Map.of("constraint", constraint));
        }
    }
}
