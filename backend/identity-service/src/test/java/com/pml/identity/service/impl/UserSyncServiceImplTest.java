package com.pml.identity.service.impl;

import com.pml.identity.domain.model.User;
import com.pml.identity.dto.sync.KeycloakUserDataDto;
import com.pml.identity.event.domain.UserRegisteredEvent;
import com.pml.identity.infrastructure.keycloak.KeycloakService;
import com.pml.identity.repository.UserRepository;
import com.pml.shared.constants.UserType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.EnumSet;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UserSyncServiceImpl} focused on the idempotent
 * {@code UserRegisteredEvent} publication ("decoupling") behaviour.
 *
 * <p>The event must be emitted exactly once per user, regardless of whether
 * this sync or Better Auth created the shared {@code users} document, and
 * regardless of whether the sync lands on the create or update branch. The
 * exactly-once guarantee is enforced by an atomic compare-and-set on
 * {@code registrationEventPublished} via {@code ReactiveMongoTemplate.findAndModify}.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserSyncService - UserRegisteredEvent decoupling")
class UserSyncServiceImplTest {

    private static final String OUTPUT_BINDING = "userOutput-out-0";
    private static final String USER_ID = "kc-sub-123";

    @Mock
    private UserRepository userRepository;

    @Mock
    private KeycloakService keycloakService;

    @Mock
    private StreamBridge streamBridge;

    @Mock
    private ReactiveMongoTemplate mongoTemplate;

    private UserSyncServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserSyncServiceImpl(userRepository, keycloakService, streamBridge, mongoTemplate);
        // save() echoes back the entity it was given.
        lenient().when(userRepository.save(any(User.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
    }

    private KeycloakUserDataDto data(String eventType) {
        return KeycloakUserDataDto.builder()
                .id(USER_ID)
                .username("jdoe")
                .email("jdoe@example.com")
                .firstName("John")
                .lastName("Doe")
                .emailVerified(true)
                .enabled(true)
                .roles(Set.of("CUSTOMER"))
                .eventType(eventType)
                .build();
    }

    private User existingUser() {
        return User.builder()
                .id(USER_ID)
                .username("jdoe")
                .email("jdoe@example.com")
                .firstName("John")
                .lastName("Doe")
                .roles(EnumSet.of(UserType.CUSTOMER))
                .build();
    }

    /** Stub the atomic claim to SUCCEED (this writer flips the flag and wins). */
    private void claimWins() {
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(User.class)))
                .thenReturn(Mono.just(existingUser()));
    }

    /** Stub the atomic claim to RETURN EMPTY (already published / lost the race). */
    private void claimLost() {
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(User.class)))
                .thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("New user (REGISTER) -> publishes exactly once")
    void newUserPublishesOnce() {
        when(userRepository.findById(USER_ID)).thenReturn(Mono.empty());
        claimWins();

        StepVerifier.create(service.syncUserFromData(data("REGISTER")))
                .expectNextCount(1)
                .verifyComplete();

        verify(streamBridge, times(1)).send(eq(OUTPUT_BINDING), any(UserRegisteredEvent.class));
    }

    @Test
    @DisplayName("Better Auth created row first; REGISTER lands on update branch -> still publishes once")
    void betterAuthCreatedThenRegisterPublishes() {
        when(userRepository.findById(USER_ID)).thenReturn(Mono.just(existingUser()));
        claimWins();

        StepVerifier.create(service.syncUserFromData(data("REGISTER")))
                .expectNextCount(1)
                .verifyComplete();

        verify(streamBridge, times(1)).send(eq(OUTPUT_BINDING), any(UserRegisteredEvent.class));
    }

    @Test
    @DisplayName("Registration already published -> does NOT publish again")
    void alreadyPublishedDoesNotPublish() {
        when(userRepository.findById(USER_ID)).thenReturn(Mono.just(existingUser()));
        claimLost();

        StepVerifier.create(service.syncUserFromData(data("REGISTER")))
                .expectNextCount(1)
                .verifyComplete();

        verify(streamBridge, never()).send(any(), any());
    }

    @Test
    @DisplayName("Non-registration event (UPDATE_PROFILE) -> never publishes")
    void updateProfileDoesNotPublish() {
        when(userRepository.findById(USER_ID)).thenReturn(Mono.just(existingUser()));

        StepVerifier.create(service.syncUserFromData(data("UPDATE_PROFILE")))
                .expectNextCount(1)
                .verifyComplete();

        verify(streamBridge, never()).send(any(), any());
        // The atomic claim is never even attempted for non-registration events.
        verify(mongoTemplate, never()).findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(User.class));
    }
}
