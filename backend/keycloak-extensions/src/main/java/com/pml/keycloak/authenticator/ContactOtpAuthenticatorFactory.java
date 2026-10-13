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
 * Registers the buyer sign-in step with Keycloak under the id {@code contact-otp-authenticator}
 * and wires it to identity-service.
 *
 * <p>What a buyer experiences: the realm's browser flow reaches this step, and the buyer either
 * types their phone number or e-mail and then the code they received, or (when the app already
 * proved the contact) is signed in without seeing a page.</p>
 *
 * <p>What this factory promises: the step is only ever built with working credentials for
 * identity-service. It reads four required variables, IDENTITY_BASE_URL, IDENTITY_CLIENT_ID,
 * IDENTITY_CLIENT_SECRET and KEYCLOAK_TOKEN_URL, plus the optional CONTACT_OTP_REGION_HINT (for
 * example ZM, used to read numbers typed without a country code). If any required variable is
 * missing or blank, Keycloak refuses to start and the error names the variable, never its value.
 * Failing at start-up is deliberate: a server that came up without them could only refuse every
 * sign-in, and the operator would otherwise find out from failed buyer sign-ins instead of from
 * the deployment itself.</p>
 *
 * <p>The authenticator it builds never creates users and never grants roles; see
 * {@link ContactOtpAuthenticator} for what happens on each refusal.</p>
 */
public class ContactOtpAuthenticatorFactory implements AuthenticatorFactory {

    public static final String PROVIDER_ID = "contact-otp-authenticator";
    private static final Logger LOG = Logger.getLogger(ContactOtpAuthenticatorFactory.class);

    private static final Requirement[] REQUIREMENTS = {Requirement.REQUIRED, Requirement.ALTERNATIVE, Requirement.DISABLED};

    private volatile ContactOtpAuthenticator authenticator;

    @Override
    public void init(Config.Scope config) {
        start(System.getenv());
    }

    /**
     * Builds the authenticator from the given environment (the process environment in production,
     * a plain map in tests).
     *
     * @throws IllegalStateException naming the missing variables when a required one is blank
     */
    public void start(Map<String, String> env) {
        IdentityHttp http = IdentityHttp.requireFromEnvironment(env, Clock.systemUTC());
        String region = env.get("CONTACT_OTP_REGION_HINT");
        authenticator = new ContactOtpAuthenticator(new ContactOtpClient(http),
                Clock.systemUTC(), Sleeper.REAL, region == null || region.isBlank() ? null : region.trim());
        LOG.info("contact-otp-authenticator ready");
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
