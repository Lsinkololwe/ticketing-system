package com.pml.catalog.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

/**
 * S3, chosen by {@code file-storage.type=s3}. The bucket stays private: pictures are served by
 * {@code MediaFileController}, with a CDN in front of {@code catalog.media.public-base-url}, so an
 * image moderation removes stops being served at the origin at once.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "file-storage.type", havingValue = "s3")
public class S3MediaStorage implements MediaStorage {

    private final S3AsyncClient s3;
    private final String bucket;
    private final String publicBaseUrl;

    public S3MediaStorage(S3AsyncClient s3,
                          @Value("${aws.s3.bucket.media}") String bucket,
                          @Value("${catalog.media.public-base-url:http://localhost:8085}") String publicBaseUrl) {
        this.s3 = s3;
        this.bucket = bucket;
        this.publicBaseUrl = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
    }

    @Override
    public Mono<Stored> store(byte[] bytes, String contentType, String scope, String fileName) {
        String key = MediaKeys.keyFor(scope, fileName);
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket).key(key).contentType(contentType).contentLength((long) bytes.length)
                .serverSideEncryption(ServerSideEncryption.AES256)
                .cacheControl("public, max-age=31536000, immutable")
                .build();
        return Mono.fromFuture(() -> s3.putObject(request, AsyncRequestBody.fromBytes(bytes)))
                .thenReturn(new Stored(key, bytes.length, MediaKeys.sha256(bytes)));
    }

    @Override
    public Mono<Void> delete(String fileKey) {
        return Mono.fromFuture(() -> s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(fileKey).build()))
                .then();
    }

    @Override
    public Mono<byte[]> read(String fileKey) {
        return Mono.fromFuture(() -> s3.getObject(GetObjectRequest.builder().bucket(bucket).key(fileKey).build(),
                        AsyncResponseTransformer.toBytes()))
                .map(response -> response.asByteArray())
                .onErrorResume(NoSuchKeyException.class, e -> Mono.empty());
    }

    @Override
    public String publicUrl(String fileKey) {
        return publicBaseUrl + "/media/files/" + fileKey;
    }

    @Configuration
    @ConditionalOnProperty(name = "file-storage.type", havingValue = "s3")
    static class Clients {

        @Bean(destroyMethod = "close")
        S3AsyncClient s3AsyncClient(@Value("${aws.s3.region:us-east-1}") String region) {
            return S3AsyncClient.builder().region(Region.of(region)).build();
        }
    }
}
