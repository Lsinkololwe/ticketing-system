package com.pml.identity.repository;

import com.pml.identity.domain.model.Consent;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface ConsentRepository extends ReactiveMongoRepository<Consent, String> {

    Flux<Consent> findByAccountId(String accountId);

    Flux<Consent> findByAccountIdAndPurpose(String accountId, String purpose);

    /** The live (not withdrawn) grant of one purpose at one version. */
    Mono<Consent> findFirstByAccountIdAndPurposeAndVersionAndWithdrawnAtIsNull(
            String accountId, String purpose, String version);
}
