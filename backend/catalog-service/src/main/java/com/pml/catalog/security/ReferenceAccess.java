package com.pml.catalog.security;

import com.pml.catalog.domain.enums.ReferenceType;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * Which reference lists a visitor with no token may read (ET-PLT-014-R11).
 *
 * <p>The gateway admits the {@code referenceData} and {@code referenceDataByParent} root fields without
 * a token, because the buyer's phone field, checkout and event filters need some lists before anyone signs
 * in. This is the second half of that decision: a signed-out caller may read only the types listed here.
 * Everything else (banks, verification documents, legal and organizer types, reason codes, tax rates, roles,
 * report periods and the money workflow statuses) needs a signed-in caller, and each of those is one line
 * to move when the buyer comes to need it. A type is public by being named, never by omission.
 */
@Component("referenceAccess")
public class ReferenceAccess {

    /** The lists the storefront needs signed out. */
    public static final Set<ReferenceType> PUBLIC_TYPES = Set.copyOf(EnumSet.of(
            ReferenceType.COUNTRY,
            ReferenceType.CURRENCY,
            ReferenceType.LANGUAGE,
            ReferenceType.TIMEZONE,
            ReferenceType.PROVINCE,
            ReferenceType.CITY,
            ReferenceType.MOBILE_MONEY_OPERATOR,
            ReferenceType.EVENT_TYPE,
            ReferenceType.EVENT_CATEGORY,
            ReferenceType.MUSIC_GENRE,
            ReferenceType.AGE_RESTRICTION,
            ReferenceType.TICKET_TIER_CATEGORY,
            ReferenceType.REFUND_REASON,
            ReferenceType.NOTIFICATION_CHANNEL,
            ReferenceType.NOTIFICATION_CATEGORY,
            ReferenceType.CARD_SCHEME,
            ReferenceType.TICKET_STATUS,
            ReferenceType.RESERVATION_STATUS,
            ReferenceType.EVENT_STATUS));

    /** Used from {@code @PreAuthorize("isAuthenticated() or @referenceAccess.isPublic(#type)")}. */
    public boolean isPublic(ReferenceType type) {
        return type != null && PUBLIC_TYPES.contains(type);
    }
}
