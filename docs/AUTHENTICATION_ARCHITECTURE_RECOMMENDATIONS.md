# Authentication Architecture Recommendations
## Event Ticketing Platform - Keycloak Integration

**Version:** 1.0
**Date:** February 2026
**Classification:** Technical Architecture Document
**Audience:** Security Engineers, Senior Developers, Solution Architects

---

## Executive Summary

This document provides architectural recommendations for implementing email and phone-based authentication flows in a mobile-first event ticketing platform using Keycloak as the single identity provider. The analysis addresses security considerations, OAuth 2.0 best practices, and practical implementation patterns suitable for production deployment.

---

## 1. Keycloak Native Capabilities Assessment

### 1.1 What Keycloak Provides Natively

| Authentication Method | Native Support | Implementation |
|-----------------------|----------------|----------------|
| Username/Password | ✅ Yes | Built-in browser flow |
| TOTP (Authenticator Apps) | ✅ Yes | Google Authenticator, FreeOTP |
| WebAuthn/Passkeys | ✅ Yes | FIDO2, Face ID, fingerprint |
| Recovery Codes | ✅ Yes | One-time backup codes |
| Social/Identity Providers | ✅ Yes | Google, Facebook, Apple, etc. |
| Kerberos/LDAP | ✅ Yes | Enterprise SSO |
| **SMS OTP** | ❌ No | Requires Custom SPI |
| **WhatsApp OTP** | ❌ No | Requires Custom SPI |
| **Phone Passwordless** | ❌ No | Requires Custom SPI |

### 1.2 Critical Clarification: OTP Types

**Keycloak's "OTP" is NOT SMS-based:**
```
Keycloak OTP = TOTP (Time-based One-Time Password)
             = Authenticator app generates codes locally
             = No external SMS/messaging required

Phone OTP    = Server generates code
             = Delivered via SMS/WhatsApp
             = Requires external messaging provider
```

This means **your existing OTP infrastructure (OtpService + MessagingService + Redis) is required** and cannot be replaced by Keycloak's native OTP. However, we can integrate this infrastructure into Keycloak's authentication flow via Custom SPI.

### 1.3 Leveraging Your Existing Infrastructure

Your current implementation has valuable components that should be preserved:

| Component | Current Location | Purpose | Reuse Strategy |
|-----------|-----------------|---------|----------------|
| `OtpService` | identity-service | Generate/verify OTP, Redis storage | Expose as internal REST API for Keycloak |
| `MessagingService` | identity-service | WhatsApp/SMS delivery | Called by OtpService |
| `Redis` | Infrastructure | OTP storage with TTL | Continue using |
| `PhoneAuthController` | identity-service | REST API for OTP | Refactor to internal service |

**Recommended Architecture:**

```
┌─────────────────────────────────────────────────────────────────┐
│                        KEYCLOAK                                  │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │              Custom Phone OTP Authenticator              │    │
│  │  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐  │    │
│  │  │ Phone Input │───▶│ Request OTP │───▶│ Verify OTP  │  │    │
│  │  │    Form     │    │   (REST)    │    │   (REST)    │  │    │
│  │  └─────────────┘    └──────┬──────┘    └──────┬──────┘  │    │
│  └────────────────────────────┼──────────────────┼─────────┘    │
│                               │                  │               │
└───────────────────────────────┼──────────────────┼───────────────┘
                                │                  │
                                ▼                  ▼
┌───────────────────────────────────────────────────────────────────┐
│                     IDENTITY SERVICE                               │
│  ┌─────────────────────────────────────────────────────────────┐  │
│  │                   Internal OTP API                           │  │
│  │  POST /api/internal/otp/request   (Generate + Send OTP)     │  │
│  │  POST /api/internal/otp/verify    (Verify OTP)              │  │
│  └─────────────────────────────────────────────────────────────┘  │
│                               │                                    │
│                               ▼                                    │
│  ┌─────────────┐    ┌─────────────────┐    ┌─────────────────┐   │
│  │  OtpService │───▶│ MessagingService│───▶│ WhatsApp/Twilio │   │
│  └──────┬──────┘    └─────────────────┘    └─────────────────┘   │
│         │                                                          │
│         ▼                                                          │
│  ┌─────────────┐                                                  │
│  │    Redis    │  (OTP Storage with TTL)                          │
│  └─────────────┘                                                  │
└───────────────────────────────────────────────────────────────────┘
```

---

## 2. Current Architecture Assessment

### 1.1 System Overview

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  Mobile App     │────▶│   API Gateway   │────▶│ Identity Service│
│  (React Native) │     │  (Spring Cloud) │     │  (Spring Boot)  │
└────────┬────────┘     └─────────────────┘     └────────┬────────┘
         │                                               │
         │              ┌─────────────────┐              │
         └─────────────▶│    Keycloak     │◀─────────────┘
                        │  (Auth Server)  │
                        └─────────────────┘
```

### 1.2 Current Implementation State

**Identity Service Configuration:**
- Port: 8083
- OAuth2 Resource Server with JWT validation
- Keycloak realm: `event-ticketing`
- Redis for OTP storage (TTL-based expiration)
- MongoDB for user profile data

**Current Phone OTP Flow (PhoneAuthController.java):**
```java
// CURRENT IMPLEMENTATION - SECURITY GAP
private Mono<OtpVerifyResponse> generateTokensForUser(User user) {
    String scopes = "openid profile email events-read tickets-purchase bookings-read phone";
    return keycloakAuthService.getServiceToken(scopes)  // ⚠️ Returns SERVICE token
        .map(tokenResponse -> new OtpVerifyResponse(
            true, "Authentication successful",
            tokenResponse.getAccessToken(),    // Service token, not user token
            tokenResponse.getRefreshToken(),
            mapUserToDto(user)
        ));
}
```

**Identified Security Gap:**
The current phone OTP verification returns a **service account token** (client_credentials grant) rather than a **user-specific token**. This means:
- All users authenticated via phone OTP share the same token identity
- No user-specific claims in the token
- Audit trails attribute actions to the service account, not the user
- Authorization policies based on user attributes cannot be enforced

---

## 2. Email Login Flow

### 2.1 Recommended Approach: Authorization Code Flow with PKCE

**Why PKCE is mandatory for mobile applications:**
- Mobile apps are public clients (cannot securely store client secrets)
- PKCE prevents authorization code interception attacks
- Compliant with RFC 7636 and OAuth 2.0 Security Best Current Practice (RFC 9700)
- Native support in Keycloak with S256 challenge method

### 2.2 Flow Diagram

```
┌──────────┐    ┌──────────────┐    ┌──────────────┐
│  Mobile  │    │   System     │    │   Keycloak   │
│   App    │    │   Browser    │    │              │
└────┬─────┘    └──────┬───────┘    └──────┬───────┘
     │                 │                    │
     │  1. Generate code_verifier (random)  │
     │  2. code_challenge = SHA256(verifier)│
     │                 │                    │
     │  3. Open browser with auth URL       │
     │────────────────▶│                    │
     │                 │  4. GET /auth      │
     │                 │   ?client_id=...   │
     │                 │   &redirect_uri=.. │
     │                 │   &code_challenge= │
     │                 │   &code_challenge_method=S256
     │                 │───────────────────▶│
     │                 │                    │
     │                 │  5. Login page     │
     │                 │◀───────────────────│
     │                 │                    │
     │                 │  6. User authenticates
     │                 │───────────────────▶│
     │                 │                    │
     │                 │  7. Redirect with code
     │                 │◀───────────────────│
     │                 │                    │
     │  8. App captures redirect (deep link)│
     │◀────────────────│                    │
     │                 │                    │
     │  9. POST /token                      │
     │     grant_type=authorization_code    │
     │     code=...                         │
     │     code_verifier=...   (proves we initiated request)
     │─────────────────────────────────────▶│
     │                                      │
     │  10. Access Token + Refresh Token    │
     │◀─────────────────────────────────────│
```

### 2.3 Implementation Requirements

**Keycloak Client Configuration:**
```yaml
client_id: "event-ticketing-mobile"
access_type: public
standard_flow_enabled: true
direct_access_grants_enabled: false  # Disable ROPC
pkce_code_challenge_method: S256
valid_redirect_uris:
  - "com.eventtickets.app://oauth2redirect"
  - "exp://127.0.0.1:19000/--/oauth2redirect"  # Expo development
web_origins:
  - "+"  # Allow all origins for mobile
```

**Mobile App Implementation (expo-auth-session):**
```typescript
import * as AuthSession from 'expo-auth-session';
import * as Crypto from 'expo-crypto';

const discovery = {
  authorizationEndpoint: 'https://keycloak.example.com/realms/event-ticketing/protocol/openid-connect/auth',
  tokenEndpoint: 'https://keycloak.example.com/realms/event-ticketing/protocol/openid-connect/token',
  revocationEndpoint: 'https://keycloak.example.com/realms/event-ticketing/protocol/openid-connect/logout',
};

const config: AuthSession.AuthRequestConfig = {
  clientId: 'event-ticketing-mobile',
  scopes: ['openid', 'profile', 'email', 'phone'],
  redirectUri: AuthSession.makeRedirectUri({
    scheme: 'com.eventtickets.app',
    path: 'oauth2redirect',
  }),
  usePKCE: true,  // Automatically generates code_verifier and code_challenge
};
```

### 2.4 Security Considerations

| Consideration | Recommendation | Rationale |
|--------------|----------------|-----------|
| Token Storage | Secure Enclave / Keychain | Mobile device secure storage APIs |
| Session Duration | Access: 5-15 min, Refresh: 7-30 days | Balance UX with security |
| Refresh Strategy | Silent refresh before expiry | Prevent session interruption |
| Logout | Revoke all tokens + clear local storage | Complete session termination |
| Deep Link Validation | Verify state parameter | Prevent CSRF on redirect |

---

## 3. Phone OTP Login Flow

### 3.1 Architecture Options Analysis

| Approach | Security Level | Complexity | Token Type | Recommendation |
|----------|---------------|------------|------------|----------------|
| **A. Custom Keycloak Authenticator SPI** | ★★★★★ | High | User Token | **Recommended** |
| **B. Token Exchange (Impersonation)** | ★★★☆☆ | Medium | User Token | Acceptable |
| **C. Direct Access Grant (ROPC)** | ★★☆☆☆ | Low | User Token | **Deprecated** |
| **D. Service Token (Current)** | ★☆☆☆☆ | Low | Service Token | **Not Acceptable** |

### 3.2 Recommended: Custom Keycloak Authenticator SPI

**Why this approach:**
- Phone OTP verification happens entirely within Keycloak
- Standard OAuth2 flows are used (no workarounds)
- User gets proper user-specific tokens
- Full audit trail in Keycloak
- Supports MFA combinations (phone + another factor)

**Architecture:**

```
┌──────────┐    ┌──────────────┐    ┌──────────────┐    ┌───────────┐
│  Mobile  │    │   Keycloak   │    │   Identity   │    │   Redis   │
│   App    │    │              │    │   Service    │    │           │
└────┬─────┘    └──────┬───────┘    └──────┬───────┘    └─────┬─────┘
     │                 │                    │                  │
     │  1. Initiate phone auth              │                  │
     │────────────────▶│                    │                  │
     │                 │                    │                  │
     │  2. Custom auth form (phone input)   │                  │
     │◀────────────────│                    │                  │
     │                 │                    │                  │
     │  3. Submit phone number              │                  │
     │────────────────▶│                    │                  │
     │                 │                    │                  │
     │                 │  4. Request OTP generation            │
     │                 │───────────────────▶│                  │
     │                 │                    │  5. Generate OTP │
     │                 │                    │─────────────────▶│
     │                 │                    │  6. Send via WhatsApp
     │                 │  7. OTP sent       │                  │
     │                 │◀───────────────────│                  │
     │                 │                    │                  │
     │  8. OTP input form                   │                  │
     │◀────────────────│                    │                  │
     │                 │                    │                  │
     │  9. Submit OTP                       │                  │
     │────────────────▶│                    │                  │
     │                 │                    │                  │
     │                 │  10. Verify OTP    │                  │
     │                 │───────────────────▶│                  │
     │                 │                    │  11. Check Redis │
     │                 │                    │─────────────────▶│
     │                 │                    │  12. Valid/Invalid│
     │                 │  13. Verification result              │
     │                 │◀───────────────────│                  │
     │                 │                    │                  │
     │  14. Redirect with auth code (PKCE flow)                │
     │◀────────────────│                    │                  │
     │                 │                    │                  │
     │  15. Exchange code for tokens        │                  │
     │────────────────▶│                    │                  │
     │                 │                    │                  │
     │  16. User-specific Access + Refresh tokens              │
     │◀────────────────│                    │                  │
```

**Custom Authenticator SPI Implementation:**

```java
// PhoneOtpAuthenticatorFactory.java
public class PhoneOtpAuthenticatorFactory implements AuthenticatorFactory {

    public static final String PROVIDER_ID = "phone-otp-authenticator";

    @Override
    public Authenticator create(KeycloakSession session) {
        return new PhoneOtpAuthenticator(session);
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayType() {
        return "Phone OTP Authentication";
    }

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return new AuthenticationExecutionModel.Requirement[] {
            AuthenticationExecutionModel.Requirement.REQUIRED,
            AuthenticationExecutionModel.Requirement.ALTERNATIVE,
            AuthenticationExecutionModel.Requirement.DISABLED
        };
    }
}

// PhoneOtpAuthenticator.java
public class PhoneOtpAuthenticator implements Authenticator {

    private final KeycloakSession session;
    private final OtpServiceClient otpServiceClient;

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        if (context.getHttpRequest().getDecodedFormParameters().containsKey("phone")) {
            // Phone number submitted - request OTP
            String phone = context.getHttpRequest().getDecodedFormParameters().getFirst("phone");
            requestOtp(context, phone);
        } else if (context.getHttpRequest().getDecodedFormParameters().containsKey("otp")) {
            // OTP submitted - verify
            verifyOtp(context);
        } else {
            // Initial state - show phone input form
            Response challenge = context.form()
                .setAttribute("realm", context.getRealm())
                .createForm("phone-otp-input.ftl");
            context.challenge(challenge);
        }
    }

    private void requestOtp(AuthenticationFlowContext context, String phone) {
        // Call Identity Service to generate and send OTP
        boolean sent = otpServiceClient.requestOtp(phone);

        if (sent) {
            // Store phone in auth session for verification step
            context.getAuthenticationSession().setAuthNote("phone_number", phone);

            Response challenge = context.form()
                .setAttribute("phone", maskPhone(phone))
                .createForm("phone-otp-verify.ftl");
            context.challenge(challenge);
        } else {
            context.failureChallenge(AuthenticationFlowError.INTERNAL_ERROR,
                context.form().setError("Failed to send OTP").createForm("phone-otp-input.ftl"));
        }
    }

    private void verifyOtp(AuthenticationFlowContext context) {
        String phone = context.getAuthenticationSession().getAuthNote("phone_number");
        String otp = context.getHttpRequest().getDecodedFormParameters().getFirst("otp");

        boolean valid = otpServiceClient.verifyOtp(phone, otp);

        if (valid) {
            // Find or create user
            UserModel user = findOrCreateUser(context, phone);
            context.setUser(user);
            context.success();
        } else {
            context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS,
                context.form().setError("Invalid OTP").createForm("phone-otp-verify.ftl"));
        }
    }

    private UserModel findOrCreateUser(AuthenticationFlowContext context, String phone) {
        RealmModel realm = context.getRealm();

        // Search by phone attribute
        List<UserModel> users = session.users()
            .searchForUserByUserAttributeStream(realm, "phone_number", phone)
            .collect(Collectors.toList());

        if (!users.isEmpty()) {
            return users.get(0);
        }

        // Create new user
        UserModel user = session.users().addUser(realm, generateUsername(phone));
        user.setSingleAttribute("phone_number", phone);
        user.setSingleAttribute("phone_verified", "true");
        user.setEnabled(true);

        // Assign default role
        RoleModel customerRole = realm.getRole("CUSTOMER");
        if (customerRole != null) {
            user.grantRole(customerRole);
        }

        return user;
    }
}
```

**Keycloak Authentication Flow Configuration:**

```json
{
  "alias": "phone-otp-browser",
  "description": "Browser flow with phone OTP option",
  "providerId": "basic-flow",
  "topLevel": true,
  "builtIn": false,
  "authenticationExecutions": [
    {
      "authenticator": "auth-cookie",
      "requirement": "ALTERNATIVE",
      "priority": 10
    },
    {
      "authenticator": "identity-provider-redirector",
      "requirement": "ALTERNATIVE",
      "priority": 20
    },
    {
      "flowAlias": "forms",
      "requirement": "ALTERNATIVE",
      "priority": 30,
      "autheticatorFlow": true
    }
  ],
  "subFlows": [
    {
      "alias": "forms",
      "description": "Username/password or phone OTP",
      "providerId": "basic-flow",
      "authenticationExecutions": [
        {
          "authenticator": "auth-username-password-form",
          "requirement": "ALTERNATIVE",
          "priority": 10
        },
        {
          "authenticator": "phone-otp-authenticator",
          "requirement": "ALTERNATIVE",
          "priority": 20
        }
      ]
    }
  ]
}
```

### 3.2.1 Internal OTP API (Identity Service)

The custom Keycloak authenticator calls your existing OTP infrastructure via internal REST endpoints. These endpoints should be secured for internal service-to-service communication only.

**InternalOtpController.java:**
```java
@RestController
@RequestMapping("/api/internal/otp")
@PreAuthorize("hasRole('INTERNAL_SERVICE')")  // Service-to-service only
public class InternalOtpController {

    private final OtpService otpService;
    private final MessagingService messagingService;

    /**
     * Request OTP generation and delivery.
     * Called by Keycloak Phone OTP Authenticator.
     */
    @PostMapping("/request")
    public Mono<OtpRequestResult> requestOtp(@RequestBody OtpRequestDto request) {
        String phone = normalizePhoneNumber(request.getPhoneNumber());
        String channel = request.getChannel() != null ? request.getChannel() : "whatsapp";

        return otpService.canSendOtp(phone)
            .flatMap(canSend -> {
                if (!canSend) {
                    return otpService.getCooldownRemaining(phone)
                        .map(remaining -> new OtpRequestResult(false, "COOLDOWN", remaining.intValue()));
                }

                return otpService.generateOtp(phone)
                    .flatMap(otp -> messagingService.sendOtp(phone, otp, channel))
                    .flatMap(sent -> {
                        if (sent) {
                            return otpService.setCooldown(phone)
                                .thenReturn(new OtpRequestResult(true, "SENT", 300));
                        }
                        return Mono.just(new OtpRequestResult(false, "DELIVERY_FAILED", 0));
                    });
            });
    }

    /**
     * Verify OTP.
     * Called by Keycloak Phone OTP Authenticator.
     */
    @PostMapping("/verify")
    public Mono<OtpVerifyResult> verifyOtp(@RequestBody OtpVerifyDto request) {
        String phone = normalizePhoneNumber(request.getPhoneNumber());

        return otpService.verifyOtp(phone, request.getOtp())
            .map(valid -> new OtpVerifyResult(valid, valid ? "VALID" : "INVALID"));
    }

    @Data
    public static class OtpRequestDto {
        private String phoneNumber;
        private String channel;  // "whatsapp" or "sms"
    }

    @Data
    public static class OtpVerifyDto {
        private String phoneNumber;
        private String otp;
    }

    @Data
    @AllArgsConstructor
    public static class OtpRequestResult {
        private boolean success;
        private String status;  // SENT, COOLDOWN, DELIVERY_FAILED
        private int expiresIn;
    }

    @Data
    @AllArgsConstructor
    public static class OtpVerifyResult {
        private boolean valid;
        private String status;  // VALID, INVALID, EXPIRED
    }
}
```

**OtpServiceClient (Keycloak Side):**
```java
/**
 * REST client for calling Identity Service OTP endpoints.
 * Used by the custom Phone OTP Authenticator within Keycloak.
 */
public class OtpServiceClient {

    private final String otpServiceUrl;
    private final String serviceToken;
    private final HttpClient httpClient;

    public OtpServiceClient(String otpServiceUrl, String serviceToken) {
        this.otpServiceUrl = otpServiceUrl;
        this.serviceToken = serviceToken;
        this.httpClient = HttpClient.newHttpClient();
    }

    public boolean requestOtp(String phoneNumber, String channel) {
        try {
            String json = String.format(
                "{\"phoneNumber\":\"%s\",\"channel\":\"%s\"}",
                phoneNumber, channel
            );

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(otpServiceUrl + "/api/internal/otp/request"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + serviceToken)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

            HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                // Parse JSON response
                JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject();
                return result.get("success").getAsBoolean();
            }
            return false;
        } catch (Exception e) {
            LOG.error("Failed to request OTP", e);
            return false;
        }
    }

    public boolean verifyOtp(String phoneNumber, String otp) {
        try {
            String json = String.format(
                "{\"phoneNumber\":\"%s\",\"otp\":\"%s\"}",
                phoneNumber, otp
            );

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(otpServiceUrl + "/api/internal/otp/verify"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + serviceToken)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

            HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject();
                return result.get("valid").getAsBoolean();
            }
            return false;
        } catch (Exception e) {
            LOG.error("Failed to verify OTP", e);
            return false;
        }
    }
}
```

**API Gateway Route for Internal OTP (Restricted):**
```yaml
# api-gateway application.yml
- id: identity-internal-otp
  uri: ${IDENTITY_SERVICE_URL:http://localhost:8083}
  predicates:
    - Path=/api/internal/otp/**
    - Header=X-Internal-Service, true  # Only allow internal calls
  filters:
    - name: CircuitBreaker
      args:
        name: identityCircuitBreaker
```

### 3.3 Alternative: Token Exchange with Impersonation

**When to use:**
- If custom Keycloak SPI development is not feasible
- For faster initial implementation
- When backend OTP verification is preferred

**Security Requirements:**
- Enable Token Exchange feature in Keycloak
- Grant impersonation permission to identity-service client ONLY
- Implement strict validation before impersonation
- Audit all impersonation requests

**Implementation:**

```java
// KeycloakAuthService.java - Token Exchange for verified phone user
public Mono<TokenResponse> exchangeTokenForUser(String userId) {
    return webClient.post()
        .uri(tokenEndpoint)
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(BodyInserters
            .fromFormData("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange")
            .with("client_id", clientId)
            .with("client_secret", clientSecret)
            .with("requested_subject", userId)  // User to get token for
            .with("requested_token_type", "urn:ietf:params:oauth:token-type:access_token")
            .with("audience", "event-ticketing-mobile"))
        .retrieve()
        .bodyToMono(TokenResponse.class);
}

// PhoneAuthController.java - Updated implementation
private Mono<OtpVerifyResponse> generateTokensForUser(User user) {
    // Get user's Keycloak ID (must be synced)
    String keycloakUserId = user.getKeycloakId();

    if (keycloakUserId == null) {
        return Mono.error(new IllegalStateException("User not synced with Keycloak"));
    }

    // Exchange for user-specific token
    return keycloakAuthService.exchangeTokenForUser(keycloakUserId)
        .map(tokenResponse -> new OtpVerifyResponse(
            true,
            "Authentication successful",
            tokenResponse.getAccessToken(),
            tokenResponse.getRefreshToken(),
            mapUserToDto(user)
        ));
}
```

**Keycloak Client Permissions for Token Exchange:**

```
Client: identity-service
├── Service Account Enabled: true
├── Client Roles:
│   └── realm-management:
│       ├── impersonation
│       └── view-users
└── Token Exchange Permission:
    └── Can exchange tokens for: event-ticketing-mobile
```

### 3.4 Not Recommended: Direct Access Grant (ROPC)

**Why ROPC is deprecated (RFC 9700):**

> "The Resource Owner Password Credentials grant type MUST NOT be used. This grant type was originally designed for migration from legacy applications but exposes the resource owner's credentials to the client application."

**Specific risks:**
1. **Credential exposure**: OTP would be treated as a "password", but OTPs are one-time and time-limited by design - not a true password
2. **No browser context**: Cannot leverage Keycloak's security features (device detection, geo-blocking, brute force protection at auth server level)
3. **OAuth 2.1 removal**: This grant type is removed from OAuth 2.1 specification
4. **Audit limitations**: Keycloak cannot distinguish between legitimate app and credential theft

---

## 4. Security Hardening Recommendations

### 4.1 OTP Security

| Parameter | Recommended Value | Rationale |
|-----------|------------------|-----------|
| OTP Length | 6 digits | Balance between security and usability |
| OTP Validity | 5 minutes | Short enough to prevent replay |
| Max Attempts | 3 per OTP | Prevent brute force |
| Cooldown | 60 seconds | Prevent enumeration |
| Storage | Redis with TTL | Automatic cleanup |

**Redis OTP Storage Pattern:**
```
Key: otp:{phone_hash}
Value: {otp}:{attempts}:{timestamp}
TTL: 300 seconds
```

### 4.2 Rate Limiting

```yaml
# API Gateway rate limiting
OTP Request:
  rate: 3 requests
  interval: 60 seconds
  scope: per phone number

OTP Verification:
  rate: 5 requests
  interval: 60 seconds
  scope: per phone number

Token Endpoint:
  rate: 100 requests
  interval: 60 seconds
  scope: per IP
```

### 4.3 Token Configuration

```yaml
# Keycloak Realm Settings
access_token:
  lifespan: 300  # 5 minutes

refresh_token:
  lifespan: 2592000  # 30 days
  revoke_on_logout: true

sso_session:
  idle_timeout: 1800  # 30 minutes
  max_lifespan: 36000  # 10 hours
```

### 4.4 Phone Number Security

```java
// Always normalize before storage/comparison
private String normalizePhoneNumber(String phone) {
    // Remove all non-digit characters except +
    String cleaned = phone.replaceAll("[^0-9+]", "");

    // Ensure international format
    if (!cleaned.startsWith("+")) {
        // Default country code (Zambia)
        cleaned = "+260" + cleaned.replaceFirst("^0", "");
    }

    return cleaned;
}

// Hash for Redis keys (prevent enumeration via key scanning)
private String hashPhoneNumber(String phone) {
    return Hashing.sha256()
        .hashString(phone + PHONE_HASH_SALT, StandardCharsets.UTF_8)
        .toString();
}
```

---

## 5. Implementation Roadmap

### Phase 1: Email Login (PKCE Flow)
1. Configure Keycloak public client with PKCE
2. Implement expo-auth-session integration in mobile app
3. Configure deep links for OAuth redirect
4. Implement secure token storage
5. Add silent refresh mechanism

### Phase 2: Phone OTP Login (Choose One)

**Option A: Custom Authenticator (Recommended)**
1. Develop Keycloak Authenticator SPI
2. Create FreeMarker templates for phone/OTP forms
3. Configure authentication flow with phone OTP option
4. Deploy custom authenticator JAR to Keycloak
5. Configure Identity Service as OTP provider (REST endpoint for Keycloak)

**Option B: Token Exchange**
1. Enable Token Exchange in Keycloak
2. Configure impersonation permissions
3. Update PhoneAuthController to use token exchange
4. Ensure user Keycloak ID synchronization
5. Add comprehensive audit logging

### Phase 3: Security Hardening
1. Implement rate limiting at gateway level
2. Configure Redis OTP security
3. Add phone number hash salting
4. Set up monitoring and alerting
5. Conduct security penetration testing

---

## 6. Decision Matrix

| Criterion | Custom Authenticator | Token Exchange | Current (Service Token) |
|-----------|---------------------|----------------|------------------------|
| **Security** | Excellent | Good | Poor |
| **Standards Compliance** | Full OAuth2/OIDC | RFC 8693 | Non-compliant |
| **User Identity** | Proper user tokens | Proper user tokens | Service account |
| **Audit Trail** | Complete | Complete | Misleading |
| **Implementation Effort** | High | Medium | Existing |
| **Maintenance** | Keycloak upgrade testing | Moderate | Low |
| **Scalability** | Excellent | Good | Good |

**Recommendation:** Implement **Custom Keycloak Authenticator SPI** for phone OTP authentication. This provides the highest security level, full OAuth2/OIDC compliance, and proper user identity management. The initial development investment is justified by the long-term security and maintainability benefits.

---

## 7. Appendix

### A. Keycloak Version Compatibility

| Feature | Minimum Version | Notes |
|---------|----------------|-------|
| PKCE | 4.6+ | S256 method |
| Token Exchange | 4.0+ | Preview feature, enable with flag |
| Custom Authenticator SPI | All versions | Stable API |
| JavaScript Authenticator | 15.0+ | Alternative to Java SPI |

### B. Related RFCs and Standards

- RFC 6749: OAuth 2.0 Authorization Framework
- RFC 7636: Proof Key for Code Exchange (PKCE)
- RFC 8693: OAuth 2.0 Token Exchange
- RFC 9700: OAuth 2.0 Security Best Current Practice
- OpenID Connect Core 1.0

### C. Security Checklist

- [ ] PKCE enabled with S256 method
- [ ] Direct Access Grants disabled for mobile client
- [ ] Token Exchange permissions scoped minimally
- [ ] OTP rate limiting implemented
- [ ] Phone numbers stored hashed in Redis keys
- [ ] Refresh tokens bound to device/client
- [ ] Logout revokes all tokens
- [ ] TLS 1.3 enforced for all endpoints
- [ ] Certificate pinning in mobile app
- [ ] Penetration test conducted

---

*Document prepared for technical review by security engineering team.*
