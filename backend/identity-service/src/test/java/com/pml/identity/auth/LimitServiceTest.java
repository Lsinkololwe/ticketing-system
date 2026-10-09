package com.pml.identity.auth;

import com.pml.shared.testing.RedisNode;
import com.pml.identity.auth.challenge.ChallengeService;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Volume limits per contact, IP, device and country (ET-IDN-001-R3), against a real Redis. */
@Tag("L2")
@Tag("ET-IDN-001")
@DisplayName("ET-IDN-001-R3 · code requests are limited per contact, IP, device and country, and nothing is sent past a cap")
class LimitServiceTest {

    /** The tests run against a real Redis, from the shared test fixtures. */
    private static final Class<?> STORE = RedisNode.class;

    private final AuthEngine engine = new AuthEngine();

    private ChallengeService.IssueCommand from(String contact, String ip, String device) {
        return new ChallengeService.IssueCommand(contact, null, null, ip, device, null);
    }

    private void passCooldown() {
        engine.clock.advance(Duration.ofSeconds(61));
    }

    @Test
    @DisplayName("per contact: the eleventh code in a day is OTP_RATE_LIMITED with retryAfterSeconds and sends nothing")
    void perContact() {
        engine.limitProperties.setContactCodesPerDay(3);
        String phone = AuthEngine.randomPhone();
        for (int i = 0; i < 3; i++) {
            engine.challenges.issue(from(phone, AuthEngine.randomIp(), null)).block();
            passCooldown();
        }
        TranslatedRefusal refusal = Refusals.of(ErrorCode.OTP_RATE_LIMITED,
                engine.challenges.issue(from(phone, AuthEngine.randomIp(), null)));
        assertThat(((Number) refusal.details().get("retryAfterSeconds")).longValue()).isBetween(1L, 86_400L);
        assertThat(refusal.retryable()).isTrue();
        assertThat(engine.captured.countTo(phone)).isEqualTo(3);
        assertThat(engine.meters.get("identity.otp.limit.refused").tag("scope", "contact").counter().count())
                .isGreaterThanOrEqualTo(1.0);
    }

    @Test
    @DisplayName("per IP: the cap is per hour and independent of the contact")
    void perIp() {
        engine.limitProperties.setIpCodesPerHour(2);
        String ip = AuthEngine.randomIp();
        engine.challenges.issue(from(AuthEngine.randomPhone(), ip, null)).block();
        engine.challenges.issue(from(AuthEngine.randomEmail(), ip, null)).block();
        String third = AuthEngine.randomPhone();
        TranslatedRefusal refusal = Refusals.of(ErrorCode.OTP_RATE_LIMITED, engine.challenges.issue(from(third, ip, null)));
        assertThat(((Number) refusal.details().get("retryAfterSeconds")).longValue()).isBetween(1L, 3_600L);
        assertThat(engine.captured.countTo(third)).isZero();
        // another IP is not affected
        engine.challenges.issue(from(AuthEngine.randomPhone(), AuthEngine.randomIp(), null)).block();
    }

    @Test
    @DisplayName("per device: a device may ask for codes for a limited number of distinct contacts, repeats are free")
    void perDevice() {
        engine.limitProperties.setDeviceDistinctContactsPerDay(2);
        String device = "device-" + UUID.randomUUID();
        String first = AuthEngine.randomPhone();
        engine.challenges.issue(from(first, AuthEngine.randomIp(), device)).block();
        engine.challenges.issue(from(AuthEngine.randomPhone(), AuthEngine.randomIp(), device)).block();
        Refusals.of(ErrorCode.OTP_RATE_LIMITED, engine.challenges.issue(from(AuthEngine.randomPhone(), AuthEngine.randomIp(), device)));
        // the same contact again is not a new contact
        passCooldown();
        engine.challenges.issue(from(first, AuthEngine.randomIp(), device)).block();
        assertThat(engine.captured.countTo(first)).isEqualTo(2);
    }

    @Test
    @DisplayName("per country: the daily cap of a country applies to its numbers only")
    void perCountry() {
        engine.limitProperties.getCountryCodesPerDay().put("MW", 0);
        engine.limitProperties.getCountryCodesPerDay().put("default", 1_000_000);
        // 0 means no codes at all for Malawi today
        Refusals.of(ErrorCode.OTP_RATE_LIMITED,
                engine.challenges.issue(from("+265991234567", AuthEngine.randomIp(), null)));
        engine.challenges.issue(from(AuthEngine.randomPhone(), AuthEngine.randomIp(), null)).block();
        assertThat(engine.meters.get("identity.otp.limit.refused").tag("scope", "country").counter().count())
                .isGreaterThanOrEqualTo(1.0);
    }

    @Test
    @DisplayName("a refusal by one scope does not burn the allowance of another")
    void refusalConsumesNothing() {
        engine.limitProperties.setIpCodesPerHour(1);
        String ip = AuthEngine.randomIp();
        engine.challenges.issue(from(AuthEngine.randomPhone(), ip, null)).block();

        String phone = AuthEngine.randomPhone();
        Refusals.of(ErrorCode.OTP_RATE_LIMITED, engine.challenges.issue(from(phone, ip, null)));
        // The contact counter was not advanced by the refused request, and the cooldown was released.
        engine.challenges.issue(from(phone, AuthEngine.randomIp(), null)).block();
        assertThat(engine.captured.countTo(phone)).isEqualTo(1);
    }

    @Test
    @DisplayName("a refused request leaves an already-live code live")
    void liveCodeSurvivesRefusal() {
        engine.limitProperties.setIpCodesPerHour(1);
        String ip = AuthEngine.randomIp();
        String phone = AuthEngine.randomPhone();
        var issued = engine.challenges.issue(from(phone, ip, null)).block();
        passCooldown();
        Refusals.of(ErrorCode.OTP_RATE_LIMITED, engine.challenges.issue(from(phone, ip, null)));
        assertThat(engine.challenges.verify(issued.challengeId(), engine.codeFor(phone)).block()).isNotNull();
    }
}
