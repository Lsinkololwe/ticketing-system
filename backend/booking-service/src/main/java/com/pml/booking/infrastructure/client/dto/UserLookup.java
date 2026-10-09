package com.pml.booking.infrastructure.client.dto;

/**
 * Identity's answer to "who has this verified contact": enough to address a transfer and show the
 * sender a masked confirmation, and nothing else.
 *
 * @param userId        the recipient's account id
 * @param displayName   first name and initial, for example {@code Mary K.}
 * @param maskedContact the contact the sender typed, masked
 */
public record UserLookup(String userId, String displayName, String maskedContact) {
}
