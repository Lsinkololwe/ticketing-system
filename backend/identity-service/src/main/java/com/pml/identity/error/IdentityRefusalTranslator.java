package com.pml.identity.error;

import com.pml.identity.exception.MissingRequiredDocumentsException;
import com.pml.identity.exception.UserNotFoundException;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.RefusalTranslator;
import com.pml.shared.error.TranslatedRefusal;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Every exception identity-service declares, mapped to its code in the shared error registry.
 *
 * <h2>This service answers questions about who exists, so every detail is withheld</h2>
 * An exception message such as {@code "User with email 'someone@example.com' already exists"}
 * answers "is this person registered?" for whoever asks — the canonical account-enumeration
 * oracle, reachable on a registration form without a session. So a refusal carries its code
 * and nothing else: no detail distinguishes "no such user" from "a user you may not see",
 * because the distinction <em>is</em> the disclosure. Every tenant-scoped lookup follows the
 * same rule.
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
@Component
public class IdentityRefusalTranslator implements RefusalTranslator {

    @Override
    public Optional<DomainRefusal> translate(Throwable exception) {
        return Optional.ofNullable(switch (exception) {

            case UserNotFoundException ignored -> new TranslatedRefusal(
                    ErrorCode.USER_UNKNOWN, "user not found or not visible to this caller");

            case MissingRequiredDocumentsException ignored -> new TranslatedRefusal(
                    // The one place a detail is warranted: the caller is the
                    // organization completing its own application, the missing
                    // document types are information it must have to proceed,
                    // and BE-4 supplies them under `missingFields`.
                    ErrorCode.DOCUMENT_REQUIRED, "verification documents outstanding");

            default -> null;
        });
    }
}
