package com.pml.keycloak.authenticator;

import com.pml.keycloak.identity.IdentityApiException;
import com.pml.keycloak.identity.IdentityUnavailableException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.keycloak.models.utils.FormMessage;

/** Maps identity-service problem codes to message keys (bundle: messages_en.properties, contact.*). */
final class ProblemMessages {

    private static final DateTimeFormatter UNTIL =
            DateTimeFormatter.ofPattern("HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private ProblemMessages() {}

    /** Kind of recovery the authenticator should take after showing the message. */
    enum Next { STAY, CONTACT_PAGE, TERMINAL }

    record Mapped(FormMessage message, Next next) {}

    static Mapped map(RuntimeException e, String field) {
        if (e instanceof IdentityUnavailableException) {
            return new Mapped(new FormMessage(null, "contact.error.unavailable"), Next.STAY);
        }
        if (!(e instanceof IdentityApiException api)) {
            return new Mapped(new FormMessage(null, "contact.error.generic"), Next.STAY);
        }
        String code = api.errorCode() == null ? "" : api.errorCode();
        var p = api.problem();
        switch (code) {
            case "CONTACT_INVALID":
                return new Mapped(new FormMessage("contact", "contact.error.contactInvalid"), Next.STAY);
            case "OTP_INVALID": {
                Integer left = p == null ? null : p.attemptsRemaining();
                return new Mapped(left == null
                        ? new FormMessage("code", "contact.error.otpInvalid")
                        : new FormMessage("code", "contact.error.otpInvalidLeft", String.valueOf(left)),
                        Next.STAY);
            }
            case "OTP_EXPIRED":
                return new Mapped(new FormMessage("contact", "contact.error.otpExpired"), Next.CONTACT_PAGE);
            case "OTP_LOCKED":
                return new Mapped(p != null && p.lockedUntil() != null
                        ? new FormMessage(null, "contact.error.lockedUntil", until(p.lockedUntil()))
                        : new FormMessage(null, "contact.error.locked"), Next.STAY);
            case "OTP_RATE_LIMITED":
            case "OTP_COOLDOWN_ACTIVE": {
                Integer wait = p == null ? null : p.retryAfterSeconds();
                return new Mapped(wait == null
                        ? new FormMessage(null, "contact.error.rateLimited")
                        : new FormMessage(null, "contact.error.retryAfter", String.valueOf(wait)), Next.STAY);
            }
            case "OTP_DELIVERY_FAILED":
                return new Mapped(new FormMessage(null, "contact.error.delivery"), Next.STAY);
            case "PROOF_INVALID":
                return new Mapped(new FormMessage(null, "contact.error.proofInvalid"), Next.CONTACT_PAGE);
            case "ACCOUNT_MERGING":
                return new Mapped(new FormMessage(null, "contact.error.merging"), Next.STAY);
            case "ACCOUNT_SUSPENDED":
            case "ACCOUNT_NOT_ACTIVE":
                return new Mapped(new FormMessage(null, "contact.error.accountUnavailable"), Next.TERMINAL);
            default:
                return new Mapped(new FormMessage(null, "contact.error.generic"), Next.STAY);
        }
    }

    private static String until(String iso) {
        try {
            return UNTIL.format(Instant.parse(iso));
        } catch (RuntimeException e) {
            return iso;
        }
    }
}
