package com.pml.identity.account;

import com.pml.identity.domain.enums.ContactType;
import com.pml.identity.domain.model.Contact;
import com.pml.shared.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("L1")
@Tag("ET-IDN-004")
@DisplayName("ET-IDN-004-R3 · the contact rules: quarantine, last verified contact, one primary")
class ContactRulesTest {

    private static final Instant NOW = Instant.parse("2026-10-04T10:00:00Z");

    private static Contact contact(String id, ContactType type, boolean primary, Instant created, Instant released) {
        return Contact.builder().id(id).accountId("a1").type(type).primary(primary)
                .verifiedAt(created).createdAt(created).releasedAt(released).build();
    }

    @Test
    @DisplayName("a released contact is quarantined for the configured period, and not a moment longer")
    void quarantineWindow() {
        Instant released = NOW.minus(Duration.ofDays(29));
        assertThat(ContactRules.quarantined(released, Duration.ofDays(30), NOW)).isTrue();
        assertThat(ContactRules.quarantined(NOW.minus(Duration.ofDays(30)), Duration.ofDays(30), NOW)).as("ends exactly at 30 days").isFalse();
        assertThat(ContactRules.quarantined(NOW.minus(Duration.ofDays(31)), Duration.ofDays(30), NOW)).isFalse();
        assertThat(ContactRules.quarantineEnds(released, Duration.ofDays(30))).isEqualTo(NOW.plus(Duration.ofDays(1)));
    }

    @Test
    @DisplayName("a zero quarantine (the test profile) or an unreleased contact is never quarantined")
    void quarantineOff() {
        assertThat(ContactRules.quarantined(NOW, Duration.ZERO, NOW)).isFalse();
        assertThat(ContactRules.quarantined(NOW, null, NOW)).isFalse();
        assertThat(ContactRules.quarantined(null, Duration.ofDays(30), NOW)).isFalse();
    }

    @Test
    @DisplayName("a code may be asked for again only after the resend wait; zero switches the wait off")
    void resendWait() {
        assertThat(ContactRules.resendWaitSeconds(NOW.minusSeconds(60), Duration.ofMinutes(5), NOW)).isEqualTo(240);
        assertThat(ContactRules.resendWaitSeconds(NOW.minusSeconds(300), Duration.ofMinutes(5), NOW)).isZero();
        assertThat(ContactRules.resendWaitSeconds(NOW.minusMillis(299_500), Duration.ofMinutes(5), NOW)).as("rounds up").isEqualTo(1);
        assertThat(ContactRules.resendWaitSeconds(NOW, Duration.ZERO, NOW)).isZero();
        assertThat(ContactRules.resendWaitSeconds(null, Duration.ofMinutes(5), NOW)).isZero();
    }

    @Test
    @DisplayName("the last verified contact cannot be removed; a contact that is not the account's is unknown")
    void removal() {
        Contact only = contact("c1", ContactType.WHATSAPP, true, NOW, null);
        assertThat(ContactRules.removalRefusal(List.of(only), "c1")).contains(ErrorCode.LAST_VERIFIED_CONTACT);
        assertThat(ContactRules.removalRefusal(List.of(only), "other")).contains(ErrorCode.CONTACT_UNKNOWN);

        Contact second = contact("c2", ContactType.EMAIL, false, NOW.plusSeconds(5), null);
        assertThat(ContactRules.removalRefusal(List.of(only, second), "c1")).isEmpty();
        assertThat(ContactRules.removalRefusal(List.of(only, second), "c2")).isEmpty();
    }

    @Test
    @DisplayName("a released contact no longer counts: with one released, the other is the last verified one")
    void releasedDoesNotCount() {
        Contact released = contact("c1", ContactType.WHATSAPP, false, NOW, NOW);
        Contact live = contact("c2", ContactType.EMAIL, true, NOW, null);
        assertThat(ContactRules.active(List.of(released, live))).containsExactly(live);
        assertThat(ContactRules.removalRefusal(List.of(released, live), "c2")).contains(ErrorCode.LAST_VERIFIED_CONTACT);
        assertThat(ContactRules.removalRefusal(List.of(released, live), "c1")).as("a released contact is not removable").contains(ErrorCode.CONTACT_UNKNOWN);
    }

    @Test
    @DisplayName("when the primary leaves, the oldest remaining contact succeeds it")
    void successor() {
        Contact old = contact("c2", ContactType.EMAIL, false, NOW.minusSeconds(100), null);
        Contact newer = contact("c3", ContactType.WHATSAPP, false, NOW, null);
        assertThat(ContactRules.successor(List.of(newer, old))).contains(old);
        assertThat(ContactRules.successor(List.of())).isEmpty();
    }

    @Test
    @DisplayName("the primary is the one active contact flagged primary; a released primary is ignored")
    void primary() {
        Contact releasedPrimary = contact("c1", ContactType.WHATSAPP, true, NOW, NOW);
        Contact livePrimary = contact("c2", ContactType.EMAIL, true, NOW, null);
        assertThat(ContactRules.primary(List.of(releasedPrimary, livePrimary))).contains(livePrimary);
        assertThat(ContactRules.primary(List.of(releasedPrimary))).isEmpty();
        assertThat(ContactRules.hasType(List.of(releasedPrimary, livePrimary), ContactType.WHATSAPP)).isFalse();
        assertThat(ContactRules.hasType(List.of(releasedPrimary, livePrimary), ContactType.EMAIL)).isTrue();
    }
}
