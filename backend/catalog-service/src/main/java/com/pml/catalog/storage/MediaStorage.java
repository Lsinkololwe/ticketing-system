package com.pml.catalog.storage;

import reactor.core.publisher.Mono;

/**
 * Where the bytes of an image live: the same contract and the same {@code file-storage.type} switch
 * identity-service uses for verification documents, written over bytes because catalog receives the
 * picture inside a GraphQL request and has already checked what it is.
 *
 * <p>Exactly one store is active, chosen by {@code file-storage.type} ({@code local} by default,
 * {@code s3}). A key is chosen by the store, never by the caller, so a caller cannot name a path.
 */
public interface MediaStorage {

    /**
     * Stores {@code bytes} under a fresh key.
     *
     * @param scope    the owning organization's id, or {@code stock}; only a folder name
     * @param fileName the original name, sanitised into the key
     */
    Mono<Stored> store(byte[] bytes, String contentType, String scope, String fileName);

    Mono<Void> delete(String fileKey);

    /** The bytes under {@code fileKey}, or empty when the store has none. */
    Mono<byte[]> read(String fileKey);

    /**
     * Permission to put one file of exactly {@code sizeBytes} and {@code contentType} under {@code fileKey},
     * without the bytes passing through this service. The grant binds the size and the type, so a
     * client cannot send a different file under it.
     *
     * @param fileKey a key from {@link #newKey}
     * @param token   the signed upload token, for a store that has to carry it in the address
     */
    Mono<UploadGrant> grantUpload(String fileKey, String contentType, long sizeBytes, java.time.Duration validFor,
                                  String token);

    /** A fresh key for a file of {@code scope}; random, so it cannot be guessed or enumerated. */
    default String newKey(String scope, String fileName) {
        return MediaKeys.keyFor(scope, fileName);
    }

    /** Writes {@code bytes} at a key this store granted; only a store that receives the upload itself supports it. */
    default Mono<Void> putAt(String fileKey, byte[] bytes) {
        return Mono.error(new UnsupportedOperationException("this store is written to directly by the client"));
    }

    /** The public address of the file; computed, so it follows the store and never goes stale. */
    String publicUrl(String fileKey);

    /** What a store did: the key it chose, the size it wrote and the SHA-256 (hex) of the bytes. */
    record Stored(String fileKey, long sizeBytes, String checksum) {
    }
}
