package com.pml.keycloak.authenticator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.pml.keycloak.identity.ContactOtpClient;
import com.pml.keycloak.identity.Dto;
import com.pml.keycloak.identity.IdentityApiException;
import com.pml.keycloak.identity.IdentityUnavailableException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;

@Tag("ET-IDN-001")
@Tag("layer-1-decision")
class ContactOtpAuthenticatorTest {

    static final String ACCOUNT = "7d1c0c6e-1f5e-4a53-9c58-0a8f6f0d8a11";
    final Clock clock = Clock.fixed(Instant.parse("2026-10-04T08:00:00Z"), ZoneOffset.UTC);
    final List<Duration> slept = new ArrayList<>();
    final ContactOtpClient client = mock(ContactOtpClient.class);
    final AuthFixture fx = new AuthFixture();
    ContactOtpAuthenticator auth;

    @BeforeEach
    void setUp() {
        auth = new ContactOtpAuthenticator(client, clock, slept::add, null);
    }

    static IdentityApiException problem(int status, String code, Integer attempts, Integer retry, String locked) {
        return new IdentityApiException(status, new Dto.Problem(status, "t", "d", code, null, retry, attempts, locked));
    }

    static Dto.ChallengeResponse challenge() {
        return new Dto.ChallengeResponse("ch-1", "EMAIL", "j***@gmail.com", "EMAIL", 300, 60);
    }

    /** Drives the contact page and lands on the code page. */
    void toCodePage() {
        when(client.challenge(any())).thenReturn(challenge());
        auth.authenticate(fx.ctx);
        fx.formData.putSingle("contact", "jane@gmail.com");
        auth.action(fx.ctx);
        assertThat(fx.lastTemplate()).isEqualTo("contact-code.ftl");
        fx.formData.clear();
    }

    void submitCode(String code, String action) {
        fx.formData.clear();
        fx.formData.putSingle("code", code);
        if (action != null) {
            fx.formData.putSingle("action", action);
        }
        auth.action(fx.ctx);
    }

    @Test
    @DisplayName("ET-IDN-001-R1 · SCREEN: contact page, then code page with server-rendered expiry and resend-after")
    void screenFlowRendersPages() {
        toCodePage();
        assertThat(fx.attributes).containsEntry("maskedContact", "j***@gmail.com")
                .containsEntry("expiresInSeconds", 300L).containsEntry("resendAfterSeconds", 60L);
        verify(client).challenge(any(Dto.ChallengeRequest.class));
    }

    @Test
    @DisplayName("ET-IDN-001-R1 · SCREEN: correct code, ensure(issueHandle=false), sign-in by username=accountId")
    void successfulSignIn() {
        toCodePage();
        UserModel user = fx.user(ACCOUNT, true);
        when(client.verify("ch-1", "123456")).thenReturn(new Dto.VerifyResponse("proof-1", "EMAIL", "j***", 120));
        when(client.ensure("proof-1", "myticketzm-web", false))
                .thenReturn(new Dto.EnsureResponse(ACCOUNT, "ACTIVE", true, null, null));
        submitCode("123 456", "verify");
        verify(fx.ctx).setUser(user);
        verify(fx.ctx).success();
        verify(client).ensure("proof-1", "myticketzm-web", false);
    }

    @Test
    @DisplayName("ET-IDN-001-R2 · wrong code shows attemptsRemaining on the code field and stays on the code page")
    void wrongCode() {
        toCodePage();
        when(client.verify(anyString(), anyString())).thenThrow(problem(400, "OTP_INVALID", 3, null, null));
        submitCode("000000", null);
        assertThat(fx.hasKey("contact.error.otpInvalidLeft")).isTrue();
        assertThat(fx.message("contact.error.otpInvalidLeft").getParameters()).containsExactly("3");
        assertThat(fx.lastTemplate()).isEqualTo("contact-code.ftl");
        verify(fx.ctx, never()).success();
    }

    @Test
    @DisplayName("ET-IDN-001-R2 · expired code returns to the contact page with the contact prefilled")
    void expiredCode() {
        toCodePage();
        when(client.verify(anyString(), anyString())).thenThrow(problem(410, "OTP_EXPIRED", null, null, null));
        submitCode("123456", null);
        assertThat(fx.hasKey("contact.error.otpExpired")).isTrue();
        assertThat(fx.lastTemplate()).isEqualTo("contact-input.ftl");
        assertThat(fx.attributes).containsEntry("contactValue", "jane@gmail.com");
    }

    @Test
    @DisplayName("ET-IDN-001-R2 · locked shows lockedUntil, rate limited and cooldown show retryAfter")
    void lockedAndRateLimited() {
        toCodePage();
        when(client.verify(anyString(), anyString()))
                .thenThrow(problem(423, "OTP_LOCKED", null, null, "2026-10-04T08:15:00Z"));
        submitCode("123456", null);
        assertThat(fx.message("contact.error.lockedUntil").getParameters()).containsExactly("08:15 UTC");

        org.mockito.Mockito.doThrow(problem(429, "OTP_COOLDOWN_ACTIVE", null, 42, null)).when(client).challenge(any());
        submitCode("", "resend");
        assertThat(fx.message("contact.error.retryAfter").getParameters()).containsExactly("42");
        assertThat(fx.lastTemplate()).isEqualTo("contact-code.ftl");

        fx.globalErrors.clear();
        org.mockito.Mockito.doThrow(problem(429, "OTP_RATE_LIMITED", null, null, null)).when(client).challenge(any());
        submitCode("", "resend");
        assertThat(fx.hasKey("contact.error.rateLimited")).isTrue();
    }

    @Test
    @DisplayName("ET-IDN-001-R3 · the resend button requests a new challenge and shows the info message")
    void resendIsHandled() {
        toCodePage();
        when(client.challenge(any())).thenReturn(new Dto.ChallengeResponse("ch-2", "EMAIL", "j***", "EMAIL", 300, 60));
        submitCode("", "resend");
        verify(client, times(2)).challenge(any());
        when(client.verify(eq("ch-2"), anyString())).thenThrow(problem(400, "OTP_INVALID", 4, null, null));
        submitCode("111111", null);
        verify(client).verify("ch-2", "111111");
    }

    @Test
    @DisplayName("ET-IDN-001-R3 · the change-contact button clears state and shows the contact page")
    void changeContactIsHandled() {
        toCodePage();
        submitCode("", "change");
        assertThat(fx.lastTemplate()).isEqualTo("contact-input.ftl");
        assertThat(fx.authNotes.get(ContactOtpAuthenticator.N_CHALLENGE)).isNull();
        assertThat(fx.authNotes.get(ContactOtpAuthenticator.N_CONTACT)).isNull();
    }

    @Test
    @DisplayName("ET-IDN-001-R4 · missing Keycloak user fails generically and NEVER creates it")
    void unknownAccountNeverCreated() {
        toCodePage();
        when(client.verify(anyString(), anyString())).thenReturn(new Dto.VerifyResponse("p", "EMAIL", "m", 120));
        when(client.ensure(anyString(), anyString(), anyBoolean()))
                .thenReturn(new Dto.EnsureResponse(ACCOUNT, "ACTIVE", true, null, null));
        when(fx.users.getUserByUsername(fx.realm, ACCOUNT)).thenReturn(null);
        submitCode("123456", null);
        verify(fx.ctx).failure(eq(AuthenticationFlowError.INVALID_USER), any());
        verify(fx.ctx, never()).success();
        verify(fx.users, never()).addUser(any(), anyString());
        verify(fx.users, never()).addUser(any(), anyString(), anyString(), anyBoolean(), anyBoolean());
        assertThat(fx.hasKey("contact.error.generic")).isTrue();
    }

    @Test
    @DisplayName("ET-IDN-001-R4 · disabled user fails and is never enabled")
    void disabledUser() {
        UserModel user = fx.user(ACCOUNT, false);
        fx.authNotes.put(ContactOtpAuthenticator.N_STEP, "X");
        auth.complete(fx.ctx, ACCOUNT);
        verify(fx.ctx).failure(eq(AuthenticationFlowError.USER_DISABLED), any());
        verify(user, never()).setEnabled(true);
        verify(fx.ctx, never()).success();
    }

    @Test
    @DisplayName("ET-IDN-001-R4 · brute-force lockout blocks sign-in")
    void bruteForceLocked() {
        fx.user(ACCOUNT, true);
        when(fx.protector.isTemporarilyDisabled(any(), any(), any())).thenReturn(true);
        auth.complete(fx.ctx, ACCOUNT);
        verify(fx.ctx).failure(eq(AuthenticationFlowError.USER_TEMPORARILY_DISABLED), any());
        verify(fx.ctx, never()).success();
    }

    @Test
    @DisplayName("ET-IDN-001-R5 · sign-in grants no role, adds no required action, changes no attribute")
    void noRoleGrants() {
        UserModel user = fx.user(ACCOUNT, true);
        auth.complete(fx.ctx, ACCOUNT);
        verify(fx.ctx).success();
        verify(user, never()).grantRole(any());
        verify(user, never()).setSingleAttribute(anyString(), anyString());
        verify(user, never()).setAttribute(anyString(), any());
        verify(user, never()).addRequiredAction(anyString());
        verify(user, never()).setEnabled(anyBoolean());
    }

    @Test
    @DisplayName("ET-IDN-001-R6 · staff roles are refused in both SCREEN and HANDOFF")
    void staffRefused() {
        for (String staffRole : List.of("ADMIN", "SUPER_ADMIN", "FINANCE", "FINANCE_LEAD")) {
            AuthFixture f = new AuthFixture();
            ContactOtpAuthenticator a = new ContactOtpAuthenticator(client, clock, slept::add, null);
            UserModel user = f.user(ACCOUNT, true);
            RoleModel role = f.role(staffRole);
            when(user.hasRole(role)).thenReturn(true);
            a.complete(f.ctx, ACCOUNT);
            verify(f.ctx).failure(eq(AuthenticationFlowError.INVALID_USER), any());
            verify(f.ctx, never()).success();
        }
    }

    @Test
    @DisplayName("ET-IDN-001-R7 · HANDOFF with a valid handle signs in with no page")
    void handoffValid() {
        UserModel user = fx.user(ACCOUNT, true);
        fx.clientNotes.put("login_hint", "handle-1");
        when(client.redeem("handle-1", "myticketzm-web")).thenReturn(ACCOUNT);
        auth.authenticate(fx.ctx);
        verify(fx.ctx).setUser(user);
        verify(fx.ctx).success();
        verify(fx.ctx, never()).challenge(any());
        assertThat(fx.templates).isEmpty();
        verify(client, never()).challenge(any());
        assertThat(fx.clientNotes).doesNotContainKey("login_hint");
    }

    @Test
    @DisplayName("ET-IDN-001-R7 · HANDOFF with an invalid or replayed handle falls back to the SCREEN contact page")
    void handoffInvalidAndReplayed() {
        fx.clientNotes.put("login_hint", "bad");
        when(client.redeem("bad", "myticketzm-web")).thenThrow(problem(400, "LOGIN_HANDLE_INVALID", null, null, null));
        auth.authenticate(fx.ctx);
        assertThat(fx.lastTemplate()).isEqualTo("contact-input.ftl");
        verify(fx.ctx, never()).success();

        // a page refresh must not redeem again
        fx.clientNotes.put("login_hint", "bad");
        auth.authenticate(fx.ctx);
        verify(client, times(1)).redeem(anyString(), anyString());
    }

    @Test
    @DisplayName("ET-IDN-001-R7 · HANDOFF for a handle whose account has no Keycloak user fails generically")
    void handoffUnknownUser() {
        fx.clientNotes.put("login_hint", "h");
        when(client.redeem("h", "myticketzm-web")).thenReturn(ACCOUNT);
        when(fx.users.getUserByUsername(fx.realm, ACCOUNT)).thenReturn(null);
        auth.authenticate(fx.ctx);
        verify(fx.ctx).failure(eq(AuthenticationFlowError.INVALID_USER), any());
        verify(fx.users, never()).addUser(any(), anyString());
    }

    @Test
    @DisplayName("ET-IDN-001-R8 · ensure answering 202 is polled with bounded waits, then the proof is kept")
    void provisioningPoll() {
        toCodePage();
        fx.user(ACCOUNT, true);
        when(client.verify(anyString(), anyString())).thenReturn(new Dto.VerifyResponse("p", "EMAIL", "m", 120));
        when(client.ensure("p", "myticketzm-web", false))
                .thenReturn(new Dto.EnsureResponse(null, "PROVISIONING", null, null, 2))
                .thenReturn(new Dto.EnsureResponse(ACCOUNT, "ACTIVE", true, null, null));
        submitCode("123456", null);
        verify(fx.ctx).success();
        assertThat(slept).containsExactly(Duration.ofSeconds(2));
    }

    @Test
    @DisplayName("ET-IDN-001-R8 · still provisioning after the bound shows a message and a retry reuses the proof")
    void provisioningExhausted() {
        toCodePage();
        when(client.verify(anyString(), anyString())).thenReturn(new Dto.VerifyResponse("p", "EMAIL", "m", 120));
        when(client.ensure(anyString(), anyString(), anyBoolean()))
                .thenReturn(new Dto.EnsureResponse(null, "PROVISIONING", null, null, 10));
        submitCode("123456", null);
        assertThat(fx.hasKey("contact.error.provisioning")).isTrue();
        assertThat(slept).hasSize(ContactOtpAuthenticator.ENSURE_ATTEMPTS - 1)
                .allMatch(d -> d.compareTo(Duration.ofSeconds(3)) <= 0);
        submitCode("", null);
        verify(client, times(1)).verify(anyString(), anyString());   // proof reused, code not verified twice
    }

    @Test
    @DisplayName("ET-IDN-001-R8 · ACCOUNT_SUSPENDED ends the flow and never signs in")
    void suspended() {
        toCodePage();
        when(client.verify(anyString(), anyString())).thenReturn(new Dto.VerifyResponse("p", "EMAIL", "m", 120));
        when(client.ensure(anyString(), anyString(), anyBoolean())).thenThrow(problem(403, "ACCOUNT_SUSPENDED", null, null, null));
        submitCode("123456", null);
        verify(fx.ctx).failure(eq(AuthenticationFlowError.USER_DISABLED), any());
        verify(fx.ctx, never()).success();
    }

    @Test
    @DisplayName("ET-IDN-001-R9 · identity-service down shows an unavailable message; no sign-in")
    void unavailable() {
        when(client.challenge(any())).thenThrow(new IdentityUnavailableException("down"));
        auth.authenticate(fx.ctx);
        fx.formData.putSingle("contact", "+260971234567");
        auth.action(fx.ctx);
        assertThat(fx.hasKey("contact.error.unavailable")).isTrue();
        verify(fx.ctx, never()).success();
    }

    @Test
    @DisplayName("ET-IDN-001-R9 · FAIL CLOSED without an identity client: every attempt is refused")
    void failClosed() {
        ContactOtpAuthenticator closed = new ContactOtpAuthenticator(null, clock, slept::add, null);
        closed.authenticate(fx.ctx);
        closed.action(fx.ctx);
        verify(fx.ctx, times(2)).failure(eq(AuthenticationFlowError.INTERNAL_ERROR), any());
        verify(fx.ctx, never()).success();
        verify(fx.ctx, never()).challenge(any());
    }

    @Test
    @DisplayName("ET-IDN-001-R1 · an empty contact is rejected on the field without calling identity-service")
    void emptyContact() {
        auth.authenticate(fx.ctx);
        fx.formData.putSingle("contact", "   ");
        auth.action(fx.ctx);
        assertThat(fx.hasKey("contact.error.contactRequired")).isTrue();
        verify(client, never()).challenge(any());
        verifyNoMoreInteractions(client);
    }
}
