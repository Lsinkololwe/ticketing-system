package com.pml.identity.service;

import com.pml.identity.config.IdentityLimitsProperties;
import com.pml.identity.domain.enums.ContactType;
import com.pml.identity.domain.model.User;
import com.pml.identity.repository.ContactRepository;
import com.pml.identity.repository.UserRepository;
import com.pml.identity.security.ContactHasher;
import com.pml.shared.error.FieldViolation;
import com.pml.shared.error.ValidationRefusal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Answers "who has this verified contact" for a service that addresses an account by what its owner
 * typed, such as a ticket transfer.
 *
 * <p>The answer is the account id, the name reduced to a first name and an initial, and the contact
 * masked. A contact nobody holds, and a contact held by an account that may not be messaged, both
 * answer empty, so the lookup cannot be used to learn which accounts exist or how they stand.
 */
@Slf4j
@Service
public class UserContactLookup {

    static final int MAX_VALUE = 320;

    /** @param displayName first name and initial, for example {@code Mary K.}; null when the account has no name */
    public record Match(String userId, String displayName, String maskedContact) {
    }

    private final ContactHasher hasher;
    private final ContactRepository contacts;
    private final UserRepository users;
    private final IdentityLimitsProperties limits;

    public UserContactLookup(ContactHasher hasher, ContactRepository contacts, UserRepository users,
                             IdentityLimitsProperties limits) {
        this.hasher = hasher;
        this.contacts = contacts;
        this.users = users;
        this.limits = limits;
    }

    public Mono<Match> lookup(String channel, String value) {
        ContactType type = typeOf(channel);
        if (type == null) {
            return Mono.error(new ValidationRefusal(List.of(new FieldViolation("channel", "must be WHATSAPP or EMAIL"))));
        }
        if (value == null || value.isBlank() || value.length() > MAX_VALUE) {
            return Mono.error(new ValidationRefusal(List.of(new FieldViolation("value", "must be 1 to " + MAX_VALUE + " characters"))));
        }
        // The same normaliser sign-in uses, so a number typed any accepted way finds the account it signs in to.
        Optional<ContactHasher.Normalized> normalized = hasher.normalize(value, type, null, limits.getAllowedCountries());
        if (normalized.isEmpty()) {
            return Mono.empty();
        }
        ContactHasher.Normalized contact = normalized.get();
        return contacts.findVerifiedOwner(type, contact.key())
                .flatMap(owner -> users.findById(owner.getAccountId()))
                .filter(UserNotifier::eligible)
                .map(user -> new Match(user.getId(), shortName(user), contact.masked()))
                .doOnNext(found -> log.debug("Contact lookup {} matched an account", contact.key().substring(0, 8)));
    }

    private static ContactType typeOf(String channel) {
        if (channel == null) {
            return null;
        }
        try {
            return ContactType.valueOf(channel.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    /** First name and the first letter of the last name: enough to recognise a person, not to identify one. */
    static String shortName(User user) {
        String first = clean(user.getFirstName());
        String last = clean(user.getLastName());
        if (first == null && last == null && user.getDisplayName() != null) {
            String[] words = user.getDisplayName().trim().split("\\s+");
            first = clean(words.length > 0 ? words[0] : null);
            last = words.length > 1 ? clean(words[words.length - 1]) : null;
        }
        if (first == null) {
            return null;
        }
        return last == null ? first : first + " " + last.substring(0, last.offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT) + ".";
    }

    private static String clean(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.replaceAll("\\p{Cntrl}", " ").trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
