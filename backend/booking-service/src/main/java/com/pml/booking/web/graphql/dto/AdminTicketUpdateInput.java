package com.pml.booking.web.graphql.dto;

/**
 * Admin Ticket Update Input DTO
 *
 * Business Intent: Allows admins to update ticket details for customer service
 * purposes like correcting buyer information or updating category.
 */
public record AdminTicketUpdateInput(
        String buyerName,
        String buyerEmail,
        String buyerPhone,
        String ticketCategoryCode,
        String notes
) {}
