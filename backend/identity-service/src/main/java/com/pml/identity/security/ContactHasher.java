package com.pml.identity.security;

import com.pml.identity.config.IdentityContactProperties;
import com.pml.identity.domain.enums.ContactType;
import com.pml.shared.util.ContactKeys;
import com.pml.shared.util.ContactMasking;
import com.pml.shared.util.Emails;
import com.pml.shared.util.PhoneNumbers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Optional;

/**
 * Turns what a person typed into a contact identity: the normalised value, its contact key and
 * its display mask (CONTRACT 3). The normalised value must not be logged or stored outside
 * {@code valueEncrypted}; callers keep {@link Normalized#value()} only as long as they need it.
 */
@Component
public class ContactHasher {

    /**
     * @param value  the normalised value (E.164 or lower-case email) - sensitive
     * @param key    HMAC-SHA256 contact key, lower-case hex
     * @param masked for display
     * @param region ISO country for phone contacts, null for email
     */
    public record Normalized(ContactType type, String value, String key, String masked, String region) {
        @Override
        public String toString() {
            return "Normalized[" + type + ", " + masked + "]";
        }
    }

    private final ContactKeys keys;

    @Autowired
    public ContactHasher(IdentityContactProperties properties) {
        this(properties.getHashKey());
    }

    public ContactHasher(String hashKey) {
        this.keys = new ContactKeys(hashKey);
    }

    /** The contact key for an already-normalised value. */
    public String hash(ContactType type, String normalized) {
        return keys.contactKey(type.name(), normalized);
    }

    public String mask(ContactType type, String normalized) {
        return ContactMasking.mask(type.name(), normalized);
    }

    /**
     * Parse and normalise a contact.
     *
     * @param type        WHATSAPP or EMAIL; null means EMAIL when the value contains {@code @}, else WHATSAPP
     * @param regionHint  ISO country used for national phone numbers, may be null
     * @param allowedCountries phone countries accepted; null accepts any
     * @return empty when the value is not a valid contact of that type
     */
    public Optional<Normalized> normalize(String raw, ContactType type, String regionHint,
                                          Collection<String> allowedCountries) {
        if (raw == null) {
            return Optional.empty();
        }
        ContactType resolved = type != null ? type : (raw.indexOf('@') >= 0 ? ContactType.EMAIL : ContactType.WHATSAPP);
        if (resolved == ContactType.EMAIL) {
            return Emails.normalize(raw).map(email ->
                    new Normalized(ContactType.EMAIL, email, hash(ContactType.EMAIL, email),
                            mask(ContactType.EMAIL, email), null));
        }
        return PhoneNumbers.parseMobile(raw, regionHint, allowedCountries).map(phone ->
                new Normalized(ContactType.WHATSAPP, phone.e164(), hash(ContactType.WHATSAPP, phone.e164()),
                        mask(ContactType.WHATSAPP, phone.e164()), phone.region()));
    }
}
