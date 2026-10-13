package com.pml.identity.ownership;

import com.pml.identity.account.AccountFixtures;
import com.pml.identity.account.AccountProvisioning;
import com.pml.identity.account.FakeKeycloak;
import com.pml.identity.account.ProofRecord;
import com.pml.identity.auth.delivery.DeliveryChannel;
import com.pml.identity.auth.delivery.DeliveryOrchestrator;
import com.pml.identity.domain.enums.ContactType;
import com.pml.identity.domain.enums.TransferStatus;
import com.pml.identity.domain.model.OwnershipTransferRequest;
import com.pml.identity.domain.model.User;
import com.pml.identity.repository.ContactRepository;
import com.pml.identity.repository.UserRepository;
import com.pml.identity.service.OwnershipConfirmationCodes;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.RedisNode;
import com.pml.shared.testing.TestClock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A nominee whose account was opened with a sign-in code has no number typed into their profile; the
 * verified contacts they proved are the only place their number or email is. The ownership
 * confirmation code is sent there.
 */
@Tag("L2")
@Tag("ET-IDN-001")
@DisplayName("the ownership confirmation code goes to the verified contact of an account opened with a sign-in code")
class OwnershipCodeForOtpAccountTest {

    private static final Instant NOW = Instant.parse("2026-10-04T10:00:00Z");
    private static final Clock CLOCK = TestClock.frozenAt(NOW);
    private static final String PHONE = "+260971234567";

    private static AccountFixtures db;
    private static LettuceConnectionFactory factory;
    private static ReactiveStringRedisTemplate redis;
    private static UserRepository users;
    private static ContactRepository contacts;

    private final DeliveryOrchestrator delivery = mock(DeliveryOrchestrator.class);
    private OwnershipConfirmationCodes codes;

    @BeforeAll
    static void connect() {
        db = new AccountFixtures(MongoReplicaSet.connectionString(), "identity_ownership_otp_account", CLOCK);
        ReactiveMongoRepositoryFactory repositories = new ReactiveMongoRepositoryFactory(db.template);
        users = repositories.getRepository(UserRepository.class);
        contacts = repositories.getRepository(ContactRepository.class);
        factory = new LettuceConnectionFactory(RedisNode.host(), RedisNode.port());
        factory.afterPropertiesSet();
        redis = new ReactiveStringRedisTemplate(factory);
    }

    @AfterAll
    static void disconnect() {
        factory.destroy();
        db.close();
    }

    @BeforeEach
    void reset() {
        db.reset();
        codes = new OwnershipConfirmationCodes(redis, users, contacts, AccountFixtures.CRYPTO, delivery, CLOCK);
        when(delivery.deliver(any(), anyString(), anyString(), any())).thenReturn(Mono.just(DeliveryChannel.WHATSAPP));
    }

    /** An ACTIVE account created exactly as a first sign-in creates it: no phone or email field on the document. */
    private String signInAccount(ContactType type, String value) {
        AccountProvisioning provisioning = new AccountProvisioning(db.template, db.transaction, db.outbox, db.proofs,
                new FakeKeycloak(), AccountFixtures.CRYPTO, CLOCK);
        String proofId = "proof-" + UUID.randomUUID();
        ProofRecord proof = db.proofFor(type, value, proofId);
        String accountId = provisioning.claim(proofId, proof.contactKey(), type).block().accountId();
        String keycloakUserId = provisioning.createKeycloakUser(accountId).block();
        provisioning.activate(accountId, keycloakUserId, "web", null, List.of()).block();
        User stored = db.template.findById(accountId, User.class).block();
        assertThat(stored.getPhoneNumber()).as("the document carries no number").isNull();
        assertThat(stored.getEmail()).as("the document carries no email").isNull();
        return accountId;
    }

    private static OwnershipTransferRequest transferTo(String nomineeId) {
        return OwnershipTransferRequest.builder().id("transfer-" + UUID.randomUUID()).newOwnerId(nomineeId)
                .status(TransferStatus.PENDING).expiresAt(NOW.plus(Duration.ofDays(3))).build();
    }

    @Test
    @DisplayName("the code is delivered on WhatsApp to the number the nominee verified at sign-in, and confirms the transfer once")
    void codeGoesToTheVerifiedWhatsappNumber() {
        String nominee = signInAccount(ContactType.WHATSAPP, PHONE);
        OwnershipTransferRequest transfer = transferTo(nominee);

        codes.issue(transfer, nominee).block();

        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(delivery).deliver(eq(ContactType.WHATSAPP), eq(PHONE), code.capture(), isNull());
        codes.verify(transfer, nominee, code.getValue()).block();
        assertThatThrownBy(() -> codes.verify(transfer, nominee, code.getValue()).block())
                .isInstanceOfSatisfying(DomainRefusal.class, e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.OTP_EXPIRED));
    }

    @Test
    @DisplayName("a nominee who only verified an email gets the code by email")
    void emailIsUsedWhenThereIsNoWhatsapp() {
        String nominee = signInAccount(ContactType.EMAIL, "nominee@example.com");

        codes.issue(transferTo(nominee), nominee).block();

        verify(delivery).deliver(eq(ContactType.EMAIL), eq("nominee@example.com"), anyString(), isNull());
    }

    @Test
    @DisplayName("a nominee whose contacts were released has nowhere to send a code, and nothing is sent")
    void releasedContactsAreNotUsed() {
        String nominee = signInAccount(ContactType.WHATSAPP, PHONE);
        db.template.updateMulti(new org.springframework.data.mongodb.core.query.Query(),
                new org.springframework.data.mongodb.core.query.Update().set("releasedAt", NOW),
                com.pml.identity.domain.model.Contact.class).block();

        assertThatThrownBy(() -> codes.issue(transferTo(nominee), nominee).block())
                .isInstanceOfSatisfying(DomainRefusal.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.PHONE_NUMBER_INVALID));
        verify(delivery, never()).deliver(any(), anyString(), anyString(), any());
    }
}
