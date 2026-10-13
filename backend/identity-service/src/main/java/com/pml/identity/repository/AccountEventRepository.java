package com.pml.identity.repository;

import com.pml.identity.domain.model.AccountEvent;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.time.Instant;

@Repository
public interface AccountEventRepository extends ReactiveMongoRepository<AccountEvent, String> {

    Flux<AccountEvent> findByAccountIdOrderByAtDesc(String accountId);

    Flux<AccountEvent> findByKindAndAtAfter(String kind, Instant after);

    /**
     * Contact locks for one contact key, found by the event id prefix {@code otp-lock:<contactKey>:}
     * on the primary key, so no extra index is needed. The caller passes a hex contact key only.
     */
    @Query("{ '_id': { $regex: ?0 }, 'kind': ?1 }")
    Flux<AccountEvent> findByIdPrefixAndKind(String anchoredPrefixRegex, String kind);
}
