package com.pml.booking.web.graphql.dto;

import com.pml.booking.domain.model.HolderMessage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MessageTicketHoldersInput(
        @NotBlank(message = "Subject is required") @Size(max = 80, message = "Subject must be at most 80 characters") String subject,
        @NotBlank(message = "Message is required") @Size(max = 500, message = "Message must be at most 500 characters") String body,
        HolderMessage.Segment segment,
        @Size(max = 64) String ticketTierId
) {
}
