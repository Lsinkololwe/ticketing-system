package com.pml.booking.repository;

import com.pml.booking.domain.model.BankAccount;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive MongoDB Repository for BankAccount entity.
 */
@Repository
public interface BankAccountRepository extends ReactiveMongoRepository<BankAccount, String> {

    Flux<BankAccount> findByOrganizerId(String organizerId);

    Mono<BankAccount> findByOrganizerIdAndIsDefaultTrue(String organizerId);

    Mono<BankAccount> findByAccountNumber(String accountNumber);

    Flux<BankAccount> findByOrganizerIdAndStatus(String organizerId, String status);

    Mono<Boolean> existsByAccountNumber(String accountNumber);

    Mono<Long> countByOrganizerId(String organizerId);
}
