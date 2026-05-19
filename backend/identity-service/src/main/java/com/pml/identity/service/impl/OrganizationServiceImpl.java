package com.pml.identity.service.impl;

import com.pml.identity.domain.enums.MemberStatus;
import com.pml.identity.domain.enums.OrganizationStatus;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.domain.model.OrganizationMember;
import com.pml.identity.domain.model.OrganizerProfile;
import com.pml.identity.domain.valueobject.OrganizationRole;
import com.pml.identity.domain.valueobject.OrganizationSettings;
import com.pml.identity.domain.valueobject.OrganizationStats;
import com.pml.identity.repository.OrganizationMemberRepository;
import com.pml.identity.repository.OrganizationRepository;
import com.pml.identity.repository.OrganizerProfileRepository;
import com.pml.identity.infrastructure.keycloak.KeycloakService;
import com.pml.identity.service.OrganizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.text.Normalizer;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Organization Service Implementation
 *
 * Manages organization lifecycle including creation, updates, and status management.
 * Organizations are created automatically when an OrganizerProfile is approved.
 *
 * KEYCLOAK INTEGRATION:
 * ====================
 * When an organization is created, this service:
 * 1. Creates a Keycloak group structure: /organizations/{slug}
 * 2. Creates sub-groups for each role: /owners, /admins, /managers, /marketers, /contributors
 * 3. Adds the owner to the /owners sub-group
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationServiceImpl implements OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;
    private final OrganizerProfileRepository organizerProfileRepository;
    private final KeycloakService keycloakService;
    private final StreamBridge streamBridge;

    private static final Pattern NONLATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]");

    // ========================================================================
    // READ OPERATIONS
    // ========================================================================

    @Override
    public Mono<Organization> findById(String id) {
        return organizationRepository.findById(id);
    }

    @Override
    public Mono<Organization> findBySlug(String slug) {
        return organizationRepository.findBySlug(slug);
    }

    @Override
    public Mono<Organization> findByOwnerId(String ownerId) {
        return organizationRepository.findByOwnerId(ownerId);
    }

    @Override
    public Mono<Organization> findByOrganizerProfileId(String organizerProfileId) {
        return organizationRepository.findByOrganizerProfileId(organizerProfileId);
    }

    @Override
    public Flux<Organization> findAll(Pageable pageable) {
        return organizationRepository.findAll()
                .skip(pageable.getOffset())
                .take(pageable.getPageSize());
    }

    @Override
    public Flux<Organization> findAll() {
        return organizationRepository.findAll();
    }

    @Override
    public Flux<Organization> findByStatus(OrganizationStatus status, Pageable pageable) {
        return organizationRepository.findByStatus(status, pageable);
    }

    @Override
    public Flux<Organization> searchByName(String query, OrganizationStatus status, Pageable pageable) {
        if (status != null) {
            return organizationRepository.searchByNameAndStatus(query, status, pageable);
        }
        return organizationRepository.searchByName(query)
                .skip(pageable.getOffset())
                .take(pageable.getPageSize());
    }

    @Override
    public Mono<Long> countByStatus(OrganizationStatus status) {
        return organizationRepository.countByStatus(status);
    }

    // ========================================================================
    // WRITE OPERATIONS
    // ========================================================================

    @Override
    public Mono<Organization> createFromOrganizerProfile(String organizerProfileId, String ownerId) {
        log.info("Creating organization from organizer profile: {} with owner: {}", organizerProfileId, ownerId);

        return organizerProfileRepository.findById(organizerProfileId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Organizer profile not found: " + organizerProfileId)))
                .flatMap(profile -> generateUniqueSlug(profile.getCompanyName())
                        .flatMap(slug -> {
                            // Note: logoUrl/bannerUrl are set later when organizer customizes their public profile
                            Organization organization = Organization.builder()
                                    .name(profile.getCompanyName())
                                    .slug(slug)
                                    .description(profile.getTagline())
                                    .organizerProfileId(organizerProfileId)
                                    .ownerId(ownerId)
                                    .status(OrganizationStatus.ACTIVE)
                                    .verified(profile.isDocumentsVerified() && profile.isBankVerified())
                                    .settings(new OrganizationSettings())
                                    .stats(new OrganizationStats())
                                    .build();

                            return organizationRepository.save(organization)
                                    .flatMap(savedOrg -> createKeycloakGroupStructure(savedOrg)
                                            .then(createOwnerMember(savedOrg.getId(), ownerId))
                                            .thenReturn(savedOrg))
                                    .doOnSuccess(org -> {
                                        log.info("Organization created successfully: {} ({})", org.getName(), org.getId());
                                        publishOrganizationCreatedEvent(org);
                                    });
                        }));
    }

    /**
     * Create Keycloak group structure for the organization
     */
    private Mono<Void> createKeycloakGroupStructure(Organization organization) {
        return keycloakService.createOrganizationGroups(organization.getSlug())
                .flatMap(groupId -> {
                    organization.setKeycloakGroupId(groupId);
                    return organizationRepository.save(organization);
                })
                .then()
                .onErrorResume(e -> {
                    log.warn("Failed to create Keycloak group structure for organization {}: {}",
                            organization.getSlug(), e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * Create the owner member record
     */
    private Mono<OrganizationMember> createOwnerMember(String organizationId, String ownerId) {
        OrganizationMember owner = OrganizationMember.builder()
                .userId(ownerId)
                .organizationId(organizationId)
                .role(OrganizationRole.OWNER)
                .status(MemberStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build();

        return memberRepository.save(owner);
    }

    @Override
    public Mono<Organization> update(String id, String name, String description, String logoUrl, String bannerUrl) {
        return organizationRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Organization not found: " + id)))
                .flatMap(org -> {
                    if (name != null && !name.isBlank()) {
                        org.setName(name);
                    }
                    if (description != null) {
                        org.setDescription(description);
                    }
                    if (logoUrl != null) {
                        org.setLogoUrl(logoUrl);
                    }
                    if (bannerUrl != null) {
                        org.setBannerUrl(bannerUrl);
                    }
                    return organizationRepository.save(org);
                });
    }

    @Override
    public Mono<Organization> updateSettings(String id, OrganizationSettings settings) {
        return organizationRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Organization not found: " + id)))
                .flatMap(org -> {
                    org.setSettings(settings);
                    return organizationRepository.save(org);
                });
    }

    @Override
    public Mono<Organization> updateStatus(String id, OrganizationStatus status) {
        return organizationRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Organization not found: " + id)))
                .flatMap(org -> {
                    org.setStatus(status);
                    return organizationRepository.save(org);
                });
    }

    @Override
    public Mono<Organization> suspend(String id, String reason) {
        log.info("Suspending organization: {} - Reason: {}", id, reason);
        return organizationRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Organization not found: " + id)))
                .flatMap(org -> {
                    org.setStatus(OrganizationStatus.SUSPENDED);
                    return organizationRepository.save(org)
                            .doOnSuccess(suspended -> publishOrganizationSuspendedEvent(suspended, reason));
                });
    }

    @Override
    public Mono<Organization> unsuspend(String id) {
        log.info("Unsuspending organization: {}", id);
        return organizationRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Organization not found: " + id)))
                .flatMap(org -> {
                    if (org.getStatus() != OrganizationStatus.SUSPENDED) {
                        return Mono.error(new IllegalStateException("Organization is not suspended"));
                    }
                    org.setStatus(OrganizationStatus.ACTIVE);
                    return organizationRepository.save(org);
                });
    }

    @Override
    public Mono<Organization> transferOwnership(String id, String newOwnerId) {
        log.info("Transferring ownership of organization {} to user {}", id, newOwnerId);
        return organizationRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Organization not found: " + id)))
                .flatMap(org -> {
                    String previousOwnerId = org.getOwnerId();
                    org.setOwnerId(newOwnerId);
                    return organizationRepository.save(org)
                            .doOnSuccess(updated -> log.info("Ownership transferred from {} to {} for organization {}",
                                    previousOwnerId, newOwnerId, id));
                });
    }

    @Override
    public Mono<Organization> updateStats(String id, int memberCount, int totalEvents, int totalTicketsSold) {
        return organizationRepository.findById(id)
                .flatMap(org -> {
                    OrganizationStats stats = org.getStats();
                    if (stats == null) {
                        stats = new OrganizationStats();
                    }
                    stats.setMemberCount(memberCount);
                    stats.setTotalEvents(totalEvents);
                    stats.setTotalTicketsSold(totalTicketsSold);
                    org.setStats(stats);
                    return organizationRepository.save(org);
                });
    }

    // ========================================================================
    // UTILITY OPERATIONS
    // ========================================================================

    @Override
    public Mono<String> generateUniqueSlug(String name) {
        String baseSlug = toSlug(name);
        return isSlugAvailable(baseSlug)
                .flatMap(available -> {
                    if (available) {
                        return Mono.just(baseSlug);
                    }
                    return findAvailableSlug(baseSlug, 1);
                });
    }

    private Mono<String> findAvailableSlug(String baseSlug, int suffix) {
        String candidateSlug = baseSlug + "-" + suffix;
        return isSlugAvailable(candidateSlug)
                .flatMap(available -> {
                    if (available) {
                        return Mono.just(candidateSlug);
                    }
                    if (suffix > 100) {
                        return Mono.just(baseSlug + "-" + System.currentTimeMillis());
                    }
                    return findAvailableSlug(baseSlug, suffix + 1);
                });
    }

    @Override
    public Mono<Boolean> isSlugAvailable(String slug) {
        return organizationRepository.existsBySlug(slug).map(exists -> !exists);
    }

    /**
     * Convert name to URL-friendly slug
     */
    private String toSlug(String input) {
        if (input == null || input.isBlank()) {
            return "organization";
        }
        String noWhitespace = WHITESPACE.matcher(input).replaceAll("-");
        String normalized = Normalizer.normalize(noWhitespace, Normalizer.Form.NFD);
        String slug = NONLATIN.matcher(normalized).replaceAll("");
        return slug.toLowerCase(Locale.ENGLISH).replaceAll("-+", "-").replaceAll("^-|-$", "");
    }

    // ========================================================================
    // EVENT PUBLISHING
    // ========================================================================

    private void publishOrganizationCreatedEvent(Organization organization) {
        try {
            // Event record for cross-service communication
            record OrganizationCreatedEvent(
                    String organizationId,
                    String name,
                    String slug,
                    String ownerId
            ) {}

            OrganizationCreatedEvent event = new OrganizationCreatedEvent(
                    organization.getId(),
                    organization.getName(),
                    organization.getSlug(),
                    organization.getOwnerId()
            );

            boolean sent = streamBridge.send("organizationOutput-out-0", event);
            if (sent) {
                log.info("Published OrganizationCreatedEvent for organization: {}", organization.getId());
            } else {
                log.warn("Failed to publish OrganizationCreatedEvent for organization: {}", organization.getId());
            }
        } catch (Exception e) {
            log.error("Error publishing OrganizationCreatedEvent for organization {}: {}",
                    organization.getId(), e.getMessage());
        }
    }

    private void publishOrganizationSuspendedEvent(Organization organization, String reason) {
        try {
            record OrganizationSuspendedEvent(
                    String organizationId,
                    String name,
                    String reason
            ) {}

            OrganizationSuspendedEvent event = new OrganizationSuspendedEvent(
                    organization.getId(),
                    organization.getName(),
                    reason
            );

            streamBridge.send("organizationOutput-out-0", event);
            log.info("Published OrganizationSuspendedEvent for organization: {}", organization.getId());
        } catch (Exception e) {
            log.error("Error publishing OrganizationSuspendedEvent: {}", e.getMessage());
        }
    }
}
