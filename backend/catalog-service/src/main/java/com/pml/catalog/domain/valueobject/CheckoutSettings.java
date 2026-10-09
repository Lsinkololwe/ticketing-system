package com.pml.catalog.domain.valueobject;

/**
 * What the organizer asks of a buyer at checkout for this event.
 *
 * @param maxTicketsPerOrder  a cap below the platform's, or {@code null} to use the platform's
 * @param collectHolderNames  whether the buyer names each ticket holder
 * @param extraQuestion       one optional question put to the buyer
 */
public record CheckoutSettings(Integer maxTicketsPerOrder, boolean collectHolderNames, String extraQuestion) {
}
