package com.pml.booking.infrastructure.gateway.exception;

/**
 * Exception thrown when no payment gateway is available for a request.
 * <p>
 * This can occur when:
 * - No gateway supports the requested mobile network
 * - All configured gateways are unavailable (health check failed)
 * - Gateway configuration is missing or invalid
 */
public class NoGatewayAvailableException extends RuntimeException {

    private final String network;
    private final String attemptedProviderId;

    public NoGatewayAvailableException(String message) {
        super(message);
        this.network = null;
        this.attemptedProviderId = null;
    }

    public NoGatewayAvailableException(String message, String network) {
        super(message);
        this.network = network;
        this.attemptedProviderId = null;
    }

    public NoGatewayAvailableException(String message, String network, String attemptedProviderId) {
        super(message);
        this.network = network;
        this.attemptedProviderId = attemptedProviderId;
    }

    public NoGatewayAvailableException(String message, Throwable cause) {
        super(message, cause);
        this.network = null;
        this.attemptedProviderId = null;
    }

    public String getNetwork() {
        return network;
    }

    public String getAttemptedProviderId() {
        return attemptedProviderId;
    }
}
