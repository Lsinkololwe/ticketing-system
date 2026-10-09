package com.pml.booking.infrastructure.client.dto;

/** A holder of {@code FINANCE_LEAD}, as identity answers it: who, and where to email them. */
public record FinanceLeadContact(String userId, String email) {
}
