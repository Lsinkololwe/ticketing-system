package com.pml.shared.util;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

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

    /** A strictly parsed mobile number: canonical E.164 plus its ISO-3166 region. */
    public record Parsed(String e164, String region) {
    }

    private static final Pattern ZAMBIAN_WITH_CC = Pattern.compile("^260\\d{9}$");
    private static final Pattern LOCAL_ZM = Pattern.compile("^0\\d{9}$");

    /**
     * Strict contact parser (CONTRACT 3). The input must carry a country code ({@code +} or
     * {@code 00} prefix) or the caller supplies {@code regionHint}. With neither, only a Zambian
     * local number ({@code 0} plus 9 digits, 10 in all) or {@code 260} plus 9 digits is accepted.
     * The result must be a valid MOBILE (or FIXED_LINE_OR_MOBILE) number. Applying the country
     * allowlist is the caller's job.
     *
     * @return empty for blank, unparseable, invalid or non-mobile input
     */
    public static Optional<Parsed> parseMobile(String raw, String regionHint) {
        if (raw == null) {
            return Optional.empty();
        }
        String text = raw.trim();
        if (text.isEmpty() || text.length() > 40 || text.indexOf('@') >= 0) {
            return Optional.empty();
        }
        // Reject letters and anything else libphonenumber would silently map (vanity numbers).
        if (!text.matches("^[+0-9 ()\\-.]+$")) {
            return Optional.empty();
        }
        String digits = text.replaceAll("[^0-9+]", "");
        String region;
        if (digits.startsWith("+")) {
            region = DEFAULT_REGION; // ignored by libphonenumber for '+' numbers
        } else if (digits.startsWith("00")) {
            digits = "+" + digits.substring(2);
            region = DEFAULT_REGION;
        } else if (regionHint != null && !regionHint.isBlank()) {
            region = regionHint.trim().toUpperCase(Locale.ROOT);
        } else if (LOCAL_ZM.matcher(digits).matches()) {
            region = DEFAULT_REGION;
        } else if (ZAMBIAN_WITH_CC.matcher(digits).matches()) {
            digits = "+" + digits;
            region = DEFAULT_REGION;
        } else {
            return Optional.empty();
        }
        if (digits.indexOf('+') > 0) {
            return Optional.empty();
        }
        try {
            Phonenumber.PhoneNumber parsed = UTIL.parse(digits, region);
            if (!UTIL.isValidNumber(parsed)) {
                return Optional.empty();
            }
            PhoneNumberType type = UTIL.getNumberType(parsed);
            if (type != PhoneNumberType.MOBILE && type != PhoneNumberType.FIXED_LINE_OR_MOBILE) {
                return Optional.empty();
            }
            String actual = UTIL.getRegionCodeForNumber(parsed);
            if (actual == null || "ZZ".equals(actual)) {
                return Optional.empty();
            }
            return Optional.of(new Parsed(UTIL.format(parsed, PhoneNumberFormat.E164), actual));
        } catch (NumberParseException e) {
            return Optional.empty();
        }
    }

    /** {@link #parseMobile} plus the caller's country allowlist (ISO alpha-2, case-insensitive). */
    public static Optional<Parsed> parseMobile(String raw, String regionHint, Collection<String> allowedCountries) {
        return parseMobile(raw, regionHint).filter(p -> {
            if (allowedCountries == null) {
                return true;
            }
            // Territories sharing a calling code with their main region (GG/JE/IM with GB) pass
            // when either the exact region or the calling code's main region is allowed.
            int cc = UTIL.getCountryCodeForRegion(p.region());
            String main = UTIL.getRegionCodeForCountryCode(cc);
            return allowedCountries.stream().anyMatch(c -> c != null
                    && (c.equalsIgnoreCase(p.region()) || c.equalsIgnoreCase(main)));
        });
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
