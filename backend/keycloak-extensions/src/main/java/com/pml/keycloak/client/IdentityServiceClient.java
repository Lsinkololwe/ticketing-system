package com.pml.keycloak.client;

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
 * REST client for calling Identity Service endpoints.
 *
 * Uses OAuth2 client credentials flow to authenticate with the Identity Service.
 * Supports:
 * - OTP endpoints (request, verify, status) - used by Phone OTP Authenticator
 * - User sync endpoints - used by UserSyncEventListener
 *
 * This client is used by both the Phone OTP Authenticator and the UserSyncEventListener
 * to communicate with the Identity Service.
 */
public class IdentityServiceClient {

    private static final Logger LOG = Logger.getLogger(IdentityServiceClient.class);
    private static final Gson GSON = new Gson();

    private final String serviceUrl;
    private final String tokenUrl;
    private final String clientId;
    private final String clientSecret;
    private final HttpClient httpClient;

    // Token caching
    private String accessToken;
    private Instant tokenExpiry;
    private final ReentrantLock tokenLock = new ReentrantLock();

    public IdentityServiceClient(String serviceUrl) {
        this.serviceUrl = serviceUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        // Read configuration from environment variables
        this.tokenUrl = System.getenv("KEYCLOAK_TOKEN_URL");
        this.clientId = System.getenv("OTP_CLIENT_ID");
        this.clientSecret = System.getenv("OTP_CLIENT_SECRET");

        LOG.infof("IdentityServiceClient initialized - Service URL: %s, Client ID: %s", serviceUrl, clientId);
    }

    // ========================================================================
    // TOKEN MANAGEMENT
    // ========================================================================

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

    // ========================================================================
    // OTP ENDPOINTS (Used by Phone OTP Authenticator)
    // ========================================================================

    /**
     * Request OTP generation and delivery.
     *
     * @param phoneNumber The phone number to send OTP to
     * @param channel     Delivery channel ("whatsapp" or "sms")
     * @return OtpRequestResult with success/failure status
     */
    public OtpRequestResult requestOtp(String phoneNumber, String channel) {
        try {
            String url = serviceUrl + "/api/internal/otp/request";

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
            String url = serviceUrl + "/api/internal/otp/verify";

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
            String url = serviceUrl + "/api/internal/otp/status/" + phoneNumber;

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

    // ========================================================================
    // USER SYNC ENDPOINTS (Used by UserSyncEventListener)
    // ========================================================================

    /**
     * Sync a user from Keycloak to MongoDB.
     * Called when user data changes in Keycloak (registration, profile update, etc.)
     *
     * @param keycloakUserId The Keycloak user ID
     * @param eventType      The type of event that triggered the sync
     * @return SyncResult indicating success or failure
     */
    public SyncResult syncUser(String keycloakUserId, String eventType) {
        try {
            String url = serviceUrl + "/api/internal/keycloak/sync/user";

            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("keycloakUserId", keycloakUserId);
            requestBody.addProperty("eventType", eventType);

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

            LOG.infof("Syncing user %s (event: %s) to Identity Service", keycloakUserId, eventType);

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            LOG.debugf("Sync user response status: %d", response.statusCode());

            if (response.statusCode() == 200 || response.statusCode() == 201) {
                LOG.infof("User %s synced successfully", keycloakUserId);
                return new SyncResult(true, "User synced successfully");
            } else {
                String errorMsg = String.format("Failed to sync user: %d - %s", response.statusCode(), response.body());
                LOG.errorf(errorMsg);
                return new SyncResult(false, errorMsg);
            }
        } catch (IOException | InterruptedException e) {
            LOG.errorf(e, "Failed to sync user %s", keycloakUserId);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new SyncResult(false, "Error: " + e.getMessage());
        }
    }

    /**
     * Notify Identity Service of a Keycloak event.
     * This endpoint can handle various event types and take appropriate action.
     *
     * @param event The Keycloak event details
     * @return SyncResult indicating success or failure
     */
    public SyncResult notifyEvent(KeycloakEventData event) {
        try {
            String url = serviceUrl + "/api/internal/keycloak/sync/event";

            String jsonBody = GSON.toJson(event);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

            // Add authorization header if token is available
            String token = getAccessToken();
            if (token != null) {
                requestBuilder.header("Authorization", "Bearer " + token);
            }

            HttpRequest request = requestBuilder.build();

            LOG.infof("Notifying event %s for user %s to Identity Service", event.getEventType(), event.getUserId());

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            LOG.debugf("Notify event response status: %d", response.statusCode());

            if (response.statusCode() == 200 || response.statusCode() == 202) {
                LOG.infof("Event %s for user %s processed successfully", event.getEventType(), event.getUserId());
                return new SyncResult(true, "Event processed successfully");
            } else {
                String errorMsg = String.format("Failed to process event: %d - %s", response.statusCode(), response.body());
                LOG.errorf(errorMsg);
                return new SyncResult(false, errorMsg);
            }
        } catch (IOException | InterruptedException e) {
            LOG.errorf(e, "Failed to notify event for user %s", event.getUserId());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new SyncResult(false, "Error: " + e.getMessage());
        }
    }

    /**
     * Notify Identity Service of a user login.
     * Updates lastLoginAt timestamp in MongoDB.
     *
     * @param keycloakUserId The Keycloak user ID
     * @return SyncResult indicating success or failure
     */
    public SyncResult notifyLogin(String keycloakUserId) {
        KeycloakEventData event = new KeycloakEventData();
        event.setUserId(keycloakUserId);
        event.setEventType("LOGIN");
        event.setTimestamp(Instant.now().toEpochMilli());
        return notifyEvent(event);
    }

    /**
     * Notify Identity Service to delete a user.
     * Called when a user is deleted in Keycloak.
     *
     * @param keycloakUserId The Keycloak user ID
     * @return SyncResult indicating success or failure
     */
    public SyncResult notifyUserDeleted(String keycloakUserId) {
        KeycloakEventData event = new KeycloakEventData();
        event.setUserId(keycloakUserId);
        event.setEventType("DELETE");
        event.setTimestamp(Instant.now().toEpochMilli());
        return notifyEvent(event);
    }

    // ========================================================================
    // RESULT CLASSES
    // ========================================================================

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

    public static class SyncResult {
        private final boolean success;
        private final String message;

        public SyncResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * Data object for Keycloak event information.
     */
    public static class KeycloakEventData {
        private String userId;
        private String eventType;
        private String operationType;  // For AdminEvents: CREATE, UPDATE, DELETE
        private String resourceType;   // For AdminEvents: users, roles, etc.
        private String resourcePath;   // For AdminEvents: path to the resource
        private long timestamp;
        private String realmId;
        private String clientId;
        private String ipAddress;

        // Getters and setters
        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getEventType() {
            return eventType;
        }

        public void setEventType(String eventType) {
            this.eventType = eventType;
        }

        public String getOperationType() {
            return operationType;
        }

        public void setOperationType(String operationType) {
            this.operationType = operationType;
        }

        public String getResourceType() {
            return resourceType;
        }

        public void setResourceType(String resourceType) {
            this.resourceType = resourceType;
        }

        public String getResourcePath() {
            return resourcePath;
        }

        public void setResourcePath(String resourcePath) {
            this.resourcePath = resourcePath;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }

        public String getRealmId() {
            return realmId;
        }

        public void setRealmId(String realmId) {
            this.realmId = realmId;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getIpAddress() {
            return ipAddress;
        }

        public void setIpAddress(String ipAddress) {
            this.ipAddress = ipAddress;
        }
    }
}
