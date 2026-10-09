package com.pml.identity.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * At the presigned-URL stage the client has declared a file but not uploaded it. The controller used
 * to hand the validator an empty byte array, so the magic-number check rejected every file with
 * "File too small or corrupted" and no document could ever be uploaded.
 */
@Tag("L1")
@Tag("ET-ORG-001")
@DisplayName("Declared file metadata is validated without file bytes")
class FileUploadValidatorMetadataTest {

    private final FileUploadValidator validator = new FileUploadValidator();

    @Test
    @DisplayName("a PNG declared before upload is accepted")
    void declaredPngAccepted() {
        assertThat(validator.validateDeclaredMetadata("national-id.png", "image/png", 180).isValid()).isTrue();
    }

    @Test
    @DisplayName("an executable, an oversized file and an empty file are still refused")
    void badDeclarationsRefused() {
        assertThat(validator.validateDeclaredMetadata("run.exe", "application/x-msdownload", 100).isValid()).isFalse();
        assertThat(validator.validateDeclaredMetadata("a.png", "image/png", 11L * 1024 * 1024).isValid()).isFalse();
        assertThat(validator.validateDeclaredMetadata("a.png", "image/png", 0).isValid()).isFalse();
    }

    @Test
    @DisplayName("the byte-level check still rejects a file with no content")
    void byteCheckStillStrict() {
        assertThat(validator.validateRawFile("a.png", "image/png", 180, new byte[0]).isValid()).isFalse();
    }
}
