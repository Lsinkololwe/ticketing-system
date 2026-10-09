package com.pml.identity.repository;

import com.pml.identity.domain.enums.ContactType;
import com.pml.identity.domain.model.Contact;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface ContactRepository extends ReactiveMongoRepository<Contact, String> {

    /** The verified, unreleased owner of a contact: at most one, by {@code uniq_verified_contact}. */
    Mono<Contact> findByTypeAndValueHashAndVerifiedAtIsNotNullAndReleasedAtIsNull(ContactType type, String valueHash);

    default Mono<Contact> findVerifiedOwner(ContactType type, String valueHash) {
        return findByTypeAndValueHashAndVerifiedAtIsNotNullAndReleasedAtIsNull(type, valueHash);
    }

    Mono<Boolean> existsByTypeAndValueHashAndVerifiedAtIsNotNullAndReleasedAtIsNull(ContactType type, String valueHash);

    Flux<Contact> findByAccountId(String accountId);

    /** The contacts an account holds now. */
    Flux<Contact> findByAccountIdAndReleasedAtIsNull(String accountId);

    Mono<Contact> findByAccountIdAndTypeAndValueHash(String accountId, ContactType type, String valueHash);

    Mono<Long> countByAccountIdAndVerifiedAtIsNotNullAndReleasedAtIsNull(String accountId);
}
