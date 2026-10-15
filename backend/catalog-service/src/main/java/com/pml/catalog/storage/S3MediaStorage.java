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
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

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
    private final S3Presigner presigner;
    private final String bucket;
    private final String publicBaseUrl;

    public S3MediaStorage(S3AsyncClient s3, S3Presigner presigner,
                          @Value("${aws.s3.bucket.media}") String bucket,
                          @Value("${catalog.media.public-base-url:http://localhost:8085}") String publicBaseUrl) {
        this.s3 = s3;
        this.presigner = presigner;
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
    public Mono<UploadGrant> grantUpload(String key, String contentType, long sizeBytes,
                                         java.time.Duration validFor, String token) {
        // Size and type are part of the signature: the bucket refuses a body of any other length or type.
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(bucket).key(key).contentType(contentType).contentLength(sizeBytes)
                .serverSideEncryption(ServerSideEncryption.AES256)
                .cacheControl("public, max-age=31536000, immutable")
                .build();
        return Mono.fromCallable(() -> presigner.presignPutObject(PutObjectPresignRequest.builder()
                        .signatureDuration(validFor).putObjectRequest(put).build()))
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .map(presigned -> {
                    java.util.Map<String, String> headers = new java.util.LinkedHashMap<>();
                    presigned.signedHeaders().forEach((name, values) -> {
                        if (!"host".equalsIgnoreCase(name)) {
                            headers.put(name, String.join(",", values));
                        }
                    });
                    return new UploadGrant(key, presigned.url().toString(), headers);
                });
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
        S3AsyncClient s3AsyncClient(@Value("${aws.s3.region:us-east-1}") String region,
                                    @Value("${aws.s3.endpoint:}") String endpoint,
                                    @Value("${aws.s3.access-key:}") String accessKey,
                                    @Value("${aws.s3.secret-key:}") String secretKey) {
            var builder = S3AsyncClient.builder().region(Region.of(region));
            if (!endpoint.isBlank()) {
                // An S3-compatible store: the bucket is in the path, not the host name.
                builder.endpointOverride(java.net.URI.create(endpoint)).forcePathStyle(true);
            }
            if (!accessKey.isBlank()) {
                builder.credentialsProvider(software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(
                        software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create(accessKey, secretKey)));
            }
            return builder.build();
        }

        @Bean(destroyMethod = "close")
        S3Presigner s3Presigner(@Value("${aws.s3.region:us-east-1}") String region,
                                @Value("${aws.s3.endpoint:}") String endpoint,
                                @Value("${aws.s3.access-key:}") String accessKey,
                                @Value("${aws.s3.secret-key:}") String secretKey) {
            var builder = S3Presigner.builder().region(Region.of(region));
            if (!endpoint.isBlank()) {
                builder.endpointOverride(java.net.URI.create(endpoint))
                        .serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder()
                                .pathStyleAccessEnabled(true).build());
            }
            if (!accessKey.isBlank()) {
                builder.credentialsProvider(software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(
                        software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create(accessKey, secretKey)));
            }
            return builder.build();
        }
    }
}
