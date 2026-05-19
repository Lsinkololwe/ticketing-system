package com.pml.keycloak.authenticator;

import org.jboss.logging.Logger;
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
 * Registers the authenticator with Keycloak and provides configuration options.
 */
public class PhoneOtpAuthenticatorFactory implements AuthenticatorFactory {

    private static final Logger LOG = Logger.getLogger(PhoneOtpAuthenticatorFactory.class);

    public static final String PROVIDER_ID = "phone-otp-authenticator";
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
        otpServiceUrl.setHelpText("Base URL of the Identity Service that handles OTP operations");
        CONFIG_PROPERTIES.add(otpServiceUrl);

        // Default channel configuration
        ProviderConfigProperty defaultChannel = new ProviderConfigProperty();
        defaultChannel.setName(CONFIG_DEFAULT_CHANNEL);
        defaultChannel.setLabel("Default Delivery Channel");
        defaultChannel.setType(ProviderConfigProperty.LIST_TYPE);
        defaultChannel.setDefaultValue("whatsapp");
        defaultChannel.setOptions(List.of("whatsapp", "sms"));
        defaultChannel.setHelpText("Default channel for OTP delivery (WhatsApp or SMS)");
        CONFIG_PROPERTIES.add(defaultChannel);
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayType() {
        return DISPLAY_TYPE;
    }

    @Override
    public String getReferenceCategory() {
        return "phone-otp";
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return new AuthenticationExecutionModel.Requirement[]{
                AuthenticationExecutionModel.Requirement.REQUIRED,
                AuthenticationExecutionModel.Requirement.ALTERNATIVE,
                AuthenticationExecutionModel.Requirement.DISABLED
        };
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false; // Users don't need to set up anything
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
        // Get configuration from environment or use default
        String otpServiceUrl = System.getenv("OTP_SERVICE_URL");
        if (otpServiceUrl == null || otpServiceUrl.isEmpty()) {
            otpServiceUrl = "http://identity-service:8083";
        }

        LOG.debugf("Creating PhoneOtpAuthenticator with OTP service URL: %s", otpServiceUrl);
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
