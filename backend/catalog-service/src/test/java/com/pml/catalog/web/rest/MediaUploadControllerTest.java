package com.pml.catalog.web.rest;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.catalog.config.CatalogIndexInitializer;
import com.pml.catalog.domain.enums.ReferenceType;
import com.pml.catalog.domain.model.MediaAsset;
import com.pml.catalog.domain.model.ReferenceData;
import com.pml.catalog.infrastructure.client.IdentityServiceClient;
import com.pml.catalog.persistence.CatalogCollections;
import com.pml.catalog.security.MediaAuthority;
import com.pml.catalog.service.MediaRules;
import com.pml.catalog.service.MediaRulesTest;
import com.pml.catalog.service.MediaService;
import com.pml.catalog.storage.LocalMediaStorage;
import com.pml.catalog.testing.CatalogWiring;
import com.pml.shared.dto.authorization.AuthorizationRequest;
import com.pml.shared.dto.authorization.AuthorizationResult;
import com.pml.shared.error.PlatformProblemDetailAdvice;
import com.pml.shared.persistence.IndexEnsurer;
import com.pml.shared.security.InternalServiceWebClients;
import com.pml.shared.testing.MongoReplicaSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Multipart uploads: the same checks as the GraphQL path, the organization from identity, and a bounded read. */
@Tag("L2")
@Tag("ET-CAT-004")
@DisplayName("ET-CAT-004-R8/R10 · a multipart upload is checked by its bytes, filed under the caller's organization, and bounded")
class MediaUploadControllerTest {

    private static final String ORG = "65a1b2c3d4e5f60718293a4b";

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static MediaService service;
    private static WebTestClient web;
    private static Stub identity;
    private String currentOrg;
    private static org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory redisFactory;
    private static com.pml.catalog.service.UploadLimiter limiter;

    @TempDir
    static Path files;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = CatalogWiring.platformTemplate(client, "catalog_media_upload");
        template.getMongoDatabase().flatMap(db -> Mono.from(db.drop())).block();
        new com.pml.catalog.config.MongoSchemaValidationConfig(template,
                new org.springframework.core.io.DefaultResourceLoader(),
                new com.pml.shared.config.MongoSchemaValidationProperties()).run(null);
        assertThat(new IndexEnsurer(template).ensure(CatalogIndexInitializer.specifications()).block().isClean()).isTrue();
        Clock clock = Clock.fixed(Instant.parse("2026-10-01T10:00:00Z"), ZoneOffset.UTC);
        service = new MediaService(template, new LocalMediaStorage(files.toString(), "http://cdn.test"), clock,
                CatalogWiring.categories(template), 500);
        identity = new Stub();
        redisFactory = new org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory(
                com.pml.shared.testing.RedisNode.host(), com.pml.shared.testing.RedisNode.port());
        redisFactory.afterPropertiesSet();
        limiter = new com.pml.catalog.service.UploadLimiter(
                new org.springframework.data.redis.core.ReactiveStringRedisTemplate(redisFactory), 3, java.time.Duration.ofHours(1));
        web = WebTestClient.bindToController(new MediaUploadController(service, new MediaAuthority(identity), limiter))
                .controllerAdvice(new PlatformProblemDetailAdvice(List.of()))
                .apply(SecurityMockServerConfigurers.springSecurity())
                .configureClient()
                .build()
                .mutateWith(SecurityMockServerConfigurers.mockJwt().jwt(jwt -> jwt.subject("user-a")));
    }

    @AfterAll
    static void disconnect() {
        client.close();
        redisFactory.destroy();
    }

    @BeforeEach
    void clean() {
        template.remove(new Query(), CatalogCollections.MEDIA).block();
        template.remove(new Query(), CatalogCollections.REFERENCE_DATA).block();
        template.insert(ReferenceData.builder().type(ReferenceType.EVENT_CATEGORY).code("MUSIC").name("Music")
                .isActive(true).isSystem(true).metadata(new HashMap<>()).build()).block();
        identity.authorized = true;
        // A fresh organization per test: the upload counter lives in a Redis the suite shares.
        identity.organization = ORG;
        currentOrg = new org.bson.types.ObjectId().toHexString();
        identity.organization = currentOrg;
    }

    private static MultipartBodyBuilder form(byte[] bytes, String filename, String contentType) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", bytes).filename(filename).contentType(MediaType.parseMediaType(contentType));
        return builder;
    }

    @Test
    @DisplayName("stores the picture under the organization identity names, and answers with its address")
    void upload() {
        MultipartBodyBuilder body = form(MediaRulesTest.PNG, "poster.png", "image/png");
        body.part("title", "Poster");
        body.part("altText", "A poster");

        web.post().uri("/api/v1/media").contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(body.build()))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isNotEmpty()
                .jsonPath("$.url").value(url -> assertThat((String) url).startsWith("http://cdn.test/media/files/media/" + currentOrg + "/"))
                .jsonPath("$.contentType").isEqualTo("image/png")
                .jsonPath("$.sizeBytes").isEqualTo(MediaRulesTest.PNG.length)
                .jsonPath("$.status").isEqualTo("ACTIVE");

        MediaAsset stored = template.findAll(MediaAsset.class).blockFirst();
        assertThat(stored.getOrganizationId()).isEqualTo(currentOrg);
        assertThat(stored.getUploadedBy()).isEqualTo("user-a");
        assertThat(stored.getTitle()).isEqualTo("Poster");
        assertThat(identity.lastRequest.getUserId()).isEqualTo("user-a");
    }

    @Test
    @DisplayName("a script called poster.png is refused, as a problem document naming the field, and stores nothing")
    void notAnImage() {
        web.post().uri("/api/v1/media").contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(form("<script>1</script>".getBytes(), "poster.png", "image/png").build()))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("COMMAND_NOT_WELL_FORMED");

        assertThat(template.count(new Query(), CatalogCollections.MEDIA).block()).isZero();
    }

    @Test
    @DisplayName("a body over 5 MB stops being read and is refused")
    void tooLarge() {
        byte[] big = new byte[MediaRules.MAX_BYTES + 10];
        System.arraycopy(MediaRulesTest.PNG, 0, big, 0, MediaRulesTest.PNG.length);

        web.post().uri("/api/v1/media").contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(form(big, "big.png", "image/png").build()))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("COMMAND_NOT_WELL_FORMED");

        assertThat(template.count(new Query(), CatalogCollections.MEDIA).block()).isZero();
    }

    @Test
    @DisplayName("an organization that uploads faster than the hourly limit is rationed, and others are not")
    void rationed() {
        for (int i = 0; i < 3; i++) {
            web.post().uri("/api/v1/media").contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(form(MediaRulesTest.PNG, "p" + i + ".png", "image/png").build()))
                    .exchange().expectStatus().isCreated();
        }

        web.post().uri("/api/v1/media").contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(form(MediaRulesTest.PNG, "p4.png", "image/png").build()))
                .exchange()
                .expectStatus().value(status -> assertThat(status).isIn(429, 503))
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("RATE_LIMIT_EXCEEDED")
                .jsonPath("$.retryAfterSeconds").isNumber();

        assertThat(template.count(new Query(), CatalogCollections.MEDIA).block()).isEqualTo(3);
        identity.organization = new org.bson.types.ObjectId().toHexString();
        web.post().uri("/api/v1/media").contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(form(MediaRulesTest.PNG, "other.png", "image/png").build()))
                .exchange().expectStatus().isCreated();
    }

    @Test
    @DisplayName("a member who may not create events may not add pictures")
    void notPermitted() {
        identity.authorized = false;

        web.post().uri("/api/v1/media").contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(form(MediaRulesTest.PNG, "poster.png", "image/png").build()))
                .exchange()
                .expectStatus().value(status -> assertThat(status).isIn(401, 403, 500));

        assertThat(template.count(new Query(), CatalogCollections.MEDIA).block()).isZero();
    }

    @Test
    @DisplayName("an administrator uploads a stock category tile")
    void stockTile() {
        MultipartBodyBuilder body = form(MediaRulesTest.PNG, "music.png", "image/png");
        body.part("purpose", "CATEGORY_TILE");
        body.part("categoryCode", "MUSIC");

        web.post().uri("/api/v1/media/stock").contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(body.build()))
                .exchange()
                .expectStatus().isCreated();

        assertThat(service.categoryTileUrl("MUSIC").block()).startsWith("http://cdn.test/media/files/media/stock/");
    }

    @Test
    @DisplayName("a stock upload with a purpose that is not one is refused naming the field")
    void stockPurpose() {
        MultipartBodyBuilder body = form(MediaRulesTest.PNG, "music.png", "image/png");
        body.part("purpose", "WALLPAPER");

        web.post().uri("/api/v1/media/stock").contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(body.build()))
                .exchange()
                .expectStatus().isBadRequest();
    }

    /** identity-service, answering for ORG. */
    static final class Stub extends IdentityServiceClient {
        boolean authorized = true;
        String organization = ORG;
        AuthorizationRequest lastRequest;

        Stub() {
            super(InternalServiceWebClients.unauthenticated(WebClient.builder()), "http://identity.invalid");
        }

        @Override
        public Mono<UserOrganizationsResponse> getUserOrganizations(String userId) {
            return Mono.just(new UserOrganizationsResponse(java.util.List.of(
                    new OrganizationMembershipInfo(organization, "OWNER", true))));
        }

        @Override
        public Mono<AuthorizationResult> checkAuthorization(AuthorizationRequest request) {
            lastRequest = request;
            return Mono.just(authorized
                    ? AuthorizationResult.authorizedAsMember(organization, "OWNER")
                    : AuthorizationResult.denied("requires event:create"));
        }
    }
}
