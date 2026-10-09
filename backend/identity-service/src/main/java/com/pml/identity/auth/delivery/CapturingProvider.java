package com.pml.identity.auth.delivery;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Clock;

/**
 * Keeps codes in memory instead of sending them. Active only when
 * {@code identity.delivery.capture.enabled=true}, which {@code IdentityConfigurationValidator}
 * allows in profiles local and test only. Takes both channels, replacing the real providers.
 * Logs nothing.
 */
@Component
@ConditionalOnProperty(name = "identity.delivery.capture.enabled", havingValue = "true")
public class CapturingProvider implements CodeDeliveryProvider {

    private final CapturedMessages captured;
    private final Clock clock;

    public CapturingProvider(CapturedMessages captured, Clock clock) {
        this.captured = captured;
        this.clock = clock;
    }

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
        return Mono.fromRunnable(() ->
                captured.add(new CapturedMessages.Message(channel, recipient, code, clock.instant())));
    }

    @Override
    public Mono<Void> sendNotice(DeliveryChannel channel, String recipient, ContactNotice notice) {
        return Mono.fromRunnable(() ->
                captured.addNotice(new CapturedMessages.Notice(channel, recipient, notice, clock.instant())));
    }
}
