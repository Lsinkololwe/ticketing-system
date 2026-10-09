package com.pml.identity.service;

import com.pml.identity.domain.enums.OnboardingStep;
import com.pml.identity.domain.enums.MemberStatus;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.domain.model.OrganizationMember;
import com.pml.identity.domain.model.User;
import com.pml.identity.domain.valueobject.OrganizationRole;
import com.pml.shared.constants.OrganizationStatus;
import com.pml.shared.constants.UserType;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import com.pml.shared.event.EventEnvelopes;
import com.pml.shared.event.EventType;
import com.pml.shared.event.Outbox;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/**
 * Every MongoDB write an organizer's review and approval makes.
 *
 * <p>Each method is one transaction and safe to run twice: a retried activity finds the document
 * already where it was moving and returns it unchanged, and step 6 stages exactly one
 * {@code identity.OrganizationApproved} however often it runs. Status changes go through
 * {@code save}, so the status semantic is stamped on the same write.
 */
@Service
public class OrganizationApprovalService {

    private final ReactiveMongoTemplate template;
    private final TransactionalOperator transaction;
    private final Outbox outbox;
    private final Clock clock;

    public OrganizationApprovalService(ReactiveMongoTemplate template, TransactionalOperator transaction,
                                       Outbox outbox, Clock clock) {
        this.template = template;
        this.transaction = transaction;
        this.outbox = outbox;
        this.clock = clock;
    }

    /** The organization, or empty when there is none. */
    public Mono<Organization> load(String organizationId) {
        return template.findById(organizationId, Organization.class);
    }

    /** Reject or request changes: {@code PENDING_REVIEW} to the outcome, with the reviewer and reason. */
    public Mono<Organization> decide(String organizationId, String reviewerId, String reason, OrganizationStatus outcome) {
        if (outcome != OrganizationStatus.REJECTED && outcome != OrganizationStatus.CHANGES_REQUESTED) {
            return Mono.error(new IllegalArgumentException("a review decision is REJECTED or CHANGES_REQUESTED, not " + outcome));
        }
        return transaction.transactional(existing(organizationId).flatMap(organization -> {
            if (organization.getStatus() == outcome && reviewerId.equals(organization.getReviewedBy())) {
                return Mono.just(organization);
            }
            if (organization.getStatus() != OrganizationStatus.PENDING_REVIEW) {
                return Mono.error(stateInvalid(organization, OrganizationStatus.PENDING_REVIEW));
            }
            Instant at = clock.instant();
            organization.setStatus(outcome);
            organization.setRejectionReason(reason);
            organization.setReviewedBy(reviewerId);
            organization.setReviewedAt(at);
            organization.setUpdatedAt(at);
            return template.save(organization);
        }));
    }

    /** Step 1 · {@code PENDING_REVIEW} to {@code ACTIVE}, with the reviewer and the approval time. */
    public Mono<Organization> activate(String organizationId, String reviewerId) {
        return transaction.transactional(existing(organizationId).flatMap(organization -> {
            if (organization.getStatus() == OrganizationStatus.ACTIVE && reached(organization, OnboardingStep.ACTIVATE)) {
                return Mono.just(organization);
            }
            if (organization.getStatus() != OrganizationStatus.PENDING_REVIEW) {
                return Mono.error(stateInvalid(organization, OrganizationStatus.PENDING_REVIEW));
            }
            Instant at = clock.instant();
            organization.setStatus(OrganizationStatus.ACTIVE);
            organization.setVerified(true);
            organization.setVerifiedAt(at);
            organization.setVerifiedBy(reviewerId);
            organization.setReviewedBy(reviewerId);
            organization.setReviewedAt(at);
            organization.setApprovedAt(at);
            organization.setRejectionReason(null);
            organization.setApprovalSagaStep(OnboardingStep.ACTIVATE.marker());
            organization.setApprovalSagaFailure(null);
            organization.setUpdatedAt(at);
            return template.save(organization);
        }));
    }

    /**
     * Compensation for step 1 · back to {@code PENDING_REVIEW}, recording why.
     *
     * <p>Acts only on an approval this saga began and did not finish: an organization that is
     * {@code ACTIVE} through a completed approval, or that step 1 never touched, is left alone.
     */
    public Mono<Organization> revertToPendingReview(String organizationId, String reason) {
        return transaction.transactional(existing(organizationId).flatMap(organization -> {
            Integer step = organization.getApprovalSagaStep();
            boolean unfinished = organization.getStatus() == OrganizationStatus.ACTIVE
                    && step != null && step > 0 && step < OnboardingStep.APPROVED.marker();
            if (!unfinished) {
                return Mono.just(organization);
            }
            organization.setStatus(OrganizationStatus.PENDING_REVIEW);
            organization.setVerified(false);
            organization.setVerifiedAt(null);
            organization.setVerifiedBy(null);
            organization.setApprovedAt(null);
            organization.setApprovalSagaStep(0);
            organization.setApprovalSagaFailure(reason);
            organization.setApprovalOwnerWasOrganizer(null);
            organization.setUpdatedAt(clock.instant());
            return template.save(organization);
        }));
    }

    /** Step 2 · the owner holds an ACTIVE {@code OWNER} membership. */
    public Mono<OrganizationMember> ensureOwnerMembership(String organizationId) {
        return transaction.transactional(existing(organizationId).flatMap(organization -> template.findOne(
                        Query.query(Criteria.where("organizationId").is(organizationId)
                                .and("userId").is(organization.getOwnerId())),
                        OrganizationMember.class)
                .flatMap(member -> {
                    if (member.getRole() == OrganizationRole.OWNER && member.getStatus() == MemberStatus.ACTIVE) {
                        return Mono.just(member);
                    }
                    member.setRole(OrganizationRole.OWNER);
                    member.setStatus(MemberStatus.ACTIVE);
                    return template.save(member);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    Instant at = clock.instant();
                    return template.insert(OrganizationMember.builder()
                            .id(OnboardingStep.ownerMembershipId(organizationId))
                            .organizationId(organizationId)
                            .userId(organization.getOwnerId())
                            .role(OrganizationRole.OWNER)
                            .status(MemberStatus.ACTIVE)
                            .joinedAt(at)
                            .createdAt(at)
                            .build());
                }))
                .flatMap(member -> markStep(organizationId, OnboardingStep.OWNER_MEMBERSHIP).thenReturn(member))));
    }

    /** Compensation for step 2 · removes only the membership step 2 created. */
    public Mono<Void> removeOwnerMembership(String organizationId) {
        return template.remove(Query.query(Criteria.where("_id").is(OnboardingStep.ownerMembershipId(organizationId))),
                OrganizationMember.class).then();
    }

    /** Step 3 · the owner holds {@code ORGANIZER}, and whether they already did is recorded once. */
    public Mono<Organization> promoteOwner(String organizationId) {
        return transaction.transactional(existing(organizationId).flatMap(organization -> owner(organization)
                .flatMap(user -> {
                    boolean held = user.hasRole(UserType.ORGANIZER);
                    if (organization.getApprovalOwnerWasOrganizer() == null) {
                        organization.setApprovalOwnerWasOrganizer(held);
                    }
                    organization.setApprovalSagaStep(Math.max(step(organization), OnboardingStep.USER_TYPE.marker()));
                    Mono<User> granted = held ? Mono.just(user) : Mono.defer(() -> {
                        user.addRole(UserType.ORGANIZER);
                        user.setUpdatedAt(clock.instant());
                        return template.save(user);
                    });
                    return granted.then(Mono.defer(() -> template.save(organization)));
                })));
    }

    /** Compensation for step 3 · removes {@code ORGANIZER} only when this approval granted it. */
    public Mono<Void> restoreOwnerUserType(String organizationId) {
        return transaction.transactional(existing(organizationId).flatMap(organization -> {
            if (!Boolean.FALSE.equals(organization.getApprovalOwnerWasOrganizer())) {
                return Mono.<User>empty();
            }
            return owner(organization).flatMap(user -> {
                if (!user.hasRole(UserType.ORGANIZER)) {
                    return Mono.just(user);
                }
                user.removeRole(UserType.ORGANIZER);
                user.setUpdatedAt(clock.instant());
                return template.save(user);
            });
        })).then();
    }

    /** The step marker, raised and never lowered by a completed step. */
    public Mono<Void> markStep(String organizationId, OnboardingStep step) {
        return template.updateFirst(Query.query(Criteria.where("_id").is(organizationId)),
                new Update().max("approvalSagaStep", step.marker()), Organization.class).then();
    }

    /** Step 6 · stores the group id, finishes the marker, and stages {@code identity.OrganizationApproved} once. */
    public Mono<Organization> markApproved(String organizationId, String keycloakGroupId) {
        return transaction.transactional(existing(organizationId).flatMap(organization -> {
            if (reached(organization, OnboardingStep.APPROVED)) {
                return Mono.just(organization);
            }
            if (organization.getStatus() != OrganizationStatus.ACTIVE) {
                return Mono.error(stateInvalid(organization, OrganizationStatus.ACTIVE));
            }
            organization.setKeycloakGroupId(keycloakGroupId);
            organization.setApprovalSagaStep(OnboardingStep.APPROVED.marker());
            organization.setApprovalSagaFailure(null);
            organization.setUpdatedAt(clock.instant());
            return template.save(organization)
                    .flatMap(saved -> outbox.stage(EventEnvelopes.of(EventType.IDENTITY_ORGANIZATION_APPROVED,
                                    clock.instant(), saved.getId(),
                                    Map.of("organizationId", saved.getId(),
                                            "ownerId", saved.getOwnerId(),
                                            "slug", saved.getSlug())))
                            .thenReturn(saved));
        }));
    }

    private Mono<Organization> existing(String organizationId) {
        return template.findById(organizationId, Organization.class)
                .switchIfEmpty(Mono.error(() -> new TranslatedRefusal(ErrorCode.ORGANIZATION_UNKNOWN,
                        "no organization " + organizationId)));
    }

    private Mono<User> owner(Organization organization) {
        return template.findById(organization.getOwnerId(), User.class)
                .switchIfEmpty(Mono.error(() -> new TranslatedRefusal(ErrorCode.USER_UNKNOWN,
                        "the owner of organization " + organization.getId() + " has no account")));
    }

    private static boolean reached(Organization organization, OnboardingStep step) {
        return step(organization) >= step.marker();
    }

    private static int step(Organization organization) {
        return organization.getApprovalSagaStep() == null ? 0 : organization.getApprovalSagaStep();
    }

    private static TranslatedRefusal stateInvalid(Organization organization, OrganizationStatus expected) {
        return new TranslatedRefusal(ErrorCode.ORGANIZATION_STATE_INVALID,
                "organization " + organization.getId() + " is " + organization.getStatus() + ", not " + expected,
                Map.of("currentStatus", String.valueOf(organization.getStatus())));
    }
}
