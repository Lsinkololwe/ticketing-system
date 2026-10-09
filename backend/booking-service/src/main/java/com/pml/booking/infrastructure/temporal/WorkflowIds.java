package com.pml.booking.infrastructure.temporal;

/**
 * Workflow ids are business ids.
 *
 * <p>One function per workflow type, so no call site builds an id by hand. A deterministic id is
 * what makes a repeated start reach the same execution and lets a webhook or a consumer address the
 * execution from the business document alone.
 */
public final class WorkflowIds {

    private WorkflowIds() {
    }

    public static String purchase(String reservationId) {
        return "purchase/" + require(reservationId);
    }

    public static String payout(String payoutRequestId) {
        return "payout/" + require(payoutRequestId);
    }

    public static String bankVerification(String bankAccountId) {
        return "bank-verification/" + require(bankAccountId);
    }

    public static String eventFinance(String eventId) {
        return "event-finance/" + require(eventId);
    }

    public static String cancellationRefunds(String eventId) {
        return "cancellation-refunds/" + require(eventId);
    }

    /** One automatic refund of late money per reservation (and so per payment: an intent has one reservation). */
    public static String lateRefund(String reservationId) {
        return "late-refund/" + require(reservationId);
    }

    public static String refund(String refundRequestId) {
        return "refund/" + require(refundRequestId);
    }

    public static String ticketTransfer(String transferId) {
        return "ticket-transfer/" + require(transferId);
    }

    public static String chargeback(String chargebackId) {
        return "chargeback/" + require(chargebackId);
    }

    public static String reconciliation(String type, String window) {
        return "recon/" + require(type) + "/" + require(window);
    }

    private static String require(String part) {
        if (part == null || part.isBlank()) {
            throw new IllegalArgumentException("a workflow id needs its business identifier");
        }
        return part;
    }
}
