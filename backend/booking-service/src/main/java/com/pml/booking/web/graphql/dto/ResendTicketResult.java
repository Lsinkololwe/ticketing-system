package com.pml.booking.web.graphql.dto;

public record ResendTicketResult(String ticketId, String ticketNumber, String status, String channel, String destination) {
}
