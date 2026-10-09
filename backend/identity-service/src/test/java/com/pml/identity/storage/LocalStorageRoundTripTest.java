package com.pml.identity.storage;

import com.pml.identity.service.storage.LocalFileStorageService;
import com.pml.identity.service.storage.LocalUploadSigner;
import com.pml.identity.web.rest.LocalStorageController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/** The URL the local store hands out accepts the browser's PUT, refuses tampering, and serves the file back. */
@Tag("L1")
@Tag("ET-ORG-001")
@DisplayName("Local document store: signed URL round trip")
class LocalStorageRoundTripTest {

    @TempDir
    Path dir;

    @Test
    void putThenGetThroughTheSignedUrl() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-06T10:00:00Z"), ZoneOffset.UTC);
        LocalUploadSigner signer = new LocalUploadSigner("test-key", clock);
        LocalFileStorageService storage = new LocalFileStorageService(signer);
        ReflectionTestUtils.setField(storage, "serverPort", "8083");
        ReflectionTestUtils.setField(storage, "publicBaseUrl", "");
        String key = "organizations/o1/verification-documents/national_id/u-id.png";
        String url = storage.generatePresignedUrl(key, 15).block();
        URI uri = URI.create(url);
        assertThat(uri.getPath()).isEqualTo("/local-storage/" + key);

        WebTestClient client = WebTestClient.bindToController(
                new LocalStorageController(signer, dir.toString())).build();
        String pathAndQuery = uri.getRawPath() + "?" + uri.getRawQuery();

        client.put().uri(pathAndQuery).bodyValue(new byte[]{1, 2, 3, 4}).exchange().expectStatus().isOk();
        client.get().uri(uri.getRawPath()).exchange().expectStatus().isOk()
                .expectBody(byte[].class).isEqualTo(new byte[]{1, 2, 3, 4});
        client.put().uri(uri.getRawPath() + "?exp=" + uri.getRawQuery().split("&")[0].substring(4) + "&sig=bad")
                .bodyValue(new byte[]{9}).exchange().expectStatus().isForbidden();
        client.put().uri("/local-storage/../escape.txt?exp=1&sig=x").bodyValue(new byte[]{9}).exchange()
                .expectStatus().is4xxClientError();
    }
}
