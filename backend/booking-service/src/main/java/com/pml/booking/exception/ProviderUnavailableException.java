package com.pml.booking.exception;

/**
 * The mobile-money provider could not be reached, so it gave no answer at all.
 *
 * <p>Unlike a refusal, this says nothing about the request itself: the same call, sent again with the
 * same provider reference, may succeed. Activity code lets it propagate so the activity is retried.
 */
public class ProviderUnavailableException extends RuntimeException {

    public ProviderUnavailableException(String message) {
        super(message);
    }
}
