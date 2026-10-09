package com.pml.identity.repository;

import com.pml.identity.domain.model.AccountEvent;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.time.Instant;

@Repository
public interface AccountEventRepository extends ReactiveMongoRepository<AccountEvent, String> {

    Flux<AccountEvent> findByAccountIdOrderByAtDesc(String accountId);

    Flux<AccountEvent> findByKindAndAtAfter(String kind, Instant after);
}
