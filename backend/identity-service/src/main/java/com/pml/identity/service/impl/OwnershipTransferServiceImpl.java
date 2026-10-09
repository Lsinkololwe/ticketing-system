package com.pml.identity.service.impl;

import com.pml.identity.domain.enums.MemberStatus;
import com.pml.identity.domain.enums.TransferStatus;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.domain.model.OrganizationMember;
import com.pml.identity.domain.model.OwnershipTransferRequest;
import com.pml.identity.domain.valueobject.OrganizationRole;
import com.pml.identity.repository.OrganizationMemberRepository;
import com.pml.identity.repository.OrganizationRepository;
import com.pml.identity.repository.OwnershipTransferRepository;
import com.pml.identity.service.OwnershipTransferService;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Ownership Transfer Service Implementation
 *
 * <p>The MongoDB half of a transfer. The process — the three-day expiry, the
 * Keycloak mirror, the notifications — is {@code OwnershipTransferWorkflow}; every write here is
 * one of its activities, and safe to run twice.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OwnershipTransferServiceImpl implements OwnershipTransferService {

    private final OwnershipTransferRepository transferRepository;

    /** The claim, the two role changes and the organization's ownerId are one transaction. */
    private final TransactionalOperator transactionalOperator;

    /** For the conditional writes; a repository save cannot express "only if still PENDING". */
    private final ReactiveMongoTemplate mongoTemplate;

    /** Every timestamp comes from here, never from the wall clock. */
    private final java.time.Clock clock;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;

    /**
     * How long a transfer stays open, from {@code identity.transfer.ttl}.
     *
     * <p>Three days rather than an hour: the nominee has to be reached, understand that they are
     * being handed a business, and complete a 2FA challenge. A window measured in hours turns an
     * ownership handshake into a race against a notification.
     */
    private static final java.time.Duration TRANSFER_TTL = java.time.Duration.ofDays(3);

    // ========================================================================
    // READ OPERATIONS
    // ========================================================================

    @Override
    public Mono<OwnershipTransferRequest> findById(String id) {
        return transferRepository.findById(id);
    }

    @Override
    public Mono<OwnershipTransferRequest> findByToken(String transferToken) {
        return transferRepository.findByTransferToken(transferToken);
    }

    @Override
    public Mono<OwnershipTransferRequest> findPendingByOrganization(String organizationId) {
        return transferRepository.findByOrganizationIdAndStatus(organizationId, TransferStatus.PENDING);
    }

    @Override
    public Flux<OwnershipTransferRequest> findByOrganization(String organizationId) {
        return transferRepository.findByOrganizationId(organizationId);
    }

    @Override
    public Flux<OwnershipTransferRequest> findPendingByNewOwner(String newOwnerId) {
        return transferRepository.findByNewOwnerIdAndStatus(newOwnerId, TransferStatus.PENDING);
    }

    @Override
    public Mono<Boolean> hasPendingTransfer(String organizationId) {
        return transferRepository.existsByOrganizationIdAndStatus(organizationId, TransferStatus.PENDING);
    }

    // ========================================================================
    // WRITE OPERATIONS
    // ========================================================================

    @Override
    public Mono<OwnershipTransferRequest> initiate(
            String transferId,
            String organizationId,
            String currentOwnerId,
            String newOwnerId,
            String reason) {
        return findById(transferId)
                .flatMap(existing -> existing.getOrganizationId().equals(organizationId)
                        && existing.getNewOwnerId().equals(newOwnerId)
                        ? Mono.just(existing)
                        : Mono.<OwnershipTransferRequest>error(new TranslatedRefusal(ErrorCode.TRANSFER_NOT_PENDING,
                                "transfer id " + transferId + " belongs to another nomination")))
                .switchIfEmpty(Mono.defer(() -> create(transferId, organizationId, currentOwnerId, newOwnerId, reason)));
    }

    private Mono<OwnershipTransferRequest> create(
            String transferId,
            String organizationId,
            String currentOwnerId,
            String newOwnerId,
            String reason) {
        log.info("Initiating ownership transfer {} for organization {} from {} to {}",
                transferId, organizationId, currentOwnerId, newOwnerId);

        return organizationRepository.findById(organizationId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Organization not found: " + organizationId)))
                .flatMap(org -> {
                    if (!org.getOwnerId().equals(currentOwnerId)) {
                        return Mono.error(new IllegalStateException("Only the owner can initiate a transfer"));
                    }

                    return hasPendingTransfer(organizationId)
                            .flatMap(hasPending -> {
                                if (hasPending) {
                                    return Mono.error(new IllegalStateException(
                                            "Organization already has a pending transfer request"));
                                }

                                return memberRepository.findByUserIdAndOrganizationId(newOwnerId, organizationId)
                                        .switchIfEmpty(Mono.error(new IllegalArgumentException(
                                                "New owner must be an existing member of the organization")))
                                        .flatMap(newOwnerMember -> {
                                            // The nominee must be an ACTIVE ADMIN, both halves. The role check
                                            // alone admits a nominee who is SUSPENDED or already
                                            // REMOVED — somebody the organization has deliberately
                                            // shut out, handed the business instead.
                                            if (!eligible(newOwnerMember)) {
                                                return Mono.error(ineligible(newOwnerId, newOwnerMember));
                                            }

                                            OwnershipTransferRequest transfer = OwnershipTransferRequest.builder()
                                                    .id(transferId)
                                                    .organizationId(organizationId)
                                                    .currentOwnerId(currentOwnerId)
                                                    .newOwnerId(newOwnerId)
                                                    .reason(reason)
                                                    .transferToken(generateToken())
                                                    .status(TransferStatus.PENDING)
                                                    .expiresAt(clock.instant().plus(TRANSFER_TTL))
                                                    .build();

                                            return transferRepository.save(transfer);
                                        });
                            });
                });
    }

    @Override
    public Mono<OwnershipTransferRequest> complete(String transferId) {
        return existing(transferId)
                .flatMap(transfer -> {
                    if (transfer.getStatus() == TransferStatus.COMPLETED) {
                        return Mono.just(transfer);
                    }
                    if (transfer.getStatus() != TransferStatus.PENDING) {
                        return Mono.error(notPending(transfer));
                    }
                    if (!transfer.isValid(clock.instant())) {
                        return Mono.error(new TranslatedRefusal(ErrorCode.TRANSFER_NOT_PENDING,
                                "transfer " + transferId + " expired at " + transfer.getExpiresAt()));
                    }
                    return executeTransfer(transfer);
                });
    }

    @Override
    public Mono<OwnershipTransferRequest> decline(String transferId, String newOwnerId) {
        return close(transferId, newOwnerId, true);
    }

    @Override
    public Mono<OwnershipTransferRequest> cancel(String transferId, String currentOwnerId) {
        return close(transferId, currentOwnerId, false);
    }

    @Override
    public Mono<OwnershipTransferRequest> expire(String transferId) {
        return mongoTemplate.updateFirst(
                        Query.query(Criteria.where("_id").is(transferId).and("status").is(TransferStatus.PENDING)),
                        Update.update("status", TransferStatus.EXPIRED),
                        OwnershipTransferRequest.class)
                .then(existing(transferId));
    }

    @Override
    public Mono<OwnershipTransferRequest> markMirrorPending(String transferId) {
        return existing(transferId)
                .flatMap(transfer -> mongoTemplate.updateMulti(
                                Query.query(Criteria.where("organizationId").is(transfer.getOrganizationId())
                                        .and("userId").in(List.of(transfer.getCurrentOwnerId(), transfer.getNewOwnerId()))),
                                Update.update("mirrorPending", true),
                                OrganizationMember.class)
                        .thenReturn(transfer));
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    private String generateToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private Mono<OwnershipTransferRequest> existing(String transferId) {
        return findById(transferId)
                .switchIfEmpty(Mono.error(() -> new TranslatedRefusal(ErrorCode.TRANSFER_NOT_PENDING,
                        "no transfer " + transferId)));
    }

    private static boolean eligible(OrganizationMember member) {
        return member.getRole() == OrganizationRole.ADMIN && member.getStatus() == MemberStatus.ACTIVE;
    }

    private static TranslatedRefusal ineligible(String userId, OrganizationMember member) {
        return new TranslatedRefusal(
                ErrorCode.TRANSFER_TARGET_INELIGIBLE,
                "nominee " + userId + " is " + member.getStatus() + " " + member.getRole()
                        + "; an ACTIVE ADMIN is required");
    }

    private static TranslatedRefusal notPending(OwnershipTransferRequest transfer) {
        return new TranslatedRefusal(ErrorCode.TRANSFER_NOT_PENDING,
                "transfer " + transfer.getId() + " is " + transfer.getStatus());
    }

    /** Decline or cancel: {@code PENDING → CANCELLED} by the party entitled to it. */
    private Mono<OwnershipTransferRequest> close(String transferId, String actorId, boolean byNominee) {
        return existing(transferId)
                .flatMap(transfer -> {
                    String party = byNominee ? transfer.getNewOwnerId() : transfer.getCurrentOwnerId();
                    if (!party.equals(actorId)) {
                        return Mono.error(new TranslatedRefusal(ErrorCode.ACTOR_NOT_PERMITTED,
                                actorId + " is not a party entitled to close transfer " + transferId));
                    }
                    if (transfer.getStatus() == TransferStatus.CANCELLED) {
                        return Mono.just(transfer);
                    }
                    return mongoTemplate.updateFirst(
                                    Query.query(Criteria.where("_id").is(transferId)
                                            .and("status").is(TransferStatus.PENDING)),
                                    Update.update("status", TransferStatus.CANCELLED)
                                            .set("cancelledAt", clock.instant()),
                                    OwnershipTransferRequest.class)
                            .then(existing(transferId))
                            .flatMap(current -> current.getStatus() == TransferStatus.CANCELLED
                                    ? Mono.just(current)
                                    : Mono.error(notPending(current)));
                });
    }

    /**
     * Moves the transfer {@code PENDING → COMPLETED}, if and only if it is still {@code PENDING}.
     *
     * @return {@code true} when this caller made the move and therefore owns the handover
     */
    private Mono<Boolean> claimPending(String transferId) {
        return mongoTemplate.updateFirst(
                        Query.query(Criteria.where("_id").is(transferId)
                                .and("status").is(TransferStatus.PENDING)),
                        Update.update("status", TransferStatus.COMPLETED)
                                .set("completedAt", clock.instant()),
                        OwnershipTransferRequest.class)
                .map(result -> result.getModifiedCount() == 1);
    }

    /**
     * The handover. The claim is inside the transaction, so a failure further down rolls the status
     * back to {@code PENDING} and a retried activity can claim it again, rather than finding it
     * spent with nobody promoted.
     */
    private Mono<OwnershipTransferRequest> executeTransfer(OwnershipTransferRequest transfer) {
        String organizationId = transfer.getOrganizationId();
        String currentOwnerId = transfer.getCurrentOwnerId();
        String newOwnerId = transfer.getNewOwnerId();

        log.info("Executing ownership transfer for organization: {}", organizationId);

        return claimPending(transfer.getId())
                .flatMap(claimed -> {
                    if (!claimed) {
                        return Mono.<Organization>error(new TranslatedRefusal(
                                ErrorCode.TRANSFER_NOT_PENDING,
                                "transfer " + transfer.getId() + " was resolved concurrently"));
                    }
                    return Mono.zip(
                                    organizationRepository.findById(organizationId),
                                    memberRepository.findByUserIdAndOrganizationId(currentOwnerId, organizationId),
                                    memberRepository.findByUserIdAndOrganizationId(newOwnerId, organizationId))
                            .switchIfEmpty(Mono.error(() -> new TranslatedRefusal(
                                    ErrorCode.TRANSFER_TARGET_INELIGIBLE,
                                    "a party to transfer " + transfer.getId() + " is no longer a member")))
                            .flatMap(parties -> {
                                Organization org = parties.getT1();
                                OrganizationMember currentOwnerMember = parties.getT2();
                                OrganizationMember newOwnerMember = parties.getT3();
                                if (!eligible(newOwnerMember)) {
                                    return Mono.error(ineligible(newOwnerId, newOwnerMember));
                                }

                                // Demote before promoting: the partial unique index on
                                // {organizationId} where role = OWNER holds one owner at a time, so
                                // promoting first would collide with the owner still in place.
                                currentOwnerMember.setRole(OrganizationRole.ADMIN);
                                newOwnerMember.setRole(OrganizationRole.OWNER);
                                org.setOwnerId(newOwnerId);

                                return memberRepository.save(currentOwnerMember)
                                        .then(memberRepository.save(newOwnerMember))
                                        .then(organizationRepository.save(org));
                            });
                })
                .as(transactionalOperator::transactional)
                .then(existing(transfer.getId()))
                .doOnSuccess(completed -> log.info("Ownership transfer completed: {} - {} is the owner",
                        completed.getId(), newOwnerId));
    }
}
