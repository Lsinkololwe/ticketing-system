package com.pml.identity.storage;

import com.pml.identity.service.storage.FileStorageService;
import com.pml.identity.service.storage.LocalFileStorageService;
import com.pml.identity.service.storage.LocalUploadSigner;
import com.pml.identity.service.storage.S3FileStorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * One key, {@code file-storage.type}, chooses where verification documents go, and exactly one
 * store is active.
 *
 * <p>S3 used to switch on a different key, {@code aws.s3.enabled}, which the configuration never
 * set; with S3 on, the local store stayed on too and the context held two stores.
 */
@Tag("L3")
@Tag("ET-ORG-001")
@DisplayName("Exactly one document store, chosen by file-storage.type")
class FileStorageSelectionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(S3AsyncClient.class, () -> mock(S3AsyncClient.class))
            .withBean(S3Presigner.class, () -> mock(S3Presigner.class))
            .withBean(Clock.class, Clock::systemUTC)
            .withPropertyValues("aws.s3.bucket.verification-documents=docs")
            .withUserConfiguration(LocalUploadSigner.class, LocalFileStorageService.class, S3FileStorageService.class);

    @Test
    @DisplayName("unset, documents stay on local disk")
    void localByDefault() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(FileStorageService.class);
            assertThat(context.getBean(FileStorageService.class)).isInstanceOf(LocalFileStorageService.class);
        });
    }

    @Test
    @DisplayName("file-storage.type=s3 puts them in S3, and only there")
    void s3WhenChosen() {
        runner.withPropertyValues("file-storage.type=s3").run(context -> {
            assertThat(context).hasSingleBean(FileStorageService.class);
            assertThat(context.getBean(FileStorageService.class)).isInstanceOf(S3FileStorageService.class);
        });
    }
}
