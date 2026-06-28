package com.pml.identity.infrastructure.cache;

import com.pml.shared.util.PhoneNumbers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.time.Duration;

/**
 * Service for OTP generation, storage, and verification.
 * Uses Redis for OTP storage with automatic expiration.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    private static final String OTP_KEY_PREFIX = "otp:phone:";
    private static final String OTP_ATTEMPTS_PREFIX = "otp:attempts:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${identity.otp.expiration-minutes:5}")
    private int otpExpirationMinutes;

    @Value("${identity.otp.length:6}")
    private int otpLength;

    @Value("${identity.otp.max-attempts:3}")
    private int maxAttempts;

    @Value("${identity.otp.cooldown-minutes:1}")
    private int cooldownMinutes;

    /**
     * Generate and store an OTP for a phone number.
     *
     * @param phoneNumber The phone number
     * @return Mono with the generated OTP
     */
    public Mono<String> generateOtp(String phoneNumber) {
        String normalizedPhone = normalizePhoneNumber(phoneNumber);
        String otp = generateRandomOtp();
        String key = OTP_KEY_PREFIX + normalizedPhone;

        log.info("Generating OTP for phone: {}", maskPhoneNumber(normalizedPhone));

        return redisTemplate.opsForValue()
                .set(key, otp, Duration.ofMinutes(otpExpirationMinutes))
                .thenReturn(otp)
                .doOnSuccess(o -> log.debug("OTP stored successfully for: {}", maskPhoneNumber(normalizedPhone)));
    }

    /**
     * Verify an OTP for a phone number.
     *
     * @param phoneNumber The phone number
     * @param otp         The OTP to verify
     * @return Mono with true if valid, false otherwise
     */
    public Mono<Boolean> verifyOtp(String phoneNumber, String otp) {
        String normalizedPhone = normalizePhoneNumber(phoneNumber);
        String key = OTP_KEY_PREFIX + normalizedPhone;
        String attemptsKey = OTP_ATTEMPTS_PREFIX + normalizedPhone;

        log.info("Verifying OTP for phone: {}", maskPhoneNumber(normalizedPhone));

        // Check attempt count
        return redisTemplate.opsForValue().get(attemptsKey)
                .map(Integer::parseInt)
                .defaultIfEmpty(0)
                .flatMap(attempts -> {
                    if (attempts >= maxAttempts) {
                        log.warn("Max OTP attempts exceeded for: {}", maskPhoneNumber(normalizedPhone));
                        return Mono.just(false);
                    }

                    // Get stored OTP
                    return redisTemplate.opsForValue().get(key)
                            .flatMap(storedOtp -> {
                                if (storedOtp != null && storedOtp.equals(otp)) {
                                    // OTP is valid - delete it and reset attempts
                                    return redisTemplate.delete(key)
                                            .then(redisTemplate.delete(attemptsKey))
                                            .thenReturn(true)
                                            .doOnSuccess(v -> log.info("OTP verified successfully for: {}", maskPhoneNumber(normalizedPhone)));
                                } else {
                                    // OTP is invalid - increment attempts
                                    return incrementAttempts(attemptsKey)
                                            .thenReturn(false)
                                            .doOnSuccess(v -> log.warn("Invalid OTP for: {}", maskPhoneNumber(normalizedPhone)));
                                }
                            })
                            .switchIfEmpty(Mono.defer(() -> {
                                log.warn("No OTP found for: {}", maskPhoneNumber(normalizedPhone));
                                return Mono.just(false);
                            }));
                });
    }

    /**
     * Check if an OTP can be sent (cooldown period).
     *
     * @param phoneNumber The phone number
     * @return Mono with true if OTP can be sent, false if in cooldown
     */
    public Mono<Boolean> canSendOtp(String phoneNumber) {
        String normalizedPhone = normalizePhoneNumber(phoneNumber);
        String cooldownKey = "otp:cooldown:" + normalizedPhone;

        return redisTemplate.hasKey(cooldownKey)
                .map(exists -> !exists);
    }

    /**
     * Set cooldown after sending OTP.
     *
     * @param phoneNumber The phone number
     * @return Mono signaling completion
     */
    public Mono<Void> setCooldown(String phoneNumber) {
        String normalizedPhone = normalizePhoneNumber(phoneNumber);
        String cooldownKey = "otp:cooldown:" + normalizedPhone;

        return redisTemplate.opsForValue()
                .set(cooldownKey, "1", Duration.ofMinutes(cooldownMinutes))
                .then();
    }

    /**
     * Get remaining cooldown time in seconds.
     *
     * @param phoneNumber The phone number
     * @return Mono with remaining seconds, or 0 if no cooldown
     */
    public Mono<Long> getCooldownRemaining(String phoneNumber) {
        String normalizedPhone = normalizePhoneNumber(phoneNumber);
        String cooldownKey = "otp:cooldown:" + normalizedPhone;

        return redisTemplate.getExpire(cooldownKey)
                .map(Duration::getSeconds)
                .defaultIfEmpty(0L);
    }

    /**
     * Invalidate OTP for a phone number.
     *
     * @param phoneNumber The phone number
     * @return Mono signaling completion
     */
    public Mono<Void> invalidateOtp(String phoneNumber) {
        String normalizedPhone = normalizePhoneNumber(phoneNumber);
        String key = OTP_KEY_PREFIX + normalizedPhone;

        return redisTemplate.delete(key).then();
    }

    /**
     * Generate a random numeric OTP.
     */
    private String generateRandomOtp() {
        StringBuilder otp = new StringBuilder();
        for (int i = 0; i < otpLength; i++) {
            otp.append(SECURE_RANDOM.nextInt(10));
        }
        return otp.toString();
    }

    /**
     * Increment failed verification attempts.
     */
    private Mono<Void> incrementAttempts(String attemptsKey) {
        return redisTemplate.opsForValue()
                .increment(attemptsKey)
                .flatMap(count -> {
                    if (count == 1) {
                        // Set expiration on first attempt
                        return redisTemplate.expire(attemptsKey, Duration.ofMinutes(otpExpirationMinutes));
                    }
                    return Mono.just(true);
                })
                .then();
    }

    /**
     * Normalize phone number to E.164 format.
     */
    private String normalizePhoneNumber(String phoneNumber) {
        // Canonical E.164 normalization (Google libphonenumber). Used as the Redis
        // key for OTP storage/lookup, so request + verify must normalize identically.
        // Falls back to a digits-only form if the number cannot be validated, so an
        // unrecognized-but-consistent value still keys correctly.
        String e164 = PhoneNumbers.toE164(phoneNumber);
        return e164 != null ? e164 : phoneNumber.replaceAll("[^0-9+]", "");
    }

    /**
     * Mask phone number for logging (show last 4 digits only).
     */
    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber.length() <= 4) {
            return "****";
        }
        return "***" + phoneNumber.substring(phoneNumber.length() - 4);
    }
}
