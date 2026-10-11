package com.pml.booking.web.graphql.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Hand one ticket to a registered person, named by the WhatsApp number or email they signed up with.
 * The recipient is resolved server side; the client never supplies an account id.
 */
public record InitiateTicketTransferInput(
        @NotBlank(message = "Ticket is required") String ticketId,
        @NotNull(message = "Say how you are naming the recipient") TransferChannel channel,
        @NotBlank(message = "Recipient is required") @Size(max = 254) String recipient,
        @Size(max = 200, message = "Note must be at most 200 characters") String note,
        @NotBlank(message = "Idempotency key is required") String idempotencyKey
) {

    public enum TransferChannel { WHATSAPP, EMAIL }
}
