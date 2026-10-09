package com.pml.identity.web.graphql.resolver;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.domain.valueobject.MobileMoneyAccount;
import com.pml.identity.domain.valueobject.PayoutBankDetails;
import com.pml.identity.security.FieldEncryptionService;
import com.pml.identity.service.OrganizationRules;
import com.pml.identity.service.OrganizationRules.PayoutAccountStatus;
import com.pml.identity.validation.FinancialDataValidator;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Organization fields that are derived or private: the commission the organization is charged
 * (as a percentage), why it was suspended, the deletion request, and the review state of its
 * payout accounts. Also the account number, which the document stores encrypted and the schema
 * promises to show masked.
 */
@DgsComponent
@RequiredArgsConstructor
public class OrganizationAdminFields {

    private final FieldEncryptionService encryption;

    /** The commission this organization is charged, as a percentage; null until one is configured. */
    @DgsData(parentType = "Organization", field = "commissionRate")
    public Mono<Double> commissionRate(DgsDataFetchingEnvironment env) {
        return OrganizationPrivateFields.forMembers(env, org -> org.getPayoutConfig() == null ? null
                : OrganizationRules.percentOf(org.getPayoutConfig().getCommissionRate()));
    }

    @DgsData(parentType = "Organization", field = "suspensionReason")
    public Mono<String> suspensionReason(DgsDataFetchingEnvironment env) {
        return OrganizationPrivateFields.forMembers(env, Organization::getSuspensionReason);
    }

    @DgsData(parentType = "Organization", field = "suspendedAt")
    public Mono<Instant> suspendedAt(DgsDataFetchingEnvironment env) {
        return OrganizationPrivateFields.forMembers(env, Organization::getSuspendedAt);
    }

    @DgsData(parentType = "Organization", field = "deletionRequestedAt")
    public Mono<Instant> deletionRequestedAt(DgsDataFetchingEnvironment env) {
        return OrganizationPrivateFields.forMembers(env, Organization::getDeletionRequestedAt);
    }

    @DgsData(parentType = "Organization", field = "deletionScheduledFor")
    public Mono<Instant> deletionScheduledFor(DgsDataFetchingEnvironment env) {
        return OrganizationPrivateFields.forMembers(env, Organization::getDeletionScheduledFor);
    }

    // ---- payout accounts ---------------------------------------------------------------------------

    @DgsData(parentType = "PayoutBankDetails", field = "accountNumber")
    public Mono<String> accountNumber(DgsDataFetchingEnvironment env) {
        return masked(env.<PayoutBankDetails>getSource());
    }

    @DgsData(parentType = "PayoutBankDetails", field = "maskedAccountNumber")
    public Mono<String> maskedAccountNumber(DgsDataFetchingEnvironment env) {
        return masked(env.<PayoutBankDetails>getSource());
    }

    @DgsData(parentType = "PayoutBankDetails", field = "status")
    public PayoutAccountStatus bankStatus(DgsDataFetchingEnvironment env) {
        return OrganizationRules.statusOf(env.<PayoutBankDetails>getSource());
    }

    @DgsData(parentType = "MobileMoneyAccount", field = "status")
    public PayoutAccountStatus walletStatus(DgsDataFetchingEnvironment env) {
        return OrganizationRules.statusOf(env.<MobileMoneyAccount>getSource());
    }

    private Mono<String> masked(PayoutBankDetails bank) {
        if (bank == null || bank.getAccountNumber() == null) {
            return Mono.empty();
        }
        return encryption.decrypt(bank.getAccountNumber())
                .map(FinancialDataValidator::maskAccountNumber)
                .onErrorReturn("****");
    }
}
