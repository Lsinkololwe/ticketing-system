package com.pml.catalog.storage;

import java.util.Map;

/**
 * Where and how a client may put one file: the address, the headers it must send exactly as given, and
 * nothing else; the caller decides how long the permission lasts. The key is the store's choice, never the caller's.
 */
public record UploadGrant(String fileKey, String url, Map<String, String> headers) {
}
