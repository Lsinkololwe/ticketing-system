package com.pml.identity.service;

import com.pml.identity.domain.enums.ContactType;
import com.pml.identity.domain.model.Contact;
import com.pml.identity.domain.model.User;
import com.pml.identity.repository.ContactRepository;
import com.pml.identity.repository.NotificationRepository;
import com.pml.identity.repository.UserRepository;
import com.pml.identity.workflow.notify.NotificationProcess;
import com.pml.identity.workflow.notify.NotificationWorkflow.Request;
import com.pml.shared.error.ValidationRefusal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Messaging an account another service names by id: the receipt, the deduplication key, what is
 * refused and what an unreachable account looks like, with the repositories and the start of the
 * message replaced.
 */
@Tag("L1")
@Tag("ET-PLT-007")
@DisplayName("A service-to-user notification is sent once, to a verified contact, and reveals only the masked destination")
class UserNotifierTest {

    private UserRepository users;
    private ContactRepository contacts;
    private NotificationRepository notifications;
    private NotificationProcess process;
    private UserNotifier notifier;

    @BeforeEach
    void wire() {
        users = mock(UserRepository.class);
        contacts = mock(ContactRepository.class);
        notifications = mock(NotificationRepository.class);
        process = mock(NotificationProcess.class);
        when(notifications.insert(any(com.pml.identity.domain.model.Notification.class)))
                .thenAnswer(call -> Mono.just(call.getArgument(0)));
        when(notifications.deleteById(anyString())).thenReturn(Mono.empty());
        when(process.start(any())).thenReturn(true);
        notifier = new UserNotifier(users, contacts, notifications, process,
                java.time.Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), java.time.ZoneOffset.UTC));
    }

    private void account(String id, boolean active, boolean locked, Contact... held) {
        User user = new User();
        user.setId(id);
        user.setActive(active);
        user.setLocked(locked);
        when(users.findById(id)).thenReturn(Mono.just(user));
        when(contacts.findByAccountIdAndReleasedAtIsNull(id)).thenReturn(Flux.just(held));
    }

    private static Contact contact(ContactType type, String masked, boolean verified) {
        return Contact.builder().type(type).valueMasked(masked).primary(true)
                .verifiedAt(verified ? Instant.now() : null).build();
    }

    @Test
    @DisplayName("a reachable account is queued on its first channel with the masked destination and the keyed request")
    void queuedWithMaskedDestination() {
        account("u1", true, false, contact(ContactType.WHATSAPP, "+260 97* ***456", true));

        UserNotifier.Receipt receipt = notifier.notifyUser("ticket.resend", "t1:9", "u1",
                Map.of("ticketNumber", "TKT-1", "eventTitle", "Jazz Night")).block();

        assertThat(receipt).isEqualTo(new UserNotifier.Receipt("QUEUED", "WHATSAPP", "+260 97* ***456", 1));
        ArgumentCaptor<Request> sent = ArgumentCaptor.forClass(Request.class);
        verify(process).start(sent.capture());
        assertThat(sent.getValue().deduplicationKey()).isEqualTo("ticket.resend:t1:9:u1");
        assertThat(sent.getValue().recipientUserId()).isEqualTo("u1");
        // The request carries ids only; the rendered text is the stored notification's, not the workflow's.
        assertThat(sent.getValue().toString()).doesNotContain("Jazz Night");
        ArgumentCaptor<com.pml.identity.domain.model.Notification> stored =
                ArgumentCaptor.forClass(com.pml.identity.domain.model.Notification.class);
        verify(notifications).insert(stored.capture());
        assertThat(stored.getValue().getId()).isEqualTo("notify:ticket.resend:t1:9:u1");
        assertThat(stored.getValue().getBody()).contains("Jazz Night");
    }

    @Test
    @DisplayName("a call whose message was already requested answers DUPLICATE and starts nothing")
    void duplicateStartsNothing() {
        account("u1", true, false, contact(ContactType.EMAIL, "j***@gmail.com", true));
        when(notifications.insert(any(com.pml.identity.domain.model.Notification.class)))
                .thenReturn(Mono.error(new org.springframework.dao.DuplicateKeyException("exists")));

        UserNotifier.Receipt receipt = notifier.notifyUser("ticket.resend", "t1:9", "u1", null).block();

        assertThat(receipt.status()).isEqualTo("DUPLICATE");
        assertThat(receipt.destination()).isNull();
        verify(process, never()).start(any());
    }

    @Test
    @DisplayName("a start that finds the key already used answers DUPLICATE")
    void alreadyStartedIsDuplicate() {
        account("u1", true, false, contact(ContactType.EMAIL, "j***@gmail.com", true));
        when(process.start(any())).thenReturn(false);

        assertThat(notifier.notifyUser("ticket.resend", "t1:9", "u1", null).block().status()).isEqualTo("DUPLICATE");
    }

    @Test
    @DisplayName("a workflow that cannot be started leaves no stored message behind, so a retry begins afresh")
    void failedStartRemovesTheRow() {
        account("u1", true, false, contact(ContactType.EMAIL, "j***@gmail.com", true));
        when(process.start(any())).thenThrow(new IllegalStateException("temporal down"));

        assertThatThrownBy(() -> notifier.notifyUser("ticket.resend", "t1:9", "u1", null).block())
                .isInstanceOf(IllegalStateException.class);
        verify(notifications).deleteById("notify:ticket.resend:t1:9:u1");
    }

    @Test
    @DisplayName("an unknown, disabled, locked or contactless account all answer NO_VERIFIED_CONTACT and send nothing")
    void unreachableAccountsAreIndistinguishable() {
        when(users.findById("ghost")).thenReturn(Mono.empty());
        account("disabled", false, false, contact(ContactType.EMAIL, "j***@gmail.com", true));
        account("locked", true, true, contact(ContactType.EMAIL, "j***@gmail.com", true));
        account("unverified", true, false, contact(ContactType.EMAIL, "j***@gmail.com", false));
        account("nowhere", true, false);

        List<UserNotifier.Receipt> receipts = new ArrayList<>();
        for (String id : List.of("ghost", "disabled", "locked", "unverified", "nowhere")) {
            receipts.add(notifier.notifyUser("ticket.resend", "t1:9", id, null).block());
        }

        assertThat(receipts).containsOnly(new UserNotifier.Receipt("NO_VERIFIED_CONTACT", null, null, 0));
        verify(process, never()).start(any());
    }

    @Test
    @DisplayName("a template identity does not hold is refused as a validation error and nothing is sent")
    void unknownTemplateRefused() {
        account("u1", true, false, contact(ContactType.EMAIL, "j***@gmail.com", true));

        assertThatThrownBy(() -> notifier.notifyUser("payout.unheard-of", "t1:9", "u1", null).block())
                .isInstanceOf(ValidationRefusal.class);
        verify(process, never()).start(any());
    }

    @Test
    @DisplayName("params beyond 20 keys, a long key, a long value or a nested value are refused")
    void paramsAreBounded() {
        account("u1", true, false, contact(ContactType.EMAIL, "j***@gmail.com", true));
        Map<String, Object> tooMany = new HashMap<>();
        for (int i = 0; i < 21; i++) {
            tooMany.put("k" + i, "v");
        }

        assertThatThrownBy(() -> notifier.notifyUser("ticket.resend", "d", "u1", tooMany).block())
                .isInstanceOf(ValidationRefusal.class);
        assertThatThrownBy(() -> notifier.notifyUser("ticket.resend", "d", "u1", Map.of("k".repeat(65), "v")).block())
                .isInstanceOf(ValidationRefusal.class);
        assertThatThrownBy(() -> notifier.notifyUser("ticket.resend", "d", "u1", Map.of("note", "x".repeat(501))).block())
                .isInstanceOf(ValidationRefusal.class);
        assertThatThrownBy(() -> notifier.notifyUser("ticket.resend", "d", "u1", Map.of("note", Map.of("a", "b"))).block())
                .isInstanceOf(ValidationRefusal.class);
        verify(process, never()).start(any());
    }

    @Test
    @DisplayName("control characters in a value are replaced and null values are dropped")
    void valuesAreCleaned() {
        account("u1", true, false, contact(ContactType.EMAIL, "j***@gmail.com", true));
        Map<String, Object> params = new HashMap<>();
        params.put("note", "line one\nline two\u0007");
        params.put("expiresAt", null);

        notifier.notifyUser("ticket.resend", "d", "u1", params).block();

        ArgumentCaptor<com.pml.identity.domain.model.Notification> stored =
                ArgumentCaptor.forClass(com.pml.identity.domain.model.Notification.class);
        verify(notifications).insert(stored.capture());
        assertThat(stored.getValue().getBody()).doesNotContainPattern("\\p{Cntrl}");
    }

    @Test
    @DisplayName("a batch of more than 100 accounts is refused before any lookup")
    void batchIsCapped() {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            ids.add("u" + i);
        }

        assertThatThrownBy(() -> notifier.notifyUsers("event.holders.message", "m:0", ids, null).block())
                .isInstanceOf(ValidationRefusal.class);
        verify(users, never()).findById(anyString());
    }

    @Test
    @DisplayName("a batch is deduplicated and answers outcome counts only")
    void batchCountsOutcomes() {
        account("a", true, false, contact(ContactType.EMAIL, "a***@gmail.com", true));
        account("b", true, false, contact(ContactType.WHATSAPP, "+260 97* ***111", true));
        account("c", true, false);
        when(notifications.insert(argThat((com.pml.identity.domain.model.Notification n) ->
                n != null && "notify:event.holders.message:m:0:b".equals(n.getId()))))
                .thenReturn(Mono.error(new org.springframework.dao.DuplicateKeyException("exists")));

        UserNotifier.BatchReceipt receipt = notifier.notifyUsers("event.holders.message", "m:0",
                List.of("a", "b", "c", "a"), Map.of("subject", "Doors", "message", "Doors open at 7")).block();

        assertThat(receipt.queued()).isEqualTo(1);
        assertThat(receipt.duplicate()).isEqualTo(1);
        assertThat(receipt.noVerifiedContact()).isEqualTo(1);
        assertThat(receipt.status()).isEqualTo("QUEUED");
        assertThat(receipt.recipients()).isEqualTo(2);
        assertThat(receipt.channel()).isNull();
        assertThat(receipt.destination()).isNull();
        verify(process, times(1)).start(any());
    }
}
