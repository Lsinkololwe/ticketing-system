package com.pml.identity.auth.delivery;

/** A provider could not deliver. The message names the channel and a category, never a contact, code or response body. */
public class DeliveryFailedException extends RuntimeException {

    public DeliveryFailedException(String message) {
        super(message, null, false, false);
    }
}
