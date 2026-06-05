package com.pml.identity.service.impl;

import com.pml.identity.domain.model.OrganizerProfile;
import com.pml.identity.domain.enums.OrganizerStatus;
import com.pml.identity.repository.OrganizerProfileRepository;
import com.pml.identity.service.OrganizerProfileService;
import com.pml.identity.service.OrganizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Implementation of OrganizerProfileService.
 *
 * <p>Manages the organizer application workflow and integrates with
 * OrganizationService for post-approval Organization creation.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizerProfileServiceImpl implements OrganizerProfileService {

    private final OrganizerProfileRepository profileRepository;
    private final OrganizationService organizationService;

    // ─────────────────────────────────────────────────────────────────────
    // Read Operations
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public Mono<OrganizerProfile> findById(String id) {
        log.debug("Finding organizer profile by ID: {}", id);
        return profileRepository.findById(id);
    }

    @Override
    public Mono<OrganizerProfile> findByUserId(String userId) {
        log.debug("Finding organizer profile by userId: {}", userId);
        return profileRepository.findByUserId(userId);
    }

    @Override
    public Mono<Boolean> existsByUserId(String userId) {
        return profileRepository.existsByUserId(userId);
    }

    @Override
    public Flux<OrganizerProfile> findByStatus(OrganizerStatus status) {
        log.debug("Finding organizer profiles by status: {}", status);
        return profileRepository.findByStatus(status);
    }

    @Override
    public Flux<OrganizerProfile> findByStatus(OrganizerStatus status, Pageable pageable) {
        log.debug("Finding organizer profiles by status: {} with pagination", status);
        return profileRepository.findByStatus(status, pageable);
    }

    @Override
    public Mono<Long> countByStatus(OrganizerStatus status) {
        return profileRepository.countByStatus(status);
    }

    @Override
    public Flux<OrganizerProfile> searchByCompanyName(String companyName) {
        log.debug("Searching organizer profiles by company name: {}", companyName);
        return profileRepository.findByCompanyNameContainingIgnoreCase(companyName);
    }

    @Override
    public Flux<OrganizerProfile> findAll() {
        log.debug("Finding all organizer profiles");
        return profileRepository.findAll();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Application Workflow
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public Mono<OrganizerProfile> applyToBeOrganizer(String userId) {
        log.info("User {} applying to become organizer", userId);

        return existsByUserId(userId)
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new IllegalStateException(
                                "Organizer profile already exists for user: " + userId));
                    }

                    OrganizerProfile profile = OrganizerProfile.builder()
                            .userId(userId)
                            .status(OrganizerStatus.DRAFT)
                            .build();

                    return profileRepository.save(profile);
                })
                .doOnSuccess(profile -> log.info("Created organizer profile {} for user {}",
                        profile.getId(), userId));
    }

    @Override
    public Mono<OrganizerProfile> updateProfile(String id, OrganizerProfile profileData) {
        log.debug("Updating organizer profile: {}", id);

        return profileRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Profile not found: " + id)))
                .flatMap(existing -> {
                    // Copy updatable fields
                    if (profileData.getCompanyName() != null) {
                        existing.setCompanyName(profileData.getCompanyName());
                    }
                    if (profileData.getCompanyDescription() != null) {
                        existing.setCompanyDescription(profileData.getCompanyDescription());
                    }
                    if (profileData.getTagline() != null) {
                        existing.setTagline(profileData.getTagline());
                    }
                    if (profileData.getWebsite() != null) {
                        existing.setWebsite(profileData.getWebsite());
                    }
                    if (profileData.getSocialLinks() != null) {
                        existing.setSocialLinks(profileData.getSocialLinks());
                    }
                    if (profileData.getTaxId() != null) {
                        existing.setTaxId(profileData.getTaxId());
                    }
                    if (profileData.getBusinessRegistrationNumber() != null) {
                        existing.setBusinessRegistrationNumber(profileData.getBusinessRegistrationNumber());
                    }
                    if (profileData.getBusinessType() != null) {
                        existing.setBusinessType(profileData.getBusinessType());
                    }
                    if (profileData.getYearEstablished() != null) {
                        existing.setYearEstablished(profileData.getYearEstablished());
                    }
                    if (profileData.getBusinessPhone() != null) {
                        existing.setBusinessPhone(profileData.getBusinessPhone());
                    }
                    if (profileData.getBusinessEmail() != null) {
                        existing.setBusinessEmail(profileData.getBusinessEmail());
                    }
                    if (profileData.getBusinessAddress() != null) {
                        existing.setBusinessAddress(profileData.getBusinessAddress());
                    }
                    if (profileData.getCity() != null) {
                        existing.setCity(profileData.getCity());
                    }
                    if (profileData.getProvince() != null) {
                        existing.setProvince(profileData.getProvince());
                    }
                    if (profileData.getCountry() != null) {
                        existing.setCountry(profileData.getCountry());
                    }
                    if (profileData.getPostalCode() != null) {
                        existing.setPostalCode(profileData.getPostalCode());
                    }

                    return profileRepository.save(existing);
                });
    }

    @Override
    public Mono<OrganizerProfile> submitForReview(String id) {
        log.info("Submitting organizer profile for review: {}", id);

        return profileRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Profile not found: " + id)))
                .flatMap(profile -> {
                    if (profile.getStatus() != OrganizerStatus.DRAFT &&
                        profile.getStatus() != OrganizerStatus.CHANGES_REQUESTED) {
                        return Mono.error(new IllegalStateException(
                                "Cannot submit profile with status: " + profile.getStatus()));
                    }

                    profile.setStatus(OrganizerStatus.PENDING_REVIEW);
                    profile.setSubmittedAt(Instant.now());

                    return profileRepository.save(profile);
                })
                .doOnSuccess(profile -> log.info("Profile {} submitted for review", id));
    }

    @Override
    public Mono<OrganizerProfile> requestChanges(String id, String reason, String adminId) {
        log.info("Admin {} requesting changes for profile {}: {}", adminId, id, reason);

        return profileRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Profile not found: " + id)))
                .flatMap(profile -> {
                    profile.setStatus(OrganizerStatus.CHANGES_REQUESTED);
                    profile.setRejectionReason(reason);
                    profile.setReviewedBy(adminId);
                    profile.setReviewedAt(Instant.now());

                    return profileRepository.save(profile);
                });
    }

    @Override
    public Mono<OrganizerProfile> approve(String id, String adminId) {
        log.info("Admin {} approving organizer profile: {}", adminId, id);

        return profileRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Profile not found: " + id)))
                .flatMap(profile -> {
                    if (profile.getStatus() != OrganizerStatus.PENDING_REVIEW) {
                        return Mono.error(new IllegalStateException(
                                "Cannot approve profile with status: " + profile.getStatus()));
                    }

                    profile.setStatus(OrganizerStatus.APPROVED);
                    profile.setReviewedBy(adminId);
                    profile.setReviewedAt(Instant.now());
                    profile.setApprovedAt(Instant.now());
                    profile.setRejectionReason(null);

                    // NOTE: Organization is no longer created here.
                    // Organizations are now created LAZILY via OrganizationOnboardingService
                    // when the user creates their first event.
                    // This follows the industry standard progressive onboarding pattern.
                    return profileRepository.save(profile);
                })
                .doOnSuccess(profile -> log.info("Profile {} approved by admin {}", id, adminId));
    }

    @Override
    public Mono<OrganizerProfile> reject(String id, String reason, String adminId) {
        log.info("Admin {} rejecting organizer profile {}: {}", adminId, id, reason);

        return profileRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Profile not found: " + id)))
                .flatMap(profile -> {
                    profile.setStatus(OrganizerStatus.REJECTED);
                    profile.setRejectionReason(reason);
                    profile.setReviewedBy(adminId);
                    profile.setReviewedAt(Instant.now());

                    return profileRepository.save(profile);
                });
    }

    @Override
    public Mono<OrganizerProfile> suspend(String id, String reason, String adminId) {
        log.info("Admin {} suspending organizer profile {}: {}", adminId, id, reason);

        return profileRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Profile not found: " + id)))
                .flatMap(profile -> {
                    profile.setStatus(OrganizerStatus.SUSPENDED);
                    profile.setRejectionReason(reason);
                    profile.setReviewedBy(adminId);
                    profile.setReviewedAt(Instant.now());

                    return profileRepository.save(profile);
                });
    }

    @Override
    public Mono<OrganizerProfile> unsuspend(String id, String adminId) {
        log.info("Admin {} unsuspending organizer profile: {}", adminId, id);

        return profileRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Profile not found: " + id)))
                .flatMap(profile -> {
                    if (profile.getStatus() != OrganizerStatus.SUSPENDED) {
                        return Mono.error(new IllegalStateException(
                                "Cannot unsuspend profile with status: " + profile.getStatus()));
                    }

                    profile.setStatus(OrganizerStatus.APPROVED);
                    profile.setRejectionReason(null);
                    profile.setReviewedBy(adminId);
                    profile.setReviewedAt(Instant.now());

                    return profileRepository.save(profile);
                });
    }

    // ─────────────────────────────────────────────────────────────────────
    // Verification
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public Mono<OrganizerProfile> verifyBusiness(String id, String adminId) {
        log.info("Admin {} verifying business for profile: {}", adminId, id);

        return profileRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Profile not found: " + id)))
                .flatMap(profile -> {
                    profile.setVerified(true);
                    profile.setVerifiedAt(Instant.now());
                    profile.setVerifiedBy(adminId);

                    return profileRepository.save(profile);
                });
    }

    @Override
    public Mono<OrganizerProfile> verifyDocuments(String id, String adminId) {
        log.info("Admin {} verifying documents for profile: {}", adminId, id);

        return profileRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Profile not found: " + id)))
                .flatMap(profile -> {
                    profile.setDocumentsVerified(true);
                    return profileRepository.save(profile);
                });
    }

    @Override
    public Mono<OrganizerProfile> verifyBankAccount(String id, String adminId) {
        log.info("Admin {} verifying bank account for profile: {}", adminId, id);

        return profileRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Profile not found: " + id)))
                .flatMap(profile -> {
                    profile.setBankVerified(true);
                    return profileRepository.save(profile);
                });
    }

    // ─────────────────────────────────────────────────────────────────────
    // Delete Operations
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public Mono<Void> delete(String id) {
        log.info("Deleting organizer profile: {}", id);

        return profileRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Profile not found: " + id)))
                .flatMap(profile -> {
                    // Only allow deletion of DRAFT or REJECTED profiles
                    if (profile.getStatus() != OrganizerStatus.DRAFT &&
                        profile.getStatus() != OrganizerStatus.REJECTED) {
                        return Mono.error(new IllegalStateException(
                                "Cannot delete profile with status: " + profile.getStatus()));
                    }
                    return profileRepository.delete(profile);
                });
    }

    @Override
    public Mono<Void> deleteByUserId(String userId) {
        log.info("Deleting organizer profile for user: {}", userId);
        return profileRepository.deleteByUserId(userId);
    }
}
