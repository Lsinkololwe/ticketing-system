package com.pml.identity.auth.delivery;

import com.pml.identity.domain.enums.ContactType;

/** How a code reaches a person: WhatsApp or email. SMS is not a channel. */
public enum DeliveryChannel {
    WHATSAPP,
    EMAIL;

    /** The channel a contact of this type is natively reachable on. */
    public static DeliveryChannel nativeFor(ContactType type) {
        return type == ContactType.EMAIL ? EMAIL : WHATSAPP;
    }
}
