package com.pml.booking.repository;

import com.pml.booking.domain.model.EscrowAccount;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Escrow Account Repository
 */
@Repository
public interface EscrowAccountRepository extends ReactiveMongoRepository<EscrowAccount, String> {

    Mono<EscrowAccount> findByAccountNumber(String accountNumber);

    Mono<EscrowAccount> findByAccountName(String accountName);

    Flux<EscrowAccount> findByStatus(String status);

    Flux<EscrowAccount> findByIsActiveTrue();

    Mono<EscrowAccount> findByAssociatedBankAccountNumber(String bankAccountNumber);

    Mono<Boolean> existsByAccountNumber(String accountNumber);
}
