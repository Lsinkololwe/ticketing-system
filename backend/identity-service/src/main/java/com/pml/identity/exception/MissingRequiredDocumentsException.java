package com.pml.identity.exception;

import com.pml.identity.domain.enums.BusinessType;

import java.util.List;

/**
 * Raised when {@code submitForReview} is called before every verification
 * document required by the applicant's business type has been supplied.
 *
 * <h2>Why this carries the missing types</h2>
 * The refusal names exactly what is missing rather than a
 * generic "incomplete". An applicant told "your application is incomplete" has
 * to go hunting; an applicant told "we still need your certificate of
 * incorporation" knows what to do next. Verification is already the
 * highest-friction moment in the product — a refusal that cannot be acted on is
 * where the application gets abandoned.
 *
 * <p>Maps to the {@code DOCUMENT_REQUIRED} error code.</p>
 */
public class MissingRequiredDocumentsException extends RuntimeException {

    /** Error code carried to the client. */
    public static final String ERROR_CODE = "DOCUMENT_REQUIRED";

    private final BusinessType businessType;
    private final List<String> missingDocumentTypes;

    public MissingRequiredDocumentsException(BusinessType businessType, List<String> missingDocumentTypes) {
        super(buildMessage(businessType, missingDocumentTypes));
        this.businessType = businessType;
        this.missingDocumentTypes = List.copyOf(missingDocumentTypes);
    }

    private static String buildMessage(BusinessType businessType, List<String> missing) {
        return "Missing required document(s) for business type %s: %s"
                .formatted(businessType, String.join(", ", missing));
    }

    public BusinessType getBusinessType() {
        return businessType;
    }

    /** The document types still outstanding, in declaration order. */
    public List<String> getMissingDocumentTypes() {
        return missingDocumentTypes;
    }

    public String getErrorCode() {
        return ERROR_CODE;
    }
}
