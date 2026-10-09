package com.pml.identity.organization;

import com.pml.identity.domain.valueobject.MobileMoneyAccount;
import com.pml.identity.domain.valueobject.PayoutBankDetails;
import com.pml.identity.domain.valueobject.PayoutConfig;
import com.pml.identity.service.OrganizationRules;
import com.pml.identity.service.OrganizationRules.PayoutAccountStatus;
import com.pml.shared.constants.OrganizationStatus;
import com.pml.shared.constants.PayoutMethod;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("L1")
@Tag("ET-ORG-001")
@DisplayName("ET-ORG-001-R10..R12 · commission bounds, payout-account state and the deletion rules")
class OrganizationRulesTest {

    @Test
    @DisplayName("a percentage becomes the stored fraction exactly, and back")
    void commissionConversion() {
        assertThat(OrganizationRules.fractionOf(5.0)).isEqualTo(0.05);
        assertThat(OrganizationRules.fractionOf(7.5)).isEqualTo(0.075);
        assertThat(OrganizationRules.fractionOf(0.0)).isEqualTo(0.0);
        assertThat(OrganizationRules.fractionOf(50.0)).isEqualTo(0.5);
        assertThat(OrganizationRules.percentOf(0.075)).isEqualTo(7.5);
        assertThat(OrganizationRules.percentOf(null)).isNull();
    }

    @Test
    @DisplayName("a commission outside 0..50 percent, missing or NaN is refused with CONFIGURATION_VALUE_INVALID")
    void commissionBounds() {
        for (Double bad : new Double[]{-0.1, 50.01, 100.0, Double.NaN, null}) {
            assertThatThrownBy(() -> OrganizationRules.fractionOf(bad))
                    .isInstanceOfSatisfying(DomainRefusal.class,
                            e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CONFIGURATION_VALUE_INVALID));
        }
    }

    @Test
    @DisplayName("business identity fields stay editable only before the application is reviewed")
    void kybEditable() {
        assertThat(OrganizationRules.kybFieldsEditable(OrganizationStatus.DRAFT)).isTrue();
        assertThat(OrganizationRules.kybFieldsEditable(OrganizationStatus.CHANGES_REQUESTED)).isTrue();
        assertThat(OrganizationRules.kybFieldsEditable(OrganizationStatus.PENDING_REVIEW)).isFalse();
        assertThat(OrganizationRules.kybFieldsEditable(OrganizationStatus.ACTIVE)).isFalse();
    }

    @Test
    @DisplayName("a deletion request is refused when one is open or the organization is suspended")
    void deletable() {
        OrganizationRules.requireDeletable(OrganizationStatus.ACTIVE);
        OrganizationRules.requireDeletable(OrganizationStatus.DRAFT);
        assertThatThrownBy(() -> OrganizationRules.requireDeletable(OrganizationStatus.PENDING_DELETION))
                .isInstanceOfSatisfying(DomainRefusal.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.ORGANIZATION_STATE_INVALID));
        assertThatThrownBy(() -> OrganizationRules.requireDeletable(OrganizationStatus.SUSPENDED))
                .isInstanceOf(DomainRefusal.class);
        assertThat(OrganizationRules.deletionDate(Instant.parse("2026-10-04T00:00:00Z")))
                .isEqualTo(Instant.parse("2026-11-03T00:00:00Z"));
    }

    @Test
    @DisplayName("a payout account is NONE, PENDING, VERIFIED, REJECTED or SUSPENDED, in that precedence")
    void accountStatus() {
        assertThat(OrganizationRules.statusOf((PayoutConfig) null)).isEqualTo(PayoutAccountStatus.NONE);

        PayoutBankDetails bank = PayoutBankDetails.builder().accountNumber("enc").build();
        assertThat(OrganizationRules.statusOf(bank)).isEqualTo(PayoutAccountStatus.PENDING);
        bank.setRejectionReason("name does not match");
        assertThat(OrganizationRules.statusOf(bank)).isEqualTo(PayoutAccountStatus.REJECTED);
        bank.setRejectionReason(null);
        bank.setVerified(true);
        assertThat(OrganizationRules.statusOf(bank)).isEqualTo(PayoutAccountStatus.VERIFIED);
        bank.setSuspended(true);
        assertThat(OrganizationRules.statusOf(bank)).as("a freeze outranks verification").isEqualTo(PayoutAccountStatus.SUSPENDED);
    }

    @Test
    @DisplayName("the account a payout would use follows the preferred method, and a freeze stops payouts")
    void activeAccountAndFreeze() {
        PayoutBankDetails bank = PayoutBankDetails.builder().accountNumber("enc").verified(true).build();
        MobileMoneyAccount wallet = MobileMoneyAccount.builder().phoneNumber("+260971234567").build();
        PayoutConfig config = PayoutConfig.builder().preferredMethod(PayoutMethod.MOBILE_MONEY)
                .bankAccount(bank).mobileMoneyAccount(wallet).verified(true).build();

        assertThat(OrganizationRules.activeAccount(config)).isSameAs(wallet);
        config.setPreferredMethod(PayoutMethod.BANK_TRANSFER);
        assertThat(OrganizationRules.activeAccount(config)).isSameAs(bank);

        assertThat(config.canProcessPayouts()).isTrue();
        bank.setSuspended(true);
        assertThat(config.isAccountSuspended()).isTrue();
        assertThat(config.canProcessPayouts()).isFalse();
    }

    @Test
    @DisplayName("a reason is required and bounded")
    void reasons() {
        assertThat(OrganizationRules.requireReason("  name mismatch ")).isEqualTo("name mismatch");
        assertThat(OrganizationRules.requireReason("x".repeat(900))).hasSize(500);
        assertThatThrownBy(() -> OrganizationRules.requireReason(" ")).isInstanceOf(DomainRefusal.class);
        assertThatThrownBy(() -> OrganizationRules.requireReason(null)).isInstanceOf(DomainRefusal.class);
    }
}
