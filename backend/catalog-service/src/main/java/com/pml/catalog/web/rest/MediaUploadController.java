package com.pml.catalog.web.rest;

import com.pml.catalog.domain.enums.StockImagePurpose;
import com.pml.catalog.domain.model.MediaAsset;
import com.pml.catalog.security.MediaAuthority;
import com.pml.catalog.service.MediaRules;
import com.pml.catalog.service.MediaService;
import com.pml.catalog.service.UploadLimiter;
import com.pml.shared.error.FieldViolation;
import com.pml.shared.error.ValidationRefusal;
import com.pml.shared.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Picture uploads as multipart form data, the platform's preferred way to move a file: the browser
 * shows real progress, nothing is base64-inflated, and the gateway's upload ceiling applies rather than
 * its GraphQL one. The GraphQL {@code uploadMedia} stays for small pictures; both run the same checks
 * in {@link MediaService}, the bytes deciding what is an image.
 *
 * <p>The read is bounded as it is received: a body over 5 MB stops being read at 5 MB.
 */
@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class MediaUploadController {

    private final MediaService media;
    private final MediaAuthority authority;
    private final UploadLimiter uploads;

    /** What the caller needs to use the picture. */
    public record Uploaded(String id, String url, String fileName, String contentType, long sizeBytes, String status) {
        static Uploaded of(MediaAsset asset, String url) {
            return new Uploaded(asset.getId(), url, asset.getFileName(), asset.getContentType(), asset.getSizeBytes(),
                    asset.getStatus().name());
        }
    }

    /** An organization's own picture. Fields: {@code file}, and optionally {@code title}, {@code altText}, {@code eventId}. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public Mono<ResponseEntity<Uploaded>> upload(@RequestPart("file") FilePart file,
                                                 @RequestPart(name = "title", required = false) String title,
                                                 @RequestPart(name = "altText", required = false) String altText,
                                                 @RequestPart(name = "eventId", required = false) String eventId) {
        return SecurityContextUtils.requireCurrentUserId().flatMap(userId -> authority.organizationOf(userId)
                .flatMap(organizationId -> uploads.take(organizationId)
                        .then(read(file))
                        .flatMap(bytes -> media.uploadBytes(file.filename(), contentType(file),
                                bytes, "file", title, altText, eventId, userId, organizationId))))
                .map(asset -> ResponseEntity.status(HttpStatus.CREATED).body(Uploaded.of(asset, media.urlOf(asset))));
    }

    /**
     * A stock image. Fields: {@code file}, {@code purpose} ({@code EVENT_COVER} or {@code CATEGORY_TILE}), and
     * for a tile {@code categoryCode}; optionally {@code title} and {@code altText}.
     */
    @PostMapping(path = "/stock", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<Uploaded>> uploadStock(@RequestPart("file") FilePart file,
                                                      @RequestPart("purpose") String purpose,
                                                      @RequestPart(name = "categoryCode", required = false) String categoryCode,
                                                      @RequestPart(name = "title", required = false) String title,
                                                      @RequestPart(name = "altText", required = false) String altText) {
        StockImagePurpose parsed = purposeOf(purpose);
        return SecurityContextUtils.requireCurrentUserId().flatMap(actor -> read(file)
                        .flatMap(bytes -> media.uploadStockBytes(file.filename(), contentType(file), bytes, "file", parsed,
                                categoryCode, title, altText, actor)))
                .map(asset -> ResponseEntity.status(HttpStatus.CREATED).body(Uploaded.of(asset, media.urlOf(asset))));
    }

    private static StockImagePurpose purposeOf(String purpose) {
        try {
            return purpose == null ? null : StockImagePurpose.valueOf(purpose.trim());
        } catch (IllegalArgumentException e) {
            throw new ValidationRefusal(List.of(new FieldViolation("purpose", "must be EVENT_COVER or CATEGORY_TILE")));
        }
    }

    private static String contentType(FilePart file) {
        MediaType type = file.headers().getContentType();
        return type == null ? "" : type.getType() + "/" + type.getSubtype();
    }

    /** The file's bytes, read no further than the 5 MB limit. */
    private static Mono<byte[]> read(FilePart file) {
        return DataBufferUtils.join(file.content(), MediaRules.MAX_BYTES + 1)
                .map(buffer -> {
                    try {
                        byte[] bytes = new byte[buffer.readableByteCount()];
                        buffer.read(bytes);
                        return bytes;
                    } finally {
                        DataBufferUtils.release(buffer);
                    }
                })
                .onErrorMap(DataBufferLimitException.class, e -> new ValidationRefusal(List.of(
                        new FieldViolation("file", "must be at most 5 MB"))))
                .switchIfEmpty(Mono.error(new ValidationRefusal(List.of(new FieldViolation("file", "is empty")))));
    }
}
