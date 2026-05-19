package com.pml.identity.service.impl;

import com.pml.identity.domain.enums.InvitationStatus;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.domain.model.OrganizationMember;
import com.pml.identity.domain.model.TeamInvitation;
import com.pml.identity.domain.valueobject.OrganizationRole;
import com.pml.identity.repository.OrganizationRepository;
import com.pml.identity.repository.TeamInvitationRepository;
import com.pml.identity.service.EventAccessService;
import com.pml.identity.service.OrganizationMemberService;
import com.pml.identity.service.TeamInvitationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * Team Invitation Service Implementation
 *
 * Manages team invitation workflow including:
 * - Creating and sending invitations
 * - Invitation acceptance/decline
 * - Expiration handling
 * - Notification sending
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TeamInvitationServiceImpl implements TeamInvitationService {

    private final TeamInvitationRepository invitationRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberService memberService;
    private final EventAccessService eventAccessService;
    private final StreamBridge streamBridge;

    private static final int INVITATION_EXPIRY_DAYS = 7;

    // ========================================================================
    // READ OPERATIONS
    // ========================================================================

    @Override
    public Mono<TeamInvitation> findById(String id) {
        return invitationRepository.findById(id);
    }

    @Override
    public Mono<TeamInvitation> findByToken(String invitationToken) {
        return invitationRepository.findByInvitationToken(invitationToken);
    }

    @Override
    public Flux<TeamInvitation> findPendingByOrganization(String organizationId, Pageable pageable) {
        return invitationRepository.findByOrganizationIdAndStatus(organizationId, InvitationStatus.PENDING, pageable);
    }

    @Override
    public Flux<TeamInvitation> findPendingByOrganization(String organizationId) {
        return invitationRepository.findByOrganizationIdAndStatus(organizationId, InvitationStatus.PENDING);
    }

    @Override
    public Flux<TeamInvitation> findByOrganization(String organizationId, Pageable pageable) {
        return invitationRepository.findByOrganizationId(organizationId, pageable);
    }

    @Override
    public Flux<TeamInvitation> findPendingByEmail(String email) {
        return invitationRepository.findByEmailAndStatus(email, InvitationStatus.PENDING);
    }

    @Override
    public Mono<Boolean> hasPendingInvitation(String email, String organizationId) {
        return invitationRepository.existsByEmailAndOrganizationIdAndStatus(
                email, organizationId, InvitationStatus.PENDING);
    }

    @Override
    public Mono<Long> countPendingByOrganization(String organizationId) {
        return invitationRepository.countByOrganizationIdAndStatus(organizationId, InvitationStatus.PENDING);
    }

    // ========================================================================
    // WRITE OPERATIONS
    // ========================================================================

    @Override
    public Mono<TeamInvitation> invite(
            String organizationId,
            String email,
            String phoneNumber,
            String inviteeName,
            OrganizationRole role,
            String message,
            List<TeamInvitation.EventAccessInput> eventAccessGrants,
            String invitedById) {
        log.info("Creating invitation for email: {} to organization: {} with role: {}",
                email, organizationId, role);

        // Validate role - cannot invite as OWNER
        if (role == OrganizationRole.OWNER) {
            return Mono.error(new IllegalArgumentException("Cannot invite someone as OWNER"));
        }

        // Check if organization exists
        return organizationRepository.findById(organizationId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Organization not found: " + organizationId)))
                .flatMap(org -> {
                    // Check for existing pending invitation
                    return hasPendingInvitation(email, organizationId)
                            .flatMap(hasPending -> {
                                if (hasPending) {
                                    return Mono.error(new IllegalStateException(
                                            "User already has a pending invitation to this organization"));
                                }

                                TeamInvitation invitation = TeamInvitation.builder()
                                        .email(email.toLowerCase().trim())
                                        .phoneNumber(phoneNumber)
                                        .inviteeName(inviteeName)
                                        .organizationId(organizationId)
                                        .proposedRole(role)
                                        .eventAccessGrants(eventAccessGrants)
                                        .invitedById(invitedById)
                                        .message(message)
                                        .invitationToken(generateToken())
                                        .expiresAt(Instant.now().plus(INVITATION_EXPIRY_DAYS, ChronoUnit.DAYS))
                                        .status(InvitationStatus.PENDING)
                                        .build();

                                return invitationRepository.save(invitation)
                                        .doOnSuccess(saved -> {
                                            log.info("Invitation created: {} for organization: {}",
                                                    saved.getId(), organizationId);
                                            sendInvitationNotification(saved, org);
                                        });
                            });
                });
    }

    @Override
    public Flux<TeamInvitation> bulkInvite(
            String organizationId,
            List<InviteRequest> invites,
            String invitedById) {
        log.info("Creating bulk invitations for organization: {} - Count: {}",
                organizationId, invites.size());

        return Flux.fromIterable(invites)
                .flatMap(request -> invite(
                        organizationId,
                        request.email(),
                        request.phoneNumber(),
                        request.inviteeName(),
                        request.role(),
                        request.message(),
                        request.eventAccessGrants(),
                        invitedById)
                        .onErrorResume(e -> {
                            log.warn("Failed to create invitation for {}: {}",
                                    request.email(), e.getMessage());
                            return Mono.empty();
                        }));
    }

    @Override
    public Mono<TeamInvitation> resend(String invitationId) {
        log.info("Resending invitation: {}", invitationId);

        return invitationRepository.findById(invitationId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Invitation not found: " + invitationId)))
                .flatMap(invitation -> {
                    if (invitation.getStatus() != InvitationStatus.PENDING) {
                        return Mono.error(new IllegalStateException(
                                "Can only resend pending invitations"));
                    }

                    // Generate new token and extend expiry
                    invitation.setInvitationToken(generateToken());
                    invitation.setExpiresAt(Instant.now().plus(INVITATION_EXPIRY_DAYS, ChronoUnit.DAYS));

                    return invitationRepository.save(invitation)
                            .flatMap(saved -> organizationRepository.findById(saved.getOrganizationId())
                                    .doOnSuccess(org -> sendInvitationNotification(saved, org))
                                    .thenReturn(saved));
                });
    }

    @Override
    public Mono<TeamInvitation> revoke(String invitationId) {
        log.info("Revoking invitation: {}", invitationId);

        return invitationRepository.findById(invitationId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Invitation not found: " + invitationId)))
                .flatMap(invitation -> {
                    if (invitation.getStatus() != InvitationStatus.PENDING) {
                        return Mono.error(new IllegalStateException(
                                "Can only revoke pending invitations"));
                    }

                    invitation.setStatus(InvitationStatus.REVOKED);
                    return invitationRepository.save(invitation)
                            .doOnSuccess(revoked -> log.info("Invitation revoked: {}", revoked.getId()));
                });
    }

    @Override
    public Mono<OrganizationMember> accept(String invitationToken, String userId) {
        log.info("Accepting invitation with token for user: {}", userId);

        return invitationRepository.findByInvitationToken(invitationToken)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Invalid invitation token")))
                .flatMap(invitation -> {
                    // Validate invitation state
                    if (!invitation.isValid()) {
                        if (invitation.isExpired()) {
                            invitation.setStatus(InvitationStatus.EXPIRED);
                            return invitationRepository.save(invitation)
                                    .then(Mono.error(new IllegalStateException("Invitation has expired")));
                        }
                        return Mono.error(new IllegalStateException("Invitation is no longer valid"));
                    }

                    // Create organization member
                    return memberService.createFromInvitation(
                                    invitation.getOrganizationId(),
                                    userId,
                                    invitation.getProposedRole(),
                                    invitation.getInvitedById())
                            .flatMap(member -> {
                                // Create event access grants if specified
                                if (invitation.getEventAccessGrants() != null && !invitation.getEventAccessGrants().isEmpty()) {
                                    return createEventAccessGrants(invitation, userId)
                                            .then(Mono.just(member));
                                }
                                return Mono.just(member);
                            })
                            .flatMap(member -> {
                                // Update invitation status
                                invitation.setStatus(InvitationStatus.ACCEPTED);
                                invitation.setAcceptedAt(Instant.now());
                                return invitationRepository.save(invitation)
                                        .doOnSuccess(accepted -> {
                                            log.info("Invitation accepted: {} by user: {}",
                                                    accepted.getId(), userId);
                                            sendAcceptanceNotification(accepted);
                                        })
                                        .thenReturn(member);
                            });
                });
    }

    @Override
    public Mono<TeamInvitation> decline(String invitationToken) {
        log.info("Declining invitation with token");

        return invitationRepository.findByInvitationToken(invitationToken)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Invalid invitation token")))
                .flatMap(invitation -> {
                    if (invitation.getStatus() != InvitationStatus.PENDING) {
                        return Mono.error(new IllegalStateException("Invitation is no longer pending"));
                    }

                    invitation.setStatus(InvitationStatus.DECLINED);
                    invitation.setDeclinedAt(Instant.now());
                    return invitationRepository.save(invitation)
                            .doOnSuccess(declined -> log.info("Invitation declined: {}", declined.getId()));
                });
    }

    @Override
    public Mono<Long> expireOldInvitations() {
        log.info("Expiring old invitations");

        return invitationRepository.findByStatusAndExpiresAtBefore(InvitationStatus.PENDING, Instant.now())
                .flatMap(invitation -> {
                    invitation.setStatus(InvitationStatus.EXPIRED);
                    return invitationRepository.save(invitation);
                })
                .count()
                .doOnSuccess(count -> log.info("Expired {} invitations", count));
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    private String generateToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private Mono<Void> createEventAccessGrants(TeamInvitation invitation, String userId) {
        return Flux.fromIterable(invitation.getEventAccessGrants())
                .flatMap(grant -> eventAccessService.grant(
                        grant.getEventId(),
                        invitation.getOrganizationId(),
                        userId,
                        grant.getRole(),
                        new HashSet<>(),
                        "Granted via team invitation",
                        grant.getExpiresAt(),
                        invitation.getInvitedById()))
                .then();
    }

    private void sendInvitationNotification(TeamInvitation invitation, Organization organization) {
        try {
            record TeamInvitationSentEvent(
                    String invitationId,
                    String email,
                    String organizationId,
                    String organizationName,
                    String role,
                    String invitationToken,
                    Instant expiresAt
            ) {}

            TeamInvitationSentEvent event = new TeamInvitationSentEvent(
                    invitation.getId(),
                    invitation.getEmail(),
                    organization.getId(),
                    organization.getName(),
                    invitation.getProposedRole().name(),
                    invitation.getInvitationToken(),
                    invitation.getExpiresAt()
            );

            streamBridge.send("notificationOutput-out-0", event);
            log.info("Sent invitation notification for: {}", invitation.getEmail());
        } catch (Exception e) {
            log.error("Failed to send invitation notification: {}", e.getMessage());
        }
    }

    private void sendAcceptanceNotification(TeamInvitation invitation) {
        try {
            record TeamInvitationAcceptedEvent(
                    String invitationId,
                    String email,
                    String organizationId,
                    String invitedById
            ) {}

            TeamInvitationAcceptedEvent event = new TeamInvitationAcceptedEvent(
                    invitation.getId(),
                    invitation.getEmail(),
                    invitation.getOrganizationId(),
                    invitation.getInvitedById()
            );

            streamBridge.send("notificationOutput-out-0", event);
            log.info("Sent acceptance notification for invitation: {}", invitation.getId());
        } catch (Exception e) {
            log.error("Failed to send acceptance notification: {}", e.getMessage());
        }
    }
}
