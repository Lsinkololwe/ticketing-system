package com.pml.identity.repository;

import com.pml.identity.domain.model.Permission;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Permission Repository
 */
@Repository
public interface PermissionRepository extends ReactiveMongoRepository<Permission, String> {

    Mono<Permission> findByName(String name);

    Mono<Boolean> existsByName(String name);

    Flux<Permission> findByIsActiveTrue();

    Flux<Permission> findByCategory(String category);

    Flux<Permission> findByCategoryAndIsActiveTrue(String category);
}
