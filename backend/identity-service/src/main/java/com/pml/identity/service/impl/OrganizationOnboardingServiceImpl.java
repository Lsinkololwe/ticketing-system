package com.pml.identity.service.impl;

import com.pml.identity.domain.valueobject.RequiredDocuments;
import com.pml.identity.domain.enums.MemberStatus;
import com.pml.identity.domain.model.VerificationDocument;
import com.pml.identity.exception.MissingRequiredDocumentsException;
import com.pml.identity.repository.VerificationDocumentRepository;
import com.pml.shared.constants.DocumentStatus;
import com.pml.shared.constants.OrganizationStatus;
import com.pml.identity.domain.enums.OrganizationType;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.domain.model.OrganizationMember;
import com.pml.identity.domain.model.User;
import com.pml.shared.constants.PayoutMethod;
import com.pml.identity.domain.enums.PayoutSchedule;
import com.pml.identity.domain.valueobject.BusinessAddress;
import com.pml.identity.domain.valueobject.OrganizationRole;
import com.pml.identity.domain.valueobject.PayoutConfig;
import com.pml.identity.domain.valueobject.SocialLinks;
import com.pml.identity.repository.OrganizationMemberRepository;
import com.pml.identity.repository.OrganizationRepository;
import com.pml.identity.repository.UserRepository;
import com.pml.shared.config.PlatformConfigurationReader;
import com.pml.shared.config.model.PlatformPaymentDefaults;
import com.pml.identity.service.OrganizationOnboardingService;
import com.pml.identity.web.graphql.dto.organization.OrganizationApplicationInput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.text.Normalizer;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Implementation of OrganizationOnboardingService.
 *
 * Handles approval-based organization onboarding:
 * - User applies → Organization created (DRAFT)
 * - User fills details and submits → PENDING_REVIEW
 * - User can create draft events during approval process
 *
 * <p>The admin decisions — approve, reject, request changes — are not here: they run in
 * {@code OrganizerOnboardingWorkflow}, whose approval is a six-step compensating saga.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationOnboardingServiceImpl implements OrganizationOnboardingService {

    private static final Pattern NONLATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]");

    private final OrganizationRepository organizationRepository;
    private final VerificationDocumentRepository verificationDocumentRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final com.pml.identity.service.OneOrganizationPerPerson oneOrganizationPerPerson;
    private final UserRepository userRepository;
    private final PlatformConfigurationReader platformConfiguration;

    /** The injected platform clock, so every timestamp below is freezable. */
    private final java.time.Clock clock;
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
                    // KYB. businessType in particular must round-trip: it is what
                    // RequiredDocuments keys off, so an application that cannot
                    // persist it can never satisfy submitForReview.
                    if (input.businessType() != null) {
                        org.setBusinessType(input.businessType());
                    }
                    if (input.taxId() != null) {
                        org.setTaxId(input.taxId());
                    }
                    if (input.businessRegistrationNumber() != null) {
                        org.setBusinessRegistrationNumber(input.businessRegistrationNumber());
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

                    org.setUpdatedAt(clock.instant());
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
                    if (org.getBusinessType() == null) {
                        return Mono.error(new IllegalStateException(
                                "Business type is required — it determines which documents must be supplied"));
                    }

                    // Verify the document set required for THIS
                    // business type, and name exactly what is missing. A generic
                    // "application incomplete" is how an applicant gives up.
                    return verifyRequiredDocuments(org).thenReturn(org);
                })
                .flatMap(org -> {
                    org.setStatus(OrganizationStatus.PENDING_REVIEW);
                    org.setSubmittedAt(clock.instant());
                    org.setRejectionReason(null); // Clear any previous rejection reason
                    org.setUpdatedAt(clock.instant());

                    // NOTE: the ORGANIZER realm role is NOT granted here. It is assigned by Keycloak
                    // at registration via the AccountTypeRoleMapper SPI (keycloak-extensions) based on
                    // the user-selected account type, and mirrored to MongoDB by UserSyncEventListener.
                    // Granting it again from the app would duplicate that flow. What actually gates a
                    // pending organizer's privileged actions is organization STATUS, enforced in
                    // AuthorizationServiceImpl — not the role.
                    return organizationRepository.save(org);
                })
                .doOnSuccess(org -> log.info("Organization {} submitted for review", org.getId()));
    }

    /**
     * Refuse the submit if any document required by this business type is absent.
     *
     * <p>Missing documents are refused with {@code DOCUMENT_REQUIRED} carrying
     * {@code missingDocumentTypes}: the refusal names exactly what is missing rather than a generic
     * "incomplete", because a generic refusal gives the applicant nothing to
     * act on.</p>
     *
     * <p>A document in {@code REJECTED} does not satisfy its requirement: a
     * reviewer who rejected the tax certificate as illegible has not been given
     * a tax certificate. Re-uploading supersedes it and returns it to
     * {@code PENDING}, which does count.</p>
     *
     * @return an empty Mono when the set is satisfied, otherwise an error Mono
     */
    private Mono<Void> verifyRequiredDocuments(Organization org) {
        return verificationDocumentRepository.findByOrganizationId(org.getId())
                .filter(doc -> doc.getStatus() != DocumentStatus.REJECTED)
                .map(VerificationDocument::getDocumentType)
                .collectList()
                .flatMap(supplied -> {
                    List<String> missing = RequiredDocuments.missingFor(org.getBusinessType(), supplied);
                    if (missing.isEmpty()) {
                        return Mono.empty();
                    }
                    log.info("Organization {} submit refused — missing documents {} for business type {}",
                            org.getId(), missing, org.getBusinessType());
                    return Mono.error(new MissingRequiredDocumentsException(org.getBusinessType(), missing));
                });
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
                .flatMap(slug -> buildDefaultPayoutConfig()
                        .flatMap(payoutConfig -> {
                            Organization organization = Organization.builder()
                                    .name(organizationName)
                                    .slug(slug)
                                    .type(OrganizationType.INDIVIDUAL)
                                    .ownerId(user.getId())
                                    .businessEmail(user.getEmail())
                                    .businessPhone(user.getPhoneNumber())
                                    .status(OrganizationStatus.DRAFT) // Start in DRAFT
                                    .payoutConfig(payoutConfig)
                                    .createdAt(clock.instant())
                                    .updatedAt(clock.instant())
                                    .build();

                            return organizationRepository.save(organization)
                                    .flatMap(savedOrg -> createOwnerMembership(savedOrg, user)
                                            .thenReturn(savedOrg))
                                    .doOnSuccess(org -> log.info(
                                            "Created organization: {} (slug: {}, status: DRAFT) for user: {}",
                                            org.getId(), org.getSlug(), user.getId()));
                        }));
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
                    org.setUpdatedAt(clock.instant());

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
    public Mono<Organization> findOrganizationByOwnerId(String userId) {
        return organizationRepository.findByOwnerId(userId);
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
                .flatMap(slug -> buildDefaultPayoutConfig().flatMap(payoutConfig -> {
                    Organization.OrganizationBuilder builder = Organization.builder()
                            .name(organizationName)
                            .slug(slug)
                            .type(input.type() != null ? input.type() : OrganizationType.INDIVIDUAL)
                            .ownerId(user.getId())
                            .businessEmail(input.businessEmail() != null ? input.businessEmail() : user.getEmail())
                            .businessPhone(input.businessPhone() != null ? input.businessPhone() : user.getPhoneNumber())
                            .status(OrganizationStatus.DRAFT)
                            .payoutConfig(payoutConfig)
                            .createdAt(clock.instant())
                            .updatedAt(clock.instant());

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
                    // KYB — same reasoning as updateApplication: businessType has
                    // to survive the very first write, because the documents step
                    // renders from it.
                    if (input.businessType() != null) {
                        builder.businessType(input.businessType());
                    }
                    if (input.taxId() != null) {
                        builder.taxId(input.taxId());
                    }
                    if (input.businessRegistrationNumber() != null) {
                        builder.businessRegistrationNumber(input.businessRegistrationNumber());
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
                }));
    }

    /**
     * Build a new organization's {@link PayoutConfig} from the platform settings. The commission
     * rate, payout method/schedule and minimum payout amount all come from the settings table's
     * {@link PlatformPaymentDefaults} — never from defaults baked into the entity.
     *
     * <p>Fails loudly if the settings or their payment section are missing, since these are
     * financial values that must originate from the configured source of truth.</p>
     */
    private Mono<PayoutConfig> buildDefaultPayoutConfig() {
        return platformConfiguration.paymentDefaults()
                .switchIfEmpty(Mono.error(new IllegalStateException(
                        "The platform settings have no payment defaults. catalog-service seeds them at "
                                + "startup; start it before creating organizations.")))
                .map(payment -> {
                    return PayoutConfig.builder()
                            .preferredMethod(PayoutMethod.valueOf(payment.getPayoutMethod()))
                            .schedule(PayoutSchedule.valueOf(payment.getPayoutSchedule()))
                            .commissionRate(payment.getCommissionRate())
                            .minimumPayoutAmount(payment.getMinimumPayoutAmount())
                            .verified(false)
                            .build();
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
        Instant now = clock.instant();
        OrganizationMember member = OrganizationMember.builder()
                .organizationId(organization.getId())
                .userId(user.getId())
                .role(OrganizationRole.OWNER)
                .status(MemberStatus.ACTIVE)
                .joinedAt(now)
                .createdAt(now)
                .build();

        return oneOrganizationPerPerson.require(user.getId(), organization.getId())
                .then(organizationMemberRepository.save(member))
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
