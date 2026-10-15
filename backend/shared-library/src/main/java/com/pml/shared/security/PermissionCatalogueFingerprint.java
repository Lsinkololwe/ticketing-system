package com.pml.shared.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.TreeSet;

/**
 * A digest of what the compiled catalogue says: every permission code with its scope, and what
 * each platform role grants. Services each carry their own copy of the shared library, so two
 * services built from different revisions would silently disagree about who may do what; comparing
 * this value with the one identity publishes makes that disagreement visible.
 */
public final class PermissionCatalogueFingerprint {

    private PermissionCatalogueFingerprint() {
    }

    public static String compute() {
        TreeSet<String> lines = new TreeSet<>();
        for (Permission permission : Permission.values()) {
            lines.add("permission|" + permission.code() + "|" + permission.scope());
        }
        for (PlatformRole role : PlatformRole.values()) {
            lines.add("platform-role|" + role.name() + "|" + String.join(",", Permission.codes(role.grants())));
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
