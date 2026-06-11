package com.pml.identity.validation;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Pattern;

/**
 * OWASP-compliant validator for financial data.
 *
 * Security Features:
 * - Input sanitization to prevent injection attacks
 * - Format validation for bank accounts, SWIFT codes, etc.
 * - E.164 phone number validation for mobile money
 * - No sensitive data in error messages (PCI-DSS compliance)
 *
 * References:
 * - OWASP Input Validation Cheat Sheet
 * - PCI-DSS Data Security Standard
 * - ISO 13616 (IBAN validation)
 * - ITU-T E.164 (Phone number format)
 */
@Slf4j
@UtilityClass
public class FinancialDataValidator {

    // ============================================================================
    // BANK ACCOUNT VALIDATION
    // ============================================================================

    /**
     * Zambian bank account numbers are typically 10-16 digits.
     * This validator accepts only alphanumeric characters with no special chars.
     *
     * SECURITY: Prevents injection attacks by strict character whitelisting.
     */
    private static final Pattern ZAMBIAN_ACCOUNT_PATTERN = Pattern.compile("^[0-9]{10,16}$");

    /**
     * Validate Zambian bank account number.
     *
     * @param accountNumber the account number to validate
     * @return true if valid
     */
    public static boolean isValidZambianAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) {
            return false;
        }

        String sanitized = sanitizeNumeric(accountNumber);
        boolean valid = ZAMBIAN_ACCOUNT_PATTERN.matcher(sanitized).matches();

        // Log validation attempt WITHOUT exposing account number (PCI-DSS)
        log.debug("Bank account validation: {} (length: {})",
            valid ? "VALID" : "INVALID",
            sanitized.length());

        return valid;
    }

    // ============================================================================
    // SWIFT/BIC CODE VALIDATION
    // ============================================================================

    /**
     * SWIFT/BIC code format: 8 or 11 alphanumeric characters
     * Format: AAAABBCCXXX
     * - AAAA: Bank code (4 letters)
     * - BB: Country code (2 letters, ISO 3166-1)
     * - CC: Location code (2 characters, alphanumeric)
     * - XXX: Branch code (3 characters, optional)
     *
     * Examples:
     * - ZANAZMLU (Zanaco, Zambia, Lusaka)
     * - SCBLZMLUXXX (Standard Chartered, Zambia, Lusaka)
     */
    private static final Pattern SWIFT_CODE_PATTERN = Pattern.compile("^[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?$");

    /**
     * Validate SWIFT/BIC code.
     *
     * @param swiftCode the SWIFT code to validate
     * @return true if valid
     */
    public static boolean isValidSwiftCode(String swiftCode) {
        if (swiftCode == null || swiftCode.isBlank()) {
            return false;
        }

        String sanitized = sanitizeAlphanumeric(swiftCode).toUpperCase();
        boolean valid = SWIFT_CODE_PATTERN.matcher(sanitized).matches();

        log.debug("SWIFT code validation: {} (length: {})",
            valid ? "VALID" : "INVALID",
            sanitized.length());

        return valid;
    }

    // ============================================================================
    // MOBILE MONEY PHONE NUMBER VALIDATION (E.164)
    // ============================================================================

    /**
     * E.164 phone number format for Zambia: +260XXXXXXXXX
     * - Country code: +260 (Zambia)
     * - National number: 9-10 digits
     * - Mobile prefixes: 95, 96, 97, 76, 77
     *
     * Supported providers:
     * - MTN: +26096XXXXXXX, +26076XXXXXXX
     * - Airtel: +26097XXXXXXX, +26077XXXXXXX
     * - Zamtel: +26095XXXXXXX
     *
     * SECURITY: Strict validation prevents phone number spoofing and injection
     */
    private static final Pattern E164_ZAMBIA_PATTERN = Pattern.compile("^\\+260(95|96|97|76|77)\\d{7}$");

    /**
     * Validate E.164 phone number for Zambian mobile networks.
     *
     * @param phoneNumber the phone number to validate
     * @return true if valid
     */
    public static boolean isValidZambianMobileNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return false;
        }

        String sanitized = sanitizePhoneNumber(phoneNumber);
        boolean valid = E164_ZAMBIA_PATTERN.matcher(sanitized).matches();

        // Log WITHOUT exposing full number (GDPR/privacy)
        log.debug("Phone validation: {} (prefix: {})",
            valid ? "VALID" : "INVALID",
            sanitized.length() > 4 ? sanitized.substring(0, 4) + "..." : "N/A");

        return valid;
    }

    /**
     * Normalize phone number to E.164 format.
     *
     * Examples:
     * - 0971234567 -> +260971234567
     * - 971234567 -> +260971234567
     * - +260971234567 -> +260971234567
     *
     * @param phoneNumber the phone number to normalize
     * @return normalized phone number or null if invalid
     */
    public static String normalizeToE164(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return null;
        }

        String sanitized = sanitizePhoneNumber(phoneNumber);

        // Already in E.164 format
        if (sanitized.startsWith("+260")) {
            return sanitized;
        }

        // Remove leading zero
        if (sanitized.startsWith("0")) {
            sanitized = sanitized.substring(1);
        }

        // Add country code
        String normalized = "+260" + sanitized;

        // Validate final format
        return isValidZambianMobileNumber(normalized) ? normalized : null;
    }

    // ============================================================================
    // ACCOUNT HOLDER NAME VALIDATION
    // ============================================================================

    /**
     * Validate account holder name.
     * - Minimum 2 characters
     * - Maximum 100 characters
     * - Letters, spaces, hyphens, apostrophes only
     * - Prevents script injection and XSS
     *
     * SECURITY: Character whitelisting prevents injection attacks
     */
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z\\s\\-']{2,100}$");

    /**
     * Validate account holder name.
     *
     * @param name the name to validate
     * @return true if valid
     */
    public static boolean isValidAccountHolderName(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }

        String trimmed = name.trim();
        boolean valid = NAME_PATTERN.matcher(trimmed).matches();

        log.debug("Account holder name validation: {} (length: {})",
            valid ? "VALID" : "INVALID",
            trimmed.length());

        return valid;
    }

    // ============================================================================
    // SANITIZATION UTILITIES (OWASP)
    // ============================================================================

    /**
     * Sanitize numeric input (digits only).
     * Prevents injection by removing all non-digit characters.
     *
     * @param input the input to sanitize
     * @return sanitized numeric string
     */
    public static String sanitizeNumeric(String input) {
        if (input == null) {
            return "";
        }
        return input.replaceAll("[^0-9]", "");
    }

    /**
     * Sanitize alphanumeric input (letters and digits only).
     * Prevents injection by removing special characters.
     *
     * @param input the input to sanitize
     * @return sanitized alphanumeric string
     */
    public static String sanitizeAlphanumeric(String input) {
        if (input == null) {
            return "";
        }
        return input.replaceAll("[^a-zA-Z0-9]", "");
    }

    /**
     * Sanitize phone number (digits and + only).
     *
     * @param input the input to sanitize
     * @return sanitized phone number
     */
    public static String sanitizePhoneNumber(String input) {
        if (input == null) {
            return "";
        }
        // Allow only digits and leading +
        String cleaned = input.replaceAll("[^0-9+]", "");
        // Ensure only one + at the start
        if (cleaned.startsWith("+")) {
            cleaned = "+" + cleaned.substring(1).replace("+", "");
        } else {
            cleaned = cleaned.replace("+", "");
        }
        return cleaned;
    }

    // ============================================================================
    // MASKING UTILITIES (PCI-DSS REQUIREMENT)
    // ============================================================================

    /**
     * Mask bank account number (show last 4 digits only).
     * PCI-DSS requires masking sensitive account data.
     *
     * @param accountNumber the account number to mask
     * @return masked account number (e.g., "****1234")
     */
    public static String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return "****";
        }
        return "****" + accountNumber.substring(accountNumber.length() - 4);
    }

    /**
     * Mask phone number (show prefix and last 4 digits).
     *
     * @param phoneNumber the phone number to mask
     * @return masked phone number (e.g., "+260****1234")
     */
    public static String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 8) {
            return "+260****";
        }

        if (phoneNumber.startsWith("+260")) {
            return "+260****" + phoneNumber.substring(phoneNumber.length() - 4);
        }

        return "****" + phoneNumber.substring(phoneNumber.length() - 4);
    }

    /**
     * Validate minimum payout amount.
     * Prevents negative amounts and ensures reasonable minimum.
     *
     * @param amount the amount to validate
     * @param minimum the minimum allowed amount
     * @return true if valid
     */
    public static boolean isValidPayoutAmount(Double amount, Double minimum) {
        if (amount == null || minimum == null) {
            return false;
        }
        return amount >= minimum && amount > 0;
    }

    /**
     * Validate commission rate.
     * Should be between 0% and 100% (0.0 - 1.0).
     *
     * @param rate the rate to validate
     * @return true if valid
     */
    public static boolean isValidCommissionRate(Double rate) {
        if (rate == null) {
            return false;
        }
        return rate >= 0.0 && rate <= 1.0;
    }
}
