package com.pml.identity.platform;

import com.pml.identity.service.PlatformRulesAssembler;
import com.pml.identity.web.graphql.dto.platform.PlatformRules;
import com.pml.shared.config.model.PlatformRulesSection;
import com.pml.shared.config.model.PlatformRulesView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("L1")
@Tag("ET-ADM-002")
@DisplayName("ET-ADM-002-R9 · platformRules: percentages, the caller's own commission, ordered policies")
class PlatformRulesAssemblerTest {

    private static PlatformRulesView view() {
        return PlatformRulesView.builder()
                .rules(PlatformRulesSection.defaults())
                .commissionRate(0.05)
                .minimumPayout(new BigDecimal("100.00"))
                .approvalSlaHours(48).approvalWarningThresholdHours(36).escalationDelayHours(12)
                .autoEscalationEnabled(true).requireCommentsOnRejection(true)
                .requireCommentsOnChangesRequested(true).allowSelfApproval(false)
                .updatedAt(Instant.parse("2026-10-03T09:00:00Z")).updatedBy("admin-1")
                .build();
    }

    @Test
    @DisplayName("without an organization override the default applies to the caller, as a percentage")
    void defaultCommission() {
        PlatformRules rules = PlatformRulesAssembler.assemble(view(), null, "Natasha Mulenga");

        assertThat(rules.commissionDefault()).isEqualTo(5.0);
        assertThat(rules.commissionRate()).isEqualTo(5.0);
        assertThat(rules.updatedBy()).isEqualTo("Natasha Mulenga");
        assertThat(rules.version()).isEqualTo(1);
    }

    @Test
    @DisplayName("an organization's own rate replaces the default for that caller only")
    void ownCommission() {
        PlatformRules rules = PlatformRulesAssembler.assemble(view(), 0.075, null);

        assertThat(rules.commissionRate()).isEqualTo(7.5);
        assertThat(rules.commissionDefault()).isEqualTo(5.0);
        assertThat(rules.updatedBy()).as("an id is never shown in place of a name").isNull();
    }

    @Test
    @DisplayName("the four refund policies come in the fixed order with their tiers, longest notice first")
    void policies() {
        PlatformRules rules = PlatformRulesAssembler.assemble(view(), null, null);

        assertThat(rules.refundPolicies()).extracting(p -> p.code())
                .containsExactly("FLEXIBLE", "MODERATE", "STRICT", "NO_REFUNDS");
        assertThat(rules.refundPolicies().get(1).rules()).extracting(t -> t.daysBefore()).containsExactly(7, 1);
        assertThat(rules.refundPolicies().get(3).rules()).isEmpty();
    }

    @Test
    @DisplayName("the approval settings and the runtime values are carried through")
    void carriesValues() {
        PlatformRules rules = PlatformRulesAssembler.assemble(view(), null, null);

        assertThat(rules.approval().slaHours()).isEqualTo(48);
        assertThat(rules.approval().warnHours()).isEqualTo(36);
        assertThat(rules.reservationHoldMinutes()).isEqualTo(10);
        assertThat(rules.maxTicketsPerBooking()).isEqualTo(8);
        assertThat(rules.minimumPayout()).isEqualByComparingTo("100");
        assertThat(rules.currency()).isEqualTo("ZMW");
    }

    @Test
    @DisplayName("ET-ADM-002-R10 · the public subset carries buyer values and none of the organizer-only ones")
    void publicSubset() {
        var rules = PlatformRulesAssembler.assemblePublic(view());

        assertThat(rules.version()).isEqualTo(1);
        assertThat(rules.updatedAt()).isEqualTo(Instant.parse("2026-10-03T09:00:00Z"));
        assertThat(rules.currency()).isEqualTo("ZMW");
        assertThat(rules.reservationHoldMinutes()).isEqualTo(10);
        assertThat(rules.maxTicketsPerBooking()).isEqualTo(8);
        assertThat(rules.refundPolicies()).extracting(p -> p.code())
                .containsExactly("FLEXIBLE", "MODERATE", "STRICT", "NO_REFUNDS");
        assertThat(java.util.Arrays.stream(rules.getClass().getRecordComponents()).map(c -> c.getName()))
                .as("no commission, payout, escrow, approval or administrator field exists on the type")
                .containsExactlyInAnyOrder("version", "updatedAt", "currency", "reservationHoldMinutes",
                        "reservationGraceMinutes", "maxTicketsPerBooking", "refundCutoffHours",
                        "rescheduleLimit", "refundPolicies");
    }
}
