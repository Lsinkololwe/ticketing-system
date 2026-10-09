package com.pml.identity.web.rest;

import com.pml.identity.service.storage.LocalUploadSigner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Receives and serves documents for the local file store. Development only: with
 * {@code file-storage.type=s3} this controller does not exist and the browser talks to S3.
 *
 * <p>Writes require the signature minted with the upload URL (key, expiry, HMAC). Reads accept the
 * same signature; a read without one is allowed because the key holds an unguessable UUID and the
 * stored document URL has no query string. This is the stand-in for a bucket policy and must not
 * be used outside development.</p>
 */
@Slf4j
@RestController
@ConditionalOnProperty(name = "file-storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageController {

    private static final long MAX_BYTES = 10L * 1024 * 1024;

    private final LocalUploadSigner signer;
    private final Path root;

    public LocalStorageController(LocalUploadSigner signer,
                                  @Value("${file-storage.local.base-path:./uploads}") String basePath) {
        this.signer = signer;
        this.root = Paths.get(basePath).toAbsolutePath().normalize();
    }

    @PutMapping("/local-storage/{*key}")
    public Mono<ResponseEntity<Void>> put(@PathVariable("key") String rawKey,
                                          @RequestParam(name = "exp", required = false, defaultValue = "0") long exp,
                                          @RequestParam(name = "sig", required = false) String sig,
                                          ServerHttpRequest request) {
        String key = rawKey.startsWith("/") ? rawKey.substring(1) : rawKey;
        Path target = resolve(key);
        if (target == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
        }
        if (!signer.isValid(key, exp, sig)) {
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
        }
        long declared = request.getHeaders().getContentLength();
        if (declared > MAX_BYTES) {
            return Mono.just(ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build());
        }
        return Mono.fromCallable(() -> {
                    Files.createDirectories(target.getParent());
                    return target;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(path -> DataBufferUtils.write(request.getBody(), path).thenReturn(path))
                .map(path -> ResponseEntity.ok().<Void>build())
                .doOnSuccess(r -> log.info("Stored local document {}", key));
    }

    @GetMapping("/local-storage/{*key}")
    public Mono<ResponseEntity<Resource>> get(@PathVariable("key") String rawKey) {
        String key = rawKey.startsWith("/") ? rawKey.substring(1) : rawKey;
        Path target = resolve(key);
        if (target == null || !Files.isRegularFile(target)) {
            return Mono.just(ResponseEntity.notFound().build());
        }
        MediaType type = MediaType.APPLICATION_OCTET_STREAM;
        try {
            String probed = Files.probeContentType(target);
            if (probed != null) {
                type = MediaType.parseMediaType(probed);
            }
        } catch (IOException ignored) {
            // fall back to octet-stream
        }
        return Mono.just(ResponseEntity.ok().contentType(type).body(new FileSystemResource(target)));
    }

    /** The file under the store root, or null when the key tries to leave it. */
    private Path resolve(String key) {
        if (key.isBlank() || key.contains("..") || key.contains("\\")) {
            return null;
        }
        Path path = root.resolve(key).normalize();
        return path.startsWith(root) ? path : null;
    }
}
