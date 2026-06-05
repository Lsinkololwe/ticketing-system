package com.pml.identity.service.impl;

import com.pml.identity.domain.enums.MemberStatus;
import com.pml.identity.domain.enums.OrganizationStatus;
import com.pml.identity.domain.enums.OrganizationType;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.domain.model.OrganizationMember;
import com.pml.identity.domain.model.User;
import com.pml.identity.domain.valueobject.BusinessAddress;
import com.pml.identity.domain.valueobject.OrganizationRole;
import com.pml.identity.domain.valueobject.SocialLinks;
import com.pml.identity.repository.OrganizationMemberRepository;
import com.pml.identity.repository.OrganizationRepository;
import com.pml.identity.repository.UserRepository;
import com.pml.identity.service.OrganizationOnboardingService;
import com.pml.identity.web.graphql.dto.organization.OrganizationApplicationInput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.text.Normalizer;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Implementation of OrganizationOnboardingService.
 *
 * Handles approval-based organization onboarding:
 * - User applies → Organization created (DRAFT)
 * - User fills details and submits → PENDING_REVIEW
 * - Admin approves/rejects → APPROVED/CHANGES_REQUESTED/REJECTED
 * - User can create draft events during approval process
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationOnboardingServiceImpl implements OrganizationOnboardingService {

    private static final Pattern NONLATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]");

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final UserRepository userRepository;

    // =========================================================================
    // USER OPERATIONS
    // =========================================================================

    @Override
    public Mono<Organization> applyToBeOrganizer(String userId, OrganizationApplicationInput input) {
        log.info("User {} applying to become organizer with name: {}", userId, input.name());

        return organizationRepository.findByOwnerId(userId)
                .flatMap(existing -> {
                    log.warn("User {} already has an organization: {}", userId, existing.getId());
                    return Mono.error(new IllegalStateException(
                            "You already have an organization. Use update instead."));
                })
                .switchIfEmpty(Mono.defer(() -> userRepository.findById(userId)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("User not found: " + userId)))
                        .flatMap(user -> createOrganizationFromInput(user, input))
                ))
                .cast(Organization.class);
    }

    @Override
    public Mono<Organization> updateApplication(String organizationId, OrganizationApplicationInput input) {
        log.debug("Updating organization application: {}", organizationId);

        return organizationRepository.findById(organizationId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Organization not found: " + organizationId)))
                .flatMap(org -> {
                    if (!org.canBeEdited()) {
                        return Mono.error(new IllegalStateException(
                                "Organization cannot be edited in status: " + org.getStatus()));
                    }

                    // Update fields from input
                    if (input.name() != null && !input.name().isBlank()) {
                        org.setName(input.name());
                    }
                    if (input.description() != null) {
                        org.setDescription(input.description());
                    }
                    if (input.tagline() != null) {
                        org.setTagline(input.tagline());
                    }
                    if (input.logoUrl() != null) {
                        org.setLogoUrl(input.logoUrl());
                    }
                    if (input.bannerUrl() != null) {
                        org.setBannerUrl(input.bannerUrl());
                    }
                    if (input.website() != null) {
                        org.setWebsite(input.website());
                    }
                    if (input.type() != null) {
                        org.setType(input.type());
                    }
                    if (input.businessPhone() != null) {
                        org.setBusinessPhone(input.businessPhone());
                    }
                    if (input.businessEmail() != null) {
                        org.setBusinessEmail(input.businessEmail());
                    }

                    // Update business address
                    if (input.city() != null || input.province() != null || input.country() != null) {
                        BusinessAddress address = org.getBusinessAddress();
                        if (address == null) {
                            address = new BusinessAddress();
                        }
                        if (input.city() != null) {
                            address.setCity(input.city());
                        }
                        if (input.province() != null) {
                            address.setProvince(input.province());
                        }
                        if (input.country() != null) {
                            address.setCountry(input.country());
                        }
                        org.setBusinessAddress(address);
                    }

                    // Update social links
                    if (input.socialLinks() != null) {
                        SocialLinks socialLinks = convertSocialLinks(input.socialLinks());
                        org.setSocialLinks(socialLinks);
                    }

                    org.setUpdatedAt(Instant.now());
                    return organizationRepository.save(org);
                })
                .doOnSuccess(org -> log.info("Updated organization application: {}", org.getId()));
    }

    @Override
    public Mono<Organization> submitForReview(String organizationId) {
        log.info("Submitting organization {} for review", organizationId);

        return organizationRepository.findById(organizationId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Organization not found: " + organizationId)))
                .flatMap(org -> {
                    if (!org.canSubmitForReview()) {
                        return Mono.error(new IllegalStateException(
                                "Organization cannot be submitted for review in status: " + org.getStatus()));
                    }

                    // Validate required fields
                    if (org.getName() == null || org.getName().isBlank()) {
                        return Mono.error(new IllegalStateException("Organization name is required"));
                    }
                    if (org.getBusinessEmail() == null || org.getBusinessEmail().isBlank()) {
                        return Mono.error(new IllegalStateException("Business email is required"));
                    }

                    org.setStatus(OrganizationStatus.PENDING_REVIEW);
                    org.setSubmittedAt(Instant.now());
                    org.setRejectionReason(null); // Clear any previous rejection reason
                    org.setUpdatedAt(Instant.now());

                    return organizationRepository.save(org);
                })
                .doOnSuccess(org -> log.info("Organization {} submitted for review", org.getId()));
    }

    @Override
    public Mono<Organization> getOrCreateOrganization(String userId) {
        log.debug("Getting or creating organization for user: {}", userId);

        return organizationRepository.findByOwnerId(userId)
                .switchIfEmpty(Mono.defer(() -> {
                    log.info("User {} has no organization, creating one", userId);
                    return userRepository.findById(userId)
                            .flatMap(this::createOrganization);
                }));
    }

    @Override
    public Mono<Organization> createOrganization(User user) {
        log.info("Creating organization for user: {} ({})", user.getId(), user.getEmail());

        String organizationName = generateOrganizationName(user);
        String baseSlug = toSlug(organizationName);

        return generateUniqueSlug(baseSlug)
                .flatMap(slug -> {
                    Organization organization = Organization.builder()
                            .name(organizationName)
                            .slug(slug)
                            .type(OrganizationType.INDIVIDUAL)
                            .ownerId(user.getId())
                            .businessEmail(user.getEmail())
                            .businessPhone(user.getPhoneNumber())
                            .status(OrganizationStatus.DRAFT) // Start in DRAFT
                            .createdAt(Instant.now())
                            .updatedAt(Instant.now())
                            .build();

                    return organizationRepository.save(organization)
                            .flatMap(savedOrg -> createOwnerMembership(savedOrg, user)
                                    .thenReturn(savedOrg))
                            .doOnSuccess(org -> log.info(
                                    "Created organization: {} (slug: {}, status: DRAFT) for user: {}",
                                    org.getId(), org.getSlug(), user.getId()));
                });
    }

    @Override
    public Mono<Organization> upgradeToBusinessOrganization(String organizationId, String businessName) {
        log.info("Upgrading organization {} to business: {}", organizationId, businessName);

        return organizationRepository.findById(organizationId)
                .flatMap(org -> {
                    if (org.getType() == OrganizationType.BUSINESS) {
                        log.debug("Organization {} is already a business", organizationId);
                        return Mono.just(org);
                    }

                    org.setType(OrganizationType.BUSINESS);
                    org.setName(businessName);
                    org.setUpdatedAt(Instant.now());

                    String newSlug = toSlug(businessName);
                    return generateUniqueSlug(newSlug)
                            .flatMap(slug -> {
                                org.setSlug(slug);
                                return organizationRepository.save(org);
                            })
                            .doOnSuccess(savedOrg -> log.info(
                                    "Upgraded organization {} to business: {} (slug: {})",
                                    savedOrg.getId(), savedOrg.getName(), savedOrg.getSlug()));
                });
    }

    @Override
    public Mono<Boolean> hasOrganization(String userId) {
        return organizationRepository.findByOwnerId(userId)
                .map(org -> true)
                .defaultIfEmpty(false);
    }

    @Override
    public Mono<Organization> findOrganizationByOwnerId(String userId) {
        return organizationRepository.findByOwnerId(userId);
    }

    // =========================================================================
    // ADMIN OPERATIONS
    // =========================================================================

    @Override
    public Mono<Organization> approve(String organizationId, String adminId) {
        log.info("Admin {} approving organization: {}", adminId, organizationId);

        return organizationRepository.findById(organizationId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Organization not found: " + organizationId)))
                .flatMap(org -> {
                    if (org.getStatus() != OrganizationStatus.PENDING_REVIEW) {
                        return Mono.error(new IllegalStateException(
                                "Only organizations in PENDING_REVIEW status can be approved. Current: " + org.getStatus()));
                    }

                    org.setStatus(OrganizationStatus.APPROVED);
                    org.setVerified(true);
                    org.setVerifiedAt(Instant.now());
                    org.setVerifiedBy(adminId);
                    org.setReviewedBy(adminId);
                    org.setReviewedAt(Instant.now());
                    org.setApprovedAt(Instant.now());
                    org.setRejectionReason(null);
                    org.setUpdatedAt(Instant.now());

                    return organizationRepository.save(org);
                })
                .doOnSuccess(org -> log.info("Organization {} approved by admin {}", org.getId(), adminId));
    }

    @Override
    public Mono<Organization> requestChanges(String organizationId, String reason, String adminId) {
        log.info("Admin {} requesting changes for organization {}: {}", adminId, organizationId, reason);

        return organizationRepository.findById(organizationId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Organization not found: " + organizationId)))
                .flatMap(org -> {
                    if (org.getStatus() != OrganizationStatus.PENDING_REVIEW) {
                        return Mono.error(new IllegalStateException(
                                "Only organizations in PENDING_REVIEW status can have changes requested. Current: " + org.getStatus()));
                    }

                    if (reason == null || reason.isBlank()) {
                        return Mono.error(new IllegalArgumentException("Reason for changes is required"));
                    }

                    org.setStatus(OrganizationStatus.CHANGES_REQUESTED);
                    org.setRejectionReason(reason);
                    org.setReviewedBy(adminId);
                    org.setReviewedAt(Instant.now());
                    org.setUpdatedAt(Instant.now());

                    return organizationRepository.save(org);
                })
                .doOnSuccess(org -> log.info("Changes requested for organization {} by admin {}", org.getId(), adminId));
    }

    @Override
    public Mono<Organization> reject(String organizationId, String reason, String adminId) {
        log.info("Admin {} rejecting organization {}: {}", adminId, organizationId, reason);

        return organizationRepository.findById(organizationId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Organization not found: " + organizationId)))
                .flatMap(org -> {
                    if (org.getStatus() != OrganizationStatus.PENDING_REVIEW) {
                        return Mono.error(new IllegalStateException(
                                "Only organizations in PENDING_REVIEW status can be rejected. Current: " + org.getStatus()));
                    }

                    if (reason == null || reason.isBlank()) {
                        return Mono.error(new IllegalArgumentException("Rejection reason is required"));
                    }

                    org.setStatus(OrganizationStatus.REJECTED);
                    org.setRejectionReason(reason);
                    org.setReviewedBy(adminId);
                    org.setReviewedAt(Instant.now());
                    org.setUpdatedAt(Instant.now());

                    return organizationRepository.save(org);
                })
                .doOnSuccess(org -> log.info("Organization {} rejected by admin {}", org.getId(), adminId));
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private Mono<Organization> createOrganizationFromInput(User user, OrganizationApplicationInput input) {
        String organizationName = input.name() != null && !input.name().isBlank()
                ? input.name()
                : generateOrganizationName(user);
        String baseSlug = toSlug(organizationName);

        return generateUniqueSlug(baseSlug)
                .flatMap(slug -> {
                    Organization.OrganizationBuilder builder = Organization.builder()
                            .name(organizationName)
                            .slug(slug)
                            .type(input.type() != null ? input.type() : OrganizationType.INDIVIDUAL)
                            .ownerId(user.getId())
                            .businessEmail(input.businessEmail() != null ? input.businessEmail() : user.getEmail())
                            .businessPhone(input.businessPhone() != null ? input.businessPhone() : user.getPhoneNumber())
                            .status(OrganizationStatus.DRAFT)
                            .createdAt(Instant.now())
                            .updatedAt(Instant.now());

                    // Set optional fields
                    if (input.description() != null) {
                        builder.description(input.description());
                    }
                    if (input.tagline() != null) {
                        builder.tagline(input.tagline());
                    }
                    if (input.logoUrl() != null) {
                        builder.logoUrl(input.logoUrl());
                    }
                    if (input.bannerUrl() != null) {
                        builder.bannerUrl(input.bannerUrl());
                    }
                    if (input.website() != null) {
                        builder.website(input.website());
                    }

                    // Set business address
                    if (input.city() != null || input.province() != null || input.country() != null) {
                        BusinessAddress address = new BusinessAddress();
                        address.setCity(input.city());
                        address.setProvince(input.province());
                        address.setCountry(input.country() != null ? input.country() : "Zambia");
                        builder.businessAddress(address);
                    }

                    // Set social links
                    if (input.socialLinks() != null) {
                        builder.socialLinks(convertSocialLinks(input.socialLinks()));
                    }

                    Organization organization = builder.build();

                    return organizationRepository.save(organization)
                            .flatMap(savedOrg -> createOwnerMembership(savedOrg, user)
                                    .thenReturn(savedOrg))
                            .doOnSuccess(org -> log.info(
                                    "Created organization: {} (slug: {}, status: DRAFT) for user: {}",
                                    org.getId(), org.getSlug(), user.getId()));
                });
    }

    private SocialLinks convertSocialLinks(OrganizationApplicationInput.SocialLinksInput input) {
        SocialLinks socialLinks = new SocialLinks();
        socialLinks.setFacebook(input.facebook());
        socialLinks.setInstagram(input.instagram());
        socialLinks.setTwitter(input.twitter());
        socialLinks.setLinkedin(input.linkedin());
        socialLinks.setYoutube(input.youtube());
        socialLinks.setTiktok(input.tiktok());
        return socialLinks;
    }

    private String generateOrganizationName(User user) {
        String firstName = user.getFirstName();
        String lastName = user.getLastName();

        if (firstName != null && !firstName.isBlank() &&
            lastName != null && !lastName.isBlank()) {
            return firstName + " " + lastName;
        }

        String email = user.getEmail();
        if (email != null && email.contains("@")) {
            String prefix = email.split("@")[0];
            return capitalizeWords(prefix.replace(".", " ").replace("_", " "));
        }

        return user.getUsername() != null ? user.getUsername() : "Organization";
    }

    private Mono<OrganizationMember> createOwnerMembership(Organization organization, User user) {
        Instant now = Instant.now();
        OrganizationMember member = OrganizationMember.builder()
                .organizationId(organization.getId())
                .userId(user.getId())
                .role(OrganizationRole.OWNER)
                .status(MemberStatus.ACTIVE)
                .joinedAt(now)
                .createdAt(now)
                .build();

        return organizationMemberRepository.save(member)
                .doOnSuccess(m -> log.debug(
                        "Created owner membership for user {} in organization {}",
                        user.getId(), organization.getId()));
    }

    private Mono<String> generateUniqueSlug(String baseSlug) {
        return organizationRepository.existsBySlug(baseSlug)
                .flatMap(exists -> {
                    if (!exists) {
                        return Mono.just(baseSlug);
                    }
                    String uniqueSlug = baseSlug + "-" + UUID.randomUUID().toString().substring(0, 6);
                    return Mono.just(uniqueSlug);
                });
    }

    private String toSlug(String input) {
        if (input == null || input.isBlank()) {
            return "org-" + UUID.randomUUID().toString().substring(0, 8);
        }

        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        String slug = WHITESPACE.matcher(normalized).replaceAll("-");
        slug = NONLATIN.matcher(slug).replaceAll("");
        slug = slug.toLowerCase(Locale.ENGLISH);
        slug = slug.replaceAll("-+", "-");
        slug = slug.replaceAll("^-|-$", "");

        if (slug.isEmpty()) {
            return "org-" + UUID.randomUUID().toString().substring(0, 8);
        }

        return slug;
    }

    private String capitalizeWords(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }

        StringBuilder result = new StringBuilder();
        String[] words = input.split("\\s+");

        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                result.append(" ");
            }
            String word = words[i];
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1).toLowerCase());
                }
            }
        }

        return result.toString();
    }
}
