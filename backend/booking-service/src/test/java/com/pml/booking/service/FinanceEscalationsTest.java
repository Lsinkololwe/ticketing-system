package com.pml.booking.service;

import com.pml.booking.domain.enums.AlertPriority;
import com.pml.booking.infrastructure.client.IdentityServiceClient;
import com.pml.booking.infrastructure.client.dto.FinanceLeadContact;
import com.pml.booking.service.FinanceEscalations.Escalation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("L1")
@Tag("ET-FIN-004")
@DisplayName("D-32 · an escalation reaches the finance channel, then every finance lead by email and WhatsApp")
class FinanceEscalationsTest {

    private final NotificationService notifications = mock(NotificationService.class);
    private final IdentityServiceClient identity = mock(IdentityServiceClient.class);
    private final FinanceEscalations escalations = new FinanceEscalations(notifications, identity);

    private final Escalation escalation = new Escalation(AlertPriority.CRITICAL, "Chargeback undecided",
            "Chargeback cb-1 has no decision.", "finance.chargeback-undecided", "chargeback:cb-1", "record-1");

    @Test
    @DisplayName("the channel is copied, each lead is emailed, and identity is asked to WhatsApp the leads")
    void everyoneIsTold() {
        when(notifications.sendAdminAlert(any(AlertPriority.class), anyString(), anyString())).thenReturn(Mono.empty());
        when(notifications.sendEmail(anyString(), anyString(), anyString())).thenReturn(Mono.empty());
        when(identity.financeLeadContacts()).thenReturn(Flux.just(
                new FinanceLeadContact("lead-1", "lead-1@example.test"), new FinanceLeadContact("lead-2", "lead-2@example.test")));
        when(identity.notifyFinanceLeads(anyString(), anyString(), anyString())).thenReturn(Mono.empty());

        StepVerifier.create(escalations.escalate(escalation)).verifyComplete();

        InOrder order = inOrder(notifications, identity);
        order.verify(notifications).sendAdminAlert(AlertPriority.CRITICAL, "Chargeback undecided", "Chargeback cb-1 has no decision.");
        order.verify(notifications).sendEmail("lead-1@example.test", "Chargeback undecided", "Chargeback cb-1 has no decision.");
        order.verify(notifications).sendEmail("lead-2@example.test", "Chargeback undecided", "Chargeback cb-1 has no decision.");
        order.verify(identity).notifyFinanceLeads("finance.chargeback-undecided", "chargeback:cb-1", "record-1");
    }

    @Test
    @DisplayName("an unreachable identity service fails the escalation, so its activity retries")
    void anUnreachableIdentityServiceFails() {
        when(notifications.sendAdminAlert(any(AlertPriority.class), anyString(), anyString())).thenReturn(Mono.empty());
        when(identity.financeLeadContacts()).thenReturn(Flux.error(new IllegalStateException("identity unavailable")));

        StepVerifier.create(escalations.escalate(escalation))
                .expectErrorMessage("identity unavailable")
                .verify();

        verify(identity, never()).notifyFinanceLeads(anyString(), anyString(), anyString());
    }
}
