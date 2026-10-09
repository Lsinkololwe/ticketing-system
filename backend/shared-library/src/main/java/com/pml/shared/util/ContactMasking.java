package com.pml.shared.util;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

/** Display masks that never reveal a full contact: {@code +260 97* ***123}, {@code j***@gmail.com}. */
public final class ContactMasking {

    private ContactMasking() {
    }

    public static String maskPhone(String e164) {
        if (e164 == null || !e164.startsWith("+")) {
            return "***";
        }
        try {
            Phonenumber.PhoneNumber p = PhoneNumberUtil.getInstance().parse(e164, "ZZ");
            String national = String.valueOf(p.getNationalNumber());
            String masked;
            if (national.length() <= 6) {
                masked = "*".repeat(national.length() - 2) + national.substring(national.length() - 2);
            } else {
                masked = national.substring(0, 2) + "*".repeat(national.length() - 5)
                        + national.substring(national.length() - 3);
            }
            StringBuilder out = new StringBuilder("+").append(p.getCountryCode());
            out.append(' ').append(masked, 0, Math.min(3, masked.length()));
            if (masked.length() > 3) {
                out.append(' ').append(masked, 3, masked.length());
            }
            return out.toString();
        } catch (NumberParseException e) {
            return "***";
        }
    }

    public static String maskEmail(String email) {
        if (email == null) {
            return "***";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    public static String mask(String type, String normalized) {
        return "EMAIL".equalsIgnoreCase(type) ? maskEmail(normalized) : maskPhone(normalized);
    }
}
