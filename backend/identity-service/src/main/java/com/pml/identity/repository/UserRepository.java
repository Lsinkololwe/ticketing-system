package com.pml.identity.repository;

import com.pml.identity.domain.model.User;
import com.pml.shared.constants.UserType;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * User Repository
 */
@Repository
public interface UserRepository extends ReactiveMongoRepository<User, String> {

    Mono<User> findByUsername(String username);

    Mono<User> findByEmail(String email);

    Mono<User> findByPhoneNumber(String phoneNumber);

    Mono<Boolean> existsByEmail(String email);

    Mono<Boolean> existsByUsername(String username);

    Mono<Boolean> existsByPhoneNumber(String phoneNumber);

    Flux<User> findByUserType(UserType userType);

    Flux<User> findByActiveTrue();

    Flux<User> findByUserTypeAndActiveTrue(UserType userType);

    Flux<User> findByEmailVerifiedFalse();

    Flux<User> findByPhoneVerifiedFalse();
}
