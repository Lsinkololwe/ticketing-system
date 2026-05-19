# Keycloak Phone OTP Authenticator - Technical Implementation Guide

## Document Information

| Field | Value |
|-------|-------|
| **Project** | Event Ticketing Platform |
| **Component** | Phone OTP Authenticator SPI |
| **Keycloak Version** | 26.5.2 |
| **Java Version** | 17 |
| **Spring Boot Version** | 3.5.4 |
| **Last Updated** | March 2026 |

---

## Table of Contents

1. [Overview](#1-overview)
2. [Architecture](#2-architecture)
3. [Technology Stack](#3-technology-stack)
4. [Project Structure](#4-project-structure)
5. [Core Implementation](#5-core-implementation)
6. [FreeMarker Templates](#6-freemarker-templates)
7. [Service Registration](#7-service-registration)
8. [Backend OTP Service Integration](#8-backend-otp-service-integration)
9. [Keycloak Realm Configuration](#9-keycloak-realm-configuration)
10. [Docker Deployment](#10-docker-deployment)
11. [Frontend Integration](#11-frontend-integration)
12. [Testing](#12-testing)
13. [Troubleshooting](#13-troubleshooting)
14. [Security Considerations](#14-security-considerations)

---

## 1. Overview

### 1.1 What is the Phone OTP Authenticator?

The Phone OTP Authenticator is a custom Keycloak SPI (Service Provider Interface) that enables passwordless authentication via phone number and OTP (One-Time Password) verification. Users can authenticate by:

1. Entering their phone number
2. Receiving an OTP via WhatsApp or SMS
3. Entering the OTP code to complete authentication

### 1.2 Why Use Keycloak SPI?

Keycloak's SPI architecture allows extending authentication without modifying Keycloak's core code. Benefits include:

- **Pluggable**: Deploy as a JAR file, no code changes to Keycloak
- **Upgradeable**: Update Keycloak independently of custom providers
- **Configurable**: Admin Console UI for runtime configuration
- **Secure**: Leverages Keycloak's proven security infrastructure

### 1.3 Use Cases

| Use Case | Description |
|----------|-------------|
| **Passwordless Login** | Users authenticate with just their phone number |
| **Mobile-First Markets** | Ideal for Zambian users who prefer WhatsApp communication |
| **Account Recovery** | Alternative authentication when password is forgotten |
| **Two-Factor Authentication** | Additional security layer after password |

---

## 2. Architecture

### 2.1 High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              CLIENT APPLICATIONS                             │
├───────────────────────────────┬─────────────────────────────────────────────┤
│         Mobile App            │              Web Application                │
│    (React Native + Expo)      │                (Next.js)                    │
│    expo-auth-session          │              keycloak-js                    │
│    PKCE + acr_values          │           PKCE + acr_values                 │
└───────────────┬───────────────┴───────────────────┬─────────────────────────┘
                │                                   │
                │  OAuth2 Authorization Code + PKCE │
                │  acr_values=phone-otp             │
                ▼                                   ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           KEYCLOAK SERVER (26.5.2)                          │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                      Authentication Flow Engine                        │  │
│  │  ┌─────────────┐   ┌─────────────────────────┐   ┌─────────────────┐  │  │
│  │  │   Cookie    │   │  Phone OTP Authenticator │   │   Username/     │  │  │
│  │  │   Auth      │   │       (Custom SPI)       │   │   Password      │  │  │
│  │  │ ALTERNATIVE │   │       ALTERNATIVE        │   │   ALTERNATIVE   │  │  │
│  │  └─────────────┘   └───────────┬─────────────┘   └─────────────────┘  │  │
│  └────────────────────────────────┼──────────────────────────────────────┘  │
│                                   │                                          │
│  ┌────────────────────────────────┼──────────────────────────────────────┐  │
│  │              Phone OTP Authenticator JAR (SPI Provider)               │  │
│  │  ┌─────────────────────────────┴─────────────────────────────────┐    │  │
│  │  │  PhoneOtpAuthenticator.java      PhoneOtpAuthenticatorFactory │    │  │
│  │  │  - authenticate()                - getId()                     │    │  │
│  │  │  - action()                      - getDisplayType()            │    │  │
│  │  │  - handlePhoneSubmission()       - getConfigProperties()       │    │  │
│  │  │  - handleOtpSubmission()         - create()                    │    │  │
│  │  └───────────────────────────────────────────────────────────────┘    │  │
│  │  ┌───────────────────────────────────────────────────────────────┐    │  │
│  │  │  OtpServiceClient.java                                        │    │  │
│  │  │  - getAccessToken() (Client Credentials)                      │    │  │
│  │  │  - requestOtp(phone, channel)                                 │    │  │
│  │  │  - verifyOtp(phone, otp)                                      │    │  │
│  │  └───────────────────────────────────────────────────────────────┘    │  │
│  │  ┌───────────────────────────────────────────────────────────────┐    │  │
│  │  │  FreeMarker Templates (theme-resources/templates/)            │    │  │
│  │  │  - phone-otp-input.ftl   (Phone number entry)                 │    │  │
│  │  │  - phone-otp-verify.ftl  (OTP code entry)                     │    │  │
│  │  └───────────────────────────────────────────────────────────────┘    │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                   │                                          │
│           /opt/keycloak/providers/phone-otp-authenticator-1.0.0.jar         │
└───────────────────────────────────┼─────────────────────────────────────────┘
                                    │ HTTP REST (Bearer Token)
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                     IDENTITY SERVICE (Spring Boot 3.5.4)                    │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                    InternalOtpController.java                         │  │
│  │  POST /api/internal/otp/request  →  Generate & send OTP               │  │
│  │  POST /api/internal/otp/verify   →  Verify OTP code                   │  │
│  │  GET  /api/internal/otp/status   →  Check cooldown status             │  │
│  │  @PreAuthorize("hasAnyAuthority('SCOPE_internal-read', ...)")         │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                         OtpService.java                               │  │
│  │  - generateOtp()      →  SecureRandom 6-digit code                    │  │
│  │  - verifyOtp()        →  Compare with Redis store                     │  │
│  │  - setCooldown()      →  Rate limiting (1 min)                        │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                      MessagingService.java                            │  │
│  │  - sendOtp(phone, otp, "whatsapp")  →  WhatsApp Business API          │  │
│  │  - sendOtp(phone, otp, "sms")       →  SMS Gateway                    │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
└───────────────────────────────────┬─────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                               REDIS (7.x)                                   │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │  OTP Storage (TTL: 5 minutes)                                         │  │
│  │  Key: otp:phone:+260971234567  →  Value: "123456"                     │  │
│  │                                                                        │  │
│  │  Cooldown Tracking (TTL: 1 minute)                                    │  │
│  │  Key: otp:cooldown:+260971234567  →  Value: "1"                       │  │
│  │                                                                        │  │
│  │  Attempt Tracking (TTL: 5 minutes)                                    │  │
│  │  Key: otp:attempts:+260971234567  →  Value: "2"                       │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Authentication Flow Sequence

```
┌──────────┐     ┌──────────┐     ┌──────────────────┐     ┌─────────────┐     ┌───────┐
│  Client  │     │ Keycloak │     │ Phone OTP Auth   │     │  Identity   │     │ Redis │
│   App    │     │  Server  │     │  (SPI Provider)  │     │  Service    │     │       │
└────┬─────┘     └────┬─────┘     └────────┬─────────┘     └──────┬──────┘     └───┬───┘
     │                │                    │                      │                │
     │ 1. Login with  │                    │                      │                │
     │ acr=phone-otp  │                    │                      │                │
     │───────────────>│                    │                      │                │
     │                │                    │                      │                │
     │                │ 2. Route to        │                      │                │
     │                │ Phone OTP Flow     │                      │                │
     │                │───────────────────>│                      │                │
     │                │                    │                      │                │
     │                │ 3. phone-otp-input.ftl                    │                │
     │<───────────────────────────────────-│                      │                │
     │                │                    │                      │                │
     │ 4. Submit      │                    │                      │                │
     │ phone: +260... │                    │                      │                │
     │───────────────────────────────────->│                      │                │
     │                │                    │                      │                │
     │                │                    │ 5. Get OAuth2 Token  │                │
     │                │                    │ (client_credentials) │                │
     │                │                    │─────────────────────>│                │
     │                │                    │                      │                │
     │                │                    │ 6. POST /otp/request │                │
     │                │                    │─────────────────────>│                │
     │                │                    │                      │                │
     │                │                    │                      │ 7. Generate    │
     │                │                    │                      │    Store OTP   │
     │                │                    │                      │───────────────>│
     │                │                    │                      │                │
     │                │                    │                      │ 8. Send via    │
     │                │                    │                      │ WhatsApp/SMS   │
     │                │                    │                      │────────────>   │
     │                │                    │                      │                │
     │                │                    │ 9. {success: true}   │                │
     │                │                    │<─────────────────────│                │
     │                │                    │                      │                │
     │                │ 10. phone-otp-verify.ftl                  │                │
     │<───────────────────────────────────-│                      │                │
     │                │                    │                      │                │
     │ 11. Submit     │                    │                      │                │
     │ otp: 123456    │                    │                      │                │
     │───────────────────────────────────->│                      │                │
     │                │                    │                      │                │
     │                │                    │ 12. POST /otp/verify │                │
     │                │                    │─────────────────────>│                │
     │                │                    │                      │ 13. Verify     │
     │                │                    │                      │───────────────>│
     │                │                    │                      │                │
     │                │                    │ 14. {valid: true}    │                │
     │                │                    │<─────────────────────│                │
     │                │                    │                      │                │
     │                │ 15. Find/Create    │                      │                │
     │                │     User           │                      │                │
     │                │<───────────────────│                      │                │
     │                │                    │                      │                │
     │ 16. Redirect   │                    │                      │                │
     │ with auth code │                    │                      │                │
     │<───────────────│                    │                      │                │
     │                │                    │                      │                │
     │ 17. Exchange   │                    │                      │                │
     │ code for token │                    │                      │                │
     │───────────────>│                    │                      │                │
     │                │                    │                      │                │
     │ 18. JWT Token  │                    │                      │                │
     │<───────────────│                    │                      │                │
     │                │                    │                      │                │
```

---

## 3. Technology Stack

### 3.1 Keycloak SPI Provider

| Technology | Version | Purpose |
|------------|---------|---------|
| **Keycloak** | 26.5.2 | Identity and Access Management |
| **Java** | 17 | SPI implementation language |
| **Maven** | 3.9+ | Build tool |
| **Gson** | 2.10.1 | JSON parsing for REST calls |
| **JBoss Logging** | 3.5.3.Final | Logging (provided by Keycloak) |

### 3.2 Backend Services

| Technology | Version | Purpose |
|------------|---------|---------|
| **Spring Boot** | 3.5.4 | Backend framework |
| **Spring WebFlux** | 6.2.x | Reactive web layer |
| **Spring Security** | 6.4.x | OAuth2 Resource Server |
| **Redis** | 7.x | OTP storage with TTL |
| **MongoDB** | 8.x | User data persistence |

### 3.3 Frontend Applications

| Technology | Version | Purpose |
|------------|---------|---------|
| **React Native** | 0.79.x | Mobile app framework |
| **Expo** | 53.x | React Native toolchain |
| **expo-auth-session** | 7.0.5 | OAuth2/PKCE flow |
| **expo-secure-store** | 15.0.8 | Secure token storage |
| **Next.js** | 16.x | Web application |
| **keycloak-js** | 26.0.0 | Keycloak JavaScript adapter |

---

## 4. Project Structure

### 4.1 Phone OTP Authenticator Module

```
backend/keycloak-phone-otp-authenticator/
├── pom.xml                                    # Maven build configuration
├── src/
│   └── main/
│       ├── java/
│       │   └── com/pml/keycloak/authenticator/
│       │       ├── PhoneOtpAuthenticator.java          # Core authenticator logic
│       │       ├── PhoneOtpAuthenticatorFactory.java   # Factory for creating instances
│       │       └── OtpServiceClient.java               # REST client for Identity Service
│       └── resources/
│           ├── META-INF/
│           │   └── services/
│           │       └── org.keycloak.authentication.AuthenticatorFactory  # SPI registration
│           └── theme-resources/
│               └── templates/
│                   ├── phone-otp-input.ftl             # Phone number input form
│                   └── phone-otp-verify.ftl            # OTP verification form
└── target/
    └── phone-otp-authenticator-1.0.0.jar               # Built JAR for deployment
```

### 4.2 Identity Service OTP Module

```
backend/identity-service/
├── src/main/java/com/pml/identity/
│   ├── controller/
│   │   └── InternalOtpController.java    # Internal OTP API endpoints
│   ├── service/
│   │   ├── OtpService.java               # OTP generation, storage, verification
│   │   └── MessagingService.java         # WhatsApp/SMS delivery
│   └── security/
│       └── SecurityConfig.java           # OAuth2 resource server config
```

---

## 5. Core Implementation

### 5.1 PhoneOtpAuthenticator.java

The main authenticator class that implements Keycloak's `Authenticator` interface.

```java
package com.pml.keycloak.authenticator;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

/**
 * Phone OTP Authenticator for Keycloak 26.x
 *
 * Enables passwordless authentication via phone number and OTP verification.
 *
 * @since 1.0.0
 * @see AuthenticatorFactory
 */
public class PhoneOtpAuthenticator implements Authenticator {

    // Authentication session notes for state tracking
    private static final String AUTH_NOTE_PHONE = "phone_otp_phone_number";
    private static final String AUTH_NOTE_STEP = "phone_otp_step";
    private static final String STEP_PHONE_INPUT = "phone_input";
    private static final String STEP_OTP_VERIFY = "otp_verify";

    private final OtpServiceClient otpServiceClient;

    public PhoneOtpAuthenticator(String otpServiceUrl) {
        this.otpServiceClient = new OtpServiceClient(otpServiceUrl);
    }

    /**
     * Main entry point - called when authentication flow reaches this authenticator.
     *
     * Decision tree:
     * - Initial request → Show phone input form
     * - Phone submitted → Request OTP, show verify form
     * - OTP submitted → Verify OTP, complete authentication
     */
    @Override
    public void authenticate(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> formData =
            context.getHttpRequest().getDecodedFormParameters();
        String currentStep = context.getAuthenticationSession()
            .getAuthNote(AUTH_NOTE_STEP);

        if (formData.containsKey("phoneNumber") && !STEP_OTP_VERIFY.equals(currentStep)) {
            handlePhoneSubmission(context, formData);
        } else if (formData.containsKey("otp") && STEP_OTP_VERIFY.equals(currentStep)) {
            handleOtpSubmission(context, formData);
        } else {
            showPhoneInputForm(context, null);
        }
    }

    /**
     * Called when user submits a form. Delegates to authenticate().
     */
    @Override
    public void action(AuthenticationFlowContext context) {
        authenticate(context);
    }

    /**
     * Display phone number input form.
     *
     * @param context The authentication flow context
     * @param errorMessage Optional error message to display
     */
    private void showPhoneInputForm(AuthenticationFlowContext context, String errorMessage) {
        // Track current step in session
        context.getAuthenticationSession().setAuthNote(AUTH_NOTE_STEP, STEP_PHONE_INPUT);

        Response challenge;
        if (errorMessage != null) {
            challenge = context.form()
                    .setError(errorMessage)
                    .createForm("phone-otp-input.ftl");
        } else {
            challenge = context.form()
                    .createForm("phone-otp-input.ftl");
        }
        context.challenge(challenge);
    }

    /**
     * Process phone number submission.
     *
     * 1. Validate phone format
     * 2. Normalize to E.164 format
     * 3. Call Identity Service to send OTP
     * 4. Show OTP verification form
     */
    private void handlePhoneSubmission(AuthenticationFlowContext context,
                                        MultivaluedMap<String, String> formData) {
        String phoneNumber = formData.getFirst("phoneNumber");
        String channel = formData.getFirst("channel");
        if (channel == null || channel.isEmpty()) {
            channel = "whatsapp";  // Default to WhatsApp in Zambia
        }

        // Validate format
        if (!isValidPhoneNumber(phoneNumber)) {
            showPhoneInputForm(context,
                "Invalid phone number format. Please enter a valid phone number.");
            return;
        }

        // Normalize to E.164 (+260XXXXXXXXX)
        String normalizedPhone = normalizePhoneNumber(phoneNumber);

        // Request OTP from Identity Service
        OtpServiceClient.OtpRequestResult result =
            otpServiceClient.requestOtp(normalizedPhone, channel);

        if (result.isSuccess()) {
            // Store phone in session for OTP verification step
            context.getAuthenticationSession()
                .setAuthNote(AUTH_NOTE_PHONE, normalizedPhone);
            context.getAuthenticationSession()
                .setAuthNote(AUTH_NOTE_STEP, STEP_OTP_VERIFY);

            // Show OTP verification form
            Response challenge = context.form()
                    .setAttribute("phone", maskPhoneNumber(normalizedPhone))
                    .setAttribute("expiresIn", result.getExpiresIn())
                    .createForm("phone-otp-verify.ftl");
            context.challenge(challenge);
        } else if ("COOLDOWN".equals(result.getStatus())) {
            showPhoneInputForm(context, String.format(
                "Please wait %d seconds before requesting another code.",
                result.getCooldownRemaining()));
        } else {
            showPhoneInputForm(context,
                "Failed to send verification code. Please try again.");
        }
    }

    /**
     * Process OTP code submission.
     *
     * 1. Validate OTP format
     * 2. Call Identity Service to verify OTP
     * 3. Find or create user
     * 4. Complete authentication
     */
    private void handleOtpSubmission(AuthenticationFlowContext context,
                                      MultivaluedMap<String, String> formData) {
        String otp = formData.getFirst("otp");
        String phoneNumber = context.getAuthenticationSession()
            .getAuthNote(AUTH_NOTE_PHONE);

        if (phoneNumber == null) {
            showPhoneInputForm(context,
                "Session expired. Please enter your phone number again.");
            return;
        }

        if (!isValidOtp(otp)) {
            Response challenge = context.form()
                    .setAttribute("phone", maskPhoneNumber(phoneNumber))
                    .setError("Invalid code format. Please enter the 6-digit code.")
                    .createForm("phone-otp-verify.ftl");
            context.challenge(challenge);
            return;
        }

        // Verify OTP with Identity Service
        OtpServiceClient.OtpVerifyResult result =
            otpServiceClient.verifyOtp(phoneNumber, otp);

        if (result.isValid()) {
            // Find existing user or create new one
            UserModel user = findOrCreateUser(context, phoneNumber);
            if (user != null) {
                context.setUser(user);
                context.success();  // Authentication complete!
            } else {
                context.failureChallenge(AuthenticationFlowError.INTERNAL_ERROR,
                    context.form()
                        .setError("Failed to create user account. Please try again.")
                        .createForm("phone-otp-input.ftl"));
            }
        } else {
            Response challenge = context.form()
                    .setAttribute("phone", maskPhoneNumber(phoneNumber))
                    .setError("Invalid or expired code. Please try again.")
                    .createForm("phone-otp-verify.ftl");
            context.challenge(challenge);
        }
    }

    /**
     * Find user by phone number or create new user.
     *
     * User is searched by the "phone_number" attribute.
     * New users are assigned the CUSTOMER role.
     */
    private UserModel findOrCreateUser(AuthenticationFlowContext context,
                                        String phoneNumber) {
        KeycloakSession session = context.getSession();
        RealmModel realm = context.getRealm();

        // Search by phone_number attribute
        List<UserModel> users = session.users()
                .searchForUserByUserAttributeStream(realm, "phone_number", phoneNumber)
                .collect(Collectors.toList());

        if (!users.isEmpty()) {
            UserModel user = users.get(0);
            user.setSingleAttribute("phone_verified", "true");
            return user;
        }

        // Create new user
        try {
            String username = generateUsername(phoneNumber);
            UserModel newUser = session.users().addUser(realm, username);
            newUser.setEnabled(true);
            newUser.setSingleAttribute("phone_number", phoneNumber);
            newUser.setSingleAttribute("phone_verified", "true");

            // Assign default CUSTOMER role
            realm.getRolesStream()
                    .filter(role -> "CUSTOMER".equalsIgnoreCase(role.getName()))
                    .findFirst()
                    .ifPresent(newUser::grantRole);

            return newUser;
        } catch (Exception e) {
            LOG.errorf(e, "Failed to create user for phone: %s",
                maskPhoneNumber(phoneNumber));
            return null;
        }
    }

    // ========== Interface Methods ==========

    @Override
    public boolean requiresUser() {
        return false;  // We find/create user during authentication
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;  // Always available
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        // No required actions
    }

    @Override
    public void close() {
        // No resources to close
    }

    // ========== Utility Methods ==========

    private boolean isValidPhoneNumber(String phone) {
        if (phone == null || phone.isEmpty()) return false;
        String cleaned = phone.replaceAll("[^0-9+]", "");
        return cleaned.matches("^\\+?[0-9]{10,15}$");
    }

    private boolean isValidOtp(String otp) {
        if (otp == null || otp.isEmpty()) return false;
        return otp.matches("^[0-9]{4,8}$");
    }

    private String normalizePhoneNumber(String phone) {
        String cleaned = phone.replaceAll("[^0-9+]", "");
        if (!cleaned.startsWith("+")) {
            // Default to Zambia country code
            cleaned = cleaned.length() <= 10 ? "+260" + cleaned : "+" + cleaned;
        }
        return cleaned;
    }

    private String maskPhoneNumber(String phone) {
        if (phone == null || phone.length() <= 4) return "****";
        return "***" + phone.substring(phone.length() - 4);
    }

    private String generateUsername(String phone) {
        String cleanPhone = phone.replaceAll("[^0-9]", "");
        String suffix = cleanPhone.length() > 8
            ? cleanPhone.substring(cleanPhone.length() - 8) : cleanPhone;
        return "user_" + suffix;
    }
}
```

### 5.2 PhoneOtpAuthenticatorFactory.java

The factory class that creates authenticator instances and provides metadata for the Admin Console.

```java
package com.pml.keycloak.authenticator;

import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory for creating Phone OTP Authenticator instances.
 *
 * Responsibilities:
 * - Register the authenticator with Keycloak (getId())
 * - Provide Admin Console metadata (getDisplayType(), getHelpText())
 * - Define configuration options (getConfigProperties())
 * - Create authenticator instances (create())
 */
public class PhoneOtpAuthenticatorFactory implements AuthenticatorFactory {

    // Unique identifier - used in realm JSON config
    public static final String PROVIDER_ID = "phone-otp-authenticator";

    // Display name shown in Admin Console
    public static final String DISPLAY_TYPE = "Phone OTP Authentication";

    // Configuration property names
    public static final String CONFIG_OTP_SERVICE_URL = "otpServiceUrl";
    public static final String CONFIG_DEFAULT_CHANNEL = "defaultChannel";

    private static final List<ProviderConfigProperty> CONFIG_PROPERTIES = new ArrayList<>();

    static {
        // OTP Service URL configuration
        ProviderConfigProperty otpServiceUrl = new ProviderConfigProperty();
        otpServiceUrl.setName(CONFIG_OTP_SERVICE_URL);
        otpServiceUrl.setLabel("OTP Service URL");
        otpServiceUrl.setType(ProviderConfigProperty.STRING_TYPE);
        otpServiceUrl.setDefaultValue("http://identity-service:8083");
        otpServiceUrl.setHelpText(
            "Base URL of the Identity Service that handles OTP operations");
        CONFIG_PROPERTIES.add(otpServiceUrl);

        // Default channel configuration
        ProviderConfigProperty defaultChannel = new ProviderConfigProperty();
        defaultChannel.setName(CONFIG_DEFAULT_CHANNEL);
        defaultChannel.setLabel("Default Delivery Channel");
        defaultChannel.setType(ProviderConfigProperty.LIST_TYPE);
        defaultChannel.setDefaultValue("whatsapp");
        defaultChannel.setOptions(List.of("whatsapp", "sms"));
        defaultChannel.setHelpText("Default channel for OTP delivery");
        CONFIG_PROPERTIES.add(defaultChannel);
    }

    @Override
    public String getId() {
        return PROVIDER_ID;  // Must match what's in realm JSON
    }

    @Override
    public String getDisplayType() {
        return DISPLAY_TYPE;  // Shown in Admin Console dropdown
    }

    @Override
    public String getReferenceCategory() {
        return "phone-otp";  // Groups related authenticators
    }

    @Override
    public boolean isConfigurable() {
        return true;  // Has configuration options
    }

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return new AuthenticationExecutionModel.Requirement[]{
            AuthenticationExecutionModel.Requirement.REQUIRED,     // Must complete
            AuthenticationExecutionModel.Requirement.ALTERNATIVE,  // One of many
            AuthenticationExecutionModel.Requirement.DISABLED      // Turned off
        };
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;  // Users don't configure this
    }

    @Override
    public String getHelpText() {
        return "Authenticate users via phone number and OTP verification. " +
               "OTPs are sent via WhatsApp or SMS using the Identity Service.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return CONFIG_PROPERTIES;
    }

    @Override
    public Authenticator create(KeycloakSession session) {
        // Read OTP service URL from environment
        String otpServiceUrl = System.getenv("OTP_SERVICE_URL");
        if (otpServiceUrl == null || otpServiceUrl.isEmpty()) {
            otpServiceUrl = "http://identity-service:8083";
        }
        return new PhoneOtpAuthenticator(otpServiceUrl);
    }

    @Override
    public void init(Config.Scope config) {
        LOG.info("Initializing Phone OTP Authenticator Factory");
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        LOG.info("Phone OTP Authenticator Factory post-initialization complete");
    }

    @Override
    public void close() {
        // No resources to close
    }
}
```

### 5.3 OtpServiceClient.java

REST client for communicating with the Identity Service OTP endpoints.

```java
package com.pml.keycloak.authenticator;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
 * REST client for Identity Service OTP endpoints.
 *
 * Uses OAuth2 Client Credentials flow to authenticate.
 * Tokens are cached and automatically refreshed.
 */
public class OtpServiceClient {

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

        // Read from environment variables (set in docker-compose)
        this.tokenUrl = System.getenv("KEYCLOAK_TOKEN_URL");
        this.clientId = System.getenv("OTP_CLIENT_ID");
        this.clientSecret = System.getenv("OTP_CLIENT_SECRET");
    }

    /**
     * Get access token using Client Credentials flow.
     * Tokens are cached and refreshed 30 seconds before expiry.
     */
    private String getAccessToken() {
        tokenLock.lock();
        try {
            // Return cached token if still valid
            if (accessToken != null && tokenExpiry != null
                && Instant.now().isBefore(tokenExpiry)) {
                return accessToken;
            }

            // Skip auth if not configured (development mode)
            if (tokenUrl == null || clientId == null || clientSecret == null) {
                return null;
            }

            // Request new token
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

            HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject tokenResponse = JsonParser.parseString(response.body())
                    .getAsJsonObject();
                this.accessToken = tokenResponse.get("access_token").getAsString();
                int expiresIn = tokenResponse.get("expires_in").getAsInt();
                // Refresh 30 seconds before expiry
                this.tokenExpiry = Instant.now().plusSeconds(expiresIn - 30);
                return accessToken;
            }
            return null;
        } catch (Exception e) {
            LOG.errorf(e, "Error obtaining access token");
            return null;
        } finally {
            tokenLock.unlock();
        }
    }

    /**
     * Request OTP generation and delivery.
     *
     * @param phoneNumber E.164 formatted phone number
     * @param channel     "whatsapp" or "sms"
     * @return Result with success status and details
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

            // Add authorization if token available
            String token = getAccessToken();
            if (token != null) {
                requestBuilder.header("Authorization", "Bearer " + token);
            }

            HttpResponse<String> response = httpClient.send(requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject result = JsonParser.parseString(response.body())
                    .getAsJsonObject();
                return new OtpRequestResult(
                    result.get("success").getAsBoolean(),
                    result.get("status").getAsString(),
                    result.has("cooldownRemaining")
                        ? result.get("cooldownRemaining").getAsInt() : 0,
                    result.has("expiresIn")
                        ? result.get("expiresIn").getAsInt() : 300
                );
            }
            return new OtpRequestResult(false, "ERROR", 0, 0);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to request OTP");
            return new OtpRequestResult(false, "ERROR", 0, 0);
        }
    }

    /**
     * Verify OTP code.
     *
     * @param phoneNumber E.164 formatted phone number
     * @param otp         The OTP code to verify
     * @return Result with valid/invalid status
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

            String token = getAccessToken();
            if (token != null) {
                requestBuilder.header("Authorization", "Bearer " + token);
            }

            HttpResponse<String> response = httpClient.send(requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject result = JsonParser.parseString(response.body())
                    .getAsJsonObject();
                return new OtpVerifyResult(
                    result.get("valid").getAsBoolean(),
                    result.get("status").getAsString()
                );
            }
            return new OtpVerifyResult(false, "ERROR");
        } catch (Exception e) {
            LOG.errorf(e, "Failed to verify OTP");
            return new OtpVerifyResult(false, "ERROR");
        }
    }

    // Result classes omitted for brevity - see full source
}
```

---

## 6. FreeMarker Templates

### 6.1 phone-otp-input.ftl (Phone Number Entry)

Location: `src/main/resources/theme-resources/templates/phone-otp-input.ftl`

```html
<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('phoneNumber')
                            displayInfo=true; section>
    <#if section = "header">
        ${msg("phoneOtpTitle", "Sign in with Phone")}
    <#elseif section = "form">
        <div id="kc-form">
            <div id="kc-form-wrapper">
                <form id="kc-phone-otp-form"
                      class="${properties.kcFormClass!}"
                      action="${url.loginAction}"
                      method="post">

                    <!-- Phone Number Input -->
                    <div class="${properties.kcFormGroupClass!}">
                        <label for="phoneNumber" class="${properties.kcLabelClass!}">
                            ${msg("phoneNumber", "Phone Number")}
                        </label>
                        <div class="${properties.kcInputWrapperClass!}">
                            <input type="tel"
                                   id="phoneNumber"
                                   name="phoneNumber"
                                   class="${properties.kcInputClass!}"
                                   placeholder="+260 97X XXX XXX"
                                   autocomplete="tel"
                                   autofocus
                                   required />
                        </div>
                        <#if messagesPerField.existsError('phoneNumber')>
                            <span class="${properties.kcInputErrorMessageClass!}">
                                ${kcSanitize(messagesPerField.get('phoneNumber'))?no_esc}
                            </span>
                        </#if>
                    </div>

                    <!-- Delivery Channel Selection -->
                    <div class="${properties.kcFormGroupClass!}">
                        <label class="${properties.kcLabelClass!}">
                            ${msg("deliveryChannel", "Receive code via")}
                        </label>
                        <div class="channel-options">
                            <label class="channel-option">
                                <input type="radio" name="channel" value="whatsapp" checked />
                                <span class="channel-label">WhatsApp</span>
                            </label>
                            <label class="channel-option">
                                <input type="radio" name="channel" value="sms" />
                                <span class="channel-label">SMS</span>
                            </label>
                        </div>
                    </div>

                    <!-- Submit Button -->
                    <div class="${properties.kcFormButtonsClass!}">
                        <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!}"
                               type="submit"
                               value="${msg("sendVerificationCode", "Send Verification Code")}" />
                    </div>
                </form>
            </div>
        </div>
    <#elseif section = "info">
        <div id="kc-info">
            <p>${msg("phoneOtpInfo", "We'll send a verification code to your phone.")}</p>
            <p><a href="${url.loginUrl}">${msg("backToLogin", "Back to login")}</a></p>
        </div>
    </#if>
</@layout.registrationLayout>
```

### 6.2 phone-otp-verify.ftl (OTP Entry)

Location: `src/main/resources/theme-resources/templates/phone-otp-verify.ftl`

```html
<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('otp')
                            displayInfo=true; section>
    <#if section = "header">
        ${msg("phoneOtpVerifyTitle", "Enter Verification Code")}
    <#elseif section = "form">
        <div id="kc-form">
            <div id="kc-form-wrapper">
                <!-- Info Box -->
                <div class="otp-info-box">
                    <p>${msg("otpSentTo", "A verification code has been sent to")}</p>
                    <p><strong>${phone!""}</strong></p>
                </div>

                <form id="kc-otp-verify-form"
                      class="${properties.kcFormClass!}"
                      action="${url.loginAction}"
                      method="post">

                    <!-- OTP Input -->
                    <div class="${properties.kcFormGroupClass!}">
                        <label for="otp" class="${properties.kcLabelClass!}">
                            ${msg("verificationCode", "Verification Code")}
                        </label>
                        <div class="${properties.kcInputWrapperClass!}">
                            <input type="text"
                                   id="otp"
                                   name="otp"
                                   class="${properties.kcInputClass!} otp-input"
                                   inputmode="numeric"
                                   pattern="[0-9]*"
                                   maxlength="6"
                                   autocomplete="one-time-code"
                                   placeholder="000000"
                                   autofocus
                                   required />
                        </div>
                        <#if messagesPerField.existsError('otp')>
                            <span class="${properties.kcInputErrorMessageClass!}">
                                ${kcSanitize(messagesPerField.get('otp'))?no_esc}
                            </span>
                        </#if>
                    </div>

                    <!-- Submit Button -->
                    <div class="${properties.kcFormButtonsClass!}">
                        <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!}"
                               type="submit"
                               value="${msg("verifyCode", "Verify Code")}" />
                    </div>
                </form>

                <!-- Timer and Resend -->
                <div class="otp-actions">
                    <p class="otp-timer">
                        Code expires in <span id="countdown">${expiresIn!300}</span> seconds
                    </p>
                    <p class="resend-link">
                        <a href="${url.loginUrl}">Use a different phone number</a>
                    </p>
                </div>
            </div>
        </div>
    </#if>
</@layout.registrationLayout>

<script>
(function() {
    var countdownEl = document.getElementById('countdown');
    var seconds = parseInt(countdownEl.textContent, 10) || 300;

    function updateCountdown() {
        if (seconds > 0) {
            seconds--;
            countdownEl.textContent = seconds;
            setTimeout(updateCountdown, 1000);
        } else {
            countdownEl.parentElement.innerHTML = 'Code has expired. Please request a new one.';
        }
    }

    setTimeout(updateCountdown, 1000);

    // Auto-format and auto-submit
    var otpInput = document.getElementById('otp');
    otpInput.addEventListener('input', function() {
        this.value = this.value.replace(/[^0-9]/g, '').slice(0, 6);
        if (this.value.length === 6) {
            setTimeout(function() {
                document.getElementById('kc-otp-verify-form').submit();
            }, 300);
        }
    });
})();
</script>
```

---

## 7. Service Registration

### 7.1 Java SPI Service File

Location: `src/main/resources/META-INF/services/org.keycloak.authentication.AuthenticatorFactory`

```plaintext
com.pml.keycloak.authenticator.PhoneOtpAuthenticatorFactory
```

This file tells Keycloak to load your factory class when scanning for providers.

### 7.2 Maven Build Configuration

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.pml.keycloak</groupId>
    <artifactId>phone-otp-authenticator</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <keycloak.version>26.0.0</keycloak.version>
    </properties>

    <dependencies>
        <!-- Keycloak SPI (provided at runtime) -->
        <dependency>
            <groupId>org.keycloak</groupId>
            <artifactId>keycloak-server-spi</artifactId>
            <version>${keycloak.version}</version>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>org.keycloak</groupId>
            <artifactId>keycloak-server-spi-private</artifactId>
            <version>${keycloak.version}</version>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>org.keycloak</groupId>
            <artifactId>keycloak-services</artifactId>
            <version>${keycloak.version}</version>
            <scope>provided</scope>
        </dependency>

        <!-- JSON Processing (bundled in JAR) -->
        <dependency>
            <groupId>com.google.code.gson</groupId>
            <artifactId>gson</artifactId>
            <version>2.10.1</version>
        </dependency>

        <!-- Logging (provided by Keycloak) -->
        <dependency>
            <groupId>org.jboss.logging</groupId>
            <artifactId>jboss-logging</artifactId>
            <version>3.5.3.Final</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- Shade plugin to bundle Gson -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.5.1</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals><goal>shade</goal></goals>
                        <configuration>
                            <artifactSet>
                                <includes>
                                    <include>com.google.code.gson:gson</include>
                                </includes>
                            </artifactSet>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>

        <resources>
            <resource>
                <directory>src/main/resources</directory>
                <includes>
                    <include>**/*</include>
                </includes>
            </resource>
        </resources>
    </build>
</project>
```

---

## 8. Backend OTP Service Integration

### 8.1 InternalOtpController.java

```java
@Slf4j
@RestController
@RequestMapping("/api/internal/otp")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('SCOPE_internal-read', 'SCOPE_internal-write', " +
              "'ROLE_INTERNAL_SERVICE', 'ROLE_SYSTEM')")
public class InternalOtpController {

    private final OtpService otpService;
    private final MessagingService messagingService;

    /**
     * Request OTP generation and delivery.
     * POST /api/internal/otp/request
     */
    @PostMapping("/request")
    public Mono<ResponseEntity<OtpRequestResult>> requestOtp(
            @Valid @RequestBody OtpRequestDto request) {

        String phoneNumber = request.getPhoneNumber();
        String channel = request.getChannel() != null ? request.getChannel() : "whatsapp";

        return otpService.canSendOtp(phoneNumber)
                .flatMap(canSend -> {
                    if (!canSend) {
                        return otpService.getCooldownRemaining(phoneNumber)
                                .map(remaining -> ResponseEntity.ok(
                                    new OtpRequestResult(false, "COOLDOWN",
                                        remaining.intValue(), 0)));
                    }

                    return otpService.generateOtp(phoneNumber)
                            .flatMap(otp -> messagingService.sendOtp(phoneNumber, otp, channel))
                            .flatMap(sent -> {
                                if (sent) {
                                    return otpService.setCooldown(phoneNumber)
                                            .thenReturn(ResponseEntity.ok(
                                                new OtpRequestResult(true, "SENT", 0, 300)));
                                }
                                return Mono.just(ResponseEntity.ok(
                                    new OtpRequestResult(false, "DELIVERY_FAILED", 0, 0)));
                            });
                });
    }

    /**
     * Verify OTP code.
     * POST /api/internal/otp/verify
     */
    @PostMapping("/verify")
    public Mono<ResponseEntity<OtpVerifyResult>> verifyOtp(
            @Valid @RequestBody OtpVerifyDto request) {

        return otpService.verifyOtp(request.getPhoneNumber(), request.getOtp())
                .map(valid -> {
                    if (valid) {
                        return ResponseEntity.ok(new OtpVerifyResult(true, "VALID"));
                    }
                    return ResponseEntity.ok(new OtpVerifyResult(false, "INVALID"));
                });
    }
}
```

### 8.2 OtpService.java

```java
@Service
@RequiredArgsConstructor
public class OtpService {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    private static final String OTP_KEY_PREFIX = "otp:phone:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${identity.otp.expiration-minutes:5}")
    private int otpExpirationMinutes;

    @Value("${identity.otp.length:6}")
    private int otpLength;

    @Value("${identity.otp.cooldown-minutes:1}")
    private int cooldownMinutes;

    public Mono<String> generateOtp(String phoneNumber) {
        String otp = generateRandomOtp();
        String key = OTP_KEY_PREFIX + normalizePhone(phoneNumber);

        return redisTemplate.opsForValue()
                .set(key, otp, Duration.ofMinutes(otpExpirationMinutes))
                .thenReturn(otp);
    }

    public Mono<Boolean> verifyOtp(String phoneNumber, String otp) {
        String key = OTP_KEY_PREFIX + normalizePhone(phoneNumber);

        return redisTemplate.opsForValue().get(key)
                .flatMap(storedOtp -> {
                    if (storedOtp != null && storedOtp.equals(otp)) {
                        return redisTemplate.delete(key).thenReturn(true);
                    }
                    return Mono.just(false);
                })
                .defaultIfEmpty(false);
    }

    public Mono<Boolean> canSendOtp(String phoneNumber) {
        String cooldownKey = "otp:cooldown:" + normalizePhone(phoneNumber);
        return redisTemplate.hasKey(cooldownKey).map(exists -> !exists);
    }

    public Mono<Void> setCooldown(String phoneNumber) {
        String cooldownKey = "otp:cooldown:" + normalizePhone(phoneNumber);
        return redisTemplate.opsForValue()
                .set(cooldownKey, "1", Duration.ofMinutes(cooldownMinutes))
                .then();
    }

    private String generateRandomOtp() {
        StringBuilder otp = new StringBuilder();
        for (int i = 0; i < otpLength; i++) {
            otp.append(SECURE_RANDOM.nextInt(10));
        }
        return otp.toString();
    }

    private String normalizePhone(String phone) {
        String cleaned = phone.replaceAll("[^0-9+]", "");
        return cleaned.startsWith("+") ? cleaned : "+260" + cleaned;
    }
}
```

---

## 9. Keycloak Realm Configuration

### 9.1 Authentication Flow Definition

In `event-ticketing-realm.json`:

```json
{
  "authenticationFlows": [
    {
      "alias": "Phone OTP Browser",
      "description": "Browser-based authentication with Phone OTP option",
      "providerId": "basic-flow",
      "topLevel": true,
      "builtIn": false,
      "authenticationExecutions": [
        {
          "authenticator": "auth-cookie",
          "authenticatorFlow": false,
          "requirement": "ALTERNATIVE",
          "priority": 10
        },
        {
          "authenticatorFlow": true,
          "requirement": "ALTERNATIVE",
          "priority": 20,
          "flowAlias": "Phone OTP Forms"
        }
      ]
    },
    {
      "alias": "Phone OTP Forms",
      "description": "Phone OTP authentication forms",
      "providerId": "basic-flow",
      "topLevel": false,
      "builtIn": false,
      "authenticationExecutions": [
        {
          "authenticator": "phone-otp-authenticator",
          "authenticatorFlow": false,
          "requirement": "REQUIRED",
          "priority": 10,
          "authenticatorConfig": "Phone OTP Config"
        }
      ]
    }
  ],
  "authenticatorConfig": [
    {
      "alias": "Phone OTP Config",
      "config": {
        "otpServiceUrl": "http://host.docker.internal:8083",
        "defaultChannel": "whatsapp"
      }
    }
  ]
}
```

### 9.2 Service Account Client

```json
{
  "clients": [
    {
      "clientId": "keycloak-otp-authenticator",
      "name": "Phone OTP Authenticator Service",
      "description": "Service account for Keycloak Phone OTP Authenticator",
      "enabled": true,
      "clientAuthenticatorType": "client-secret",
      "secret": "${KEYCLOAK_OTP_AUTHENTICATOR_SECRET}",
      "publicClient": false,
      "standardFlowEnabled": false,
      "directAccessGrantsEnabled": false,
      "serviceAccountsEnabled": true,
      "protocol": "openid-connect",
      "defaultClientScopes": [
        "openid",
        "internal-read",
        "internal-write"
      ]
    }
  ]
}
```

### 9.3 ACR-to-LoA Mapping

```json
{
  "attributes": {
    "acr.loa.map": "{\"phone-otp\":\"1\",\"password\":\"0\"}"
  }
}
```

---

## 10. Docker Deployment

### 10.1 docker-compose.yml Configuration

```yaml
services:
  dev_keycloak:
    image: quay.io/keycloak/keycloak:26.5.2
    container_name: dev_keycloak
    command: start-dev --import-realm
    environment:
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: ${KEYCLOAK_ADMIN_PASSWORD}
      KC_DB: postgres
      KC_DB_URL: jdbc:postgresql://dev_postgres:5432/shared_db?currentSchema=keycloak
      KC_DB_USERNAME: ${POSTGRES_USER}
      KC_DB_PASSWORD: ${POSTGRES_PASSWORD}
      KC_FEATURES: token-exchange,admin-fine-grained-authz
      KC_HEALTH_ENABLED: true
      KC_METRICS_ENABLED: true
      # Phone OTP Authenticator Configuration
      OTP_SERVICE_URL: ${OTP_SERVICE_URL:-http://host.docker.internal:8083}
      OTP_CLIENT_ID: ${OTP_CLIENT_ID:-keycloak-otp-authenticator}
      OTP_CLIENT_SECRET: ${OTP_CLIENT_SECRET}
      KEYCLOAK_TOKEN_URL: ${KEYCLOAK_TOKEN_URL}
    ports:
      - "${KEYCLOAK_PORT:-8084}:8080"
    volumes:
      # Import realm configurations
      - ./keycloak:/opt/keycloak/data/import:ro
      # Mount custom providers
      - ./keycloak/providers:/opt/keycloak/providers:ro
    depends_on:
      dev_postgres:
        condition: service_healthy
```

### 10.2 Build Script

`scripts/build-phone-otp-authenticator.sh`:

```bash
#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_RESOURCES_DIR="$(dirname "$SCRIPT_DIR")"
TICKETING_SYSTEM_DIR="${TICKETING_SYSTEM_DIR:-$HOME/Documents/Software Projects/personal/ticketing-system}"
AUTHENTICATOR_DIR="$TICKETING_SYSTEM_DIR/backend/keycloak-phone-otp-authenticator"
PROVIDERS_DIR="$DOCKER_RESOURCES_DIR/keycloak/providers"

echo "Building Phone OTP Authenticator..."

# Check dependencies
if ! command -v mvn &> /dev/null; then
    echo "Error: Maven is not installed."
    exit 1
fi

# Create providers directory
mkdir -p "$PROVIDERS_DIR"

# Build JAR
cd "$AUTHENTICATOR_DIR"
mvn clean package -DskipTests

# Copy to providers
JAR_FILE="$AUTHENTICATOR_DIR/target/phone-otp-authenticator-1.0.0.jar"
if [ -f "$JAR_FILE" ]; then
    cp "$JAR_FILE" "$PROVIDERS_DIR/"
    echo "Build successful! JAR copied to: $PROVIDERS_DIR/"
else
    echo "Error: Build failed."
    exit 1
fi

echo ""
echo "To deploy: docker compose restart dev_keycloak"
```

### 10.3 Environment Variables

`.env`:

```env
# Phone OTP Authenticator
OTP_SERVICE_URL=http://host.docker.internal:8083
OTP_CLIENT_ID=keycloak-otp-authenticator
OTP_CLIENT_SECRET=your-secure-secret-here
KEYCLOAK_TOKEN_URL=http://localhost:8084/realms/event-ticketing/protocol/openid-connect/token
```

---

## 11. Frontend Integration

### 11.1 Mobile App (React Native + Expo)

```typescript
// keycloak-auth.service.ts
export const loginWithPhone = async (options?: PhoneLoginOptions) => {
  const codeVerifier = await generateCodeVerifier();
  const codeChallenge = await generateCodeChallenge(codeVerifier);

  const authRequest = new AuthSession.AuthRequest({
    clientId: KEYCLOAK_CONFIG.clientId,
    redirectUri: KEYCLOAK_CONFIG.redirectUri,
    scopes: KEYCLOAK_CONFIG.scopes,
    codeChallenge,
    codeChallengeMethod: AuthSession.CodeChallengeMethod.S256,
    extraParams: {
      acr_values: 'phone-otp',  // Trigger Phone OTP authenticator
      login_hint: options?.phoneNumber,
    },
  });

  const result = await authRequest.promptAsync({
    authorizationEndpoint: `${KEYCLOAK_CONFIG.issuer}/protocol/openid-connect/auth`,
  });

  if (result.type === 'success' && result.params.code) {
    return exchangeCodeForTokens(result.params.code, codeVerifier);
  }
  return null;
};
```

### 11.2 Web App (Next.js)

```typescript
// auth/page.tsx
const handlePhoneLogin = () => {
  keycloak.login({
    acr: { values: ['phone-otp'], essential: true }
  });
};
```

---

## 12. Testing

### 12.1 Build and Deploy

```bash
# 1. Build the JAR
cd backend/keycloak-phone-otp-authenticator
mvn clean package -DskipTests

# 2. Copy to providers
cp target/phone-otp-authenticator-1.0.0.jar \
   /path/to/docker-resources/keycloak/providers/

# 3. Restart Keycloak
docker compose restart dev_keycloak

# 4. Check logs
docker compose logs -f dev_keycloak | grep -i "phone"
```

### 12.2 Verify Provider Loaded

In Keycloak Admin Console:
1. Go to **Authentication** → **Flows**
2. Check that "Phone OTP Browser" flow exists
3. Go to **Realm Settings** → **Events** → **Config**
4. Check provider logs

### 12.3 Test Flow

1. Open app and click "Sign in with Phone"
2. Enter phone number (+260...)
3. Check Identity Service logs for OTP generation
4. Enter OTP code
5. Verify successful authentication

---

## 13. Troubleshooting

| Issue | Cause | Solution |
|-------|-------|----------|
| "phone-otp-authenticator not found" | JAR not deployed | Copy JAR to providers/, restart Keycloak |
| "OTP service unreachable" | Network issue | Check OTP_SERVICE_URL, use host.docker.internal |
| "401 Unauthorized" on OTP request | Auth failure | Verify client credentials and scopes |
| "ACR values not triggering flow" | Mapping missing | Add ACR-to-LoA mapping in realm settings |
| FTL template not found | Templates not in JAR | Check theme-resources/templates/ in JAR |

---

## 14. Security Considerations

1. **Rate Limiting**: 1-minute cooldown between OTP requests
2. **OTP Expiry**: 5-minute TTL in Redis
3. **Max Attempts**: 3 attempts before lockout
4. **Phone Normalization**: E.164 format validation
5. **Secure Transport**: HTTPS in production
6. **Token Caching**: Client credentials tokens cached securely
7. **Audit Logging**: All OTP attempts logged

---

## References

- [Keycloak Server Development Guide](https://www.keycloak.org/docs/latest/server_development/)
- [Authentication SPI Documentation](https://www.keycloak.org/docs/latest/server_development/#_auth_spi)
- [Keycloak Quickstarts Repository](https://github.com/keycloak/keycloak-quickstarts)
- [Spring Security OAuth2 Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/index.html)
