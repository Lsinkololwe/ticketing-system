package com.pml.identity.service.storage;

import java.net.URI;
import java.util.Optional;

/**
 * Turns a document URL a client supplied into a storage key, and only if that key lies inside the
 * organization's own folder.
 *
 * <p>The URL is client input. Treating it as trusted lets an organization register a document that
 * points at another organization's file, and the delete that follows then removes the victim's
 * file. The key is therefore taken from the URL's path and accepted only when it starts with
 * {@code organizations/{id}/verification-documents/}, the prefix the upload-URL endpoint hands out.
 * A path with a {@code ..} segment is refused outright rather than normalised, because the storage
 * backend may resolve it differently from this check.</p>
 */
public final class DocumentKeys {

    private DocumentKeys() {
    }

    public static Optional<String> ownedKey(String organizationId, String documentUrl) {
        if (organizationId == null || organizationId.isBlank() || documentUrl == null || documentUrl.isBlank()) {
            return Optional.empty();
        }
        String path;
        try {
            path = URI.create(documentUrl.trim()).getPath();
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
        if (path == null || path.contains("\\") || path.contains("//")) {
            return Optional.empty();
        }
        for (String segment : path.split("/")) {
            if (segment.equals("..") || segment.equals(".")) {
                return Optional.empty();
            }
        }
        String prefix = "organizations/" + organizationId + "/verification-documents/";
        int at = path.indexOf(prefix);
        if (at < 0 || (at > 0 && path.charAt(at - 1) != '/') || path.length() == at + prefix.length()) {
            return Optional.empty();
        }
        return Optional.of(path.substring(at));
    }
}
