package com.pml.booking.infrastructure.client.dto;

/**
 * Identity's answer to a notification request.
 *
 * @param status      {@code QUEUED} once accepted for delivery, {@code DUPLICATE} when the discriminator was
 *                    already sent, {@code NO_VERIFIED_CONTACT} when the recipient has nowhere verified to
 *                    receive it
 * @param channel     {@code WHATSAPP}, {@code SMS} or {@code EMAIL}; null when nothing was sent
 * @param destination the destination masked for display, never the raw contact; null when nothing was sent
 * @param recipients  how many people the request reached (one for a direct notification)
 */
public record NotificationReceipt(String status, String channel, String destination, int recipients) {

    public static NotificationReceipt queued(String channel, String destination, int recipients) {
        return new NotificationReceipt("QUEUED", channel, destination, recipients);
    }
}
