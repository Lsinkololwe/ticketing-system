package com.pml.identity.validation;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import com.pml.shared.util.PhoneNumbers;
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
    // MOBILE MONEY PHONE NUMBER VALIDATION
    // ============================================================================

    /**
     * The first two digits of a Zambian national mobile number that a payout can be sent to.
     * - MTN: 96, 76
     * - Airtel: 97, 77
     * - Zamtel: 95
     * This is the only rule that is specific to payouts; reading the number itself is the platform's
     * one phone normaliser ({@link PhoneNumbers#parseMobile}), not code in this class.
     */
    private static final java.util.Set<String> MOBILE_MONEY_PREFIXES = java.util.Set.of("95", "96", "97", "76", "77");

    /**
     * Validate a Zambian mobile-money number. Any form the shared normaliser reads as a Zambian
     * mobile number is accepted, then it must belong to a mobile-money network.
     *
     * @param phoneNumber the phone number to validate
     * @return true if valid
     */
    public static boolean isValidZambianMobileNumber(String phoneNumber) {
        return normalizeToE164(phoneNumber) != null;
    }

    /**
     * Canonical E.164 form of a payout number, or null when it is not a Zambian mobile-money number.
     * Parsing is delegated to {@link PhoneNumbers#parseMobile}; this method only adds the payout
     * network check.
     *
     * @param phoneNumber the phone number to normalize
     * @return normalized phone number or null if invalid
     */
    public static String normalizeToE164(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return null;
        }
        // Log WITHOUT exposing the number (privacy): only whether it was accepted.
        String normalized = PhoneNumbers.parseMobile(phoneNumber, PhoneNumbers.DEFAULT_REGION)
                .filter(parsed -> PhoneNumbers.DEFAULT_REGION.equals(parsed.region()))
                .map(PhoneNumbers.Parsed::e164)
                // a Zambian national mobile number is nine digits; its first two name the network
                .filter(e164 -> e164.length() == 13 && MOBILE_MONEY_PREFIXES.contains(e164.substring(4, 6)))
                .orElse(null);
        log.debug("Phone validation: {}", normalized != null ? "VALID" : "INVALID");
        return normalized;
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
     * @return masked phone number (e.g., "+260****1234" for a Zambian number)
     */
    public static String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 8) {
            return "****";
        }
        String last4 = phoneNumber.substring(phoneNumber.length() - 4);
        // The country prefix comes from the platform's phone normaliser, not from a hand-written rule.
        return PhoneNumbers.parseMobile(phoneNumber, PhoneNumbers.DEFAULT_REGION)
                .map(parsed -> parsed.e164().substring(0, 4) + "****" + last4)
                .orElse("****" + last4);
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
}
