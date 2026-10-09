package com.pml.identity.service.impl;

import java.util.Map;
import com.pml.shared.event.EventType;
import com.pml.shared.event.EventEnvelopes;
import com.pml.shared.constants.OrganizationStatus;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.domain.valueobject.OrganizationSettings;
import com.pml.identity.repository.OrganizationRepository;
import com.pml.identity.service.OrganizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Organization Service Implementation
 *
 * Manages organization lifecycle including updates and status management.
 *
 * PROGRESSIVE ONBOARDING (Industry Standard):
 * ==========================================
 * Organizations are created LAZILY via OrganizationOnboardingService when a user
 * creates their first event. This class handles existing organization management.
 *
 * KEYCLOAK INTEGRATION:
 * ====================
 * When an organization is created, this service:
 * 1. Creates a Keycloak group structure: /organizations/{slug}
 * 2. Creates sub-groups for each role: /owners, /admins, /managers, /marketers, /contributors
 * 3. Adds the owner to the /owners sub-group
 *
 * @see OrganizationOnboardingService For lazy organization creation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationServiceImpl implements OrganizationService {

    private final OrganizationRepository organizationRepository;

    /** The injected platform clock, so every timestamp below is freezable. */
    private final java.time.Clock clock;

    /** Envelopes are staged here, inside the business transaction. */
    private final com.pml.shared.event.Outbox outbox;

    /** So the document and its envelope commit together, or neither does. */
    private final org.springframework.transaction.reactive.TransactionalOperator transaction;
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

    @Override
    public Flux<Organization> findByStatus(OrganizationStatus status) {
        return organizationRepository.findByStatus(status);
    }

    @Override
    public Flux<Organization> findInApprovalWorkflow() {
        // Find organizations in approval workflow statuses
        return organizationRepository.findByStatusIn(java.util.List.of(
                OrganizationStatus.DRAFT,
                OrganizationStatus.PENDING_REVIEW,
                OrganizationStatus.CHANGES_REQUESTED,
                OrganizationStatus.APPROVED,
                OrganizationStatus.REJECTED
        ));
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
                    org.setSuspensionReason(reason);
                    org.setSuspendedAt(clock.instant());
                    // The suspension and its message commit together.
                    //
                    // A publish after the commit leaves a window with no owner: the document is
                    // durable, the send can fail, and nothing records that the message is owed.
                    // Staging the envelope in the same transaction closes that window — a
                    // suspended organisation that no other service hears about keeps selling.
                    return transaction.transactional(
                            organizationRepository.save(org)
                                    .flatMap(suspended -> outbox.stage(EventEnvelopes.of(
                                                    EventType.IDENTITY_ORGANIZATION_SUSPENDED,
                                                    clock.instant(),
                                                    suspended.getId(),
                                                    Map.of("organizationId", suspended.getId(),
                                                            "reason", reason)))
                                            .thenReturn(suspended)));
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
                    org.setSuspensionReason(null);
                    org.setSuspendedAt(null);
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
                        return Mono.just(baseSlug + "-" + clock.millis());
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

    /*
     * A suspension is announced in exactly one way: suspend() stages
     * IDENTITY_ORGANIZATION_SUSPENDED into the outbox inside its own transaction, and OutboxDrain
     * publishes it, so the message is owed rather than attempted. There is deliberately no direct
     * send beside it — a second, lossy way to announce the same fact would read more naturally.
     */

}
