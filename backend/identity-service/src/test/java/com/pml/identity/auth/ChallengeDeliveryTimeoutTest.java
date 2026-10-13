package com.pml.identity.auth;

import com.pml.identity.auth.challenge.ChallengeKeys;
import com.pml.identity.auth.delivery.CodeDeliveryProvider;
import com.pml.identity.auth.delivery.DeliveryChannel;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import com.pml.shared.testing.RedisNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** A provider that never answers fails the request within the delivery budget and leaves nothing behind. */
@Tag("L2")
@Tag("ET-IDN-001")
@DisplayName("a delivery that times out is a retryable failure and no code, attempt count or cooldown stays in Redis")
class ChallengeDeliveryTimeoutTest {

    /** The tests run against a real Redis, from the shared test fixtures. */
    private static final Class<?> STORE = RedisNode.class;

    private static CodeDeliveryProvider neverAnswers() {
        return new CodeDeliveryProvider() {
            @Override
            public boolean supports(DeliveryChannel channel) {
                return true;
            }

            @Override
            public boolean enabled() {
                return true;
            }

            @Override
            public Mono<Void> send(DeliveryChannel channel, String recipient, String code) {
                return Mono.never();
            }
        };
    }

    @Test
    @DisplayName("the provider times out: OTP_DELIVERY_FAILED, retryable, and the challenge, id, attempts and cooldown keys are gone")
    void timeoutLeavesNothingBehind() {
        AuthEngine engine = new AuthEngine(List.of(neverAnswers()), null, Duration.ofMillis(400));
        String phone = AuthEngine.randomPhone();
        String contactKey = engine.hasher.normalize(phone, null, null, null).orElseThrow().key();

        long started = System.nanoTime();
        TranslatedRefusal refusal = Refusals.of(ErrorCode.OTP_DELIVERY_FAILED, engine.challenges.issue(engine.command(phone)));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

        assertThat(refusal.retryable()).isTrue();
        assertThat(elapsed).as("the buyer is not left waiting").isLessThan(Duration.ofSeconds(3));
        assertThat(engine.redis.hasKey(ChallengeKeys.challenge(contactKey)).block()).isFalse();
        assertThat(engine.redis.hasKey(ChallengeKeys.attempts(contactKey)).block()).isFalse();
        assertThat(engine.redis.hasKey(ChallengeKeys.cooldown(contactKey)).block()).isFalse();
        assertThat(engine.redis.keys("chid:*").flatMap(key -> engine.redis.opsForValue().get(key))
                .filter(contactKey::equals).collectList().block()).as("no challenge id points at the contact").isEmpty();

        // and the buyer can ask again at once, without waiting out a cooldown
        AuthEngine retry = new AuthEngine();
        assertThat(retry.challenges.issue(retry.command(phone)).block().challengeId()).isNotBlank();
    }
}
