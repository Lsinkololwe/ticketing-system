package com.pml.booking.service;

import com.pml.booking.domain.model.BankAccount;
import com.pml.booking.domain.model.BankAccount.VerificationStatus;
import com.pml.booking.workflow.bank.BankVerificationRules;
import com.pml.booking.workflow.bank.BankVerificationWorkflow.View;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

/**
 * The MongoDB half of micro-deposit verification.
 *
 * <p>{@code isVerified} and {@code verificationStatus} are always written together, so the payout
 * path's check on the flag and the workbench's reading of the status cannot disagree.
 */
@Service
public class BankVerificationService {

    private final ReactiveMongoTemplate template;
    private final Clock clock;

    public BankVerificationService(ReactiveMongoTemplate template, Clock clock) {
        this.template = template;
        this.clock = clock;
    }

    /** Records the deposit to be sent. A verification already under way keeps its first amount. */
    public Mono<View> prepare(String bankAccountId, BigDecimal depositAmount) {
        return load(bankAccountId).flatMap(account -> {
            if (account.getVerificationStatus() == VerificationStatus.VERIFYING && account.getMicroDepositAmount() != null
                    || account.getVerificationStatus() == VerificationStatus.VERIFIED) {
                return Mono.just(account);
            }
            account.setVerificationStatus(VerificationStatus.VERIFYING);
            account.setVerified(false);
            account.setMicroDepositAmount(depositAmount);
            account.setVerificationAttempts(0);
            account.setVerificationLockedUntil(null);
            return template.save(account);
        }).map(BankVerificationService::view);
    }

    public Mono<View> confirm(String bankAccountId, String actorId, BigDecimal amount) {
        return load(bankAccountId).flatMap(account -> {
            if (account.getVerificationStatus() == VerificationStatus.VERIFIED) {
                return Mono.just(account);
            }
            if (account.getVerificationStatus() != VerificationStatus.VERIFYING) {
                return Mono.error(new TranslatedRefusal(ErrorCode.BANK_ACCOUNT_NOT_VERIFIED,
                        "no verification deposit is awaiting confirmation"));
            }
            Instant now = clock.instant();
            if (account.getVerificationLockedUntil() != null && account.getVerificationLockedUntil().isAfter(now)) {
                return Mono.error(new TranslatedRefusal(ErrorCode.BANK_ACCOUNT_NOT_VERIFIED,
                        "verification is locked until " + account.getVerificationLockedUntil()));
            }
            if (BankVerificationRules.matches(account.getMicroDepositAmount(), amount)) {
                account.setVerificationStatus(VerificationStatus.VERIFIED);
                account.setVerified(true);
                account.setVerifiedAt(now);
                account.setVerifiedBy(actorId);
                account.setVerificationAttempts(0);
            } else {
                int attempts = account.getVerificationAttempts() + 1;
                account.setVerificationAttempts(attempts);
                if (BankVerificationRules.locksAfter(attempts)) {
                    account.setVerificationLockedUntil(now.plus(BankVerificationRules.LOCKOUT));
                }
            }
            return template.save(account);
        }).map(BankVerificationService::view);
    }

    public Mono<View> unlock(String bankAccountId) {
        return load(bankAccountId).flatMap(account -> {
            account.setVerificationAttempts(0);
            account.setVerificationLockedUntil(null);
            return template.save(account);
        }).map(BankVerificationService::view);
    }

    /** An unconfirmed deposit lapses; the owner starts again, and a new deposit is sent. */
    public Mono<View> expire(String bankAccountId) {
        return load(bankAccountId).flatMap(account -> {
            if (account.getVerificationStatus() != VerificationStatus.VERIFYING) {
                return Mono.just(account);
            }
            account.setVerificationStatus(VerificationStatus.PENDING);
            account.setMicroDepositAmount(null);
            account.setVerificationAttempts(0);
            account.setVerificationLockedUntil(null);
            return template.save(account);
        }).map(BankVerificationService::view);
    }

    public Mono<BankAccount> load(String bankAccountId) {
        return template.findById(bankAccountId, BankAccount.class)
                .switchIfEmpty(Mono.error(new TranslatedRefusal(ErrorCode.BANK_ACCOUNT_UNKNOWN, "no bank account " + bankAccountId)));
    }

    static View view(BankAccount account) {
        return new View(account.getId(),
                account.getVerificationStatus() != null ? account.getVerificationStatus() : VerificationStatus.PENDING,
                account.getVerificationAttempts(),
                account.getVerificationLockedUntil() != null ? account.getVerificationLockedUntil().toEpochMilli() : 0L);
    }
}
