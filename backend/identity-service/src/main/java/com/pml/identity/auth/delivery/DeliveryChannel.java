package com.pml.identity.auth.delivery;

import com.pml.identity.domain.enums.ContactType;

/** How a code reaches a person. SMS is not a channel (D-39). */
public enum DeliveryChannel {
    WHATSAPP,
    EMAIL;

    /** The channel a contact of this type is natively reachable on. */
    public static DeliveryChannel nativeFor(ContactType type) {
        return type == ContactType.EMAIL ? EMAIL : WHATSAPP;
    }
}
