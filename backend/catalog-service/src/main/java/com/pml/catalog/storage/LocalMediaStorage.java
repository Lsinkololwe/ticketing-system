package com.pml.catalog.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Local disk, for development and tests; production sets {@code file-storage.type=s3}.
 *
 * <p>Files are served back by {@code MediaFileController}, which refuses those moderation has
 * removed, so the public address is this service's own.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "file-storage.type", havingValue = "local", matchIfMissing = true)
public class LocalMediaStorage implements MediaStorage {

    private final Path root;
    private final String publicBaseUrl;

    public LocalMediaStorage(
            @Value("${file-storage.local.base-path:./uploads}") String basePath,
            @Value("${catalog.media.public-base-url:http://localhost:8085}") String publicBaseUrl) {
        this.root = Path.of(basePath).toAbsolutePath().normalize();
        this.publicBaseUrl = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
    }

    @Override
    public Mono<Stored> store(byte[] bytes, String contentType, String scope, String fileName) {
        return Mono.fromCallable(() -> {
            String key = MediaKeys.keyFor(scope, fileName);
            Path target = resolve(key);
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
            return new Stored(key, bytes.length, MediaKeys.sha256(bytes));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> delete(String fileKey) {
        return Mono.fromCallable(() -> {
            Files.deleteIfExists(resolve(fileKey));
            return true;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    public Mono<byte[]> read(String fileKey) {
        return Mono.fromCallable(() -> {
            Path file = resolve(fileKey);
            return Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public String publicUrl(String fileKey) {
        return publicBaseUrl + "/media/files/" + fileKey;
    }

    /** The file for {@code key}, which must stay inside the root; a key that escapes it is refused. */
    private Path resolve(String key) throws IOException {
        Path file = root.resolve(key).normalize();
        if (!file.startsWith(root)) {
            throw new IOException("a media key must stay inside the store");
        }
        return file;
    }
}
