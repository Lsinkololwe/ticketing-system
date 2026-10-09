package com.pml.identity.migration;

import com.pml.identity.persistence.IdentityCollections;
import com.pml.shared.security.Permission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static java.util.Map.entry;

/**
 * Brings stored data in line with the permission catalogue declared in code.
 *
 * <ol>
 *   <li>Drops the collections that once held permissions and role mappings. Nothing reads them:
 *       the catalogue and every role's set are declared in code.</li>
 *   <li>Turns on both owner switches — managers see financial figures, admins request payouts —
 *       for every organization that existed before the switches did, so nobody loses access they
 *       had. An organization created afterwards starts with both off. An organization that
 *       already carries the new fields is left alone, so a switch its owner turned off stays off.</li>
 *   <li>Rewrites members' custom and denied permissions, and event grants' custom permissions,
 *       from the old upper-case names to catalogue codes. A name with no catalogue equivalent is
 *       removed; it granted nothing before this migration and grants nothing after it.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class PermissionModelMigrationService {

    /** Collections dropped outright, under their registry names and the names they had before them. */
    static final List<String> RETIRED_COLLECTIONS = List.of(
            IdentityCollections.PERMISSIONS, IdentityCollections.ROLE_PERMISSIONS,
            "identity_role_permission_changes", "permissions", "role_permissions");

    /** Every upper-case permission name identity has stored, and the catalogue permission it meant. */
    static final Map<String, Permission> LEGACY_NAMES = Map.ofEntries(
            entry("EVENT_VIEW", Permission.EVENT_VIEW),
            entry("EVENT_CREATE", Permission.EVENT_CREATE),
            entry("EVENT_EDIT", Permission.EVENT_EDIT),
            entry("EVENT_MANAGE_TICKETS", Permission.EVENT_EDIT),
            entry("EVENT_PUBLISH", Permission.EVENT_PUBLISH),
            entry("EVENT_DELETE", Permission.EVENT_DELETE),
            entry("TICKET_SCAN", Permission.TICKET_SCAN),
            entry("EVENT_MANAGE_CHECK_IN", Permission.TICKET_SCAN),
            entry("ATTENDEE_VIEW", Permission.ATTENDEE_VIEW),
            entry("EVENT_VIEW_ATTENDEES", Permission.ATTENDEE_VIEW),
            entry("ANALYTICS_VIEW", Permission.ANALYTICS_VIEW),
            entry("EVENT_VIEW_ANALYTICS", Permission.ANALYTICS_VIEW),
            entry("REFUND_ISSUE", Permission.TICKET_REFUND),
            entry("PROMOTION_MANAGE", Permission.PROMOTION_MANAGE),
            entry("FINANCIAL_VIEW", Permission.FINANCIAL_VIEW),
            entry("FIN_VIEW_REVENUE", Permission.FINANCIAL_VIEW),
            entry("FIN_VIEW_TRANSACTIONS", Permission.FINANCIAL_VIEW),
            entry("PAYOUT_REQUEST", Permission.PAYOUT_REQUEST),
            entry("FIN_REQUEST_PAYOUT", Permission.PAYOUT_REQUEST),
            entry("MEMBER_INVITE", Permission.TEAM_INVITE),
            entry("MEMBER_REMOVE", Permission.TEAM_REMOVE),
            entry("MEMBER_ROLE_CHANGE", Permission.TEAM_ROLE),
            entry("MEMBER_EDIT_ROLE", Permission.TEAM_ROLE),
            entry("ORG_MANAGE_MEMBERS", Permission.TEAM_ROLE),
            entry("ORG_VIEW_MEMBERS", Permission.TEAM_VIEW),
            entry("EVENT_MANAGE_ACCESS", Permission.EVENT_ACCESS_GRANT),
            entry("ORG_VIEW", Permission.ORGANIZATION_VIEW),
            entry("ORG_EDIT", Permission.ORGANIZATION_EDIT),
            entry("ORG_SETTINGS", Permission.ORGANIZATION_EDIT),
            entry("ORG_MANAGE_SETTINGS", Permission.ORGANIZATION_EDIT),
            entry("ORG_DELETE", Permission.ORGANIZATION_DELETE),
            entry("OWNERSHIP_TRANSFER", Permission.ORGANIZATION_TRANSFER),
            entry("TRANSFER_OWNERSHIP", Permission.ORGANIZATION_TRANSFER));

    private final ObjectProvider<ReactiveMongoTemplate> mongoTemplateProvider;

    public Mono<Result> migrate() {
        ReactiveMongoTemplate mongo = mongoTemplateProvider.getObject();
        return dropRetired(mongo)
                .zipWith(switchOnForExistingOrganizations(mongo))
                .zipWith(rewrite(mongo, IdentityCollections.ORGANIZATION_MEMBERS, List.of("customPermissions", "deniedPermissions"))
                        .zipWith(rewrite(mongo, IdentityCollections.EVENT_ACCESS_GRANTS, List.of("customPermissions")),
                                Long::sum))
                .map(done -> new Result(done.getT1().getT1(), done.getT1().getT2(), done.getT2()))
                .doOnNext(result -> log.info("Permission model migration: dropped {} collection(s), switched on {} organization(s), rewrote {} document(s)",
                        result.collectionsDropped(), result.organizationsSwitchedOn(), result.documentsRewritten()));
    }

    /** Catalogue codes for {@code stored}: a code is kept, a legacy name translated, anything else removed. */
    static List<String> translate(Collection<?> stored) {
        TreeSet<String> codes = new TreeSet<>();
        for (Object value : stored) {
            if (!(value instanceof String name)) {
                continue;
            }
            Permission.fromCode(name).or(() -> java.util.Optional.ofNullable(LEGACY_NAMES.get(name)))
                    .ifPresent(permission -> codes.add(permission.code()));
        }
        return new ArrayList<>(codes);
    }

    private Mono<Long> dropRetired(ReactiveMongoTemplate mongo) {
        return Flux.fromIterable(RETIRED_COLLECTIONS)
                .concatMap(name -> mongo.collectionExists(name)
                        .flatMap(exists -> exists ? mongo.dropCollection(name).thenReturn(1L) : Mono.just(0L)))
                .reduce(0L, Long::sum);
    }

    private Mono<Long> switchOnForExistingOrganizations(ReactiveMongoTemplate mongo) {
        Document untouched = new Document("settings.managersCanViewFinancials", new Document("$exists", false));
        Document withSettings = new Document(untouched).append("settings", new Document("$type", "object"));
        Document switchOn = new Document("$set", new Document("settings.managersCanViewFinancials", true)
                .append("settings.adminsCanRequestPayouts", true))
                .append("$unset", new Document("settings.managersCanRequestPayouts", "")
                        .append("settings.marketersCanViewFinancials", ""));

        // An organization without a settings document gets a complete one, so reading it back does
        // not depend on which defaults the mapper applies to fields it never saw.
        Document withoutSettings = new Document("$or", List.of(
                new Document("settings", new Document("$exists", false)),
                new Document("settings", null)));
        Document fullSettings = new Document("$set", new Document("settings", new Document()
                .append("defaultEventVisibility", "PUBLIC")
                .append("requireEventApproval", false)
                .append("allowMembersToInvite", false)
                .append("inviteRequiresApproval", false)
                .append("managersCanViewFinancials", true)
                .append("adminsCanRequestPayouts", true)
                .append("notifyOwnerOnMemberJoin", true)
                .append("notifyOwnerOnEventCreated", true)
                .append("notifyOwnerOnPayoutRequest", true)));

        return mongo.getCollection(IdentityCollections.ORGANIZATIONS)
                .flatMap(organizations -> Mono.from(organizations.updateMany(withSettings, switchOn))
                        .zipWith(Mono.from(organizations.updateMany(withoutSettings, fullSettings)),
                                (a, b) -> a.getModifiedCount() + b.getModifiedCount()));
    }

    private Mono<Long> rewrite(ReactiveMongoTemplate mongo, String collection, List<String> fields) {
        List<Document> anyPresent = fields.stream()
                .map(field -> new Document(field + ".0", new Document("$exists", true)))
                .toList();
        return mongo.getCollection(collection)
                .flatMapMany(documents -> Flux.from(documents.find(new Document("$or", anyPresent)))
                        .concatMap(document -> {
                            Document set = new Document();
                            for (String field : fields) {
                                Object stored = document.get(field);
                                if (stored instanceof Collection<?> values) {
                                    List<String> codes = translate(values);
                                    if (!codes.equals(values instanceof List<?> list ? list : List.copyOf(values))) {
                                        set.append(field, codes);
                                    }
                                }
                            }
                            if (set.isEmpty()) {
                                return Mono.just(0L);
                            }
                            return Mono.from(documents.updateOne(new Document("_id", document.get("_id")), new Document("$set", set)))
                                    .map(result -> result.getModifiedCount());
                        }))
                .reduce(0L, Long::sum);
    }

    public record Result(long collectionsDropped, long organizationsSwitchedOn, long documentsRewritten) {}
}
