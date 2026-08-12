package com.pml.identity.security.revocation;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Durable access to {@link RevocationRecord}s.
 *
 * <p>Lookups use {@code findAllById}: the document id is derived from (type, value), so checking
 * a token's three identifiers is a primary-key read of at most three documents and needs no
 * secondary index.</p>
 */
@Repository
public interface RevocationRepository extends ReactiveMongoRepository<RevocationRecord, String> {
}
