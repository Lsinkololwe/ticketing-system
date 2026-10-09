package com.pml.identity.service;

import com.pml.identity.domain.model.Organization;
import com.pml.identity.domain.valueobject.MobileMoneyAccount;
import com.pml.identity.domain.valueobject.PayoutBankDetails;
import com.pml.identity.repository.OrganizationRepository;
import com.pml.identity.security.FieldEncryptionService;
import com.pml.identity.service.OrganizationRules.PayoutAccountStatus;
import com.pml.identity.validation.FinancialDataValidator;
import com.pml.identity.web.graphql.dto.platform.PayoutAccountFilterInput;
import com.pml.identity.web.graphql.dto.platform.PayoutAccountRecord;
import com.pml.shared.constants.PayoutMethod;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.Locale;

/**
 * The admin review table of payout accounts. The account lives inside its organization's
 * document, so this derives one row per organization that has one on file; the number is
 * decrypted only to be masked and never leaves this class in the clear.
 */
@Service
@RequiredArgsConstructor
public class PayoutAccountQueryService {

    private final OrganizationRepository organizations;
    private final FieldEncryptionService encryption;

    public Flux<PayoutAccountRecord> list(PayoutAccountFilterInput filter) {
        return organizations.findAll()
                .filter(org -> OrganizationRules.statusOf(org.getPayoutConfig()) != PayoutAccountStatus.NONE)
                .filter(org -> matches(org, filter))
                .concatMap(this::record)
                .sort(Comparator.comparing(PayoutAccountRecord::updatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())));
    }

    static boolean matches(Organization org, PayoutAccountFilterInput filter) {
        if (filter == null) {
            return true;
        }
        if (filter.status() != null && OrganizationRules.statusOf(org.getPayoutConfig()) != filter.status()) {
            return false;
        }
        if (filter.method() != null && org.getPayoutConfig().getPreferredMethod() != filter.method()) {
            return false;
        }
        if (filter.search() != null && !filter.search().isBlank()) {
            String needle = filter.search().toLowerCase(Locale.ROOT);
            return (org.getName() != null && org.getName().toLowerCase(Locale.ROOT).contains(needle))
                    || (org.getSlug() != null && org.getSlug().toLowerCase(Locale.ROOT).contains(needle));
        }
        return true;
    }

    private Mono<PayoutAccountRecord> record(Organization org) {
        Object account = OrganizationRules.activeAccount(org.getPayoutConfig());
        PayoutAccountStatus status = OrganizationRules.statusOf(org.getPayoutConfig());
        if (account instanceof PayoutBankDetails bank) {
            return maskedNumber(bank.getAccountNumber()).map(masked -> new PayoutAccountRecord(
                    org.getId(), org.getName(), org.getSlug(), PayoutMethod.BANK_TRANSFER, status,
                    bank.getBankName(), masked, bank.getAccountHolderName(), null, null,
                    bank.getRejectionReason(), bank.getSuspendedReason(), bank.getTestDepositSentAt(),
                    bank.getVerificationAttemptsLeft(), org.getUpdatedAt()));
        }
        MobileMoneyAccount wallet = (MobileMoneyAccount) account;
        return Mono.just(new PayoutAccountRecord(
                org.getId(), org.getName(), org.getSlug(), PayoutMethod.MOBILE_MONEY, status,
                null, null, wallet.getAccountHolderName(), wallet.getProvider(), wallet.getMaskedPhoneNumber(),
                wallet.getRejectionReason(), wallet.getSuspendedReason(), wallet.getTestDepositSentAt(),
                wallet.getVerificationAttemptsLeft(), org.getUpdatedAt()));
    }

    /** The last four digits of the stored (encrypted) number; a number that cannot be decrypted shows as fully masked. */
    Mono<String> maskedNumber(String stored) {
        if (stored == null) {
            return Mono.just("****");
        }
        return encryption.decrypt(stored)
                .map(FinancialDataValidator::maskAccountNumber)
                .onErrorReturn("****");
    }
}
