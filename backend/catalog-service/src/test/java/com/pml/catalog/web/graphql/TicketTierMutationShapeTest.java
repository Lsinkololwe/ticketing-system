package com.pml.catalog.web.graphql;

import com.pml.catalog.domain.model.TicketTier;
import com.pml.catalog.service.TicketTierService;
import com.pml.catalog.web.graphql.mutation.TicketTierMutationResolver;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The tier mutations answer with the {@code TicketTier} the schema declares, and a refusal reaches
 * the caller as a GraphQL error carrying its code.
 *
 * <p>They used to answer with a {@code success/message/data} wrapper the schema does not have: every
 * {@code TicketTier!} field then resolved to null on the wrapper, and every refusal — a tier on a
 * published event, a price below zero — was swallowed into a 200 with the exception's text.
 */
@Tag("L1")
@Tag("ET-CAT-002")
@DisplayName("Tier mutations return a TicketTier and let refusals through")
class TicketTierMutationShapeTest {

    private final TicketTierService tiers = mock(TicketTierService.class);
    private final TicketTierMutationResolver resolver = new TicketTierMutationResolver(tiers);

    @Test
    @DisplayName("activating a tier answers with the tier itself")
    void answersWithTheTier() {
        TicketTier tier = TicketTier.builder().id("tier-1").eventId("event-1").build();
        when(tiers.activateTier("tier-1")).thenReturn(Mono.just(tier));

        StepVerifier.create(resolver.activateTicketTier("tier-1")).expectNext(tier).verifyComplete();
    }

    @Test
    @DisplayName("a refusal propagates, so the error contract gives it its code")
    void refusalPropagates() {
        TranslatedRefusal refused = new TranslatedRefusal(ErrorCode.EVENT_STATE_INVALID, "event is published");
        when(tiers.deactivateTier("tier-1")).thenReturn(Mono.error(refused));

        StepVerifier.create(resolver.deactivateTicketTier("tier-1")).expectErrorMatches(e -> e == refused).verify();
    }
}
