package com.pml.identity.account;

import com.pml.identity.config.IdentityAccountRepairProperties;
import com.pml.identity.domain.enums.AccountState;
import com.pml.identity.domain.enums.ContactType;
import com.pml.identity.domain.enums.PendingKind;
import com.pml.identity.domain.model.AccountEvent;
import com.pml.identity.domain.model.Contact;
import com.pml.identity.domain.model.User;
import com.pml.identity.security.ContactCrypto;
import com.pml.identity.workflow.contactchange.ContactChangeProcess;
import com.pml.shared.constants.UserType;
import com.pml.shared.error.DomainRefusal;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The repair of drift between MongoDB and Keycloak, classes D1..D9 of ET-IDN-004 section 4. Each class has exactly
 * one repair; the database is the record and Keycloak is rebuilt from it (consistency rule 1), except credentials.
 *
 * <p>Three passes, because they walk different things: {@link #repairAccounts} (D1, D3, D4, D5, D6: one Keycloak
 * read per account), {@link #repairKeycloakUsers} (D2, D9: the buyer realm's users) and {@link #repairStale} (D7,
 * D8: markers past their maximum age). Every repair is idempotent and writes one {@code REPAIR_Dn} account event
 * naming the class (account ids and counts only, never a contact); a second run finds nothing and writes nothing.
 * Alerts (PROVISIONING past 10 minutes, MERGING past 2 hours, CHANGING and DELETION_REQUESTED past 48 hours) are a
 * metric and a log line without personal data.</p>
 *
 * <p>Which side wins: roles and attribute (D5) and email (D6) come from the database; a SUSPENDED account is
 * disabled in Keycloak (D4) but a console-disabled ACTIVE account is adopted as SUSPENDED, never the reverse, so a
 * console change cannot re-open an account. Accounts that Keycloak owns (staff, legacy) are not touched.</p>
 */
@Service
public class AccountRepair {

    private static final Logger log = LoggerFactory.getLogger(AccountRepair.class);

    /** Accounts made by this service; the repair never edits an account Keycloak is the source for. */
    private static final List<String> OWNED = List.of("OTP", "ADOPTED");
    public static final String ADOPTED = "ADOPTED";
    private static final int PAGE = 100;

    private final ReactiveMongoTemplate template;
    private final ContactChangeProcess changes;
    private final IdentityAccountRepairProperties properties;
    private final KeycloakUserAdminPort keycloak;
    private final KeycloakAccountPort accountPort;
    private final AccountProvisioning provisioning;
    private final ContactCrypto crypto;
    private final MeterRegistry meters;
    private final Clock clock;

    public AccountRepair(ReactiveMongoTemplate template, ContactChangeProcess changes,
                         IdentityAccountRepairProperties properties, KeycloakUserAdminPort keycloak,
                         KeycloakAccountPort accountPort, AccountProvisioning provisioning, ContactCrypto crypto,
                         MeterRegistry meters, Clock clock) {
        this.template = template;
        this.changes = changes;
        this.properties = properties;
        this.keycloak = keycloak;
        this.accountPort = accountPort;
        this.provisioning = provisioning;
        this.crypto = crypto;
        this.meters = meters;
        this.clock = clock;
    }

    /** Every pass, in order; the counts by class ({@code D1}..{@code D9}) and by alert kind ({@code alert:PROVISIONING}...). */
    public Mono<Map<String, Long>> run() {
        return repairKeycloakUsers().flatMap(users -> repairAccounts().map(accounts -> merge(users, accounts)))
                .flatMap(done -> repairStale().map(stale -> merge(done, stale)));
    }

    private static Map<String, Long> merge(Map<String, Long> a, Map<String, Long> b) {
        Map<String, Long> out = new TreeMap<>(a);
        b.forEach((key, value) -> out.merge(key, value, Long::sum));
        return out;
    }

    // ---- pass 1 · accounts: D1, D3, D4, D5, D6 -----------------------------------------------------------

    public Mono<Map<String, Long>> repairAccounts() {
        Map<String, Long> counts = new java.util.concurrent.ConcurrentHashMap<>();
        return template.find(Query.query(Criteria.where("createdVia").in(OWNED)
                        .and("status").in(AccountState.ACTIVE, AccountState.SUSPENDED)), User.class)
                .concatMap(account -> repairAccount(account, counts))
                .then(Mono.fromSupplier(() -> new TreeMap<>(counts)));
    }

    private Mono<Void> repairAccount(User account, Map<String, Long> counts) {
        return locate(account, counts)
                .flatMap(view -> view.isEmpty() ? Mono.<Void>empty() : enabledAndRoles(account, view.get(), counts)
                        .then(Mono.defer(() -> emailDrift(account, view.get(), counts))));
    }

    /** D1 and D3: the account's Keycloak user, created (D1) or linked (D3) when it is missing or unlinked. */
    private Mono<java.util.Optional<KeycloakUserAdminPort.KeycloakUserView>> locate(User account, Map<String, Long> counts) {
        boolean linked = account.getKeycloakUserId() != null && !account.getKeycloakUserId().isBlank();
        Mono<java.util.Optional<KeycloakUserAdminPort.KeycloakUserView>> byLink = linked
                ? keycloak.readUser(null, account.getKeycloakUserId()).map(java.util.Optional::of).defaultIfEmpty(java.util.Optional.empty())
                : Mono.just(java.util.Optional.empty());
        return byLink.flatMap(found -> {
            if (found.isPresent()) {
                return Mono.just(found);
            }
            return keycloak.readUserByUsername(null, account.getId()).flatMap(byName -> {
                if (byName.isPresent()) {
                    // D3: it exists under the account's username but the link is missing or stale
                    return link(account, byName.get().id())
                            .then(provisioning.applyAttributes(account.getId(), byName.get().id()))
                            .then(audit("D3", account.getId(), Map.of()))
                            .doOnSuccess(done -> count(counts, "D3"))
                            .thenReturn(byName);
                }
                // D1: no Keycloak user at all; complete forward
                return accountPort.createUser(account.getId(), AccountStates.of(account) == AccountState.ACTIVE, UserType.CUSTOMER)
                        .flatMap(id -> link(account, id).then(provisioning.applyAttributes(account.getId(), id))
                                .then(audit("D1", account.getId(), Map.of()))
                                .doOnSuccess(done -> count(counts, "D1"))
                                .then(keycloak.readUser(null, id)).map(java.util.Optional::of));
            });
        });
    }

    private Mono<Void> link(User account, String keycloakUserId) {
        return template.updateFirst(Query.query(Criteria.where("_id").is(account.getId())),
                new Update().set("keycloakUserId", keycloakUserId).set("updatedAt", clock.instant()), User.class).then()
                .doOnSuccess(done -> account.setKeycloakUserId(keycloakUserId));
    }

    /** D4 (enabled vs status) then D5 (realm roles and accountId attribute vs the database policy). */
    private Mono<Void> enabledAndRoles(User account, KeycloakUserAdminPort.KeycloakUserView view, Map<String, Long> counts) {
        AccountState state = AccountStates.of(account);
        Mono<Void> d4 = Mono.empty();
        boolean wantEnabled = state == AccountState.ACTIVE;
        if (account.getPendingKind() == null && view.enabled() != wantEnabled) {
            if (!wantEnabled) {
                d4 = keycloak.setEnabled(null, view.id(), false).then(keycloak.endSessions(null, view.id()))
                        .then(audit("D4", account.getId(), Map.of("direction", "DISABLED_IN_KEYCLOAK")))
                        .doOnSuccess(done -> count(counts, "D4"));
            } else {
                // the console disabled an ACTIVE account: adopt it as SUSPENDED (never re-open by the console)
                d4 = Mono.defer(() -> {
                            AccountStates.apply(account, AccountState.SUSPENDED);
                            return template.updateFirst(Query.query(Criteria.where("_id").is(account.getId()).and("status").is(AccountState.ACTIVE)),
                                    new Update().set("status", AccountState.SUSPENDED)
                                            .set("accountStatus", AccountStates.legacyStatus(AccountState.SUSPENDED))
                                            .set("active", false).set("updatedAt", clock.instant()), User.class);
                        }).then(audit("D4", account.getId(), Map.of("direction", "ADOPTED_AS_SUSPENDED")))
                        .doOnSuccess(done -> count(counts, "D4"));
            }
        }
        return d4.then(Mono.defer(() -> rolesAndAttribute(account, view, counts)));
    }

    private Mono<Void> rolesAndAttribute(User account, KeycloakUserAdminPort.KeycloakUserView view, Map<String, Long> counts) {
        Set<String> platform = EnumSet.allOf(UserType.class).stream().map(Enum::name).collect(Collectors.toCollection(TreeSet::new));
        Set<UserType> policyRoles = account.getRoles() == null || account.getRoles().isEmpty()
                ? EnumSet.of(UserType.CUSTOMER) : account.getRoles();
        Set<String> policy = policyRoles.stream().map(Enum::name).collect(Collectors.toCollection(TreeSet::new));
        Set<String> held = view.realmRoles().stream().filter(platform::contains).collect(Collectors.toCollection(TreeSet::new));
        Set<String> missing = new TreeSet<>(policy);
        missing.removeAll(held);
        Set<String> extra = new TreeSet<>(held);
        extra.removeAll(policy);
        return keycloak.readAttribute(null, view.id(), ACCOUNT_ID_ATTRIBUTE)
                .map(value -> value.filter(account.getId()::equals).isPresent())
                .flatMap(attributeOk -> {
                    if (missing.isEmpty() && extra.isEmpty() && attributeOk) {
                        return Mono.<Void>empty();
                    }
                    Mono<Void> fix = Flux.fromIterable(extra).concatMap(role -> keycloak.revokeRealmRole(null, view.id(), role)).then();
                    if (!missing.isEmpty() || !attributeOk) {
                        fix = fix.then(provisioning.applyAttributes(account.getId(), view.id()));
                    }
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("grantedRoles", missing.toString());
                    data.put("revokedRoles", extra.toString());
                    data.put("attributeRewritten", !attributeOk);
                    return fix.then(audit("D5", account.getId(), data)).doOnSuccess(done -> count(counts, "D5"));
                });
    }

    /** D6: Keycloak's email and emailVerified rewritten from the account's verified email contact. */
    private Mono<Void> emailDrift(User account, KeycloakUserAdminPort.KeycloakUserView view, Map<String, Long> counts) {
        if (account.getPendingKind() == PendingKind.CHANGING) {
            return Mono.empty(); // the change workflow is writing it
        }
        return template.find(Query.query(Criteria.where("accountId").is(account.getId()).and("type").is(ContactType.EMAIL)
                                .and("verifiedAt").exists(true).and("releasedAt").is(null)), Contact.class)
                .next().flatMap(contact -> crypto.decrypt(contact.getValueEncrypted()).map(java.util.Optional::of))
                .defaultIfEmpty(java.util.Optional.empty())
                .flatMap(expected -> {
                    String kcEmail = view.email() == null ? null : view.email().toLowerCase(Locale.ROOT);
                    boolean same = expected.isPresent()
                            ? expected.get().toLowerCase(Locale.ROOT).equals(kcEmail) && view.emailVerified()
                            : kcEmail == null;
                    if (same) {
                        return Mono.<Void>empty();
                    }
                    return accountPort.setEmail(view.id(), expected.orElse(null), expected.isPresent())
                            .then(audit("D6", account.getId(), Map.of("emailContact", expected.isPresent() ? "PRESENT" : "NONE")))
                            .doOnSuccess(done -> count(counts, "D6"));
                });
    }

    // ---- pass 2 · the buyer realm's users: D2, D9 -------------------------------------------------------

    public Mono<Map<String, Long>> repairKeycloakUsers() {
        Map<String, Long> counts = new java.util.concurrent.ConcurrentHashMap<>();
        return pages(0).concatMap(view -> classify(view, counts)).then(Mono.fromSupplier(() -> new TreeMap<>(counts)));
    }

    private Flux<KeycloakUserAdminPort.KeycloakUserView> pages(int first) {
        return keycloak.listUsers(null, first, PAGE).collectList().flatMapMany(page ->
                page.isEmpty() ? Flux.<KeycloakUserAdminPort.KeycloakUserView>empty()
                        : Flux.fromIterable(page).concatWith(page.size() < PAGE ? Flux.empty() : Flux.defer(() -> pages(first + PAGE))));
    }

    private Mono<Void> classify(KeycloakUserAdminPort.KeycloakUserView user, Map<String, Long> counts) {
        return template.exists(Query.query(new Criteria().orOperator(Criteria.where("keycloakUserId").is(user.id()),
                Criteria.where("_id").is(user.id()))), User.class).flatMap(known -> {
            if (known) {
                return Mono.<Void>empty();
            }
            return template.findById(user.username() == null ? "" : user.username(), User.class)
                    .map(java.util.Optional::of).defaultIfEmpty(java.util.Optional.empty()).flatMap(named -> {
                        if (named.isEmpty()) {
                            // a user that names an account through its accountId attribute is that account's duplicate, not a stranger
                            return keycloak.readAttribute(null, user.id(), ACCOUNT_ID_ATTRIBUTE).flatMap(claimed -> claimed.isPresent()
                                    ? template.findById(claimed.get(), User.class)
                                    .filter(a -> a.getKeycloakUserId() != null && !a.getKeycloakUserId().isBlank()
                                            && !a.getKeycloakUserId().equals(user.id()))
                                    .map(java.util.Optional::of).defaultIfEmpty(java.util.Optional.empty())
                                    .flatMap(owner -> owner.isPresent() ? quarantineDuplicate(user, owner.get(), counts) : adopt(user, counts))
                                    : adopt(user, counts));
                        }
                        User account = named.get();
                        boolean unlinked = account.getKeycloakUserId() == null || account.getKeycloakUserId().isBlank();
                        return unlinked ? Mono.<Void>empty() : quarantineDuplicate(user, account, counts);
                    });
        });
    }

    /** D2: a user with no account becomes an account (ADOPTED, CUSTOMER by policy), audited and flagged for review. */
    private Mono<Void> adopt(KeycloakUserAdminPort.KeycloakUserView user, Map<String, Long> counts) {
        Instant now = clock.instant();
        User account = User.builder().id(user.id()).keycloakUserId(user.id()).username(user.username())
                .roles(EnumSet.of(UserType.CUSTOMER)).createdVia(ADOPTED).createdAt(now).updatedAt(now).build();
        AccountStates.apply(account, user.enabled() ? AccountState.ACTIVE : AccountState.SUSPENDED);
        return template.insert(account)
                .then(audit("D2", account.getId(), Map.of("needsReview", true)))
                .doOnSuccess(done -> {
                    count(counts, "D2");
                    log.warn("Repair D2: Keycloak user {} had no account and was adopted; it needs review", user.id());
                })
                .onErrorResume(DuplicateKeyException.class, concurrent -> Mono.empty());
    }

    /** D9: a second Keycloak user for an account already linked to another: disabled, and a support task opened. */
    private Mono<Void> quarantineDuplicate(KeycloakUserAdminPort.KeycloakUserView extra, User account, Map<String, Long> counts) {
        String taskId = "repair:D9:" + extra.id();
        return template.exists(Query.query(Criteria.where("_id").is(taskId)), AccountEvent.class).flatMap(done -> {
            if (done && !extra.enabled()) {
                return Mono.<Void>empty();
            }
            Mono<Void> disable = extra.enabled()
                    ? keycloak.setEnabled(null, extra.id(), false).then(keycloak.endSessions(null, extra.id())) : Mono.empty();
            Mono<Void> task = done ? Mono.empty() : template.insert(AccountEvent.builder().id(taskId).accountId(account.getId())
                            .kind("REPAIR_D9").at(clock.instant())
                            .data(Map.of("repairClass", "D9", "supportTask", true, "duplicateKeycloakUserId", extra.id(),
                                    "linkedKeycloakUserId", String.valueOf(account.getKeycloakUserId())))
                            .build()).then()
                    .onErrorResume(DuplicateKeyException.class, seen -> Mono.empty());
            return disable.then(task).doOnSuccess(x -> {
                count(counts, "D9");
                log.warn("Repair D9: account {} has a second Keycloak user {}; quarantined, support task opened", account.getId(), extra.id());
            });
        });
    }

    // ---- pass 3 · markers past their age: D7, D8 ---------------------------------------------------------

    public Mono<Map<String, Long>> repairStale() {
        Map<String, Long> counts = new java.util.concurrent.ConcurrentHashMap<>();
        return stalledProvisioning(counts).then(Mono.defer(() -> stalePending(counts)))
                .then(Mono.fromSupplier(() -> new TreeMap<>(counts)));
    }

    /** D7: a PROVISIONING account past its maximum age is resumed from where it stopped, and alerted on. */
    private Mono<Void> stalledProvisioning(Map<String, Long> counts) {
        Instant cutoff = clock.instant().minus(properties.getProvisioningMaxAge());
        return template.find(Query.query(Criteria.where("status").is(AccountState.PROVISIONING).and("createdAt").lt(cutoff)), User.class)
                .concatMap(account -> {
                    alert(counts, "PROVISIONING", account.getId());
                    return provisioning.createKeycloakUser(account.getId())
                            .flatMap(id -> provisioning.applyAttributes(account.getId(), id)
                                    .then(provisioning.activate(account.getId(), id, "repair", null, List.of())))
                            .then(audit("D7", account.getId(), Map.of()))
                            .doOnSuccess(done -> count(counts, "D7"))
                            .onErrorResume(DomainRefusal.class, refused -> {
                                log.warn("Repair D7: account {} cannot be resumed: {}", account.getId(), refused.errorCode());
                                return Mono.empty();
                            });
                }).then();
    }

    /** D8: CHANGING with no open workflow is cleared; MERGING and DELETION_REQUESTED past their age are alerts. */
    private Mono<Void> stalePending(Map<String, Long> counts) {
        Instant now = clock.instant();
        Mono<Void> changing = clearStaleChanging().doOnSuccess(cleared -> {
            if (cleared != null && cleared > 0) {
                counts.merge("D8", cleared, Long::sum);
            }
        }).then();
        Mono<Void> merging = template.find(Query.query(Criteria.where("pendingKind").is(PendingKind.MERGING)
                        .and("pendingSince").lt(now.minus(properties.getMergingMaxAge()))), User.class)
                .concatMap(account -> staleAlert(counts, "MERGING", account)).then();
        Mono<Void> deletion = template.find(Query.query(Criteria.where("pendingKind").is(PendingKind.DELETION_REQUESTED)
                        .and("pendingSince").lt(now.minus(properties.getChangingMaxAge()))), User.class)
                .concatMap(account -> staleAlert(counts, "DELETION_REQUESTED", account)).then();
        return changing.then(merging).then(deletion);
    }

    private Mono<Void> staleAlert(Map<String, Long> counts, String kind, User account) {
        alert(counts, kind, account.getId());
        String id = "repair:D8:" + account.getId() + ":" + kind;
        return template.insert(AccountEvent.builder().id(id).accountId(account.getId()).kind("REPAIR_D8").at(clock.instant())
                        .data(Map.of("repairClass", "D8", "pendingKind", kind, "alert", true)).build())
                .then().onErrorResume(DuplicateKeyException.class, seen -> Mono.empty());
    }

    /**
     * A {@code CHANGING} marker older than {@code identity.account.repair.changing-max-age} whose
     * {@code ContactChangeWorkflow} is no longer open is a marker nobody is working on: it is cleared, so the
     * person can change a contact again. A marker whose workflow is still open is left alone and alerted on: the
     * workflow is waiting for Keycloak and will finish, and clearing the marker under it would let a second change start.
     *
     * @return how many orphaned markers were cleared
     */
    public Mono<Long> clearStaleChanging() {
        Instant cutoff = clock.instant().minus(properties.getChangingMaxAge());
        return template.find(Query.query(Criteria.where("pendingKind").is(PendingKind.CHANGING)
                        .and("pendingSince").lt(cutoff)), User.class)
                .concatMap(account -> changes.status(account.getId()).flatMap(open -> {
                    if (open.isPresent()) {
                        log.warn("ALERT contact change of account {} is open past {}: waiting for the workflow", account.getId(),
                                properties.getChangingMaxAge());
                        meters.counter("identity.account.repair.alert", "kind", "CHANGING").increment();
                        return Mono.just(false);
                    }
                    return template.updateFirst(Query.query(Criteria.where("_id").is(account.getId())
                                            .and("pendingKind").is(PendingKind.CHANGING)),
                                    new Update().unset("pendingKind").unset("pendingSince").set("updatedAt", clock.instant()), User.class)
                            .flatMap(result -> result.getModifiedCount() == 1
                                    ? audit("D8", account.getId(), Map.of("pendingKind", "CHANGING")).thenReturn(true)
                                    : Mono.just(false));
                }))
                .filter(cleared -> cleared)
                .count();
    }

    // ---- shared ----------------------------------------------------------------------------------------

    private static final String ACCOUNT_ID_ATTRIBUTE = com.pml.identity.infrastructure.keycloak.KeycloakService.ACCOUNT_ID_ATTRIBUTE;

    private Mono<Void> audit(String repairClass, String accountId, Map<String, Object> detail) {
        Map<String, Object> data = new LinkedHashMap<>(detail);
        data.put("repairClass", repairClass);
        return template.insert(AccountEvent.builder().id("repair:" + repairClass + ":" + accountId + ":" + UUID.randomUUID())
                .accountId(accountId).kind("REPAIR_" + repairClass).at(clock.instant()).data(data).build()).then();
    }

    private void count(Map<String, Long> counts, String repairClass) {
        counts.merge(repairClass, 1L, Long::sum);
        meters.counter("identity.account.repair", "class", repairClass).increment();
    }

    private void alert(Map<String, Long> counts, String kind, String accountId) {
        counts.merge("alert:" + kind, 1L, Long::sum);
        meters.counter("identity.account.repair.alert", "kind", kind).increment();
        log.warn("ALERT account {} has been {} longer than its maximum age", accountId, kind);
    }
}
