package com.pml.identity.security;

import com.pml.identity.domain.valueobject.EventRole;
import com.pml.identity.domain.valueobject.OrganizationRole;
import com.pml.identity.persistence.IdentityCollections;
import com.pml.shared.security.Permission;
import com.pml.shared.security.PermissionCatalogueFingerprint;
import com.pml.shared.security.PlatformRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Publishes the compiled permission catalogue as rows, so an operator can read in the database what
 * each code means and which roles hold it. The code is the source of truth; these rows are a
 * projection rewritten on every start, never read by an authorization decision.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionCatalogueSeeder implements ApplicationRunner {

    public static final String META_ID = "__meta";

    private final ReactiveMongoTemplate mongo;

    @Override
    public void run(ApplicationArguments args) {
        seed().block();
    }

    /** Upserts one row per permission, a meta row with the fingerprint, and drops rows for retired codes. */
    public Mono<Void> seed() {
        String collection = IdentityCollections.PERMISSION_CATALOGUE;
        List<String> codes = Arrays.stream(Permission.values()).map(Permission::code).toList();
        Mono<Void> rows = Flux.fromArray(Permission.values())
                .concatMap(permission -> mongo.upsert(
                        Query.query(Criteria.where("_id").is(permission.code())), update(permission), collection))
                .then();
        Mono<Void> meta = mongo.upsert(Query.query(Criteria.where("_id").is(META_ID)),
                new Update().set("fingerprint", PermissionCatalogueFingerprint.compute())
                        .set("permissionCount", codes.size()), collection).then();
        Mono<Void> retired = mongo.remove(Query.query(Criteria.where("_id").nin(
                java.util.stream.Stream.concat(codes.stream(), java.util.stream.Stream.of(META_ID)).toList())),
                collection).then();
        return rows.then(meta).then(retired)
                .doOnSuccess(done -> log.info("Permission catalogue published: {} permissions", codes.size()));
    }

    private static Update update(Permission permission) {
        return new Update()
                .set("scope", permission.scope().name())
                .set("module", permission.module())
                .set("action", permission.action().verb())
                .set("description", permission.description())
                .set("platformRoles", names(Arrays.stream(PlatformRole.values())
                        .filter(role -> role.grants().contains(permission)).map(Enum::name)))
                .set("organizationRoles", names(Arrays.stream(OrganizationRole.values())
                        .filter(role -> role.permissions().contains(permission)).map(Enum::name)))
                .set("eventRoles", names(Arrays.stream(EventRole.values())
                        .filter(role -> role.permissions().contains(permission)).map(Enum::name)));
    }

    private static Set<String> names(java.util.stream.Stream<String> stream) {
        return stream.collect(Collectors.toCollection(java.util.TreeSet::new));
    }
}
