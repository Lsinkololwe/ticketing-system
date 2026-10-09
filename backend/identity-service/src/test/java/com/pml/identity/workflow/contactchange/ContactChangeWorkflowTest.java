package com.pml.identity.workflow.contactchange;

import com.pml.identity.account.AccountFixtures;
import com.pml.identity.account.AccountStates;
import com.pml.identity.account.ContactChangeKind;
import com.pml.identity.account.ContactChangeSteps;
import com.pml.identity.account.FakeKeycloak;
import com.pml.identity.account.ProofRecord;
import com.pml.identity.auth.delivery.CapturedMessages;
import com.pml.identity.auth.delivery.CapturingProvider;
import com.pml.identity.auth.delivery.ContactNotice;
import com.pml.identity.auth.delivery.DeliveryOrchestrator;
import com.pml.identity.config.IdentityContactProperties;
import com.pml.identity.domain.enums.AccountState;
import com.pml.identity.domain.enums.ContactType;
import com.pml.identity.domain.enums.PendingKind;
import com.pml.identity.domain.model.AccountEvent;
import com.pml.identity.domain.model.Contact;
import com.pml.identity.domain.model.User;
import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.identity.infrastructure.temporal.WorkflowIds;
import com.pml.identity.workflow.TemporalTestEnvironments;
import com.pml.identity.workflow.contactchange.ContactChangeWorkflow.ChangeCommand;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.infrastructure.temporal.TemporalGateway;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.testing.WorkflowReplayer;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The contact-change process on the in-process Temporal server (time-skipping), with the real MongoDB
 * steps and a Keycloak that can be down.
 *
 * <p>The properties: the new contact is claimed before the old one is released, and a lost claim changes
 * nothing; the account is reachable by its old contact until the switch; a crash anywhere converges; one
 * change is open at a time; an unconfirmed change expires after 48 hours and an abused one aborts after five
 * wrong codes, each clearing the marker; and the history replays.</p>
 */
@Tag("L3")
@Tag("ET-IDN-004")
@DisplayName("ET-IDN-004 · ContactChangeWorkflow claims before it releases, converges after a crash and expires")
class ContactChangeWorkflowTest {

    private static final String OLD_EMAIL = "old.person@example.com";
    private static final String NEW_EMAIL = "new.person@example.com";
    private static final String PHONE = "+260971234567";

    /** A clock the test can move, for the quarantine. */
    static final class MovableClock extends Clock {
        private volatile Instant now = Instant.parse("2026-10-04T10:00:00Z");

        void advance(Duration by) {
            now = now.plus(by);
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

    private static final MovableClock CLOCK = new MovableClock();
    private static AccountFixtures db;

    private TestWorkflowEnvironment env;
    private FakeKeycloak keycloak;
    private CapturedMessages captured;
    private FaultyActivities activities;
    private ContactChangeProcess process;
    private IdentityContactProperties properties;

    @BeforeAll
    static void connect() {
        db = new AccountFixtures("identity_contact_change_workflow", CLOCK);
    }

    @AfterAll
    static void disconnect() {
        db.close();
    }

    @BeforeEach
    void start() {
        db.reset();
        keycloak = new FakeKeycloak();
        captured = new CapturedMessages();
        properties = new IdentityContactProperties();
        properties.setQuarantine(Duration.ofDays(30));
        DeliveryOrchestrator delivery = new DeliveryOrchestrator(List.of(new CapturingProvider(captured, CLOCK)), new SimpleMeterRegistry());
        ContactChangeSteps steps = new ContactChangeSteps(db.template, db.transaction, db.outbox, db.proofs, keycloak, keycloak,
                AccountFixtures.CRYPTO, delivery, properties, CLOCK);
        activities = new FaultyActivities(new ContactChangeActivitiesImpl(steps));
        env = TemporalTestEnvironments.newInstance();
        Worker worker = env.newWorker(TaskQueues.ACCOUNT);
        worker.registerWorkflowImplementationTypes(ContactChangeWorkflowImpl.class);
        worker.registerActivitiesImplementations(activities);
        env.start();
        process = new ContactChangeProcess(new TemporalGateway(env.getWorkflowClient()));
    }

    @AfterEach
    void stop() {
        env.close();
    }

    // ---------------------------------------------------------------------------------- fixtures

    /** An ACTIVE account with one verified, primary contact and a Keycloak user. */
    private String account(ContactType type, String value) {
        String accountId = UUID.randomUUID().toString();
        String keycloakUserId = keycloak.preexisting(accountId);
        ProofRecord proof = db.proofFor(type, value, "seed-" + accountId);
        Contact contact = Contact.builder().id("c-" + accountId).accountId(accountId).type(type).valueHash(proof.contactKey())
                .valueEncrypted(proof.valueEncrypted()).valueMasked(proof.valueMasked()).verifiedAt(CLOCK.instant())
                .primary(true).source("OTP").createdAt(CLOCK.instant()).build();
        User user = User.builder().id(accountId).username(accountId).keycloakUserId(keycloakUserId)
                .primaryContactId(contact.getId()).emailVerified(type == ContactType.EMAIL)
                .phoneVerified(type == ContactType.WHATSAPP).createdAt(CLOCK.instant()).build();
        AccountStates.apply(user, AccountState.ACTIVE);
        db.template.insert(user).block();
        db.template.insert(contact).block();
        return accountId;
    }

    private ProofRecord proof(ContactType type, String value) {
        return db.proofFor(type, value, "proof-" + UUID.randomUUID());
    }

    private ChangeCommand add(String accountId, ProofRecord proof) {
        return new ChangeCommand(UUID.randomUUID().toString(), accountId, ContactChangeKind.ADD, null, proof.type(),
                proof.contactKey(), proof.valueMasked(), proof.proofId(), null, null);
    }

    private ChangeCommand change(String accountId, String contactId, ProofRecord target) {
        return new ChangeCommand(UUID.randomUUID().toString(), accountId, ContactChangeKind.CHANGE, contactId, target.type(),
                target.contactKey(), target.valueMasked(), null, "n-ch", "c-ch");
    }

    private ChangeCommand simple(String accountId, ContactChangeKind kind, String contactId) {
        return new ChangeCommand(UUID.randomUUID().toString(), accountId, kind, contactId, null, null, null, null, null, null);
    }

    private ContactChangeWorkflow.Outcome run(ChangeCommand command) {
        process.start(command).block();
        return process.await(command.accountId(), Duration.ofMinutes(5)).block().orElseThrow();
    }

    private List<Contact> contacts(String accountId) {
        return db.template.find(Query.query(Criteria.where("accountId").is(accountId)), Contact.class).collectList().block();
    }

    private List<Contact> active(String accountId) {
        return contacts(accountId).stream().filter(c -> c.getReleasedAt() == null).toList();
    }

    private User user(String accountId) {
        return db.template.findById(accountId, User.class).block();
    }

    private static void assertRefused(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOf(DomainRefusal.class)
                .extracting(error -> ((DomainRefusal) error).errorCode()).isEqualTo(code);
    }

    // ---------------------------------------------------------------------------------- add

    @Test
    @DisplayName("add: a WhatsApp account gains a verified email; the email goes to Keycloak, the primary stays, the new contact is told")
    void addEmailToAWhatsAppAccount() {
        String accountId = account(ContactType.WHATSAPP, PHONE);
        ProofRecord email = proof(ContactType.EMAIL, NEW_EMAIL);

        var outcome = run(add(accountId, email));

        assertThat(outcome.status()).isEqualTo("COMPLETED");
        List<Contact> now = active(accountId);
        assertThat(now).hasSize(2).allSatisfy(c -> assertThat(c.getVerifiedAt()).isNotNull());
        assertThat(now.stream().filter(Contact::isPrimary)).as("exactly one primary").hasSize(1)
                .allSatisfy(c -> assertThat(c.getType()).isEqualTo(ContactType.WHATSAPP));
        User account = user(accountId);
        assertThat(account.getPendingKind()).as("marker cleared").isNull();
        assertThat(account.isEmailVerified()).isTrue();
        assertThat(account.isPhoneVerified()).isTrue();
        assertThat(keycloak.emails.get(account.getKeycloakUserId())).isEqualTo(NEW_EMAIL);
        assertThat(keycloak.sessionsEnded).as("an add does not sign anyone out").isEmpty();
        AccountEvent event = db.template.findAll(AccountEvent.class).collectList().block().stream()
                .filter(e -> "CONTACT_ADDED".equals(e.getKind())).findFirst().orElseThrow();
        assertThat(event.getData().toString()).doesNotContain(NEW_EMAIL).doesNotContain("260971234567");
        assertThat(db.count(AccountFixtures.OUTBOX)).isEqualTo(1);
        assertThat(captured.noticesTo(NEW_EMAIL)).extracting(CapturedMessages.Notice::notice).containsExactly(ContactNotice.CONTACT_ADDED);
    }

    @Test
    @DisplayName("add: a contact another account holds is refused with CONTACT_ALREADY_CLAIMED and nothing changes, in Mongo or Keycloak")
    void addRefusedWhenAnotherAccountHoldsIt() {
        String holder = account(ContactType.EMAIL, NEW_EMAIL);
        String adder = account(ContactType.WHATSAPP, PHONE);
        keycloak.calls.clear();

        assertRefused(() -> run(add(adder, proof(ContactType.EMAIL, NEW_EMAIL))), ErrorCode.CONTACT_ALREADY_CLAIMED);

        assertThat(active(adder)).hasSize(1);
        assertThat(active(holder)).hasSize(1);
        assertThat(user(adder).getPendingKind()).isNull();
        assertThat(keycloak.emails).isEmpty();
        assertThat(keycloak.calls).as("Keycloak was not touched").noneMatch(call -> call.equals("setEmail"));
    }

    @Test
    @DisplayName("add: asking again for a contact the account already holds is a no-op, not an error")
    void addIsIdempotent() {
        String accountId = account(ContactType.EMAIL, OLD_EMAIL);
        run(add(accountId, proof(ContactType.EMAIL, OLD_EMAIL)));

        assertThat(active(accountId)).hasSize(1);
    }

    // ---------------------------------------------------------------------------------- change

    @Test
    @DisplayName("change: the new contact is claimed, Keycloak gets the new email, the old contact is released, sessions end, both are told")
    void changeEmail() {
        String accountId = account(ContactType.EMAIL, OLD_EMAIL);
        String oldContact = "c-" + accountId;
        ProofRecord target = proof(ContactType.EMAIL, NEW_EMAIL);
        ChangeCommand command = change(accountId, oldContact, target);
        process.start(command).block();

        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(20)).until(() ->
                process.status(accountId).block().map(s -> s.phase()).orElse("").equals("AWAITING_CODES"));
        assertThat(user(accountId).getPendingKind()).isEqualTo(PendingKind.CHANGING);
        assertThat(active(accountId)).as("nothing changed while the codes are awaited").extracting(Contact::getId).containsExactly(oldContact);

        process.authorise(accountId).block();
        process.acceptNew(accountId, target.proofId()).block();
        assertThat(process.await(accountId, Duration.ofMinutes(5)).block().orElseThrow().status()).isEqualTo("COMPLETED");

        List<Contact> all = contacts(accountId);
        Contact released = all.stream().filter(c -> c.getId().equals(oldContact)).findFirst().orElseThrow();
        assertThat(released.getReleasedAt()).isNotNull();
        assertThat(released.isPrimary()).isFalse();
        Contact current = active(accountId).get(0);
        assertThat(current.getValueHash()).isEqualTo(target.contactKey());
        assertThat(current.isPrimary()).as("the primary moved with the change").isTrue();
        User account = user(accountId);
        assertThat(account.getPrimaryContactId()).isEqualTo(current.getId());
        assertThat(account.getPendingKind()).isNull();
        assertThat(keycloak.emails.get(account.getKeycloakUserId())).isEqualTo(NEW_EMAIL);
        assertThat(keycloak.sessionsEnded).contains(account.getKeycloakUserId());
        assertThat(captured.noticesTo(OLD_EMAIL)).extracting(CapturedMessages.Notice::notice).containsExactly(ContactNotice.CONTACT_CHANGED);
        assertThat(captured.noticesTo(NEW_EMAIL)).extracting(CapturedMessages.Notice::notice).containsExactly(ContactNotice.CONTACT_CHANGED);
        AccountEvent event = db.template.findAll(AccountEvent.class).collectList().block().stream()
                .filter(e -> "CONTACT_CHANGED".equals(e.getKind())).findFirst().orElseThrow();
        assertThat(event.getData().toString()).doesNotContain(OLD_EMAIL).doesNotContain(NEW_EMAIL);
        assertThat(event.getData()).containsKeys("oldMasked", "newMasked");
    }

    @Test
    @DisplayName("change: the released contact is quarantined; another account cannot claim it until the period passes")
    void quarantineIsHonoured() {
        String accountId = account(ContactType.EMAIL, OLD_EMAIL);
        ProofRecord target = proof(ContactType.EMAIL, NEW_EMAIL);
        process.start(change(accountId, "c-" + accountId, target)).block();
        process.authorise(accountId).block();
        process.acceptNew(accountId, target.proofId()).block();
        process.await(accountId, Duration.ofMinutes(5)).block();
        String other = account(ContactType.WHATSAPP, PHONE);

        CLOCK.advance(Duration.ofDays(29));
        assertRefused(() -> run(add(other, proof(ContactType.EMAIL, OLD_EMAIL))), ErrorCode.CONTACT_ALREADY_CLAIMED);
        assertThat(active(other)).hasSize(1);

        CLOCK.advance(Duration.ofDays(2));
        run(add(other, proof(ContactType.EMAIL, OLD_EMAIL)));
        assertThat(active(other)).hasSize(2);
        // the account that released it may take it back at any time
    }

    @Test
    @DisplayName("change: a lost claim aborts with CONTACT_ALREADY_CLAIMED, the old contact stays, the marker is cleared, Keycloak is untouched")
    void lostClaimChangesNothing() {
        String accountId = account(ContactType.EMAIL, OLD_EMAIL);
        account(ContactType.EMAIL, NEW_EMAIL);
        ProofRecord target = proof(ContactType.EMAIL, NEW_EMAIL);
        process.start(change(accountId, "c-" + accountId, target)).block();
        process.authorise(accountId).block();
        process.acceptNew(accountId, target.proofId()).block();

        assertRefused(() -> process.await(accountId, Duration.ofMinutes(5)).block(), ErrorCode.CONTACT_ALREADY_CLAIMED);

        assertThat(active(accountId)).extracting(Contact::getId).containsExactly("c-" + accountId);
        assertThat(user(accountId).getPendingKind()).isNull();
        assertThat(keycloak.emails).isEmpty();
        assertThat(keycloak.sessionsEnded).isEmpty();
    }

    @Test
    @DisplayName("change: Keycloak down mid-change - the old contact stays the only live one until it returns, then the change completes")
    void keycloakDownDuringTheChange() {
        String accountId = account(ContactType.EMAIL, OLD_EMAIL);
        ProofRecord target = proof(ContactType.EMAIL, NEW_EMAIL);
        process.start(change(accountId, "c-" + accountId, target)).block();
        process.authorise(accountId).block();
        keycloak.down = true;
        process.acceptNew(accountId, target.proofId()).block();

        env.sleep(Duration.ofMinutes(2));

        assertThat(process.await(accountId, Duration.ofMillis(10)).block()).as("still finishing").isEmpty();
        Contact old = contacts(accountId).stream().filter(c -> c.getId().equals("c-" + accountId)).findFirst().orElseThrow();
        assertThat(old.getReleasedAt()).as("not released until Keycloak holds the new email").isNull();
        assertThat(old.isPrimary()).isTrue();
        assertThat(user(accountId).getPendingKind()).isEqualTo(PendingKind.CHANGING);

        keycloak.down = false;
        env.sleep(Duration.ofMinutes(3));

        assertThat(process.await(accountId, Duration.ofMinutes(1)).block().orElseThrow().status()).isEqualTo("COMPLETED");
        assertThat(active(accountId)).extracting(Contact::getValueHash).containsExactly(target.contactKey());
        assertThat(keycloak.emails.get(user(accountId).getKeycloakUserId())).isEqualTo(NEW_EMAIL);
    }

    @Test
    @DisplayName("change: a worker that dies right after the claim and again before the commit converges to one new contact")
    void crashBetweenStepsConverges() {
        String accountId = account(ContactType.EMAIL, OLD_EMAIL);
        ProofRecord target = proof(ContactType.EMAIL, NEW_EMAIL);
        activities.failSync.set(2);
        activities.failCommit.set(2);
        process.start(change(accountId, "c-" + accountId, target)).block();
        process.authorise(accountId).block();
        process.acceptNew(accountId, target.proofId()).block();

        assertThat(process.await(accountId, Duration.ofMinutes(10)).block().orElseThrow().status()).isEqualTo("COMPLETED");

        assertThat(contacts(accountId)).as("one old (released), one new").hasSize(2);
        assertThat(active(accountId)).extracting(Contact::getValueHash).containsExactly(target.contactKey());
        assertThat(activities.claims.get()).as("claimed once, not repeated").isEqualTo(1);
        assertThat(db.count(AccountFixtures.OUTBOX)).as("one outbox row however often the commit ran").isEqualTo(1);
        assertThat(db.template.findAll(AccountEvent.class).collectList().block().stream().filter(e -> "CONTACT_CHANGED".equals(e.getKind())))
                .hasSize(1);
    }

    @Test
    @DisplayName("change: a change nobody confirms expires after 48 hours with OTP_EXPIRED, and the account is as it was")
    void expiresAfterFortyEightHours() {
        String accountId = account(ContactType.EMAIL, OLD_EMAIL);
        process.start(change(accountId, "c-" + accountId, proof(ContactType.EMAIL, NEW_EMAIL))).block();
        process.authorise(accountId).block();

        env.sleep(Duration.ofHours(47));
        assertThat(process.status(accountId).block()).as("still open at 47 hours").isPresent();
        env.sleep(Duration.ofHours(2));

        assertRefused(() -> process.await(accountId, Duration.ofMinutes(1)).block(), ErrorCode.OTP_EXPIRED);
        assertThat(user(accountId).getPendingKind()).isNull();
        assertThat(active(accountId)).hasSize(1);
        assertThat(process.status(accountId).block()).isEmpty();
    }

    @Test
    @DisplayName("change: five wrong codes abort the change with OTP_ATTEMPTS_EXHAUSTED and clear the marker")
    void attemptsRunOut() {
        String accountId = account(ContactType.EMAIL, OLD_EMAIL);
        process.start(change(accountId, "c-" + accountId, proof(ContactType.EMAIL, NEW_EMAIL))).block();
        for (int i = 0; i < 4; i++) {
            process.failedAttempt(accountId).block();
        }
        assertThat(process.status(accountId).block().orElseThrow().attemptsRemaining()).isEqualTo(1);
        process.failedAttempt(accountId).block();

        assertRefused(() -> process.await(accountId, Duration.ofMinutes(1)).block(), ErrorCode.OTP_ATTEMPTS_EXHAUSTED);
        assertThat(user(accountId).getPendingKind()).isNull();
        assertThat(active(accountId)).hasSize(1);
    }

    @Test
    @DisplayName("change: a second start while one is open is CONTACT_CHANGE_IN_PROGRESS; a cancelled one frees the account")
    void oneOpenChangePerAccount() {
        String accountId = account(ContactType.EMAIL, OLD_EMAIL);
        process.start(change(accountId, "c-" + accountId, proof(ContactType.EMAIL, NEW_EMAIL))).block();

        assertRefused(() -> process.start(change(accountId, "c-" + accountId, proof(ContactType.EMAIL, "third@example.com"))).block(),
                ErrorCode.CONTACT_CHANGE_IN_PROGRESS);

        process.cancel(accountId).block();
        assertThat(process.await(accountId, Duration.ofMinutes(1)).block().orElseThrow().status()).isEqualTo("CANCELLED");
        assertThat(user(accountId).getPendingKind()).isNull();
        process.start(change(accountId, "c-" + accountId, proof(ContactType.EMAIL, "third@example.com"))).block();
        assertThat(process.status(accountId).block()).isPresent();
    }

    @Test
    @DisplayName("an account that is not ACTIVE cannot open a change: ACCOUNT_NOT_ACTIVE and no marker")
    void inactiveAccountRefused() {
        String accountId = account(ContactType.EMAIL, OLD_EMAIL);
        db.template.updateFirst(Query.query(Criteria.where("_id").is(accountId)),
                new org.springframework.data.mongodb.core.query.Update().set("status", AccountState.SUSPENDED), User.class).block();

        assertRefused(() -> run(add(accountId, proof(ContactType.WHATSAPP, PHONE))), ErrorCode.ACCOUNT_NOT_ACTIVE);
        assertThat(user(accountId).getPendingKind()).isNull();
    }

    // ---------------------------------------------------------------------------------- remove and primary

    @Test
    @DisplayName("remove: the last verified contact is refused with LAST_VERIFIED_CONTACT and the marker is cleared")
    void lastVerifiedContactStays() {
        String accountId = account(ContactType.EMAIL, OLD_EMAIL);

        assertRefused(() -> run(simple(accountId, ContactChangeKind.REMOVE, "c-" + accountId)), ErrorCode.LAST_VERIFIED_CONTACT);

        assertThat(active(accountId)).hasSize(1);
        assertThat(user(accountId).getPendingKind()).isNull();
    }

    @Test
    @DisplayName("remove: removing the primary of two hands the primary to the other, clears a removed email from Keycloak and ends sessions")
    void removePrimary() {
        String accountId = account(ContactType.EMAIL, OLD_EMAIL);
        run(add(accountId, proof(ContactType.WHATSAPP, PHONE)));

        run(simple(accountId, ContactChangeKind.REMOVE, "c-" + accountId));

        List<Contact> now = active(accountId);
        assertThat(now).hasSize(1);
        assertThat(now.get(0).getType()).isEqualTo(ContactType.WHATSAPP);
        assertThat(now.get(0).isPrimary()).isTrue();
        assertThat(user(accountId).getPrimaryContactId()).isEqualTo(now.get(0).getId());
        assertThat(user(accountId).isEmailVerified()).isFalse();
        assertThat(keycloak.emails).as("the removed email left Keycloak").doesNotContainKey(user(accountId).getKeycloakUserId());
        assertThat(keycloak.sessionsEnded).contains(user(accountId).getKeycloakUserId());
    }

    @Test
    @DisplayName("primary: switching among verified contacts leaves exactly one primary; a contact that is not the account's is CONTACT_UNKNOWN")
    void switchPrimary() {
        String accountId = account(ContactType.WHATSAPP, PHONE);
        run(add(accountId, proof(ContactType.EMAIL, NEW_EMAIL)));
        Contact email = active(accountId).stream().filter(c -> c.getType() == ContactType.EMAIL).findFirst().orElseThrow();

        run(simple(accountId, ContactChangeKind.PRIMARY, email.getId()));

        assertThat(active(accountId).stream().filter(Contact::isPrimary)).singleElement()
                .satisfies(c -> assertThat(c.getId()).isEqualTo(email.getId()));
        assertThat(user(accountId).getPrimaryContactId()).isEqualTo(email.getId());
        assertThat(keycloak.sessionsEnded).as("a primary switch does not sign anyone out").isEmpty();
        assertRefused(() -> run(simple(accountId, ContactChangeKind.PRIMARY, "somebody-elses-contact")), ErrorCode.CONTACT_UNKNOWN);
        assertThat(user(accountId).getPendingKind()).isNull();
    }

    // ---------------------------------------------------------------------------------- replay and ids

    @Test
    @DisplayName("PLT-015 R3 · a finished change's history replays against the implementation")
    void theHistoryReplays() throws Exception {
        String accountId = account(ContactType.EMAIL, OLD_EMAIL);
        ProofRecord target = proof(ContactType.EMAIL, NEW_EMAIL);
        activities.failSync.set(1);
        process.start(change(accountId, "c-" + accountId, target)).block();
        process.authorise(accountId).block();
        process.acceptNew(accountId, target.proofId()).block();
        process.await(accountId, Duration.ofMinutes(5)).block();

        String history = env.getWorkflowExecutionHistory(
                WorkflowExecution.newBuilder().setWorkflowId(WorkflowIds.contactChange(accountId)).build()).toJson(true);
        WorkflowReplayer.replayWorkflowExecution(history, ContactChangeWorkflowImpl.class);
    }

    @Test
    @DisplayName("the execution is addressed and tagged by the opaque account id: no id, search attribute or history holds a contact or a code")
    void idsCarryNoContact() {
        String accountId = account(ContactType.EMAIL, OLD_EMAIL);
        ProofRecord target = proof(ContactType.EMAIL, NEW_EMAIL);
        process.start(change(accountId, "c-" + accountId, target)).block();
        process.authorise(accountId).block();
        process.acceptNew(accountId, target.proofId()).block();
        process.await(accountId, Duration.ofMinutes(5)).block();

        String workflowId = WorkflowIds.contactChange(accountId);
        assertThat(workflowId).doesNotContain("@");
        String history = env.getWorkflowExecutionHistory(WorkflowExecution.newBuilder().setWorkflowId(workflowId).build()).toJson(true);
        assertThat(history).doesNotContain(OLD_EMAIL).doesNotContain(NEW_EMAIL).doesNotContain("old.person").doesNotContain("new.person");
    }

    /** The real activities, with crashes that can be scheduled at chosen steps. */
    static final class FaultyActivities implements ContactChangeActivities {
        private final ContactChangeActivities real;
        final AtomicInteger claims = new AtomicInteger();
        final AtomicInteger failSync = new AtomicInteger();
        final AtomicInteger failCommit = new AtomicInteger();

        FaultyActivities(ContactChangeActivities real) {
            this.real = real;
        }

        private static void die(AtomicInteger counter, String where) {
            if (counter.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0) {
                throw new IllegalStateException("worker crashed " + where);
            }
        }

        @Override
        public void begin(String accountId) {
            real.begin(accountId);
        }

        @Override
        public String claimContact(Claim claim) {
            claims.incrementAndGet();
            return real.claimContact(claim);
        }

        @Override
        public void syncKeycloak(String accountId, String excludeContactId) {
            die(failSync, "after the claim");
            real.syncKeycloak(accountId, excludeContactId);
        }

        @Override
        public void commit(Commit commit) {
            die(failCommit, "before the commit");
            real.commit(commit);
        }

        @Override
        public void endSessions(String accountId) {
            real.endSessions(accountId);
        }

        @Override
        public void clearMarker(String accountId) {
            real.clearMarker(accountId);
        }

        @Override
        public void notifyContacts(Notice notice) {
            real.notifyContacts(notice);
        }
    }
}
