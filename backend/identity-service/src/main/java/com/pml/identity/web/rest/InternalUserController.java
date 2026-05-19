package com.pml.identity.web.rest;

import com.pml.identity.service.UserService;
import com.pml.shared.dto.UserSummaryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * Internal User Controller
 *
 * Provides internal APIs for other microservices to fetch user information.
 * These endpoints are not exposed externally through the API Gateway.
 */
@Slf4j
@RestController
@RequestMapping("/api/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserService userService;

    /**
     * Get user summary by ID
     * Used by Catalog and Booking services to fetch user details
     */
    @GetMapping("/{id}")
    public Mono<ResponseEntity<UserSummaryDto>> getUserById(@PathVariable String id) {
        log.debug("Internal request for user: {}", id);

        return userService.findById(id)
                .map(user -> UserSummaryDto.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .firstName(user.getFirstName())
                        .lastName(user.getLastName())
                        .phoneNumber(user.getPhoneNumber())
                        .userType(user.getUserType().name())
                        .emailVerified(user.isEmailVerified())
                        .phoneVerified(user.isPhoneVerified())
                        .active(user.isActive())
                        .build())
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Get user summary by email
     */
    @GetMapping("/email/{email}")
    public Mono<ResponseEntity<UserSummaryDto>> getUserByEmail(@PathVariable String email) {
        log.debug("Internal request for user by email: {}", email);

        return userService.findByEmail(email)
                .map(user -> UserSummaryDto.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .firstName(user.getFirstName())
                        .lastName(user.getLastName())
                        .phoneNumber(user.getPhoneNumber())
                        .userType(user.getUserType().name())
                        .emailVerified(user.isEmailVerified())
                        .phoneVerified(user.isPhoneVerified())
                        .active(user.isActive())
                        .build())
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Validate if a user exists and is active.
     *
     * ARCHITECTURE NOTE: Account enabled/disabled status is managed by Keycloak.
     * This endpoint only checks the MongoDB isActive field (soft delete status).
     * For full enabled check, verify against Keycloak.
     */
    @GetMapping("/{id}/exists")
    public Mono<ResponseEntity<Boolean>> userExists(@PathVariable String id) {
        return userService.findById(id)
                .map(user -> ResponseEntity.ok(user.isActive()))
                .defaultIfEmpty(ResponseEntity.ok(false));
    }
}
