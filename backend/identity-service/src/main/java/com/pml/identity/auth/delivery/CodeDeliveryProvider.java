package com.pml.identity.auth.delivery;

import reactor.core.publisher.Mono;

/**
 * Sends a one-time code over one channel. Implementations never log the recipient, the code or
 * any response body; failures are reported as {@link DeliveryFailedException}, which carries
 * neither.
 */
public interface CodeDeliveryProvider {

    boolean supports(DeliveryChannel channel);

    /** Whether the channel is switched on and configured. */
    boolean enabled();

    /** @param recipient the normalised contact value (E.164 number or lower-case email) */
    Mono<Void> send(DeliveryChannel channel, String recipient, String code);

    /** Tells a contact about a change to its account. Best effort by design; a provider that cannot says nothing. */
    default Mono<Void> sendNotice(DeliveryChannel channel, String recipient, ContactNotice notice) {
        return Mono.empty();
    }
}
