package com.pml.identity.auth.delivery;

import com.pml.identity.domain.enums.ContactType;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * Chooses the channel for a code and sends it.
 *
 * <p>A WhatsApp contact is reached on WhatsApp, an email contact by email. The 'other' channel is
 * tried after a failure only when the caller named it as {@code preferredChannel} AND the contact
 * type can be reached on it; today neither type can (a phone number has no address and the other
 * way round), so a code is never silently redirected to somewhere the person did not give. The
 * fallback list is built in one place so a future contact type that holds both works unchanged.
 * Returns the channel actually used; a total failure is OTP_DELIVERY_FAILED (retryable).</p>
 */
@Service
public class DeliveryOrchestrator {

    private final List<CodeDeliveryProvider> providers;
    private final MeterRegistry meters;

    public DeliveryOrchestrator(List<CodeDeliveryProvider> providers, MeterRegistry meters) {
        this.providers = List.copyOf(providers);
        this.meters = meters;
    }

    /** Fails with NOTIFICATION_CHANNEL_UNAVAILABLE when no enabled provider can reach this contact type. */
    public void assertAvailable(ContactType type) {
        if (candidates(type, null).isEmpty()) {
            throw new TranslatedRefusal(ErrorCode.NOTIFICATION_CHANNEL_UNAVAILABLE,
                    "no delivery channel enabled for " + type);
        }
    }

    /**
     * @param recipient the normalised contact value
     * @return the channel used
     */
    public Mono<DeliveryChannel> deliver(ContactType type, String recipient, String code, DeliveryChannel preferred) {
        List<DeliveryChannel> order = channelOrder(type, preferred);
        return attempt(order, 0, recipient, code);
    }

    private Mono<DeliveryChannel> attempt(List<DeliveryChannel> order, int index, String recipient, String code) {
        if (index >= order.size()) {
            return Mono.error(new TranslatedRefusal(ErrorCode.OTP_DELIVERY_FAILED, "no channel could deliver the code"));
        }
        DeliveryChannel channel = order.get(index);
        CodeDeliveryProvider provider = provider(channel);
        if (provider == null) {
            return attempt(order, index + 1, recipient, code);
        }
        return provider.send(channel, recipient, code)
                .then(Mono.fromSupplier(() -> {
                    meters.counter("identity.otp.delivery", "channel", channel.name(), "outcome", "sent").increment();
                    return channel;
                }))
                .onErrorResume(error -> {
                    meters.counter("identity.otp.delivery", "channel", channel.name(), "outcome", "failed").increment();
                    return attempt(order, index + 1, recipient, code);
                });
    }

    /** Sends a notice on the contact's own channel. Fails only for the caller to log; never carries the recipient. */
    public Mono<Void> notice(ContactType type, String recipient, ContactNotice notice) {
        DeliveryChannel channel = DeliveryChannel.nativeFor(type);
        CodeDeliveryProvider provider = provider(channel);
        if (provider == null) {
            return Mono.empty();
        }
        return provider.sendNotice(channel, recipient, notice)
                .doOnSuccess(done -> meters.counter("identity.contact.notice", "channel", channel.name(), "outcome", "sent").increment());
    }

    private List<DeliveryChannel> channelOrder(ContactType type, DeliveryChannel preferred) {
        List<DeliveryChannel> order = new ArrayList<>();
        order.add(DeliveryChannel.nativeFor(type));
        if (preferred != null && !order.contains(preferred) && reachableOn(type, preferred)) {
            order.add(preferred);
        }
        return order;
    }

    /** Whether a contact of this type holds an address on the channel. */
    private static boolean reachableOn(ContactType type, DeliveryChannel channel) {
        return DeliveryChannel.nativeFor(type) == channel;
    }

    private List<DeliveryChannel> candidates(ContactType type, DeliveryChannel preferred) {
        return channelOrder(type, preferred).stream().filter(c -> provider(c) != null).toList();
    }

    private CodeDeliveryProvider provider(DeliveryChannel channel) {
        CodeDeliveryProvider real = null;
        for (CodeDeliveryProvider candidate : providers) {
            if (candidate instanceof CapturingProvider) {
                return candidate; // capture replaces the real providers
            }
            if (candidate.supports(channel) && candidate.enabled()) {
                real = candidate;
            }
        }
        return real;
    }
}
