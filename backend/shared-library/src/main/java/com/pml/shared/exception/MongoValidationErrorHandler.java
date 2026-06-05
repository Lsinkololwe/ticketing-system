package com.pml.shared.exception;

import com.mongodb.MongoWriteException;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles MongoDB validation errors and converts them to user-friendly exceptions.
 * <p>
 * MongoDB error code 121 indicates document validation failure.
 * This handler extracts validation details from MongoDB's error response
 * and creates a structured {@link MongoSchemaValidationException}.
 * </p>
 * <p>
 * Example MongoDB validation error structure:
 * <pre>
 * {
 *   "code": 121,
 *   "codeName": "DocumentValidationFailure",
 *   "errInfo": {
 *     "failingDocumentId": "...",
 *     "details": {
 *       "operatorName": "$jsonSchema",
 *       "schemaRulesNotSatisfied": [...]
 *     }
 *   }
 * }
 * </pre>
 * </p>
 *
 * @since 1.0.0
 */
@Slf4j
@Component
public class MongoValidationErrorHandler {

    /**
     * MongoDB error code for document validation failure.
     */
    private static final int DOCUMENT_VALIDATION_FAILURE = 121;

    /**
     * Handles a MongoDB write exception and converts it to a MongoSchemaValidationException
     * if it represents a validation failure.
     *
     * @param exception The MongoDB write exception
     * @param collectionName The collection where the error occurred
     * @param document The document that failed validation
     * @return A Mono that emits a MongoSchemaValidationException
     */
    public Mono<MongoSchemaValidationException> handleValidationError(
            MongoWriteException exception,
            String collectionName,
            Document document) {

        return Mono.fromCallable(() -> {
            // Check if this is a validation error
            if (exception.getError().getCode() != DOCUMENT_VALIDATION_FAILURE) {
                // Not a validation error, rethrow original
                throw exception;
            }

            // Extract validation details
            List<String> validationErrors = extractValidationErrors(exception);

            // Sanitize document to remove sensitive fields
            Document sanitizedDoc = sanitizeDocument(document);

            String message = String.format(
                    "Document validation failed for collection '%s': %s",
                    collectionName,
                    String.join(", ", validationErrors)
            );

            return new MongoSchemaValidationException(
                    message,
                    collectionName,
                    sanitizedDoc,
                    validationErrors,
                    exception.getError().getCode(),
                    exception
            );
        });
    }

    /**
     * Extracts validation error details from MongoDB's error response.
     * <p>
     * Parses the nested error structure to find specific validation failures.
     * </p>
     *
     * @param exception The MongoDB write exception
     * @return List of validation error messages
     */
    private List<String> extractValidationErrors(MongoWriteException exception) {
        List<String> errors = new ArrayList<>();

        try {
            // MongoDB 4.4+ error structure
            String message = exception.getError().getMessage();

            // Extract errInfo if available
            Object errInfo = exception.getError().getDetails();
            if (errInfo instanceof Document errDoc) {
                // Try to parse the details field
                Object details = errDoc.get("details");
                if (details instanceof Document detailsDoc) {
                    Object rulesNotSatisfied = detailsDoc.get("schemaRulesNotSatisfied");
                    if (rulesNotSatisfied instanceof List<?> rulesList) {
                        parseSchemaRulesNotSatisfied(rulesList, errors);
                    }
                }
            }

            // Fallback to generic message if no specific errors found
            if (errors.isEmpty() && message != null) {
                errors.add(parseGenericMessage(message));
            }

            // Final fallback
            if (errors.isEmpty()) {
                errors.add("Document failed schema validation");
            }

        } catch (Exception e) {
            log.warn("Failed to parse MongoDB validation error details", e);
            errors.add("Document validation failed (error details unavailable)");
        }

        return errors;
    }

    /**
     * Parses MongoDB's schemaRulesNotSatisfied array to extract validation failures.
     *
     * @param rulesList The list of rules that were not satisfied
     * @param errors The list to populate with error messages
     */
    @SuppressWarnings("unchecked")
    private void parseSchemaRulesNotSatisfied(List<?> rulesList, List<String> errors) {
        for (Object rule : rulesList) {
            if (rule instanceof Document ruleDoc) {
                String operatorName = ruleDoc.getString("operatorName");

                // Handle missing required fields
                if ("required".equals(operatorName)) {
                    Object missingProps = ruleDoc.get("specifiedAs");
                    if (missingProps instanceof Document specDoc) {
                        Object requiredFields = specDoc.get("required");
                        if (requiredFields instanceof List<?> fields) {
                            errors.add("Missing required fields: " + fields);
                        }
                    }
                }

                // Handle property validation failures
                Object propertiesNotSatisfied = ruleDoc.get("propertiesNotSatisfied");
                if (propertiesNotSatisfied instanceof List<?> propsList) {
                    for (Object prop : propsList) {
                        if (prop instanceof Document propDoc) {
                            String propertyName = propDoc.getString("propertyName");
                            String description = propDoc.getString("description");
                            if (propertyName != null) {
                                String error = description != null
                                    ? String.format("Field '%s': %s", propertyName, description)
                                    : String.format("Field '%s' failed validation", propertyName);
                                errors.add(error);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Parses a generic MongoDB error message to extract useful information.
     *
     * @param message The error message
     * @return A sanitized error message
     */
    private String parseGenericMessage(String message) {
        // Extract meaningful part of error message
        if (message.contains("Document failed validation")) {
            return "Document failed validation";
        }
        // Truncate very long messages
        return message.length() > 200 ? message.substring(0, 200) + "..." : message;
    }

    /**
     * Sanitizes a document by removing sensitive fields before including in exception.
     * <p>
     * OWASP A03:2021 - Sensitive Data Exposure prevention.
     * Remove fields like passwords, tokens, payment details from error responses.
     * </p>
     *
     * @param document The document to sanitize
     * @return A sanitized copy of the document
     */
    private Document sanitizeDocument(Document document) {
        if (document == null) {
            return new Document();
        }

        Document sanitized = new Document(document);

        // Remove sensitive fields
        List<String> sensitiveFields = List.of(
                "password",
                "passwordHash",
                "token",
                "accessToken",
                "refreshToken",
                "apiKey",
                "secret",
                "creditCardNumber",
                "cvv",
                "pin",
                "ssn",
                "nationalId"
        );

        sensitiveFields.forEach(sanitized::remove);

        // Mask email addresses and phone numbers
        if (sanitized.containsKey("email")) {
            sanitized.put("email", maskEmail(sanitized.getString("email")));
        }
        if (sanitized.containsKey("phone") || sanitized.containsKey("phoneNumber")) {
            String phoneKey = sanitized.containsKey("phone") ? "phone" : "phoneNumber";
            sanitized.put(phoneKey, maskPhone(sanitized.getString(phoneKey)));
        }

        return sanitized;
    }

    /**
     * Masks an email address for logging/error reporting.
     * Example: john.doe@example.com → j***@example.com
     */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        String[] parts = email.split("@");
        String localPart = parts[0];
        String maskedLocal = localPart.length() > 1
            ? localPart.charAt(0) + "***"
            : "***";
        return maskedLocal + "@" + parts[1];
    }

    /**
     * Masks a phone number for logging/error reporting.
     * Example: +260977123456 → +260***3456
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) {
            return "***";
        }
        return phone.substring(0, 4) + "***" + phone.substring(phone.length() - 4);
    }
}
