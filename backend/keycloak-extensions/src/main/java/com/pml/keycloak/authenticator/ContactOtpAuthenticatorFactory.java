package com.pml.keycloak.authenticator;

import com.pml.keycloak.identity.ContactOtpClient;
import com.pml.keycloak.identity.IdentityHttp;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel.Requirement;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

/**
 * Provider id {@code contact-otp-authenticator}. Environment: IDENTITY_BASE_URL, IDENTITY_CLIENT_ID,
 * IDENTITY_CLIENT_SECRET, KEYCLOAK_TOKEN_URL (all required, no unauthenticated mode) and optional
 * CONTACT_OTP_REGION_HINT. A missing variable logs an ERROR naming the variable and every
 * authentication through this provider is refused.
 */
public class ContactOtpAuthenticatorFactory implements AuthenticatorFactory {

    public static final String PROVIDER_ID = "contact-otp-authenticator";
    private static final Logger LOG = Logger.getLogger(ContactOtpAuthenticatorFactory.class);

    private static final Requirement[] REQUIREMENTS = {Requirement.REQUIRED, Requirement.ALTERNATIVE, Requirement.DISABLED};

    private volatile ContactOtpAuthenticator authenticator;

    @Override
    public void init(Config.Scope config) {
        Map<String, String> env = System.getenv();
        List<String> missing = IdentityHttp.missing(env);
        IdentityHttp http = IdentityHttp.fromEnvironment(env, Clock.systemUTC());
        if (http == null) {
            LOG.errorf("contact-otp-authenticator DISABLED (fail closed): missing environment %s", missing);
        }
        String region = env.get("CONTACT_OTP_REGION_HINT");
        authenticator = new ContactOtpAuthenticator(http == null ? null : new ContactOtpClient(http),
                Clock.systemUTC(), Sleeper.REAL, region == null || region.isBlank() ? null : region.trim());
    }

    /** For tests and embedding. */
    public ContactOtpAuthenticatorFactory withAuthenticator(ContactOtpAuthenticator a) {
        this.authenticator = a;
        return this;
    }

    @Override
    public Authenticator create(KeycloakSession session) {
        ContactOtpAuthenticator a = authenticator;
        return a != null ? a : new ContactOtpAuthenticator(null, Clock.systemUTC(), Sleeper.REAL, null);
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // nothing
    }

    @Override
    public void close() {
        // nothing
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayType() {
        return "Contact OTP (WhatsApp or email)";
    }

    @Override
    public String getReferenceCategory() {
        return "contact-otp";
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public Requirement[] getRequirementChoices() {
        return REQUIREMENTS;
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public String getHelpText() {
        return "Signs a buyer in by a one-time code sent to a verified contact (SCREEN) or by a "
                + "single-use login handle supplied as login_hint (HANDOFF). Never creates users or roles.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return List.of();
    }
}
