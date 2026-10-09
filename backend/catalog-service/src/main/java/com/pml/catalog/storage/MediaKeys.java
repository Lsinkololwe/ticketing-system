package com.pml.catalog.storage;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/** Key and checksum rules shared by every store. */
final class MediaKeys {

    private MediaKeys() {
    }

    /** {@code media/{scope}/{uuid}-{name}}: a random part, so a key cannot be guessed or enumerated. */
    static String keyFor(String scope, String fileName) {
        return "media/" + safe(scope, "stock") + "/" + UUID.randomUUID() + "-" + safe(basename(fileName), "image");
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is part of every Java runtime", e);
        }
    }

    private static String basename(String fileName) {
        if (fileName == null) {
            return "";
        }
        int slash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        return slash >= 0 ? fileName.substring(slash + 1) : fileName;
    }

    private static String safe(String value, String fallback) {
        String cleaned = value == null ? "" : value.replaceAll("[^a-zA-Z0-9._-]", "_");
        cleaned = cleaned.replaceAll("^\\.+", "_");
        if (cleaned.isBlank()) {
            return fallback;
        }
        return cleaned.length() > 100 ? cleaned.substring(cleaned.length() - 100).toLowerCase(Locale.ROOT) : cleaned;
    }
}
