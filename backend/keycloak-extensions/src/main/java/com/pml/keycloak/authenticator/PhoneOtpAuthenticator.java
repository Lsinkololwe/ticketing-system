package com.pml.keycloak.authenticator;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Phone OTP Authenticator for Keycloak.
 *
 * Enables passwordless authentication via phone number and OTP verification.
 * Works with the Identity Service's OTP infrastructure via REST API calls.
 *
 * Flow:
 * 1. User enters phone number
 * 2. Authenticator requests OTP from Identity Service
 * 3. User receives OTP via WhatsApp/SMS
 * 4. User enters OTP
 * 5. Authenticator verifies OTP with Identity Service
 * 6. On success, user is authenticated (found or created)
 */
public class PhoneOtpAuthenticator implements Authenticator {

    private static final Logger LOG = Logger.getLogger(PhoneOtpAuthenticator.class);

    private static final String AUTH_NOTE_PHONE = "phone_otp_phone_number";
    private static final String AUTH_NOTE_STEP = "phone_otp_step";
    private static final String STEP_PHONE_INPUT = "phone_input";
    private static final String STEP_OTP_VERIFY = "otp_verify";

    private final OtpServiceClient otpServiceClient;

    public PhoneOtpAuthenticator(String otpServiceUrl) {
        this.otpServiceClient = new OtpServiceClient(otpServiceUrl);
    }

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        LOG.debug("PhoneOtpAuthenticator.authenticate() called");

        // Check if this is a form submission
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        String currentStep = context.getAuthenticationSession().getAuthNote(AUTH_NOTE_STEP);

        if (formData.containsKey("phoneNumber") && !STEP_OTP_VERIFY.equals(currentStep)) {
            // Phone number submitted - request OTP
            handlePhoneSubmission(context, formData);
        } else if (formData.containsKey("otp") && STEP_OTP_VERIFY.equals(currentStep)) {
            // OTP submitted - verify
            handleOtpSubmission(context, formData);
        } else {
            // Initial state - show phone input form
            showPhoneInputForm(context, null);
        }
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        LOG.debug("PhoneOtpAuthenticator.action() called");
        authenticate(context);
    }

    /**
     * Show phone number input form.
     */
    private void showPhoneInputForm(AuthenticationFlowContext context, String errorMessage) {
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
     * Handle phone number submission - request OTP.
     */
    private void handlePhoneSubmission(AuthenticationFlowContext context, MultivaluedMap<String, String> formData) {
        String phoneNumber = formData.getFirst("phoneNumber");
        String channel = formData.getFirst("channel");
        if (channel == null || channel.isEmpty()) {
            channel = "whatsapp";
        }

        LOG.infof("Phone number submitted: %s, channel: %s", maskPhoneNumber(phoneNumber), channel);

        // Validate phone number format
        if (!isValidPhoneNumber(phoneNumber)) {
            showPhoneInputForm(context, "Invalid phone number format. Please enter a valid phone number.");
            return;
        }

        // Normalize phone number
        String normalizedPhone = normalizePhoneNumber(phoneNumber);

        // Request OTP from Identity Service
        OtpServiceClient.OtpRequestResult result = otpServiceClient.requestOtp(normalizedPhone, channel);

        if (result.isSuccess()) {
            // OTP sent successfully - show OTP input form
            context.getAuthenticationSession().setAuthNote(AUTH_NOTE_PHONE, normalizedPhone);
            context.getAuthenticationSession().setAuthNote(AUTH_NOTE_STEP, STEP_OTP_VERIFY);

            Response challenge = context.form()
                    .setAttribute("phone", maskPhoneNumber(normalizedPhone))
                    .setAttribute("expiresIn", result.getExpiresIn())
                    .createForm("phone-otp-verify.ftl");
            context.challenge(challenge);
        } else if ("COOLDOWN".equals(result.getStatus())) {
            // Rate limited
            showPhoneInputForm(context,
                    String.format("Please wait %d seconds before requesting another code.", result.getCooldownRemaining()));
        } else {
            // Delivery failed
            showPhoneInputForm(context, "Failed to send verification code. Please try again.");
        }
    }

    /**
     * Handle OTP submission - verify and authenticate.
     */
    private void handleOtpSubmission(AuthenticationFlowContext context, MultivaluedMap<String, String> formData) {
        String otp = formData.getFirst("otp");
        String phoneNumber = context.getAuthenticationSession().getAuthNote(AUTH_NOTE_PHONE);

        if (phoneNumber == null) {
            // Session expired or invalid state
            showPhoneInputForm(context, "Session expired. Please enter your phone number again.");
            return;
        }

        LOG.infof("OTP submitted for phone: %s", maskPhoneNumber(phoneNumber));

        // Validate OTP format
        if (!isValidOtp(otp)) {
            Response challenge = context.form()
                    .setAttribute("phone", maskPhoneNumber(phoneNumber))
                    .setError("Invalid code format. Please enter the 6-digit code.")
                    .createForm("phone-otp-verify.ftl");
            context.challenge(challenge);
            return;
        }

        // Verify OTP with Identity Service
        OtpServiceClient.OtpVerifyResult result = otpServiceClient.verifyOtp(phoneNumber, otp);

        if (result.isValid()) {
            // OTP verified - find or create user
            UserModel user = findOrCreateUser(context, phoneNumber);
            if (user != null) {
                context.setUser(user);
                context.success();
                LOG.infof("User authenticated via phone OTP: %s", maskPhoneNumber(phoneNumber));
            } else {
                context.failureChallenge(AuthenticationFlowError.INTERNAL_ERROR,
                        context.form().setError("Failed to create user account. Please try again.")
                                .createForm("phone-otp-input.ftl"));
            }
        } else {
            // Invalid OTP
            Response challenge = context.form()
                    .setAttribute("phone", maskPhoneNumber(phoneNumber))
                    .setError("Invalid or expired code. Please try again.")
                    .createForm("phone-otp-verify.ftl");
            context.challenge(challenge);
        }
    }

    /**
     * Find existing user by phone number or create a new one.
     */
    private UserModel findOrCreateUser(AuthenticationFlowContext context, String phoneNumber) {
        KeycloakSession session = context.getSession();
        RealmModel realm = context.getRealm();

        // Search for user by phone_number attribute
        List<UserModel> users = session.users()
                .searchForUserByUserAttributeStream(realm, "phone_number", phoneNumber)
                .collect(Collectors.toList());

        if (!users.isEmpty()) {
            UserModel user = users.get(0);
            // Ensure phone is marked as verified
            user.setSingleAttribute("phone_verified", "true");
            LOG.infof("Found existing user with phone: %s", maskPhoneNumber(phoneNumber));
            return user;
        }

        // Create new user
        try {
            String username = generateUsername(phoneNumber);
            UserModel newUser = session.users().addUser(realm, username);
            newUser.setEnabled(true);
            newUser.setSingleAttribute("phone_number", phoneNumber);
            newUser.setSingleAttribute("phone_verified", "true");

            // Assign default CUSTOMER role if it exists
            realm.getRolesStream()
                    .filter(role -> "CUSTOMER".equalsIgnoreCase(role.getName()))
                    .findFirst()
                    .ifPresent(newUser::grantRole);

            LOG.infof("Created new user with phone: %s, username: %s", maskPhoneNumber(phoneNumber), username);
            return newUser;
        } catch (Exception e) {
            LOG.errorf(e, "Failed to create user for phone: %s", maskPhoneNumber(phoneNumber));
            return null;
        }
    }

    /**
     * Generate username from phone number.
     */
    private String generateUsername(String phoneNumber) {
        // Use last 8 digits of phone number as username base
        String cleanPhone = phoneNumber.replaceAll("[^0-9]", "");
        String suffix = cleanPhone.length() > 8 ? cleanPhone.substring(cleanPhone.length() - 8) : cleanPhone;
        return "user_" + suffix;
    }

    /**
     * Validate phone number format.
     */
    private boolean isValidPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return false;
        }
        String cleaned = phoneNumber.replaceAll("[^0-9+]", "");
        return cleaned.matches("^\\+?[0-9]{10,15}$");
    }

    /**
     * Validate OTP format.
     */
    private boolean isValidOtp(String otp) {
        if (otp == null || otp.isEmpty()) {
            return false;
        }
        return otp.matches("^[0-9]{4,8}$");
    }

    /**
     * Normalize phone number to E.164 format.
     */
    private String normalizePhoneNumber(String phoneNumber) {
        // Canonical E.164 rule (kept in sync with backend PhoneNumbers util and the
        // frontend libphonenumber-js normalizer). The previous version prepended
        // "+260" without dropping the national trunk 0, producing phantom-0 numbers
        // like +2600969944454 that are invalid for delivery.
        String cleaned = phoneNumber.replaceAll("[^0-9+]", "");
        if (cleaned.startsWith("+")) {
            return cleaned;
        }
        if (cleaned.startsWith("00")) {
            return "+" + cleaned.substring(2);
        }
        if (cleaned.startsWith("260")) {
            return "+" + cleaned;
        }
        if (cleaned.startsWith("0")) {
            // Local national format → drop trunk 0, add Zambia country code
            return "+260" + cleaned.substring(1);
        }
        // Bare national subscriber number
        return "+260" + cleaned;
    }

    /**
     * Mask phone number for logging/display.
     */
    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() <= 4) {
            return "****";
        }
        return "***" + phoneNumber.substring(phoneNumber.length() - 4);
    }

    @Override
    public boolean requiresUser() {
        return false; // We find/create the user during authentication
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true; // Always available
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        // No required actions needed
    }

    @Override
    public void close() {
        // No resources to close
    }
}
