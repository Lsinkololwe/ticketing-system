package com.pml.booking.web.graphql.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Input for creating a ticket reservation (ET-TKT-001 R6).
 */
public record ReserveTicketsInput(
    @NotBlank(message = "Event ID is required")
    String eventId,

    @NotNull(message = "Ticket selections are required")
    @Size(min = 1, message = "At least one ticket selection is required")
    List<TicketSelectionInput> selections,

    String promoCode,

    /**
     * The client's key for this purchase.
     *
     * <p>Client-supplied rather than server-generated, which looks backwards
     * until you consider which retry actually needs it: the one where the
     * client never saw a response. A key the server invented travelled back in
     * the reply the buyer's phone never received, so it cannot help them ask
     * "did that go through?" — only a key they chose before sending can.
     *
     * <p>Required by R6. Nullable in the record so a malformed request produces
     * a validation message rather than a deserialisation failure, but a blank
     * one is refused before any inventory moves.
     */
    String idempotencyKey
) {}
