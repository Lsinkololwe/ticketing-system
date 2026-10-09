package com.pml.identity.service;

import com.pml.identity.domain.model.AuditLog;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.domain.model.PayoutConfigAuditLog;
import com.pml.identity.domain.valueobject.BusinessAddress;
import com.pml.identity.domain.valueobject.MobileMoneyAccount;
import com.pml.identity.domain.valueobject.PayoutBankDetails;
import com.pml.identity.domain.valueobject.PayoutConfig;
import com.pml.identity.domain.valueobject.SocialLinks;
import com.pml.identity.repository.OrganizationRepository;
import com.pml.identity.repository.PayoutConfigAuditLogRepository;
import com.pml.identity.service.OrganizationRules.PayoutAccountStatus;
import com.pml.identity.web.graphql.dto.organization.UpdateOrganizationInput;
import com.pml.shared.constants.OrganizationStatus;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Administrator and owner operations on an organization that are not part of onboarding:
 * commission, payout-account review, profile edits and deletion requests.
 *
 * <p>Authorization is the resolver's job (who may call); this class enforces the state rules and
 * records the audit rows. Every administrator action writes an {@link AuditLog} row with the
 * actor, and the payout ones also a {@link PayoutConfigAuditLog} row.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationAdminService {

    private final OrganizationRepository organizations;
    private final PayoutConfigAuditLogRepository payoutAudit;
    private final AdminAuditService audit;
    private final Clock clock;

    // ---- commission --------------------------------------------------------------------------------

    /**
     * Sets the organization's own commission rate, replacing the platform default for it.
     *
     * @param percent 0 to 50, where 5 means 5%
     */
    public Mono<Organization> setCommissionRate(String organizationId, Double percent, String reason, String actorId) {
        double fraction = OrganizationRules.fractionOf(percent);
        return require(organizationId).flatMap(org -> {
            PayoutConfig config = org.getPayoutConfig() != null ? org.getPayoutConfig() : PayoutConfig.builder().build();
            Double previous = config.getCommissionRate();
            config.setCommissionRate(fraction);
            config.setCommissionSetBy(actorId);
            config.setCommissionSetAt(clock.instant());
            org.setPayoutConfig(config);
            return organizations.save(org)
                    .flatMap(saved -> audit.record(AuditLog.AuditAction.ORGANIZATION_COMMISSION_CHANGED,
                                    "Organization", saved.getId(), actorId, Map.of(
                                            "previousPercent", String.valueOf(OrganizationRules.percentOf(previous)),
                                            "newPercent", String.valueOf(percent),
                                            "reason", reason == null ? "" : reason))
                            .then(payoutAudit.save(PayoutConfigAuditLog.builder()
                                    .organizationId(saved.getId())
                                    .userId(actorId)
                                    .action(PayoutConfigAuditLog.AuditAction.COMMISSION_RATE_CHANGED)
                                    .timestamp(clock.instant())
                                    .metadata(Map.of("newPercent", String.valueOf(percent)))
                                    .success(true)
                                    .build()))
                            .thenReturn(saved));
        });
    }

    // ---- payout account review ---------------------------------------------------------------------

    /** Rejects the payout account on file: it is unverified and the owner is told why. */
    public Mono<Organization> rejectPayoutAccount(String organizationId, String reason, String actorId) {
        String why = OrganizationRules.requireReason(reason);
        return requireConfigured(organizationId).flatMap(org -> {
            mutateActive(org.getPayoutConfig(), bank -> {
                bank.setVerified(false);
                bank.setRejectionReason(why);
            }, wallet -> {
                wallet.setVerified(false);
                wallet.setRejectionReason(why);
            });
            org.getPayoutConfig().setVerified(false);
            org.setPayoutAccountVerified(false);
            return saveAudited(org, AuditLog.AuditAction.PAYOUT_ACCOUNT_REJECTED,
                    PayoutConfigAuditLog.AuditAction.PAYOUT_ACCOUNT_REJECTED, actorId, why);
        });
    }

    /** Freezes payouts to the account without removing it. Idempotent. */
    public Mono<Organization> suspendPayoutAccount(String organizationId, String reason, String actorId) {
        String why = OrganizationRules.requireReason(reason);
        return requireConfigured(organizationId).flatMap(org -> {
            mutateActive(org.getPayoutConfig(), bank -> {
                bank.setSuspended(true);
                bank.setSuspendedReason(why);
            }, wallet -> {
                wallet.setSuspended(true);
                wallet.setSuspendedReason(why);
            });
            return saveAudited(org, AuditLog.AuditAction.PAYOUT_ACCOUNT_SUSPENDED,
                    PayoutConfigAuditLog.AuditAction.PAYOUT_ACCOUNT_SUSPENDED, actorId, why);
        });
    }

    /** Lifts a freeze. Idempotent. */
    public Mono<Organization> reinstatePayoutAccount(String organizationId, String actorId) {
        return requireConfigured(organizationId).flatMap(org -> {
            mutateActive(org.getPayoutConfig(), bank -> {
                bank.setSuspended(false);
                bank.setSuspendedReason(null);
            }, wallet -> {
                wallet.setSuspended(false);
                wallet.setSuspendedReason(null);
            });
            return saveAudited(org, AuditLog.AuditAction.PAYOUT_ACCOUNT_REINSTATED,
                    PayoutConfigAuditLog.AuditAction.PAYOUT_ACCOUNT_REINSTATED, actorId, "reinstated");
        });
    }

    // ---- profile -----------------------------------------------------------------------------------

    /** Writes every present field of {@code input}; KYB identity fields only while the application is editable. */
    public Mono<Organization> updateProfile(String organizationId, UpdateOrganizationInput input) {
        return require(organizationId).flatMap(org -> {
            boolean touchesKyb = input.businessType() != null || input.taxId() != null
                    || input.businessRegistrationNumber() != null;
            if (touchesKyb && !OrganizationRules.kybFieldsEditable(org.getStatus())) {
                return Mono.<Organization>error(new TranslatedRefusal(ErrorCode.ORGANIZATION_STATE_INVALID,
                        "business identity fields are fixed once the application has been reviewed",
                        Map.of("currentStatus", String.valueOf(org.getStatus()))));
            }
            if (input.name() != null && !input.name().isBlank()) org.setName(input.name().trim());
            if (input.description() != null) org.setDescription(input.description());
            if (input.logoUrl() != null) org.setLogoUrl(input.logoUrl());
            if (input.bannerUrl() != null) org.setBannerUrl(input.bannerUrl());
            if (input.tagline() != null) org.setTagline(input.tagline());
            if (input.website() != null) org.setWebsite(input.website());
            if (input.businessType() != null) org.setBusinessType(input.businessType());
            if (input.taxId() != null) org.setTaxId(input.taxId());
            if (input.businessRegistrationNumber() != null) org.setBusinessRegistrationNumber(input.businessRegistrationNumber());
            if (input.yearEstablished() != null) org.setYearEstablished(input.yearEstablished());
            if (input.businessPhone() != null) org.setBusinessPhone(input.businessPhone());
            if (input.businessEmail() != null) org.setBusinessEmail(input.businessEmail());
            if (input.socialLinks() != null) {
                SocialLinks links = org.getSocialLinks() != null ? org.getSocialLinks() : new SocialLinks();
                var in = input.socialLinks();
                if (in.facebook() != null) links.setFacebook(in.facebook());
                if (in.instagram() != null) links.setInstagram(in.instagram());
                if (in.twitter() != null) links.setTwitter(in.twitter());
                if (in.linkedin() != null) links.setLinkedin(in.linkedin());
                if (in.youtube() != null) links.setYoutube(in.youtube());
                if (in.tiktok() != null) links.setTiktok(in.tiktok());
                org.setSocialLinks(links);
            }
            if (input.businessAddress() != null) {
                BusinessAddress address = org.getBusinessAddress() != null ? org.getBusinessAddress() : new BusinessAddress();
                var in = input.businessAddress();
                if (in.addressLine1() != null) address.setAddressLine1(in.addressLine1());
                if (in.addressLine2() != null) address.setAddressLine2(in.addressLine2());
                if (in.city() != null) address.setCity(in.city());
                if (in.province() != null) address.setProvince(in.province());
                if (in.postalCode() != null) address.setPostalCode(in.postalCode());
                if (in.country() != null) address.setCountry(in.country());
                if (in.countryCode() != null) address.setCountryCode(in.countryCode());
                org.setBusinessAddress(address);
            }
            org.setUpdatedAt(clock.instant());
            return organizations.save(org);
        });
    }

    // ---- suspension --------------------------------------------------------------------------------

    /** Audit row for a suspension (the status change itself is {@code OrganizationService#suspend}). */
    public Mono<Void> auditSuspension(String organizationId, String reason, String actorId, boolean suspended) {
        return audit.record(suspended ? AuditLog.AuditAction.ORGANIZATION_SUSPENDED
                                : AuditLog.AuditAction.ORGANIZATION_UNSUSPENDED,
                        "Organization", organizationId, actorId,
                        reason == null ? null : Map.of("reason", reason))
                .then();
    }

    // ---- deletion ----------------------------------------------------------------------------------

    /**
     * Opens a deletion request: the organization moves to {@code PENDING_DELETION} and stays
     * recoverable for the grace period. Nothing is removed here; the owner can cancel until
     * {@code deletionScheduledFor}. Idempotent per request: a second call is refused, not repeated.
     */
    public Mono<Organization> requestDeletion(String organizationId, String requesterId, String reason) {
        return require(organizationId).flatMap(org -> {
            OrganizationRules.requireDeletable(org.getStatus());
            Instant now = clock.instant();
            org.setStatusBeforeDeletion(org.getStatus());
            org.setStatus(OrganizationStatus.PENDING_DELETION);
            org.setDeletionRequestedAt(now);
            org.setDeletionScheduledFor(OrganizationRules.deletionDate(now));
            org.setDeletionReason(reason == null || reason.isBlank() ? null : reason.trim());
            org.setUpdatedAt(now);
            return organizations.save(org)
                    .flatMap(saved -> audit.record(AuditLog.AuditAction.ORGANIZATION_DELETION_REQUESTED,
                            "Organization", saved.getId(), requesterId,
                            Map.of("scheduledFor", String.valueOf(saved.getDeletionScheduledFor()))).thenReturn(saved));
        });
    }

    /** Withdraws an open deletion request and restores the previous status. */
    public Mono<Organization> cancelDeletion(String organizationId, String requesterId) {
        return require(organizationId).flatMap(org -> {
            if (org.getStatus() != OrganizationStatus.PENDING_DELETION) {
                return Mono.<Organization>error(new TranslatedRefusal(ErrorCode.ORGANIZATION_STATE_INVALID,
                        "no deletion request is open", Map.of("currentStatus", String.valueOf(org.getStatus()))));
            }
            org.setStatus(org.getStatusBeforeDeletion() != null ? org.getStatusBeforeDeletion() : OrganizationStatus.ACTIVE);
            org.setStatusBeforeDeletion(null);
            org.setDeletionRequestedAt(null);
            org.setDeletionScheduledFor(null);
            org.setDeletionReason(null);
            org.setUpdatedAt(clock.instant());
            return organizations.save(org)
                    .flatMap(saved -> audit.record(AuditLog.AuditAction.ORGANIZATION_DELETION_CANCELLED,
                            "Organization", saved.getId(), requesterId, null).thenReturn(saved));
        });
    }

    // ---- helpers -----------------------------------------------------------------------------------

    private Mono<Organization> require(String organizationId) {
        return organizations.findById(organizationId)
                .switchIfEmpty(Mono.error(() -> new TranslatedRefusal(ErrorCode.ORGANIZATION_UNKNOWN,
                        "organization " + organizationId)));
    }

    private Mono<Organization> requireConfigured(String organizationId) {
        return require(organizationId).flatMap(org -> {
            if (OrganizationRules.statusOf(org.getPayoutConfig()) == PayoutAccountStatus.NONE) {
                return Mono.<Organization>error(new TranslatedRefusal(ErrorCode.BANK_ACCOUNT_UNKNOWN,
                        "organization " + organizationId + " has no payout account"));
            }
            return Mono.just(org);
        });
    }

    private static void mutateActive(PayoutConfig config,
                                     java.util.function.Consumer<PayoutBankDetails> onBank,
                                     java.util.function.Consumer<MobileMoneyAccount> onWallet) {
        Object account = OrganizationRules.activeAccount(config);
        if (account instanceof PayoutBankDetails bank) {
            onBank.accept(bank);
        } else if (account instanceof MobileMoneyAccount wallet) {
            onWallet.accept(wallet);
        }
    }

    private Mono<Organization> saveAudited(Organization org, AuditLog.AuditAction action,
                                           PayoutConfigAuditLog.AuditAction payoutAction, String actorId, String detail) {
        org.setUpdatedAt(clock.instant());
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("detail", detail);
        return organizations.save(org)
                .flatMap(saved -> audit.record(action, "Organization", saved.getId(), actorId, meta)
                        .then(payoutAudit.save(PayoutConfigAuditLog.builder()
                                .organizationId(saved.getId())
                                .userId(actorId)
                                .action(payoutAction)
                                .timestamp(clock.instant())
                                .metadata(meta)
                                .success(true)
                                .build()))
                        .thenReturn(saved));
    }
}
