package com.pml.identity.web.graphql.mutation;

import com.pml.identity.event.domain.OrganizerApprovedEvent;
import com.pml.identity.web.graphql.dto.organizer.CreateOrganizerProfileInput;
import com.pml.identity.web.graphql.dto.organizer.UpdateOrganizerProfileInput;
import com.pml.identity.domain.model.OrganizerProfile;
import com.pml.identity.domain.valueobject.SocialLinks;
import com.pml.identity.infrastructure.keycloak.KeycloakService;
import com.pml.identity.service.OrganizerProfileService;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Mono;

/**
 * GraphQL Mutation Resolver for Organizer Profile operations.
 *
 * <p>This resolver uses OrganizerProfileService for all operations,
 * following the interface-based design pattern.</p>
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class OrganizerMutationResolver {

    private final OrganizerProfileService organizerProfileService;
    private final KeycloakService keycloakService;
    private final StreamBridge streamBridge;

    /**
     * Create organizer profile for the current user.
     * Creates a new profile in DRAFT status.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<OrganizerProfile> createOrganizerProfile(
            @InputArgument CreateOrganizerProfileInput input,
            @AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return Mono.error(new IllegalStateException("Authentication required"));
        }

        String userId = jwt.getSubject();
        log.info("Creating organizer profile for user: {}", userId);

        // Create the profile via service, then update with input data
        return organizerProfileService.applyToBeOrganizer(userId)
                .flatMap(profile -> {
                    // Build updated profile data from input
                    OrganizerProfile updateData = OrganizerProfile.builder()
                            .companyName(input.companyName())
                            .companyDescription(input.companyDescription())
                            .website(input.website())
                            .businessPhone(input.businessPhone())
                            .businessEmail(input.businessEmail())
                            .businessAddress(input.businessAddress())
                            .city(input.city())
                            .province(input.province())
                            .build();

                    return organizerProfileService.updateProfile(profile.getId(), updateData);
                })
                .doOnSuccess(p -> log.info("Organizer profile created for user: {}", userId));
    }

    /**
     * Update organizer profile for the current user.
     */
    @DgsMutation
    @PreAuthorize("hasRole('ORGANIZER')")
    public Mono<OrganizerProfile> updateOrganizerProfile(
            @InputArgument UpdateOrganizerProfileInput input,
            @AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return Mono.error(new IllegalStateException("Authentication required"));
        }

        String userId = jwt.getSubject();
        log.info("Updating organizer profile for user: {}", userId);

        return organizerProfileService.findByUserId(userId)
                .switchIfEmpty(Mono.error(new IllegalStateException("Organizer profile not found")))
                .flatMap(profile -> {
                    // Build update data from input
                    OrganizerProfile updateData = buildUpdateDataFromInput(input);
                    return organizerProfileService.updateProfile(profile.getId(), updateData);
                });
    }

    /**
     * Submit organizer profile for admin review.
     */
    @DgsMutation
    @PreAuthorize("hasRole('ORGANIZER')")
    public Mono<OrganizerProfile> submitOrganizerProfileForReview(
            @AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return Mono.error(new IllegalStateException("Authentication required"));
        }

        String userId = jwt.getSubject();
        log.info("Submitting organizer profile for review: user={}", userId);

        return organizerProfileService.findByUserId(userId)
                .switchIfEmpty(Mono.error(new IllegalStateException("Organizer profile not found")))
                .flatMap(profile -> organizerProfileService.submitForReview(profile.getId()));
    }

    /**
     * Approve organizer application (admin only).
     * This also triggers Organization creation via the service.
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<OrganizerProfile> approveOrganizer(
            @InputArgument String profileId,
            @AuthenticationPrincipal Jwt jwt) {
        String adminId = jwt != null ? jwt.getSubject() : "system";
        log.info("Admin {} approving organizer profile: {}", adminId, profileId);

        return organizerProfileService.approve(profileId, adminId)
                .flatMap(profile ->
                        // Add ORGANIZER role in Keycloak
                        keycloakService.addRoleToUser(profile.getUserId(), "ORGANIZER")
                                .thenReturn(profile)
                )
                .doOnSuccess(profile -> publishOrganizerApprovedEvent(profile, adminId));
    }

    /**
     * Approve organizer by user ID (legacy endpoint - admin only).
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<OrganizerProfile> approveOrganizerByUserId(
            @InputArgument String userId,
            @AuthenticationPrincipal Jwt jwt) {
        String adminId = jwt != null ? jwt.getSubject() : "system";
        log.info("Admin {} approving organizer for user: {}", adminId, userId);

        return organizerProfileService.findByUserId(userId)
                .switchIfEmpty(Mono.error(new IllegalStateException("Organizer profile not found")))
                .flatMap(profile -> organizerProfileService.approve(profile.getId(), adminId))
                .flatMap(profile ->
                        keycloakService.addRoleToUser(profile.getUserId(), "ORGANIZER")
                                .thenReturn(profile)
                )
                .doOnSuccess(profile -> publishOrganizerApprovedEvent(profile, adminId));
    }

    /**
     * Request changes from organizer (admin only).
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<OrganizerProfile> requestOrganizerChanges(
            @InputArgument String profileId,
            @InputArgument String reason,
            @AuthenticationPrincipal Jwt jwt) {
        String adminId = jwt != null ? jwt.getSubject() : "system";
        log.info("Admin {} requesting changes for organizer profile: {} - reason: {}", adminId, profileId, reason);

        if (reason == null || reason.isBlank()) {
            return Mono.error(new IllegalArgumentException("Reason is required"));
        }

        return organizerProfileService.requestChanges(profileId, reason, adminId);
    }

    /**
     * Reject organizer application (admin only).
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<OrganizerProfile> rejectOrganizer(
            @InputArgument String profileId,
            @InputArgument String reason,
            @AuthenticationPrincipal Jwt jwt) {
        String adminId = jwt != null ? jwt.getSubject() : "system";
        log.info("Admin {} rejecting organizer profile: {} - reason: {}", adminId, profileId, reason);

        if (reason == null || reason.isBlank()) {
            return Mono.error(new IllegalArgumentException("Rejection reason is required"));
        }

        return organizerProfileService.reject(profileId, reason, adminId);
    }

    /**
     * Reject organizer by user ID (legacy endpoint - admin only).
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<OrganizerProfile> rejectOrganizerByUserId(
            @InputArgument String userId,
            @InputArgument String reason,
            @AuthenticationPrincipal Jwt jwt) {
        String adminId = jwt != null ? jwt.getSubject() : "system";
        log.info("Admin {} rejecting organizer for user: {} - reason: {}", adminId, userId, reason);

        if (reason == null || reason.isBlank()) {
            return Mono.error(new IllegalArgumentException("Rejection reason is required"));
        }

        return organizerProfileService.findByUserId(userId)
                .switchIfEmpty(Mono.error(new IllegalStateException("Organizer profile not found")))
                .flatMap(profile -> organizerProfileService.reject(profile.getId(), reason, adminId));
    }

    /**
     * Suspend organizer account (admin only).
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<OrganizerProfile> suspendOrganizer(
            @InputArgument String profileId,
            @InputArgument String reason,
            @AuthenticationPrincipal Jwt jwt) {
        String adminId = jwt != null ? jwt.getSubject() : "system";
        log.info("Admin {} suspending organizer profile: {} - reason: {}", adminId, profileId, reason);

        if (reason == null || reason.isBlank()) {
            return Mono.error(new IllegalArgumentException("Suspension reason is required"));
        }

        return organizerProfileService.suspend(profileId, reason, adminId)
                .flatMap(profile ->
                        keycloakService.removeRoleFromUser(profile.getUserId(), "ORGANIZER")
                                .thenReturn(profile)
                );
    }

    /**
     * Suspend organizer by user ID (legacy endpoint - admin only).
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<OrganizerProfile> suspendOrganizerByUserId(
            @InputArgument String userId,
            @InputArgument String reason,
            @AuthenticationPrincipal Jwt jwt) {
        String adminId = jwt != null ? jwt.getSubject() : "system";
        log.info("Admin {} suspending organizer for user: {} - reason: {}", adminId, userId, reason);

        if (reason == null || reason.isBlank()) {
            return Mono.error(new IllegalArgumentException("Suspension reason is required"));
        }

        return organizerProfileService.findByUserId(userId)
                .switchIfEmpty(Mono.error(new IllegalStateException("Organizer profile not found")))
                .flatMap(profile -> organizerProfileService.suspend(profile.getId(), reason, adminId))
                .flatMap(profile ->
                        keycloakService.removeRoleFromUser(profile.getUserId(), "ORGANIZER")
                                .thenReturn(profile)
                );
    }

    /**
     * Reactivate suspended organizer (admin only).
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<OrganizerProfile> reactivateOrganizer(
            @InputArgument String profileId,
            @AuthenticationPrincipal Jwt jwt) {
        String adminId = jwt != null ? jwt.getSubject() : "system";
        log.info("Admin {} reactivating organizer profile: {}", adminId, profileId);

        return organizerProfileService.unsuspend(profileId, adminId)
                .flatMap(profile ->
                        keycloakService.addRoleToUser(profile.getUserId(), "ORGANIZER")
                                .thenReturn(profile)
                )
                .doOnSuccess(profile -> publishOrganizerApprovedEvent(profile, adminId));
    }

    /**
     * Reactivate suspended organizer by user ID (legacy endpoint - admin only).
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<OrganizerProfile> reactivateOrganizerByUserId(
            @InputArgument String userId,
            @AuthenticationPrincipal Jwt jwt) {
        String adminId = jwt != null ? jwt.getSubject() : "system";
        log.info("Admin {} reactivating organizer for user: {}", adminId, userId);

        return organizerProfileService.findByUserId(userId)
                .switchIfEmpty(Mono.error(new IllegalStateException("Organizer profile not found")))
                .flatMap(profile -> organizerProfileService.unsuspend(profile.getId(), adminId))
                .flatMap(profile ->
                        keycloakService.addRoleToUser(profile.getUserId(), "ORGANIZER")
                                .thenReturn(profile)
                )
                .doOnSuccess(profile -> publishOrganizerApprovedEvent(profile, adminId));
    }

    /**
     * Verify organizer's business details (admin only).
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<OrganizerProfile> verifyOrganizerBusiness(
            @InputArgument String profileId,
            @AuthenticationPrincipal Jwt jwt) {
        String adminId = jwt != null ? jwt.getSubject() : "system";
        log.info("Admin {} verifying business for profile: {}", adminId, profileId);

        return organizerProfileService.verifyBusiness(profileId, adminId);
    }

    /**
     * Verify organizer's documents (admin only).
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<OrganizerProfile> verifyOrganizerDocuments(
            @InputArgument String profileId,
            @AuthenticationPrincipal Jwt jwt) {
        String adminId = jwt != null ? jwt.getSubject() : "system";
        log.info("Admin {} verifying documents for profile: {}", adminId, profileId);

        return organizerProfileService.verifyDocuments(profileId, adminId);
    }

    /**
     * Verify organizer's bank account (admin only).
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<OrganizerProfile> verifyOrganizerBankAccount(
            @InputArgument String profileId,
            @AuthenticationPrincipal Jwt jwt) {
        String adminId = jwt != null ? jwt.getSubject() : "system";
        log.info("Admin {} verifying bank account for profile: {}", adminId, profileId);

        return organizerProfileService.verifyBankAccount(profileId, adminId);
    }

    /**
     * Delete organizer profile (owner only, only for DRAFT/REJECTED).
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<Boolean> deleteOrganizerProfile(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return Mono.error(new IllegalStateException("Authentication required"));
        }

        String userId = jwt.getSubject();
        log.info("User {} deleting their organizer profile", userId);

        return organizerProfileService.findByUserId(userId)
                .switchIfEmpty(Mono.error(new IllegalStateException("Organizer profile not found")))
                .flatMap(profile -> organizerProfileService.delete(profile.getId()))
                .thenReturn(true);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helper Methods
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Build OrganizerProfile update data from input DTO.
     */
    private OrganizerProfile buildUpdateDataFromInput(UpdateOrganizerProfileInput input) {
        OrganizerProfile.OrganizerProfileBuilder builder = OrganizerProfile.builder();

        if (input.companyName() != null) {
            builder.companyName(input.companyName());
        }
        if (input.companyDescription() != null) {
            builder.companyDescription(input.companyDescription());
        }
        if (input.website() != null) {
            builder.website(input.website());
        }
        if (input.socialLinks() != null) {
            SocialLinks links = new SocialLinks();
            links.setFacebook(input.socialLinks().facebook());
            links.setTwitter(input.socialLinks().twitter());
            links.setInstagram(input.socialLinks().instagram());
            links.setLinkedin(input.socialLinks().linkedin());
            links.setYoutube(input.socialLinks().youtube());
            builder.socialLinks(links);
        }
        if (input.taxId() != null) {
            builder.taxId(input.taxId());
        }
        if (input.businessRegistrationNumber() != null) {
            builder.businessRegistrationNumber(input.businessRegistrationNumber());
        }
        if (input.businessPhone() != null) {
            builder.businessPhone(input.businessPhone());
        }
        if (input.businessEmail() != null) {
            builder.businessEmail(input.businessEmail());
        }
        if (input.businessAddress() != null) {
            builder.businessAddress(input.businessAddress());
        }
        if (input.city() != null) {
            builder.city(input.city());
        }
        if (input.province() != null) {
            builder.province(input.province());
        }

        return builder.build();
    }

    /**
     * Publish OrganizerApprovedEvent to Azure Service Bus.
     */
    private void publishOrganizerApprovedEvent(OrganizerProfile profile, String approvedBy) {
        try {
            OrganizerApprovedEvent event = new OrganizerApprovedEvent(
                    profile.getId(),
                    profile.getUserId(),
                    profile.getCompanyName(),
                    profile.getCompanyName(),
                    approvedBy
            );

            boolean sent = streamBridge.send("userOutput-out-0", event);
            if (sent) {
                log.info("Published OrganizerApprovedEvent for organizer: {}", profile.getId());
            } else {
                log.warn("Failed to publish OrganizerApprovedEvent for organizer: {}", profile.getId());
            }
        } catch (Exception e) {
            // Don't fail approval if event publishing fails
            log.error("Error publishing OrganizerApprovedEvent for organizer {}: {}", profile.getId(), e.getMessage());
        }
    }
}
