package com.pml.identity.web.rest;

import com.pml.identity.infrastructure.messaging.MessagingService;
import com.pml.identity.infrastructure.cache.OtpService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * Internal OTP API Controller.
 *
 * Exposes OTP operations for internal service-to-service communication.
 * Primarily used by Keycloak's custom Phone OTP Authenticator to:
 * 1. Request OTP generation and delivery
 * 2. Verify OTP codes
 *
 * Security: These endpoints are restricted to internal services only.
 * The API Gateway should not expose these to external clients.
 */
@Slf4j
@RestController
@RequestMapping("/api/internal/otp")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('SCOPE_internal-read', 'SCOPE_internal-write', 'ROLE_INTERNAL_SERVICE', 'ROLE_SYSTEM')")
public class InternalOtpController {

    private final OtpService otpService;
    private final MessagingService messagingService;

    /**
     * Request OTP generation and delivery.
     * Called by Keycloak Phone OTP Authenticator when user submits phone number.
     *
     * POST /api/internal/otp/request
     */
    @PostMapping("/request")
    public Mono<ResponseEntity<OtpRequestResult>> requestOtp(@Valid @RequestBody OtpRequestDto request) {
        String phoneNumber = request.getPhoneNumber();
        String channel = request.getChannel() != null ? request.getChannel() : "whatsapp";

        log.info("Internal OTP request for phone: {}, channel: {}", maskPhoneNumber(phoneNumber), channel);

        return otpService.canSendOtp(phoneNumber)
                .flatMap(canSend -> {
                    if (!canSend) {
                        return otpService.getCooldownRemaining(phoneNumber)
                                .map(remaining -> ResponseEntity.ok(
                                        new OtpRequestResult(false, OtpStatus.COOLDOWN, remaining.intValue(), 0)
                                ));
                    }

                    // Generate and send OTP
                    return otpService.generateOtp(phoneNumber)
                            .flatMap(otp -> messagingService.sendOtp(phoneNumber, otp, channel))
                            .flatMap(sent -> {
                                if (sent) {
                                    return otpService.setCooldown(phoneNumber)
                                            .thenReturn(ResponseEntity.ok(
                                                    new OtpRequestResult(true, OtpStatus.SENT, 0, 300)
                                            ));
                                } else {
                                    return Mono.just(ResponseEntity.ok(
                                            new OtpRequestResult(false, OtpStatus.DELIVERY_FAILED, 0, 0)
                                    ));
                                }
                            });
                })
                .onErrorResume(e -> {
                    log.error("Error requesting OTP: {}", e.getMessage(), e);
                    return Mono.just(ResponseEntity.internalServerError().body(
                            new OtpRequestResult(false, OtpStatus.ERROR, 0, 0)
                    ));
                });
    }

    /**
     * Verify OTP code.
     * Called by Keycloak Phone OTP Authenticator when user submits OTP.
     *
     * POST /api/internal/otp/verify
     */
    @PostMapping("/verify")
    public Mono<ResponseEntity<OtpVerifyResult>> verifyOtp(@Valid @RequestBody OtpVerifyDto request) {
        String phoneNumber = request.getPhoneNumber();
        String otp = request.getOtp();

        log.info("Internal OTP verification for phone: {}", maskPhoneNumber(phoneNumber));

        return otpService.verifyOtp(phoneNumber, otp)
                .map(valid -> {
                    if (valid) {
                        log.info("OTP verified successfully for phone: {}", maskPhoneNumber(phoneNumber));
                        return ResponseEntity.ok(new OtpVerifyResult(true, OtpVerifyStatus.VALID));
                    } else {
                        log.warn("OTP verification failed for phone: {}", maskPhoneNumber(phoneNumber));
                        return ResponseEntity.ok(new OtpVerifyResult(false, OtpVerifyStatus.INVALID));
                    }
                })
                .onErrorResume(e -> {
                    log.error("Error verifying OTP: {}", e.getMessage(), e);
                    return Mono.just(ResponseEntity.internalServerError().body(
                            new OtpVerifyResult(false, OtpVerifyStatus.ERROR)
                    ));
                });
    }

    /**
     * Check if OTP can be sent (cooldown status).
     * Used by Keycloak to check rate limiting before showing OTP form.
     *
     * GET /api/internal/otp/status/{phoneNumber}
     */
    @GetMapping("/status/{phoneNumber}")
    public Mono<ResponseEntity<OtpStatusResult>> getOtpStatus(@PathVariable String phoneNumber) {
        log.debug("Checking OTP status for phone: {}", maskPhoneNumber(phoneNumber));

        return otpService.canSendOtp(phoneNumber)
                .flatMap(canSend -> {
                    if (canSend) {
                        return Mono.just(ResponseEntity.ok(
                                new OtpStatusResult(true, 0)
                        ));
                    } else {
                        return otpService.getCooldownRemaining(phoneNumber)
                                .map(remaining -> ResponseEntity.ok(
                                        new OtpStatusResult(false, remaining.intValue())
                                ));
                    }
                });
    }

    /**
     * Invalidate OTP for a phone number.
     * Used when authentication is cancelled or times out.
     *
     * DELETE /api/internal/otp/{phoneNumber}
     */
    @DeleteMapping("/{phoneNumber}")
    public Mono<ResponseEntity<Void>> invalidateOtp(@PathVariable String phoneNumber) {
        log.info("Invalidating OTP for phone: {}", maskPhoneNumber(phoneNumber));

        return otpService.invalidateOtp(phoneNumber)
                .thenReturn(ResponseEntity.noContent().build());
    }

    // ========== DTOs ==========

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OtpRequestDto {
        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Invalid phone number format")
        private String phoneNumber;

        private String channel; // "whatsapp" or "sms", defaults to "whatsapp"
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OtpVerifyDto {
        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Invalid phone number format")
        private String phoneNumber;

        @NotBlank(message = "OTP is required")
        @Pattern(regexp = "^[0-9]{4,8}$", message = "Invalid OTP format")
        private String otp;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OtpRequestResult {
        private boolean success;
        private OtpStatus status;
        private int cooldownRemaining; // seconds remaining in cooldown
        private int expiresIn;         // seconds until OTP expires
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OtpVerifyResult {
        private boolean valid;
        private OtpVerifyStatus status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OtpStatusResult {
        private boolean canSend;
        private int cooldownRemaining; // seconds remaining in cooldown
    }

    public enum OtpStatus {
        SENT,           // OTP generated and sent successfully
        COOLDOWN,       // Rate limited - must wait before requesting again
        DELIVERY_FAILED,// Failed to deliver OTP via messaging service
        ERROR           // Internal error
    }

    public enum OtpVerifyStatus {
        VALID,          // OTP is correct
        INVALID,        // OTP is incorrect or expired
        MAX_ATTEMPTS,   // Maximum verification attempts exceeded
        ERROR           // Internal error
    }

    // ========== Helper Methods ==========

    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() <= 4) {
            return "****";
        }
        return "***" + phoneNumber.substring(phoneNumber.length() - 4);
    }
}
