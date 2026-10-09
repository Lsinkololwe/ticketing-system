package com.pml.booking.web.graphql.dto.checkin;

import com.pml.booking.domain.enums.ValidationMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * One scan presented at a gate.
 *
 * <p>There is deliberately no actor field. The scanning user and their
 * organization both come from the JWT; accepting them here would let a client
 * record an admission under someone else's name.
 *
 * @param eventId   the gate's event — a ticket for another event is refused
 *                  here even though it is a perfectly valid ticket
 * @param code      the ticket number scanned or typed
 * @param method    how it was presented; defaults to QR_ONLINE
 * @param scanId    client-generated id for one physical scan, for offline
 *                  upload idempotency. Supplying it is what stops a retried
 *                  upload from admitting the same person twice
 * @param deviceId  the scanning device, for the reconciliation report
 * @param scannedAt device clock, for offline scans
 * @param reason    required for MANUAL
 */
public record ValidateTicketInput(
        @NotBlank(message = "Event ID is required")
        String eventId,

        @NotBlank(message = "Ticket code is required")
        @Size(max = 128)
        String code,

        ValidationMethod method,

        @Size(max = 128)
        String scanId,

        @Size(max = 128)
        String deviceId,

        Instant scannedAt,

        @Size(max = 500)
        String reason
) {}
