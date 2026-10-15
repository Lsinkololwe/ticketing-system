package com.pml.catalog.service;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.catalog.config.CatalogIndexInitializer;
import com.pml.catalog.domain.enums.MediaKind;
import com.pml.catalog.domain.enums.MediaStatus;
import com.pml.catalog.domain.enums.StockImagePurpose;
import com.pml.catalog.domain.model.MediaAsset;
import com.pml.catalog.persistence.CatalogCollections;
import com.pml.catalog.storage.S3MediaStorage;
import com.pml.catalog.testing.CatalogWiring;
import com.pml.catalog.testing.ObjectStoreNode;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.ValidationRefusal;
import com.pml.shared.persistence.IndexEnsurer;
import com.pml.shared.security.tenancy.CurrentTenantScope;
import com.pml.shared.security.tenancy.TenantScope;
import com.pml.shared.testing.MongoReplicaSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The two-step upload on a real MongoDB and a real S3-compatible object store: the grant is a real
 * presigned address, the file goes to it with a plain HTTP client, and nothing is served until the
 * service has read the bytes and found an image.
 *
 * <p>OWASP A01 (a token is for its holder alone), A03 (the bytes decide, not the name or the declared
 * type), A04 (size and type are bound into the grant), A05 (a bucket that stays private), A08 (a token
 * that is altered or expired is nothing) and A09 (a refusal leaves no file behind).
 */
@Tag("L2")
@Tag("ET-CAT-004")
@Tag("ET-PLT-007")
@DisplayName("ET-CAT-004-T8 · a picture over 5 MB goes straight to the private bucket and is registered only after its bytes prove to be an image")
class MediaLargeUploadTest {

    private static final Instant T0 = Instant.parse("2026-10-15T10:00:00Z");
    private static final String ORG_A = "65a1b2c3d4e5f60718293a4b";
    private static final String ORG_B = "65a1b2c3d4e5f60718293a4c";
    private static final String BUCKET = "ticketing-media-large";
    private static final TenantScope SCOPE_A = TenantScope.of("user-a", Set.of(ORG_A));
    private static final int SIX_MB = 6 * 1024 * 1024;

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static S3AsyncClient s3;
    private static S3Presigner presigner;
    private static S3MediaStorage storage;
    private static MediaService service;
    private static MediaUploadTokens tokens;
    private static MutableClock clock;
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @BeforeAll
    static void start() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = CatalogWiring.platformTemplate(client, "catalog_media_large");
        template.getMongoDatabase().flatMap(db -> Mono.from(db.drop())).block();
        new com.pml.catalog.config.MongoSchemaValidationConfig(template,
                new org.springframework.core.io.DefaultResourceLoader(),
                new com.pml.shared.config.MongoSchemaValidationProperties()).run(null);
        assertThat(new IndexEnsurer(template).ensure(CatalogIndexInitializer.specifications()).block().isClean()).isTrue();

        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(ObjectStoreNode.ACCESS_KEY, ObjectStoreNode.SECRET_KEY));
        s3 = S3AsyncClient.builder().region(Region.US_EAST_1).credentialsProvider(credentials)
                .endpointOverride(URI.create(ObjectStoreNode.endpoint())).forcePathStyle(true).build();
        presigner = S3Presigner.builder().region(Region.US_EAST_1).credentialsProvider(credentials)
                .endpointOverride(URI.create(ObjectStoreNode.endpoint()))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build()).build();
        s3.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build()).join();

        clock = new MutableClock(T0);
        storage = new S3MediaStorage(s3, presigner, BUCKET, "http://cdn.test");
        tokens = new MediaUploadTokens("large-upload-signing-key-0123456789abcdef", "s3", clock);
        service = new MediaService(template, storage, clock, CatalogWiring.categories(template), 3, tokens);
    }

    @AfterAll
    static void stop() {
        presigner.close();
        s3.close();
        client.close();
    }

    @BeforeEach
    void clean() {
        template.remove(new Query(), CatalogCollections.MEDIA).block();
        clock.set(T0);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** A PNG signature followed by filler: enough for the bytes to be recognised, large enough to matter. */
    private static byte[] png(int size) {
        byte[] bytes = new byte[size];
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'};
        System.arraycopy(signature, 0, bytes, 0, signature.length);
        return bytes;
    }

    private static byte[] script(int size) {
        byte[] bytes = new byte[size];
        Arrays.fill(bytes, (byte) 'x');
        byte[] head = "<script>alert(1)</script>".getBytes();
        System.arraycopy(head, 0, bytes, 0, head.length);
        return bytes;
    }

    private static <T> T as(TenantScope scope, Mono<T> call) {
        return call.contextWrite(ctx -> CurrentTenantScope.seed(ctx, Mono.just(scope))).block();
    }

    private static MediaService.UploadTicket ticket(String user, String org, String name, String type, long size) {
        return service.requestUpload(name, type, size, user, org).block();
    }

    private static int put(MediaService.UploadTicket ticket, byte[] body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(ticket.uploadUrl()))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(body));
        ticket.headers().forEach((name, value) -> {
            // The client sets these itself from the body.
            if (!"content-length".equalsIgnoreCase(name) && !"connection".equalsIgnoreCase(name)) {
                request.header(name, value);
            }
        });
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private static boolean objectExists(String key) {
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(BUCKET).key(key).build()).join();
            return true;
        } catch (CompletionException e) {
            if (e.getCause() instanceof NoSuchKeyException
                    || (e.getCause() instanceof software.amazon.awssdk.services.s3.model.S3Exception s && s.statusCode() == 404)) {
                return false;
            }
            throw e;
        }
    }

    private static String keyOf(MediaService.UploadTicket ticket) {
        return tokens.verify(ticket.uploadToken()).orElseThrow().fileKey();
    }

    private static DomainRefusal refused(Runnable call) {
        try {
            call.run();
        } catch (DomainRefusal refusal) {
            return refusal;
        } catch (CompletionException e) {
            if (e.getCause() instanceof DomainRefusal refusal) {
                return refusal;
            }
            throw e;
        }
        throw new AssertionError("expected a refusal");
    }

    // ── the happy path ───────────────────────────────────────────────────────

    @Test
    @DisplayName("a 6 MB picture is put to the presigned address and registered; the asset row, not the bucket, is what serves it")
    void sixMegabytesGoThrough() throws Exception {
        MediaService.UploadTicket ticket = ticket("user-a", ORG_A, "poster.png", "image/png", SIX_MB);

        assertThat(ticket.method()).isEqualTo("PUT");
        assertThat(ticket.maxBytes()).isEqualTo(MediaRules.MAX_UPLOAD_BYTES);
        assertThat(ticket.uploadUrl()).contains(BUCKET).contains("X-Amz-Signature");
        assertThat(template.count(new Query(), MediaAsset.class).block()).as("nothing is registered yet").isZero();

        assertThat(put(ticket, png(SIX_MB))).isBetween(200, 299);
        MediaAsset asset = as(SCOPE_A, service.completeOrganizationUpload(ticket.uploadToken(), "Poster", "A poster",
                null, "user-a", ORG_A));

        assertThat(asset.getKind()).isEqualTo(MediaKind.ORGANIZER_UPLOAD);
        assertThat(asset.getStatus()).isEqualTo(MediaStatus.ACTIVE);
        assertThat(asset.getOrganizationId()).isEqualTo(ORG_A);
        assertThat(asset.getSizeBytes()).isEqualTo(SIX_MB);
        assertThat(asset.getContentType()).isEqualTo("image/png");
        assertThat(asset.getFileKey()).isEqualTo(keyOf(ticket));
        assertThat(service.bytes(asset).block()).hasSize(SIX_MB);
        assertThat(service.urlOf(asset)).startsWith("http://cdn.test/media/files/media/" + ORG_A + "/");
    }

    @Test
    @DisplayName("completing the same upload twice answers with the asset the first completion made")
    void completionIsIdempotent() throws Exception {
        MediaService.UploadTicket ticket = ticket("user-a", ORG_A, "twice.png", "image/png", SIX_MB);
        put(ticket, png(SIX_MB));

        MediaAsset first = as(SCOPE_A, service.completeOrganizationUpload(ticket.uploadToken(), null, null, null, "user-a", ORG_A));
        MediaAsset second = as(SCOPE_A, service.completeOrganizationUpload(ticket.uploadToken(), null, null, null, "user-a", ORG_A));

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(template.count(new Query(), MediaAsset.class).block()).isEqualTo(1);
    }

    // ── what the bytes say ───────────────────────────────────────────────────

    @Test
    @DisplayName("A03 · a script declared as a PNG is refused at completion and removed from the bucket")
    void scriptIsRefusedAndRemoved() throws Exception {
        MediaService.UploadTicket ticket = ticket("user-a", ORG_A, "evil.png", "image/png", SIX_MB);
        put(ticket, script(SIX_MB));
        String key = keyOf(ticket);
        assertThat(objectExists(key)).isTrue();

        DomainRefusal refusal = refused(() -> as(SCOPE_A, service.completeOrganizationUpload(ticket.uploadToken(),
                null, null, null, "user-a", ORG_A)));

        assertThat(refusal).isInstanceOf(ValidationRefusal.class);
        assertThat(objectExists(key)).as("a refused file leaves nothing behind").isFalse();
        assertThat(template.count(new Query(), MediaAsset.class).block()).isZero();
    }

    @Test
    @DisplayName("A04 · a body of another size than was granted is refused, by the bucket or at completion, and never registered")
    void wrongSizeIsNeverRegistered() throws Exception {
        MediaService.UploadTicket ticket = ticket("user-a", ORG_A, "short.png", "image/png", SIX_MB);
        int status = put(ticket, png(SIX_MB - 1024));
        String key = keyOf(ticket);

        if (status >= 200 && status < 300) {
            // A store that does not enforce the signed length: the registration still compares the size.
            assertThat(refused(() -> as(SCOPE_A, service.completeOrganizationUpload(ticket.uploadToken(),
                    null, null, null, "user-a", ORG_A)))).isInstanceOf(ValidationRefusal.class);
            assertThat(objectExists(key)).isFalse();
        } else {
            assertThat(objectExists(key)).as("the bucket took nothing").isFalse();
        }
        assertThat(template.count(new Query(), MediaAsset.class).block()).isZero();
    }

    @Test
    @DisplayName("completing before any file was sent is a refusal that names the token, not a failure")
    void nothingUploaded() {
        MediaService.UploadTicket ticket = ticket("user-a", ORG_A, "ghost.png", "image/png", SIX_MB);

        DomainRefusal refusal = refused(() -> as(SCOPE_A, service.completeOrganizationUpload(ticket.uploadToken(),
                null, null, null, "user-a", ORG_A)));

        assertThat(((ValidationRefusal) refusal).violations()).extracting(v -> v.path())
                .containsExactly("input.uploadToken");
    }

    // ── what is asked for ────────────────────────────────────────────────────

    @Test
    @DisplayName("A04 · the declaration is bounded: over 20 MB, empty, a non-image type and a blank name are refused before a grant")
    void declarationIsBounded() {
        for (Object[] bad : new Object[][]{
                {"big.png", "image/png", (long) MediaRules.MAX_UPLOAD_BYTES + 1},
                {"zero.png", "image/png", 0L},
                {"a.svg", "image/svg+xml", 1024L},
                {"a.pdf", "application/pdf", 1024L},
                {" ", "image/png", 1024L}}) {
            DomainRefusal refusal = refused(() -> service.requestUpload((String) bad[0], (String) bad[1],
                    (Long) bad[2], "user-a", ORG_A).block());
            assertThat(refusal).as("%s %s %s", bad).isInstanceOf(ValidationRefusal.class);
        }
        assertThat(template.count(new Query(), MediaAsset.class).block()).isZero();
    }

    @Test
    @DisplayName("the library cap is checked at the request, so a full library is not offered a grant")
    void fullLibraryGetsNoGrant() throws Exception {
        for (int i = 0; i < 3; i++) {
            MediaService.UploadTicket t = ticket("user-a", ORG_A, "p" + i + ".png", "image/png", 2048);
            put(t, png(2048));
            as(SCOPE_A, service.completeOrganizationUpload(t.uploadToken(), null, null, null, "user-a", ORG_A));
        }

        assertThat(refused(() -> service.requestUpload("p4.png", "image/png", 2048, "user-a", ORG_A).block()))
                .isInstanceOf(ValidationRefusal.class);
    }

    // ── whose token it is ────────────────────────────────────────────────────

    @Test
    @DisplayName("A01 · another organization's member, or another user of the same organization, gets MEDIA_UNKNOWN")
    void tokenIsForItsHolder() throws Exception {
        MediaService.UploadTicket ticket = ticket("user-a", ORG_A, "mine.png", "image/png", SIX_MB);
        put(ticket, png(SIX_MB));

        DomainRefusal otherOrg = refused(() -> as(TenantScope.of("user-b", Set.of(ORG_B)),
                service.completeOrganizationUpload(ticket.uploadToken(), null, null, null, "user-b", ORG_B)));
        DomainRefusal otherUser = refused(() -> as(SCOPE_A,
                service.completeOrganizationUpload(ticket.uploadToken(), null, null, null, "user-a2", ORG_A)));

        assertThat(otherOrg.errorCode()).isEqualTo(ErrorCode.MEDIA_UNKNOWN);
        assertThat(otherUser.errorCode()).isEqualTo(ErrorCode.MEDIA_UNKNOWN);
        assertThat(template.count(new Query(), MediaAsset.class).block()).isZero();
    }

    @Test
    @DisplayName("A08 · an altered, truncated, foreign or expired token is the same refusal and registers nothing")
    void badTokensAreAllTheSame() throws Exception {
        MediaService.UploadTicket ticket = ticket("user-a", ORG_A, "tok.png", "image/png", SIX_MB);
        put(ticket, png(SIX_MB));
        String token = ticket.uploadToken();
        String altered = token.substring(0, token.length() - 3) + (token.endsWith("AAA") ? "BBB" : "AAA");
        String bodyOnly = token.substring(0, token.indexOf('.'));
        String forged = new MediaUploadTokens("another-signing-key-0123456789abcdef", "s3", clock)
                .issue(keyOf(ticket), "org:" + ORG_A, "user-a", "tok.png", "image/png", SIX_MB, Duration.ofMinutes(5));

        for (String bad : new String[]{altered, bodyOnly, forged, "", "not-a-token", "a.b"}) {
            DomainRefusal refusal = refused(() -> as(SCOPE_A,
                    service.completeOrganizationUpload(bad, null, null, null, "user-a", ORG_A)));
            assertThat(((ValidationRefusal) refusal).violations()).extracting(v -> v.path())
                    .containsExactly("input.uploadToken");
        }

        clock.advance(MediaService.UPLOAD_GRANT_VALID_FOR.plusSeconds(1));
        assertThat(refused(() -> as(SCOPE_A,
                service.completeOrganizationUpload(token, null, null, null, "user-a", ORG_A))))
                .isInstanceOf(ValidationRefusal.class);
        assertThat(template.count(new Query(), MediaAsset.class).block()).isZero();
    }

    // ── stock images ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("an administrator's stock image goes through the same two steps, and an organization's token cannot be completed as stock")
    void stockImages() throws Exception {
        com.pml.catalog.domain.model.ReferenceData music = com.pml.catalog.domain.model.ReferenceData.builder()
                .type(com.pml.catalog.domain.enums.ReferenceType.EVENT_CATEGORY).code("MUSIC").name("Music")
                .isActive(true).isSystem(true).metadata(new java.util.HashMap<>()).build();
        template.save(music).block();

        MediaService.UploadTicket stock = service.requestUpload("cover.png", "image/png", SIX_MB, "admin-1", null).block();
        put(stock, png(SIX_MB));
        MediaAsset asset = service.completeStockUpload(stock.uploadToken(), StockImagePurpose.EVENT_COVER, null,
                "Cover", "A cover", "admin-1").block();
        assertThat(asset.getKind()).isEqualTo(MediaKind.STOCK);
        assertThat(asset.getSizeBytes()).isEqualTo(SIX_MB);

        MediaService.UploadTicket mine = ticket("user-a", ORG_A, "org.png", "image/png", SIX_MB);
        put(mine, png(SIX_MB));
        assertThat(refused(() -> service.completeStockUpload(mine.uploadToken(), StockImagePurpose.EVENT_COVER, null,
                null, null, "user-a").block()).errorCode()).isEqualTo(ErrorCode.MEDIA_UNKNOWN);
    }

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void set(Instant instant) {
            this.now = instant;
        }

        void advance(Duration by) {
            this.now = now.plus(by);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
