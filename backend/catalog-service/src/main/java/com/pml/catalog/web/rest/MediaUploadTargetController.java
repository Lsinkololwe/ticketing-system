package com.pml.catalog.web.rest;

import com.pml.catalog.service.MediaRules;
import com.pml.catalog.service.MediaUploadTokens;
import com.pml.catalog.storage.MediaStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Where a picture is put when the store is the local disk (development). The object store grants a
 * signed address of its own; this is that address for the disk store, and it checks the same things:
 * a genuine, unexpired token, the token's type, and exactly the size the token was issued for.
 *
 * <p>The token is the credential, so the path is public in {@code platform.security.public-paths}; it
 * exists only while {@code file-storage.type=local}.
 */
@RestController
@RequestMapping("/api/v1/media/upload-target")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "file-storage.type", havingValue = "local", matchIfMissing = true)
public class MediaUploadTargetController {

    private final MediaUploadTokens tokens;
    private final MediaStorage storage;

    @PutMapping
    public Mono<ResponseEntity<Void>> put(@RequestParam("token") String token, ServerHttpRequest request) {
        var claims = tokens.verify(token).orElse(null);
        if (claims == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
        }
        MediaType type = request.getHeaders().getContentType();
        String declared = type == null ? "" : type.getType() + "/" + type.getSubtype();
        long length = request.getHeaders().getContentLength();
        if (!declared.equalsIgnoreCase(claims.contentType())) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build());
        }
        if (length != claims.sizeBytes() || length > MediaRules.MAX_UPLOAD_BYTES) {
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
        }
        return DataBufferUtils.join(request.getBody(), MediaRules.MAX_UPLOAD_BYTES + 1)
                .map(buffer -> read(buffer))
                .flatMap(bytes -> bytes.length == claims.sizeBytes()
                        ? storage.putAt(claims.fileKey(), bytes).thenReturn(ResponseEntity.ok().<Void>build())
                        : Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).<Void>build()))
                .onErrorReturn(DataBufferLimitException.class, ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build())
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
    }

    private static byte[] read(DataBuffer buffer) {
        try {
            byte[] bytes = new byte[buffer.readableByteCount()];
            buffer.read(bytes);
            return bytes;
        } finally {
            DataBufferUtils.release(buffer);
        }
    }
}
