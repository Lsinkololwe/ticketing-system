package com.pml.keycloak.authenticator;

import com.pml.keycloak.identity.ContactOtpClient;
import com.pml.keycloak.identity.Dto;
import com.pml.keycloak.identity.IdentityApiException;
import com.pml.keycloak.identity.IdentityUnavailableException;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.events.Errors;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.services.managers.BruteForceProtector;
import org.keycloak.sessions.AuthenticationSessionModel;

/**
 * Buyer sign-in by verified contact. Two modes:
 *
 * <ul>
 *   <li><b>HANDOFF</b>: the app already proved the contact; {@code login_hint} carries a single-use
 *       login handle which is redeemed server-side. No page is shown.</li>
 *   <li><b>SCREEN</b>: contact page, then code page (challenge, verify, ensure without handle).</li>
 * </ul>
 *
 * The authenticator never creates users and never grants roles: the Keycloak user is created by
 * identity-service, looked up here by {@code username = accountId}. Staff accounts are refused.
 * Without a configured identity client it fails closed.
 */
public class ContactOtpAuthenticator implements Authenticator {

    private static final Logger LOG = Logger.getLogger(ContactOtpAuthenticator.class);

    static final String LOGIN_HINT = "login_hint";
    static final Set<String> STAFF_ROLES = Set.of("ADMIN", "SUPER_ADMIN", "FINANCE", "FINANCE_LEAD");

    static final String N_STEP = "contact-otp.step";
    static final String N_CHALLENGE = "contact-otp.challengeId";
    static final String N_CONTACT = "contact-otp.contact";
    static final String N_MASKED = "contact-otp.masked";
    static final String N_EXPIRES_AT = "contact-otp.expiresAt";
    static final String N_RESEND_AT = "contact-otp.resendAt";
    static final String N_PROOF = "contact-otp.proof";
    static final String N_HANDOFF_TRIED = "contact-otp.handoffTried";
    private static final List<String> ALL_NOTES = List.of(N_STEP, N_CHALLENGE, N_CONTACT, N_MASKED,
            N_EXPIRES_AT, N_RESEND_AT, N_PROOF);

    static final String STEP_CONTACT = "CONTACT";
    static final String STEP_CODE = "CODE";

    static final int ENSURE_ATTEMPTS = 5;
    private static final Duration MAX_POLL = Duration.ofSeconds(3);

    private final ContactOtpClient client;
    private final Clock clock;
    private final Sleeper sleeper;
    private final String regionHint;

    public ContactOtpAuthenticator(ContactOtpClient client, Clock clock, Sleeper sleeper, String regionHint) {
        this.client = client;
        this.clock = clock;
        this.sleeper = sleeper;
        this.regionHint = regionHint;
    }

    // ---- entry points -------------------------------------------------------------------------

    @Override
    public void authenticate(AuthenticationFlowContext ctx) {
        if (client == null) {
            LOG.error("contact-otp-authenticator has no identity credentials: refusing every sign-in");
            fail(ctx, AuthenticationFlowError.INTERNAL_ERROR, Errors.INVALID_CONFIG, null);
            return;
        }
        AuthenticationSessionModel as = ctx.getAuthenticationSession();
        if (as.getAuthNote(N_STEP) != null) {          // refresh of a page already in progress
            renderCurrent(ctx, List.of());
            return;
        }
        String handle = as.getClientNote(LOGIN_HINT);
        if (handle != null && !handle.isBlank() && as.getAuthNote(N_HANDOFF_TRIED) == null) {
            as.setAuthNote(N_HANDOFF_TRIED, "1");
            as.removeClientNote(LOGIN_HINT);
            handoff(ctx, handle.trim());
            return;
        }
        showContact(ctx, List.of(), null);
    }

    @Override
    public void action(AuthenticationFlowContext ctx) {
        if (client == null) {
            fail(ctx, AuthenticationFlowError.INTERNAL_ERROR, Errors.INVALID_CONFIG, null);
            return;
        }
        AuthenticationSessionModel as = ctx.getAuthenticationSession();
        MultivaluedMap<String, String> form = ctx.getHttpRequest().getDecodedFormParameters();
        String step = as.getAuthNote(N_STEP);
        if (STEP_CODE.equals(step)) {
            String action = form.getFirst("action");
            if ("change".equals(action)) {
                clearNotes(as);
                showContact(ctx, List.of(), null);
            } else if ("resend".equals(action)) {
                resend(ctx);
            } else {
                verify(ctx, form.getFirst("code"));
            }
        } else {
            requestChallenge(ctx, form.getFirst("contact"));
        }
    }

    // ---- HANDOFF ------------------------------------------------------------------------------

    private void handoff(AuthenticationFlowContext ctx, String handle) {
        try {
            String accountId = client.redeem(handle, clientId(ctx));
            complete(ctx, accountId);
        } catch (IdentityApiException e) {
            LOG.infof("Login handle refused (%s); falling back to the contact screen", e.errorCode());
            showContact(ctx, List.of(), null);
        } catch (IdentityUnavailableException e) {
            LOG.warn("Login handle could not be redeemed: " + e.getMessage());
            showContact(ctx, List.of(new FormMessage(null, "contact.error.unavailable")), null);
        }
    }

    // ---- SCREEN: contact page -------------------------------------------------------------------

    private void requestChallenge(AuthenticationFlowContext ctx, String rawContact) {
        String contact = rawContact == null ? "" : rawContact.trim();
        if (contact.isEmpty()) {
            showContact(ctx, List.of(new FormMessage("contact", "contact.error.contactRequired")), "");
            return;
        }
        try {
            Dto.ChallengeResponse c = client.challenge(new Dto.ChallengeRequest(
                    new Dto.ContactRef(contact, null), regionHint, ctx.getConnection().getRemoteAddr(), null, null));
            storeChallenge(ctx.getAuthenticationSession(), c, contact);
            showCode(ctx, List.of());
        } catch (RuntimeException e) {
            ProblemMessages.Mapped m = ProblemMessages.map(e, "contact");
            LOG.infof("Challenge refused: %s", e instanceof IdentityApiException a ? a.errorCode() : e.getClass().getSimpleName());
            showContact(ctx, List.of(m.message()), contact);
        }
    }

    // ---- SCREEN: code page ----------------------------------------------------------------------

    private void verify(AuthenticationFlowContext ctx, String rawCode) {
        AuthenticationSessionModel as = ctx.getAuthenticationSession();
        String proof = as.getAuthNote(N_PROOF);
        try {
            if (proof == null) {
                String code = rawCode == null ? "" : rawCode.replaceAll("\\s", "");
                String challengeId = as.getAuthNote(N_CHALLENGE);
                if (challengeId == null) {
                    showContact(ctx, List.of(), null);
                    return;
                }
                if (code.isEmpty()) {
                    showCode(ctx, List.of(new FormMessage("code", "contact.error.codeRequired")));
                    return;
                }
                Dto.VerifyResponse v = client.verify(challengeId, code);
                proof = v.proof();
                as.setAuthNote(N_PROOF, proof);
            }
            ensureAndComplete(ctx, proof);
        } catch (RuntimeException e) {
            handleFailure(ctx, e);
        }
    }

    private void ensureAndComplete(AuthenticationFlowContext ctx, String proof) {
        AuthenticationSessionModel as = ctx.getAuthenticationSession();
        for (int attempt = 1; attempt <= ENSURE_ATTEMPTS; attempt++) {
            Dto.EnsureResponse r;
            try {
                r = client.ensure(proof, clientId(ctx), false);
            } catch (IdentityApiException e) {
                if ("CONTACT_ALREADY_CLAIMED".equals(e.errorCode()) && attempt < ENSURE_ATTEMPTS) {
                    continue;                                        // race lost: contract says retry
                }
                handleFailure(ctx, e);
                return;
            } catch (IdentityUnavailableException e) {
                handleFailure(ctx, e);
                return;
            }
            if (r != null && r.active()) {
                complete(ctx, r.accountId());
                return;
            }
            if (attempt < ENSURE_ATTEMPTS) {
                int wait = r == null || r.retryAfterSeconds() == null ? 2 : Math.max(1, r.retryAfterSeconds());
                try {
                    sleeper.sleep(Duration.ofSeconds(wait).compareTo(MAX_POLL) > 0 ? MAX_POLL : Duration.ofSeconds(wait));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        // Still provisioning: keep the proof so the next press only repeats ensure.
        as.setAuthNote(N_PROOF, proof);
        showCode(ctx, List.of(new FormMessage(null, "contact.error.provisioning")));
    }

    private void resend(AuthenticationFlowContext ctx) {
        AuthenticationSessionModel as = ctx.getAuthenticationSession();
        String contact = as.getAuthNote(N_CONTACT);
        if (contact == null) {
            showContact(ctx, List.of(), null);
            return;
        }
        try {
            Dto.ChallengeResponse c = client.challenge(new Dto.ChallengeRequest(
                    new Dto.ContactRef(contact, null), regionHint, ctx.getConnection().getRemoteAddr(), null, null));
            as.removeAuthNote(N_PROOF);
            storeChallenge(as, c, contact);
            showCode(ctx, List.of(), "contact.info.resent");
        } catch (RuntimeException e) {
            ProblemMessages.Mapped m = ProblemMessages.map(e, "code");
            showCode(ctx, List.of(m.message()));
        }
    }

    private void handleFailure(AuthenticationFlowContext ctx, RuntimeException e) {
        ProblemMessages.Mapped m = ProblemMessages.map(e, "code");
        LOG.infof("Contact sign-in step refused: %s",
                e instanceof IdentityApiException a ? a.errorCode() : e.getClass().getSimpleName());
        switch (m.next()) {
            case TERMINAL -> {
                ctx.getEvent().error(Errors.USER_DISABLED);
                Response page = ctx.form().setError(m.message().getMessage()).createErrorPage(Response.Status.FORBIDDEN);
                ctx.failure(AuthenticationFlowError.USER_DISABLED, page);
            }
            case CONTACT_PAGE -> {
                String contact = ctx.getAuthenticationSession().getAuthNote(N_CONTACT);
                AuthenticationSessionModel as = ctx.getAuthenticationSession();
                as.removeAuthNote(N_PROOF);
                as.removeAuthNote(N_CHALLENGE);
                showContact(ctx, List.of(m.message()), contact);
            }
            default -> showCode(ctx, List.of(m.message()));
        }
    }

    // ---- completion ---------------------------------------------------------------------------

    /** Loads the Keycloak user for the account and signs it in. Never creates, never grants roles. */
    void complete(AuthenticationFlowContext ctx, String accountId) {
        if (accountId == null || accountId.isBlank() || accountId.length() > 128) {
            fail(ctx, AuthenticationFlowError.INVALID_USER, Errors.USER_NOT_FOUND, null);
            return;
        }
        KeycloakSession session = ctx.getSession();
        RealmModel realm = ctx.getRealm();
        UserModel user = session.users().getUserByUsername(realm, accountId);
        if (user == null) {
            LOG.warn("Account is ACTIVE in identity-service but has no Keycloak user; refusing sign-in");
            fail(ctx, AuthenticationFlowError.INVALID_USER, Errors.USER_NOT_FOUND, null);
            return;
        }
        if (!user.isEnabled()) {
            fail(ctx, AuthenticationFlowError.USER_DISABLED, Errors.USER_DISABLED, user);
            return;
        }
        if (isStaff(realm, user)) {
            LOG.warn("Staff account attempted contact sign-in; refused");
            fail(ctx, AuthenticationFlowError.INVALID_USER, Errors.NOT_ALLOWED, user);
            return;
        }
        BruteForceProtector protector = ctx.getProtector();
        if (protector != null && (protector.isPermanentlyLockedOut(session, realm, user)
                || protector.isTemporarilyDisabled(session, realm, user))) {
            fail(ctx, AuthenticationFlowError.USER_TEMPORARILY_DISABLED, Errors.USER_TEMPORARILY_DISABLED, user);
            return;
        }
        clearNotes(ctx.getAuthenticationSession());
        ctx.setUser(user);
        ctx.success();
    }

    static boolean isStaff(RealmModel realm, UserModel user) {
        for (String name : STAFF_ROLES) {
            RoleModel role = realm.getRole(name);
            if (role != null && user.hasRole(role)) {
                return true;
            }
        }
        return false;
    }

    private void fail(AuthenticationFlowContext ctx, AuthenticationFlowError error, String eventError, UserModel user) {
        if (user != null) {
            ctx.getEvent().user(user);
        }
        ctx.getEvent().error(eventError);
        clearNotes(ctx.getAuthenticationSession());
        Response page = ctx.form().setError("contact.error.generic").createErrorPage(Response.Status.UNAUTHORIZED);
        ctx.failure(error, page);
    }

    // ---- rendering ----------------------------------------------------------------------------

    private void renderCurrent(AuthenticationFlowContext ctx, List<FormMessage> errors) {
        if (STEP_CODE.equals(ctx.getAuthenticationSession().getAuthNote(N_STEP))) {
            showCode(ctx, errors);
        } else {
            showContact(ctx, errors, null);
        }
    }

    private void showContact(AuthenticationFlowContext ctx, List<FormMessage> errors, String prefill) {
        ctx.getAuthenticationSession().setAuthNote(N_STEP, STEP_CONTACT);
        LoginFormsProvider form = ctx.form();
        applyErrors(form, errors);
        form.setAttribute("contactValue", prefill == null ? "" : prefill);
        ctx.challenge(form.createForm("contact-input.ftl"));
    }

    private void showCode(AuthenticationFlowContext ctx, List<FormMessage> errors) {
        showCode(ctx, errors, null);
    }

    private void showCode(AuthenticationFlowContext ctx, List<FormMessage> errors, String infoKey) {
        AuthenticationSessionModel as = ctx.getAuthenticationSession();
        as.setAuthNote(N_STEP, STEP_CODE);
        LoginFormsProvider form = ctx.form();
        applyErrors(form, errors);
        if (infoKey != null) {
            form.setInfo(infoKey);
        }
        long now = clock.millis();
        form.setAttribute("maskedContact", orEmpty(as.getAuthNote(N_MASKED)));
        form.setAttribute("expiresInSeconds", remaining(as.getAuthNote(N_EXPIRES_AT), now));
        form.setAttribute("resendAfterSeconds", remaining(as.getAuthNote(N_RESEND_AT), now));
        ctx.challenge(form.createForm("contact-code.ftl"));
    }

    private static void applyErrors(LoginFormsProvider form, List<FormMessage> errors) {
        for (FormMessage m : errors) {
            if (m.getField() == null) {
                form.setError(m.getMessage(), m.getParameters());
            } else {
                form.addError(m);
            }
        }
    }

    // ---- helpers ------------------------------------------------------------------------------

    private void storeChallenge(AuthenticationSessionModel as, Dto.ChallengeResponse c, String contact) {
        long now = clock.millis();
        as.setAuthNote(N_CHALLENGE, c.challengeId());
        as.setAuthNote(N_CONTACT, contact);
        as.setAuthNote(N_MASKED, orEmpty(c.maskedContact()));
        as.setAuthNote(N_EXPIRES_AT, String.valueOf(now + c.expiresInSeconds() * 1000L));
        as.setAuthNote(N_RESEND_AT, String.valueOf(now + c.resendAfterSeconds() * 1000L));
        as.setAuthNote(N_STEP, STEP_CODE);
    }

    private static void clearNotes(AuthenticationSessionModel as) {
        ALL_NOTES.forEach(as::removeAuthNote);
    }

    private static long remaining(String epochMillis, long now) {
        if (epochMillis == null) {
            return 0;
        }
        try {
            return Math.max(0, (Long.parseLong(epochMillis) - now + 999) / 1000);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String clientId(AuthenticationFlowContext ctx) {
        return ctx.getAuthenticationSession().getClient().getClientId();
    }

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        // never adds required actions
    }

    @Override
    public void close() {
        // nothing to release
    }
}
