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
import com.pml.identity.infrastructure.keycloak.KeycloakService;
import com.pml.identity.service.OwnershipTransferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Ownership Transfer Service Implementation
 *
 * Manages organization ownership transfer workflow with:
 * - 2FA verification requirement
 * - Expiration handling
 * - Member role updates
 * - Keycloak group synchronization
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OwnershipTransferServiceImpl implements OwnershipTransferService {

    private final OwnershipTransferRepository transferRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;
    private final KeycloakService keycloakService;
    private final StreamBridge streamBridge;

    private static final int TRANSFER_EXPIRY_HOURS = 72;

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
            String organizationId,
            String currentOwnerId,
            String newOwnerId,
            String reason) {
        log.info("Initiating ownership transfer for organization {} from {} to {}",
                organizationId, currentOwnerId, newOwnerId);

        // Validate organization exists and current user is owner
        return organizationRepository.findById(organizationId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Organization not found: " + organizationId)))
                .flatMap(org -> {
                    if (!org.getOwnerId().equals(currentOwnerId)) {
                        return Mono.error(new IllegalStateException("Only the owner can initiate a transfer"));
                    }

                    // Check for existing pending transfer
                    return hasPendingTransfer(organizationId)
                            .flatMap(hasPending -> {
                                if (hasPending) {
                                    return Mono.error(new IllegalStateException(
                                            "Organization already has a pending transfer request"));
                                }

                                // Validate new owner is an ADMIN member
                                return memberRepository.findByUserIdAndOrganizationId(newOwnerId, organizationId)
                                        .switchIfEmpty(Mono.error(new IllegalArgumentException(
                                                "New owner must be an existing member of the organization")))
                                        .flatMap(newOwnerMember -> {
                                            if (newOwnerMember.getRole() != OrganizationRole.ADMIN) {
                                                return Mono.error(new IllegalArgumentException(
                                                        "New owner must be an ADMIN to receive ownership"));
                                            }

                                            OwnershipTransferRequest transfer = OwnershipTransferRequest.builder()
                                                    .organizationId(organizationId)
                                                    .currentOwnerId(currentOwnerId)
                                                    .newOwnerId(newOwnerId)
                                                    .reason(reason)
                                                    .transferToken(generateToken())
                                                    .status(TransferStatus.PENDING)
                                                    .expiresAt(Instant.now().plus(TRANSFER_EXPIRY_HOURS, ChronoUnit.HOURS))
                                                    .build();

                                            return transferRepository.save(transfer)
                                                    .doOnSuccess(saved -> {
                                                        log.info("Transfer request created: {} for organization: {}",
                                                                saved.getId(), organizationId);
                                                        sendTransferNotification(saved, org);
                                                    });
                                        });
                            });
                });
    }

    @Override
    public Mono<OwnershipTransferRequest> cancel(String organizationId, String currentOwnerId) {
        log.info("Cancelling ownership transfer for organization: {}", organizationId);

        return findPendingByOrganization(organizationId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("No pending transfer found")))
                .flatMap(transfer -> {
                    if (!transfer.getCurrentOwnerId().equals(currentOwnerId)) {
                        return Mono.error(new IllegalStateException("Only the current owner can cancel the transfer"));
                    }

                    transfer.setStatus(TransferStatus.CANCELLED);
                    transfer.setCancelledAt(Instant.now());

                    return transferRepository.save(transfer)
                            .doOnSuccess(cancelled -> log.info("Transfer cancelled: {}", cancelled.getId()));
                });
    }

    @Override
    public Mono<OwnershipTransferRequest> accept(
            String transferToken,
            String newOwnerId,
            String confirmationCode) {
        log.info("Accepting ownership transfer for user: {}", newOwnerId);

        return transferRepository.findByTransferToken(transferToken)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Invalid transfer token")))
                .flatMap(transfer -> {
                    // Validate transfer state
                    if (!transfer.isValid()) {
                        if (transfer.isExpired()) {
                            transfer.setStatus(TransferStatus.EXPIRED);
                            return transferRepository.save(transfer)
                                    .then(Mono.error(new IllegalStateException("Transfer request has expired")));
                        }
                        return Mono.error(new IllegalStateException("Transfer request is no longer valid"));
                    }

                    // Validate new owner matches
                    if (!transfer.getNewOwnerId().equals(newOwnerId)) {
                        return Mono.error(new IllegalStateException("User is not the designated new owner"));
                    }

                    // Verify 2FA code
                    return keycloakService.verify2FACode(newOwnerId, confirmationCode)
                            .flatMap(isValid -> {
                                if (!isValid) {
                                    return Mono.error(new IllegalArgumentException("Invalid confirmation code"));
                                }

                                // Execute ownership transfer
                                return executeTransfer(transfer);
                            });
                });
    }

    @Override
    public Mono<OwnershipTransferRequest> decline(String transferToken, String newOwnerId) {
        log.info("Declining ownership transfer");

        return transferRepository.findByTransferToken(transferToken)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Invalid transfer token")))
                .flatMap(transfer -> {
                    if (transfer.getStatus() != TransferStatus.PENDING) {
                        return Mono.error(new IllegalStateException("Transfer request is no longer pending"));
                    }

                    if (!transfer.getNewOwnerId().equals(newOwnerId)) {
                        return Mono.error(new IllegalStateException("User is not the designated new owner"));
                    }

                    transfer.setStatus(TransferStatus.CANCELLED);
                    transfer.setCancelledAt(Instant.now());

                    return transferRepository.save(transfer)
                            .doOnSuccess(declined -> log.info("Transfer declined: {}", declined.getId()));
                });
    }

    @Override
    public Mono<Long> expireOldTransfers() {
        log.info("Expiring old ownership transfers");

        return transferRepository.findByStatusAndExpiresAtBefore(TransferStatus.PENDING, Instant.now())
                .flatMap(transfer -> {
                    transfer.setStatus(TransferStatus.EXPIRED);
                    return transferRepository.save(transfer);
                })
                .count()
                .doOnSuccess(count -> log.info("Expired {} ownership transfer requests", count));
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    private String generateToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private Mono<OwnershipTransferRequest> executeTransfer(OwnershipTransferRequest transfer) {
        String organizationId = transfer.getOrganizationId();
        String currentOwnerId = transfer.getCurrentOwnerId();
        String newOwnerId = transfer.getNewOwnerId();

        log.info("Executing ownership transfer for organization: {}", organizationId);

        return organizationRepository.findById(organizationId)
                .flatMap(org -> {
                    // Get both members
                    return Mono.zip(
                            memberRepository.findByUserIdAndOrganizationId(currentOwnerId, organizationId),
                            memberRepository.findByUserIdAndOrganizationId(newOwnerId, organizationId)
                    ).flatMap(tuple -> {
                        OrganizationMember currentOwnerMember = tuple.getT1();
                        OrganizationMember newOwnerMember = tuple.getT2();

                        // Update roles
                        currentOwnerMember.setRole(OrganizationRole.ADMIN);
                        newOwnerMember.setRole(OrganizationRole.OWNER);

                        // Update organization owner
                        org.setOwnerId(newOwnerId);

                        // Save all changes
                        return memberRepository.save(currentOwnerMember)
                                .then(memberRepository.save(newOwnerMember))
                                .then(organizationRepository.save(org))
                                .then(updateKeycloakGroups(org.getSlug(), currentOwnerId, newOwnerId))
                                .then(Mono.defer(() -> {
                                    transfer.setStatus(TransferStatus.COMPLETED);
                                    transfer.setCompletedAt(Instant.now());
                                    return transferRepository.save(transfer);
                                }))
                                .doOnSuccess(completed -> {
                                    log.info("Ownership transfer completed: {} - {} is now owner",
                                            completed.getId(), newOwnerId);
                                    sendTransferCompletedNotification(completed, org);
                                });
                    });
                });
    }

    private Mono<Void> updateKeycloakGroups(String orgSlug, String currentOwnerId, String newOwnerId) {
        return keycloakService.removeUserFromOrganizationGroup(currentOwnerId, orgSlug, "owners")
                .then(keycloakService.addUserToOrganizationGroup(currentOwnerId, orgSlug, "admins"))
                .then(keycloakService.removeUserFromOrganizationGroup(newOwnerId, orgSlug, "admins"))
                .then(keycloakService.addUserToOrganizationGroup(newOwnerId, orgSlug, "owners"))
                .onErrorResume(e -> {
                    log.warn("Failed to update Keycloak groups during ownership transfer: {}", e.getMessage());
                    return Mono.empty();
                });
    }

    private void sendTransferNotification(OwnershipTransferRequest transfer, Organization organization) {
        try {
            record OwnershipTransferInitiatedEvent(
                    String transferId,
                    String organizationId,
                    String organizationName,
                    String currentOwnerId,
                    String newOwnerId,
                    String transferToken,
                    Instant expiresAt
            ) {}

            OwnershipTransferInitiatedEvent event = new OwnershipTransferInitiatedEvent(
                    transfer.getId(),
                    organization.getId(),
                    organization.getName(),
                    transfer.getCurrentOwnerId(),
                    transfer.getNewOwnerId(),
                    transfer.getTransferToken(),
                    transfer.getExpiresAt()
            );

            streamBridge.send("notificationOutput-out-0", event);
            log.info("Sent transfer notification for organization: {}", organization.getId());
        } catch (Exception e) {
            log.error("Failed to send transfer notification: {}", e.getMessage());
        }
    }

    private void sendTransferCompletedNotification(OwnershipTransferRequest transfer, Organization organization) {
        try {
            record OwnershipTransferCompletedEvent(
                    String transferId,
                    String organizationId,
                    String organizationName,
                    String previousOwnerId,
                    String newOwnerId
            ) {}

            OwnershipTransferCompletedEvent event = new OwnershipTransferCompletedEvent(
                    transfer.getId(),
                    organization.getId(),
                    organization.getName(),
                    transfer.getCurrentOwnerId(),
                    transfer.getNewOwnerId()
            );

            streamBridge.send("notificationOutput-out-0", event);
            log.info("Sent transfer completed notification for organization: {}", organization.getId());
        } catch (Exception e) {
            log.error("Failed to send transfer completed notification: {}", e.getMessage());
        }
    }
}
