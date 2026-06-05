package com.pml.identity.web.graphql.mutation;

import com.pml.identity.web.graphql.dto.auth.OtpRequestResponse;
import com.pml.identity.web.graphql.dto.auth.PhoneAuthPayload;
import com.pml.identity.domain.model.User;
import com.pml.identity.infrastructure.keycloak.KeycloakAuthService;
import com.pml.identity.infrastructure.keycloak.KeycloakService;
import com.pml.identity.infrastructure.messaging.MessagingService;
import com.pml.identity.infrastructure.cache.OtpService;
import com.pml.identity.service.UserService;
import com.pml.shared.constants.UserType;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * GraphQL Mutation Resolver for Phone OTP Authentication.
 * Enables passwordless login via OTP verification.
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class PhoneOtpMutationResolver {

    private final OtpService otpService;
    private final MessagingService messagingService;
    private final UserService userService;
    private final KeycloakService keycloakService;
    private final KeycloakAuthService keycloakAuthService;

    /**
     * Request OTP for phone number authentication.
     */
    @DgsMutation
    public Mono<OtpRequestResponse> requestPhoneOtp(
            @InputArgument String phoneNumber,
            @InputArgument String channel) {
        String normalizedPhone = normalizePhoneNumber(phoneNumber);
        String deliveryChannel = channel != null ? channel : "whatsapp";

        log.info("OTP request for phone: {}, channel: {}", maskPhoneNumber(normalizedPhone), deliveryChannel);

        return otpService.canSendOtp(normalizedPhone)
                .flatMap(canSend -> {
                    if (!canSend) {
                        return otpService.getCooldownRemaining(normalizedPhone)
                                .map(remaining -> new OtpRequestResponse(
                                        false,
                                        "Please wait before requesting another OTP",
                                        remaining.intValue()
                                ));
                    }

                    return otpService.generateOtp(normalizedPhone)
                            .flatMap(otp -> messagingService.sendOtp(normalizedPhone, otp, deliveryChannel)
                                    .flatMap(sent -> {
                                        if (sent) {
                                            return otpService.setCooldown(normalizedPhone)
                                                    .thenReturn(new OtpRequestResponse(
                                                            true,
                                                            "OTP sent successfully",
                                                            300
                                                    ));
                                        } else {
                                            return Mono.just(new OtpRequestResponse(
                                                    false,
                                                    "Failed to send OTP. Please try again.",
                                                    0
                                            ));
                                        }
                                    }));
                });
    }

    /**
     * Verify OTP and authenticate user.
     */
    @DgsMutation
    public Mono<PhoneAuthPayload> verifyPhoneOtp(
            @InputArgument String phoneNumber,
            @InputArgument String otp) {
        String normalizedPhone = normalizePhoneNumber(phoneNumber);

        log.info("OTP verification for phone: {}", maskPhoneNumber(normalizedPhone));

        return otpService.verifyOtp(normalizedPhone, otp)
                .flatMap(isValid -> {
                    if (!isValid) {
                        return Mono.just(new PhoneAuthPayload(
                                false,
                                "Invalid or expired OTP",
                                null,
                                null,
                                null
                        ));
                    }

                    return findOrCreateUserByPhone(normalizedPhone)
                            .flatMap(user -> userService.verifyPhone(user.getId()))
                            .flatMap(this::generateTokensForUser);
                });
    }

    /**
     * Find existing user by phone or create a new one.
     *
     * ARCHITECTURE NOTE: Keycloak is the source of truth for authentication.
     * We create the user in Keycloak FIRST with a random password, then store
     * the profile data in MongoDB with the Keycloak user ID as the document ID.
     */
    private Mono<User> findOrCreateUserByPhone(String phoneNumber) {
        return userService.findByPhoneNumber(phoneNumber)
                .switchIfEmpty(Mono.defer(() -> {
                    // Build user profile data (NO password - Keycloak handles auth)
                    String username = "user_" + phoneNumber.replace("+", "").substring(Math.max(0, phoneNumber.length() - 8));
                    String placeholderEmail = phoneNumber.replace("+", "") + "@phone.local";
                    String randomPassword = UUID.randomUUID().toString();

                    User newUser = User.builder()
                            .phoneNumber(phoneNumber)
                            .username(username)
                            .email(placeholderEmail)
                            .firstName("Phone")
                            .lastName("User")
                            .roles(java.util.EnumSet.of(UserType.CUSTOMER))
                            .phoneVerified(true)
                            .emailVerified(false)
                            .active(true)
                            .build();

                    log.info("Creating new user for phone: {}", maskPhoneNumber(phoneNumber));

                    // Create in Keycloak first, then save profile to MongoDB
                    return keycloakService.createUser(newUser, randomPassword)
                            .flatMap(keycloakUserId -> {
                                // Set the MongoDB document ID to match Keycloak user ID
                                newUser.setId(keycloakUserId);
                                return userService.createUser(newUser);
                            })
                            .onErrorResume(e -> {
                                log.error("Failed to create user in Keycloak: {}", e.getMessage());
                                // Still save to MongoDB for recovery (admin can link later)
                                return userService.createUser(newUser);
                            });
                }));
    }

    /**
     * Generate tokens for the authenticated user.
     */
    private Mono<PhoneAuthPayload> generateTokensForUser(User user) {
        String scopes = "openid profile email events-read tickets-purchase bookings-read phone";

        return keycloakAuthService.getServiceToken(scopes)
                .map(tokenResponse -> new PhoneAuthPayload(
                        true,
                        "Authentication successful",
                        tokenResponse.getAccessToken(),
                        tokenResponse.getRefreshToken(),
                        user
                ))
                .onErrorResume(e -> {
                    log.error("Failed to get Keycloak token: {}", e.getMessage());
                    return Mono.just(new PhoneAuthPayload(
                            true,
                            "Phone verified. Please complete login via Keycloak.",
                            null,
                            null,
                            user
                    ));
                });
    }

    private String normalizePhoneNumber(String phoneNumber) {
        String cleaned = phoneNumber.replaceAll("[^0-9+]", "");
        if (!cleaned.startsWith("+")) {
            if (cleaned.length() <= 10) {
                cleaned = "+260" + cleaned;
            } else {
                cleaned = "+" + cleaned;
            }
        }
        return cleaned;
    }

    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber.length() <= 4) {
            return "****";
        }
        return "***" + phoneNumber.substring(phoneNumber.length() - 4);
    }
}
