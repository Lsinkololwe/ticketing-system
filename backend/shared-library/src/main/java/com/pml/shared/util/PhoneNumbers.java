package com.pml.shared.util;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber;

/**
 * Canonical phone-number handling for the platform.
 *
 * <p>This is the single source of truth for turning any user-supplied phone
 * number into a valid <a href="https://en.wikipedia.org/wiki/E.164">E.164</a>
 * string (e.g. {@code +260971234567}). It delegates to Google's libphonenumber,
 * which validates per-country length/prefix rules and correctly handles the
 * national trunk prefix (so {@code "0969944454"} in Zambia becomes
 * {@code "+260969944454"}, dropping the leading {@code 0} — a bug the previous
 * hand-rolled normalizers got wrong by keeping it).</p>
 *
 * <p>All backend write paths into {@code users.phoneNumber} (Better Auth aside,
 * which normalizes on the frontend with libphonenumber-js) MUST funnel through
 * here so the value always satisfies the strict {@code users} {@code $jsonSchema}
 * validator (<code>^\+[1-9]\d{1,14}$</code>) and OTP/mobile-money delivery
 * targets a real MSISDN.</p>
 */
public final class PhoneNumbers {

    /** Default region (ISO-3166 alpha-2) assumed when a number has no country code. */
    public static final String DEFAULT_REGION = "ZM";

    private static final PhoneNumberUtil UTIL = PhoneNumberUtil.getInstance();

    private PhoneNumbers() {
    }

    /**
     * Normalize to E.164 using the default region ({@value #DEFAULT_REGION}).
     *
     * @return canonical E.164 string, or {@code null} if blank/unparseable/invalid.
     */
    public static String toE164(String raw) {
        return toE164(raw, DEFAULT_REGION);
    }

    /**
     * Normalize to E.164.
     *
     * @param raw           any user-supplied form (local, international, with separators)
     * @param defaultRegion ISO-3166 alpha-2 used only when {@code raw} has no '+' country code
     * @return canonical E.164 string, or {@code null} if blank/unparseable/invalid.
     */
    public static String toE164(String raw, String defaultRegion) {
        Phonenumber.PhoneNumber parsed = parse(raw, defaultRegion);
        if (parsed == null || !UTIL.isValidNumber(parsed)) {
            return null;
        }
        return UTIL.format(parsed, PhoneNumberFormat.E164);
    }

    /**
     * @return {@code true} if {@code raw} is a valid number for some region.
     */
    public static boolean isValid(String raw, String defaultRegion) {
        Phonenumber.PhoneNumber parsed = parse(raw, defaultRegion);
        return parsed != null && UTIL.isValidNumber(parsed);
    }

    public static boolean isValid(String raw) {
        return isValid(raw, DEFAULT_REGION);
    }

    /**
     * Resolve the ISO-3166 alpha-2 country for a number (e.g. {@code "ZM"}).
     *
     * @return region code, or {@code null} if it cannot be determined.
     */
    public static String regionFor(String raw, String defaultRegion) {
        Phonenumber.PhoneNumber parsed = parse(raw, defaultRegion);
        if (parsed == null) {
            return null;
        }
        String region = UTIL.getRegionCodeForNumber(parsed);
        return (region == null || "ZZ".equals(region)) ? null : region;
    }

    public static String regionFor(String raw) {
        return regionFor(raw, DEFAULT_REGION);
    }

    private static Phonenumber.PhoneNumber parse(String raw, String defaultRegion) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String region = (defaultRegion == null || defaultRegion.isBlank())
                ? DEFAULT_REGION
                : defaultRegion.trim().toUpperCase();
        try {
            // When `trimmed` begins with '+', libphonenumber ignores the region hint.
            return UTIL.parse(trimmed, region);
        } catch (NumberParseException e) {
            return null;
        }
    }
}
