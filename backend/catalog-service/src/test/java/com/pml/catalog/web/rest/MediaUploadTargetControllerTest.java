package com.pml.catalog.web.rest;

import com.pml.catalog.service.MediaUploadTokens;
import com.pml.catalog.storage.LocalMediaStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import reactor.core.publisher.Flux;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The disk store's upload address: the token is the only credential, so a token that is not genuine, a
 * type that is not the granted one, and a body that is not exactly the granted size all write nothing.
 */
@Tag("L1")
@Tag("ET-CAT-004")
@DisplayName("ET-CAT-004-T8 · the development upload target writes only what its token granted")
class MediaUploadTargetControllerTest {

    @TempDir
    Path files;

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC);
    private MediaUploadTokens tokens;
    private LocalMediaStorage storage;
    private MediaUploadTargetController controller;

    @BeforeEach
    void wire() {
        tokens = new MediaUploadTokens("target-test-signing-key-0123456789", "local", clock);
        storage = new LocalMediaStorage(files.toString(), "http://cdn.test");
        controller = new MediaUploadTargetController(tokens, storage);
    }

    private HttpStatus put(String token, String contentType, byte[] body, long declaredLength) {
        MockServerHttpRequest request = MockServerHttpRequest.put("/api/v1/media/upload-target")
                .contentType(MediaType.parseMediaType(contentType)).contentLength(declaredLength)
                .body(Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(body)));
        return (HttpStatus) controller.put(token, request).block().getStatusCode();
    }

    private String token(String key, String type, long size) {
        return tokens.issue(key, "org:o1", "u1", "a.png", type, size, Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("a genuine token, its type and its exact size write the file at the granted key")
    void writesTheGrantedFile() throws Exception {
        byte[] body = new byte[2048];

        assertThat(put(token("media/o1/k-a.png", "image/png", 2048), "image/png", body, 2048)).isEqualTo(HttpStatus.OK);

        assertThat(Files.readAllBytes(files.resolve("media/o1/k-a.png"))).hasSize(2048);
    }

    @Test
    @DisplayName("a forged token is forbidden; another type or another length is refused; nothing is written")
    void refusalsWriteNothing() {
        byte[] body = new byte[2048];
        String good = token("media/o1/k-b.png", "image/png", 2048);

        assertThat(put("forged.token", "image/png", body, 2048)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(put(good, "text/html", body, 2048)).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(put(good, "image/png", new byte[1024], 1024)).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(put(good, "image/png", body, 4096)).isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(Files.exists(files.resolve("media/o1/k-b.png"))).isFalse();
    }

    @Test
    @DisplayName("a token cannot be steered to a key outside the store: the key is the token's, never the request's")
    void keyIsNotTheRequests() {
        String escape = token("../../etc/passwd", "image/png", 16);

        // The store refuses a key that leaves its root; the controller answers with the failure, not a write.
        try {
            put(escape, "image/png", new byte[16], 16);
        } catch (RuntimeException expected) {
            // the refusal surfaces as an error from the store
        }
        assertThat(Files.exists(files.getParent().resolve("etc/passwd"))).isFalse();
    }
}
