package com.pml.keycloak.authenticator;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

/**
 * REST client for calling Identity Service OTP endpoints.
 * Uses OAuth2 client credentials flow to authenticate with the Identity Service.
 * Used by the Phone OTP Authenticator to request and verify OTPs.
 */
public class OtpServiceClient {

    private static final Logger LOG = Logger.getLogger(OtpServiceClient.class);
    private static final Gson GSON = new Gson();

    private final String otpServiceUrl;
    private final String tokenUrl;
    private final String clientId;
    private final String clientSecret;
    private final HttpClient httpClient;

    // Token caching
    private String accessToken;
    private Instant tokenExpiry;
    private final ReentrantLock tokenLock = new ReentrantLock();

    public OtpServiceClient(String otpServiceUrl) {
        this.otpServiceUrl = otpServiceUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        // Read configuration from environment variables
        this.tokenUrl = System.getenv("KEYCLOAK_TOKEN_URL");
        this.clientId = System.getenv("OTP_CLIENT_ID");
        this.clientSecret = System.getenv("OTP_CLIENT_SECRET");

        LOG.infof("OtpServiceClient initialized - Service URL: %s, Client ID: %s", otpServiceUrl, clientId);
    }

    /**
     * Get access token using client credentials flow.
     * Tokens are cached and refreshed before expiry.
     */
    private String getAccessToken() {
        tokenLock.lock();
        try {
            // Check if we have a valid cached token
            if (accessToken != null && tokenExpiry != null && Instant.now().isBefore(tokenExpiry)) {
                return accessToken;
            }

            // If no token URL configured, skip authentication (for development)
            if (tokenUrl == null || tokenUrl.isEmpty() || clientId == null || clientSecret == null) {
                LOG.warn("Token URL or credentials not configured - skipping authentication");
                return null;
            }

            LOG.debug("Fetching new access token from Keycloak");

            String formData = String.format(
                    "grant_type=client_credentials&client_id=%s&client_secret=%s&scope=%s",
                    URLEncoder.encode(clientId, StandardCharsets.UTF_8),
                    URLEncoder.encode(clientSecret, StandardCharsets.UTF_8),
                    URLEncoder.encode("internal-read internal-write", StandardCharsets.UTF_8)
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(tokenUrl))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(formData))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject tokenResponse = JsonParser.parseString(response.body()).getAsJsonObject();
                this.accessToken = tokenResponse.get("access_token").getAsString();
                int expiresIn = tokenResponse.get("expires_in").getAsInt();
                // Refresh token 30 seconds before expiry
                this.tokenExpiry = Instant.now().plusSeconds(expiresIn - 30);
                LOG.debug("Access token obtained successfully");
                return accessToken;
            } else {
                LOG.errorf("Failed to obtain access token: %d - %s", response.statusCode(), response.body());
                return null;
            }
        } catch (IOException | InterruptedException e) {
            LOG.errorf(e, "Error obtaining access token");
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        } finally {
            tokenLock.unlock();
        }
    }

    /**
     * Request OTP generation and delivery.
     *
     * @param phoneNumber The phone number to send OTP to
     * @param channel     Delivery channel ("whatsapp" or "sms")
     * @return OtpRequestResult with success/failure status
     */
    public OtpRequestResult requestOtp(String phoneNumber, String channel) {
        try {
            String url = otpServiceUrl + "/api/internal/otp/request";

            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("phoneNumber", phoneNumber);
            requestBody.addProperty("channel", channel);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody)));

            // Add authorization header if token is available
            String token = getAccessToken();
            if (token != null) {
                requestBuilder.header("Authorization", "Bearer " + token);
            }

            HttpRequest request = requestBuilder.build();

            LOG.debugf("Requesting OTP from: %s", url);

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            LOG.debugf("OTP request response status: %d", response.statusCode());

            if (response.statusCode() == 200) {
                JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject();
                return new OtpRequestResult(
                        result.get("success").getAsBoolean(),
                        result.get("status").getAsString(),
                        result.has("cooldownRemaining") ? result.get("cooldownRemaining").getAsInt() : 0,
                        result.has("expiresIn") ? result.get("expiresIn").getAsInt() : 300
                );
            } else {
                LOG.errorf("OTP request failed with status: %d, body: %s", response.statusCode(), response.body());
                return new OtpRequestResult(false, "ERROR", 0, 0);
            }
        } catch (IOException | InterruptedException e) {
            LOG.errorf(e, "Failed to request OTP");
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new OtpRequestResult(false, "ERROR", 0, 0);
        }
    }

    /**
     * Verify OTP code.
     *
     * @param phoneNumber The phone number
     * @param otp         The OTP code to verify
     * @return OtpVerifyResult with valid/invalid status
     */
    public OtpVerifyResult verifyOtp(String phoneNumber, String otp) {
        try {
            String url = otpServiceUrl + "/api/internal/otp/verify";

            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("phoneNumber", phoneNumber);
            requestBody.addProperty("otp", otp);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody)));

            // Add authorization header if token is available
            String token = getAccessToken();
            if (token != null) {
                requestBuilder.header("Authorization", "Bearer " + token);
            }

            HttpRequest request = requestBuilder.build();

            LOG.debugf("Verifying OTP at: %s", url);

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            LOG.debugf("OTP verify response status: %d", response.statusCode());

            if (response.statusCode() == 200) {
                JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject();
                return new OtpVerifyResult(
                        result.get("valid").getAsBoolean(),
                        result.get("status").getAsString()
                );
            } else {
                LOG.errorf("OTP verification failed with status: %d, body: %s", response.statusCode(), response.body());
                return new OtpVerifyResult(false, "ERROR");
            }
        } catch (IOException | InterruptedException e) {
            LOG.errorf(e, "Failed to verify OTP");
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new OtpVerifyResult(false, "ERROR");
        }
    }

    /**
     * Check OTP status (cooldown).
     *
     * @param phoneNumber The phone number
     * @return OtpStatusResult with can send status
     */
    public OtpStatusResult getOtpStatus(String phoneNumber) {
        try {
            String url = otpServiceUrl + "/api/internal/otp/status/" + phoneNumber;

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .GET();

            // Add authorization header if token is available
            String token = getAccessToken();
            if (token != null) {
                requestBuilder.header("Authorization", "Bearer " + token);
            }

            HttpRequest request = requestBuilder.build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject();
                return new OtpStatusResult(
                        result.get("canSend").getAsBoolean(),
                        result.has("cooldownRemaining") ? result.get("cooldownRemaining").getAsInt() : 0
                );
            } else {
                return new OtpStatusResult(true, 0); // Assume can send on error
            }
        } catch (IOException | InterruptedException e) {
            LOG.errorf(e, "Failed to get OTP status");
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new OtpStatusResult(true, 0);
        }
    }

    // Result classes

    public static class OtpRequestResult {
        private final boolean success;
        private final String status;
        private final int cooldownRemaining;
        private final int expiresIn;

        public OtpRequestResult(boolean success, String status, int cooldownRemaining, int expiresIn) {
            this.success = success;
            this.status = status;
            this.cooldownRemaining = cooldownRemaining;
            this.expiresIn = expiresIn;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getStatus() {
            return status;
        }

        public int getCooldownRemaining() {
            return cooldownRemaining;
        }

        public int getExpiresIn() {
            return expiresIn;
        }
    }

    public static class OtpVerifyResult {
        private final boolean valid;
        private final String status;

        public OtpVerifyResult(boolean valid, String status) {
            this.valid = valid;
            this.status = status;
        }

        public boolean isValid() {
            return valid;
        }

        public String getStatus() {
            return status;
        }
    }

    public static class OtpStatusResult {
        private final boolean canSend;
        private final int cooldownRemaining;

        public OtpStatusResult(boolean canSend, int cooldownRemaining) {
            this.canSend = canSend;
            this.cooldownRemaining = cooldownRemaining;
        }

        public boolean isCanSend() {
            return canSend;
        }

        public int getCooldownRemaining() {
            return cooldownRemaining;
        }
    }
}
