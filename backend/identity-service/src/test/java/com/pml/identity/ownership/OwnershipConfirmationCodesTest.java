package com.pml.identity.ownership;

import com.pml.identity.domain.enums.ContactType;
import com.pml.identity.domain.enums.TransferStatus;
import com.pml.identity.domain.model.OwnershipTransferRequest;
import com.pml.identity.domain.model.User;
import com.pml.identity.auth.delivery.DeliveryChannel;
import com.pml.identity.auth.delivery.DeliveryOrchestrator;
import com.pml.identity.security.ContactHasher;
import com.pml.identity.repository.UserRepository;
import com.pml.identity.service.OwnershipConfirmationCodes;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.testing.RedisNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Accepting an organization's ownership needs a code sent to the nominee's verified phone, used once.
 * Runs against a real Redis, where the codes, attempt counters and cooldowns live.
 */
@Tag("L2")
@Tag("ET-ORG-002")
@DisplayName("An ownership transfer is confirmed only with the code sent to the nominee's phone")
class OwnershipConfirmationCodesTest {

    private static final Instant NOW = Instant.parse("2026-09-19T10:00:00Z");
    private static final String NOMINEE = "user-nominee";
    private static final String PHONE = "+260971234567";

    private static LettuceConnectionFactory factory;
    private static ReactiveStringRedisTemplate redis;

    private final UserRepository users = mock(UserRepository.class);
    private final DeliveryOrchestrator delivery = mock(DeliveryOrchestrator.class);
    private OwnershipConfirmationCodes codes;
    private OwnershipTransferRequest transfer;

    @BeforeAll
    static void connect() {
        factory = new LettuceConnectionFactory(RedisNode.host(), RedisNode.port());
        factory.afterPropertiesSet();
        redis = new ReactiveStringRedisTemplate(factory);
    }

    @AfterAll
    static void close() {
        factory.destroy();
    }

    @BeforeEach
    void pendingTransfer() {
        codes = new OwnershipConfirmationCodes(redis, users, delivery, new ContactHasher("ownership-test-hash-key"),
                Clock.fixed(NOW, ZoneOffset.UTC));
        transfer = OwnershipTransferRequest.builder()
                .id("transfer-" + UUID.randomUUID())
                .newOwnerId(NOMINEE)
                .status(TransferStatus.PENDING)
                .expiresAt(NOW.plus(Duration.ofDays(3)))
                .build();
        when(users.findById(NOMINEE)).thenReturn(Mono.just(User.builder()
                .id(NOMINEE).phoneNumber(PHONE).phoneVerified(true).build()));
        when(delivery.deliver(any(), anyString(), anyString(), any())).thenReturn(Mono.just(DeliveryChannel.WHATSAPP));
    }

    private String issuedCode() {
        codes.issue(transfer, NOMINEE).block();
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(delivery).deliver(eq(ContactType.WHATSAPP), eq(PHONE), code.capture(), isNull());
        return code.getValue();
    }

    private static ErrorCode refusal(Runnable call) {
        try {
            call.run();
        } catch (DomainRefusal refusal) {
            return refusal.errorCode();
        }
        throw new AssertionError("expected a refusal");
    }

    @Test
    @DisplayName("the code sent to the nominee's phone confirms the transfer, once")
    void theSentCodeWorksOnce() {
        String code = issuedCode();
        assertThat(code).matches("\\d{6}");

        codes.verify(transfer, NOMINEE, code).block();

        assertThat(refusal(() -> codes.verify(transfer, NOMINEE, code).block())).isEqualTo(ErrorCode.OTP_EXPIRED);
    }

    @Test
    @DisplayName("three wrong codes lock it; the right one no longer works")
    void wrongCodesLockIt() {
        String code = issuedCode();
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 3; i++) {
            assertThat(refusal(() -> codes.verify(transfer, NOMINEE, wrong).block())).isEqualTo(ErrorCode.OTP_INVALID);
        }

        assertThat(refusal(() -> codes.verify(transfer, NOMINEE, code).block()))
                .isEqualTo(ErrorCode.OTP_ATTEMPTS_EXHAUSTED);
    }

    @Test
    @DisplayName("someone else holding the link is refused, and cannot lock the nominee out")
    void strangerIsRefusedWithoutSpendingAttempts() {
        String code = issuedCode();
        for (int i = 0; i < 5; i++) {
            assertThat(refusal(() -> codes.verify(transfer, "user-stranger", "000000").block()))
                    .isEqualTo(ErrorCode.ACTOR_NOT_PERMITTED);
        }

        codes.verify(transfer, NOMINEE, code).block();
    }

    @Test
    @DisplayName("a login code for the same phone does not confirm a transfer")
    void loginCodesDoNotCount() {
        // A live login challenge for the same phone (keyed by contact key, never by the number).
        String loginKey = "ch:" + new ContactHasher("ownership-test-hash-key").hash(ContactType.WHATSAPP, PHONE);
        redis.opsForValue().set(loginKey, "123456", Duration.ofMinutes(5)).block();

        assertThat(refusal(() -> codes.verify(transfer, NOMINEE, "123456").block())).isEqualTo(ErrorCode.OTP_EXPIRED);
    }

    @Test
    @DisplayName("a second code cannot be requested within the cooldown")
    void cooldown() {
        issuedCode();

        assertThat(refusal(() -> codes.issue(transfer, NOMINEE).block())).isEqualTo(ErrorCode.OTP_COOLDOWN_ACTIVE);
    }

    @Test
    @DisplayName("an expired transfer cannot be confirmed, and no code is sent for it")
    void expiredTransfer() {
        OwnershipTransferRequest expired = transfer.toBuilder().expiresAt(NOW.minusSeconds(1)).build();

        assertThat(refusal(() -> codes.issue(expired, NOMINEE).block())).isEqualTo(ErrorCode.TRANSFER_NOT_PENDING);
        verify(delivery, never()).deliver(any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("a nominee without a verified phone gets no code")
    void unverifiedPhone() {
        when(users.findById(NOMINEE)).thenReturn(Mono.just(User.builder()
                .id(NOMINEE).phoneNumber(PHONE).phoneVerified(false).build()));

        assertThat(refusal(() -> codes.issue(transfer, NOMINEE).block())).isEqualTo(ErrorCode.PHONE_NUMBER_INVALID);
        verify(delivery, never()).deliver(any(), anyString(), anyString(), any());
    }
}
