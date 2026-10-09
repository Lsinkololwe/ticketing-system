package com.pml.shared.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Email normalisation for contact identity (CONTRACT 3): trim, NFC, lower-case, simple RFC 5322 form, max 254. */
public final class Emails {

    public static final int MAX_LENGTH = 254;

    private static final Pattern SIMPLE = Pattern.compile(
            "^[a-z0-9.!#$%&'*+/=?^_`{|}~-]{1,64}@[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+$");

    private Emails() {
    }

    /** @return the normalised address, or empty when blank or not a plausible address. */
    public static Optional<String> normalize(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String value = Normalizer.normalize(raw.trim(), Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        if (value.isEmpty() || value.length() > MAX_LENGTH || !SIMPLE.matcher(value).matches()) {
            return Optional.empty();
        }
        String local = value.substring(0, value.indexOf('@'));
        if (local.startsWith(".") || local.endsWith(".") || local.contains("..")) {
            return Optional.empty();
        }
        return Optional.of(value);
    }
}
