package com.pml.identity.account;

import com.pml.identity.domain.enums.ContactType;
import com.pml.identity.domain.model.Contact;
import com.pml.shared.error.ErrorCode;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The decisions of contact management that need neither a database nor a server (ET-IDN-004 R3, R5).
 * Decided for implementation on 2026-10-04, see specs/FINDINGS.md F-044.
 */
public final class ContactRules {

    private ContactRules() {
    }

    /**
     * Whether a contact released at {@code releasedAt} still keeps other accounts from claiming it.
     * A zero or negative quarantine switches the rule off.
     */
    public static boolean quarantined(Instant releasedAt, Duration quarantine, Instant now) {
        if (releasedAt == null || quarantine == null || quarantine.isZero() || quarantine.isNegative()) {
            return false;
        }
        return releasedAt.plus(quarantine).isAfter(now);
    }

    /** Seconds still to wait before a code sent at {@code sentAt} may be asked for again; 0 when it may. */
    public static long resendWaitSeconds(Instant sentAt, Duration resendAfter, Instant now) {
        if (sentAt == null || resendAfter == null || resendAfter.isZero() || resendAfter.isNegative()) {
            return 0;
        }
        long wait = Duration.between(now, sentAt.plus(resendAfter)).toMillis();
        return wait <= 0 ? 0 : (wait + 999) / 1000;
    }

    /** When the quarantine of a contact released at {@code releasedAt} ends. */
    public static Instant quarantineEnds(Instant releasedAt, Duration quarantine) {
        return releasedAt.plus(quarantine == null ? Duration.ZERO : quarantine);
    }

    /** The contacts that count: verified and not released. */
    public static List<Contact> active(List<Contact> contacts) {
        return contacts.stream()
                .filter(contact -> contact.getVerifiedAt() != null && contact.getReleasedAt() == null)
                .toList();
    }

    /** The primary among the active contacts, if any. */
    public static Optional<Contact> primary(List<Contact> contacts) {
        return active(contacts).stream().filter(Contact::isPrimary).findFirst();
    }

    /**
     * Why a removal is refused, if it is: the contact must be one of the account's, and not its last
     * verified one (decision c).
     */
    public static Optional<ErrorCode> removalRefusal(List<Contact> contacts, String contactId) {
        List<Contact> active = active(contacts);
        if (active.stream().noneMatch(contact -> contact.getId().equals(contactId))) {
            return Optional.of(ErrorCode.CONTACT_UNKNOWN);
        }
        return active.size() <= 1 ? Optional.of(ErrorCode.LAST_VERIFIED_CONTACT) : Optional.empty();
    }

    /** The contact that becomes primary when the primary leaves: the oldest remaining one. */
    public static Optional<Contact> successor(List<Contact> remaining) {
        return active(remaining).stream().min(Comparator.comparing(Contact::getCreatedAt,
                Comparator.nullsLast(Comparator.naturalOrder())));
    }

    public static boolean hasType(List<Contact> contacts, ContactType type) {
        return active(contacts).stream().anyMatch(contact -> contact.getType() == type);
    }
}
