package com.pml.identity.service;

import com.pml.identity.config.IdentityLimitsProperties;
import com.pml.identity.domain.enums.ContactType;
import com.pml.identity.domain.model.Contact;
import com.pml.identity.domain.model.User;
import com.pml.identity.repository.ContactRepository;
import com.pml.identity.repository.UserRepository;
import com.pml.identity.security.ContactHasher;
import com.pml.shared.error.ValidationRefusal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * "Who has this contact": the same normaliser as sign-in, a short name and a masked contact, and the
 * same empty answer for a contact nobody holds and one whose account may not be messaged.
 */
@Tag("L1")
@Tag("ET-PLT-007")
@DisplayName("A contact lookup answers the account, a short name and a mask, and nothing for an ineligible account")
class UserContactLookupTest {

    private final ContactHasher hasher = new ContactHasher("lookup-test-hash-key");
    private ContactRepository contacts;
    private UserRepository users;
    private UserContactLookup lookup;

    @BeforeEach
    void wire() {
        contacts = mock(ContactRepository.class);
        users = mock(UserRepository.class);
        lookup = new UserContactLookup(hasher, contacts, users, new IdentityLimitsProperties());
    }

    private void owner(String phone, User user) {
        String key = hasher.hash(ContactType.WHATSAPP, phone);
        when(contacts.findVerifiedOwner(ContactType.WHATSAPP, key))
                .thenReturn(Mono.just(Contact.builder().accountId(user.getId()).type(ContactType.WHATSAPP).build()));
        when(users.findById(user.getId())).thenReturn(Mono.just(user));
    }

    private static User user(String id, String first, String last, boolean active) {
        User user = new User();
        user.setId(id);
        user.setFirstName(first);
        user.setLastName(last);
        user.setActive(active);
        return user;
    }

    @Test
    @DisplayName("a number typed nationally finds the account and answers first name, initial and the masked contact")
    void found() {
        owner("+260971234567", user("u1", "Mary", "Kabwe", true));

        UserContactLookup.Match match = lookup.lookup("whatsapp", "0971 234 567").block();

        assertThat(match.userId()).isEqualTo("u1");
        assertThat(match.displayName()).isEqualTo("Mary K.");
        assertThat(match.maskedContact()).contains("*").doesNotContain("971234567").doesNotContain("0971 234 567");
    }

    @Test
    @DisplayName("a contact nobody holds and a contact held by a disabled account both answer empty")
    void notFoundAndDisabledAreIndistinguishable() {
        when(contacts.findVerifiedOwner(any(), any())).thenReturn(Mono.empty());
        owner("+260972000000", user("u2", "Joe", "Banda", false));

        assertThat(lookup.lookup("WHATSAPP", "+260972000000").blockOptional()).isEmpty();
        assertThat(lookup.lookup("WHATSAPP", "+260973999999").blockOptional()).isEmpty();
    }

    @Test
    @DisplayName("a locked or suspended owner answers empty")
    void lockedOwnerAnswersEmpty() {
        User locked = user("u3", "Ann", "Phiri", true);
        locked.setLocked(true);
        owner("+260974000000", locked);

        assertThat(lookup.lookup("WHATSAPP", "+260974000000").blockOptional()).isEmpty();
    }

    @Test
    @DisplayName("a channel that is not WHATSAPP or EMAIL is refused before any lookup")
    void channelMustBeAContactType() {
        assertThatThrownBy(() -> lookup.lookup("SMS", "+260971234567").block()).isInstanceOf(ValidationRefusal.class);
        assertThatThrownBy(() -> lookup.lookup(null, "+260971234567").block()).isInstanceOf(ValidationRefusal.class);
        verify(contacts, never()).findVerifiedOwner(any(), any());
    }

    @Test
    @DisplayName("a value that is not a contact answers empty without a lookup")
    void malformedValueAnswersEmpty() {
        assertThat(lookup.lookup("EMAIL", "not-an-email").blockOptional()).isEmpty();
        verify(contacts, never()).findVerifiedOwner(any(), any());
    }

    @Test
    @DisplayName("the short name falls back to the display name and is null when the account has none")
    void shortName() {
        User named = user("u4", null, null, true);
        named.setDisplayName("Chanda Mulenga Zulu");
        assertThat(UserContactLookup.shortName(named)).isEqualTo("Chanda Z.");
        assertThat(UserContactLookup.shortName(user("u5", null, null, true))).isNull();
    }
}
