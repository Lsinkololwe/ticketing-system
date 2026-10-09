package com.pml.catalog.web.rest;

import com.pml.catalog.service.MediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Serves the pictures. Public by design (an event page shows them to anyone), and public only for
 * what moderation has not removed: the lookup is by the asset row, so a removed or deleted image is a
 * 404 at the origin the moment it is removed, whatever a CDN in front still holds for its short TTL.
 *
 * <p>The key is looked up, never turned into a path here; the store decides what a key means.
 */
@RestController
@RequiredArgsConstructor
public class MediaFileController {

    private final MediaService media;

    @GetMapping("/media/files/{*fileKey}")
    public Mono<ResponseEntity<byte[]>> file(@PathVariable String fileKey) {
        String key = fileKey.startsWith("/") ? fileKey.substring(1) : fileKey;
        if (!key.startsWith("media/") || key.contains("..")) {
            return Mono.just(ResponseEntity.notFound().build());
        }
        return media.servable(key)
                .flatMap(asset -> media.bytes(asset).map(bytes -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(asset.getContentType()))
                        .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                        .eTag("\"" + asset.getChecksum() + "\"")
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                        .header("X-Content-Type-Options", "nosniff")
                        .header("Content-Security-Policy", "default-src 'none'; sandbox")
                        .body(bytes)))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
