package com.pml.identity.workflow.onboarding;

import com.pml.identity.domain.enums.OnboardingStep;
import com.pml.shared.workflow.Refusal;
import com.pml.shared.constants.OrganizationStatus;
import com.pml.shared.error.ErrorCode;
import io.temporal.common.RetryOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("L1")
@Tag("ET-ORG-001")
@DisplayName("ET-ORG-001-R3/R5/R6 · onboarding rules: who may decide, what a reversal records, the retry budget")
class OnboardingRulesTest {

    @Test
    @DisplayName("R5 · approval is legal only from PENDING_REVIEW, for every other status it refuses")
    void approvalOnlyFromPendingReview() {
        for (OrganizationStatus status : OrganizationStatus.values()) {
            if (status == OrganizationStatus.PENDING_REVIEW) {
                assertThat(OnboardingRules.approvalRefusal(status, false)).isEmpty();
            } else {
                assertThat(OnboardingRules.approvalRefusal(status, false))
                        .as(status.name())
                        .map(Refusal::code)
                        .contains(ErrorCode.ORGANIZATION_STATE_INVALID);
            }
        }
        assertThat(OnboardingRules.approvalRefusal(null, false)).map(Refusal::code).contains(ErrorCode.ORGANIZATION_UNKNOWN);
    }

    @Test
    @DisplayName("R6 · a second approval while one is running is refused")
    void oneApprovalAtATime() {
        assertThat(OnboardingRules.approvalRefusal(OrganizationStatus.PENDING_REVIEW, true))
                .map(Refusal::code)
                .contains(ErrorCode.ORGANIZATION_STATE_INVALID);
    }

    @Test
    @DisplayName("R5 · reject and request-changes need a reason before anything else is asked")
    void decisionsNeedAReason() {
        assertThat(OnboardingRules.decisionRefusal(OrganizationStatus.PENDING_REVIEW, false, " "))
                .map(Refusal::code).contains(ErrorCode.COMMAND_NOT_WELL_FORMED);
        assertThat(OnboardingRules.decisionRefusal(OrganizationStatus.ACTIVE, false, null))
                .map(Refusal::code).contains(ErrorCode.COMMAND_NOT_WELL_FORMED);
        assertThat(OnboardingRules.decisionRefusal(OrganizationStatus.PENDING_REVIEW, false, "tax certificate illegible"))
                .isEmpty();
        assertThat(OnboardingRules.decisionRefusal(OrganizationStatus.REJECTED, false, "again"))
                .map(Refusal::code).contains(ErrorCode.ORGANIZATION_STATE_INVALID);
    }

    @Test
    @DisplayName("R3 · a submission is legal from DRAFT and CHANGES_REQUESTED only")
    void submissionStates() {
        EnumSet<OrganizationStatus> legal = EnumSet.of(OrganizationStatus.DRAFT, OrganizationStatus.CHANGES_REQUESTED);
        for (OrganizationStatus status : OrganizationStatus.values()) {
            assertThat(OnboardingRules.submissionRefusal(status).isEmpty()).as(status.name()).isEqualTo(legal.contains(status));
        }
    }

    @Test
    @DisplayName("the execution stays open only while the organization waits on a person")
    void awaitingPerson() {
        for (OrganizationStatus status : OrganizationStatus.values()) {
            boolean waiting = status == OrganizationStatus.PENDING_REVIEW || status == OrganizationStatus.CHANGES_REQUESTED;
            assertThat(OnboardingRules.awaitingPerson(status)).as(status.name()).isEqualTo(waiting);
        }
        assertThat(OnboardingRules.awaitingPerson(null)).isFalse();
    }

    @Test
    @DisplayName("R6 · a reversal records which step failed and why")
    void compensationReasonNamesTheStep() {
        String reason = OnboardingRules.compensationReason(OnboardingStep.REALM_ROLE, "keycloak unavailable");
        assertThat(reason)
                .contains("step 4")
                .contains(OnboardingStep.REALM_ROLE.description())
                .contains("keycloak unavailable");
        assertThat(OnboardingRules.compensationReason(OnboardingStep.GROUP_TREE, null)).contains("no cause reported");
    }

    @Test
    @DisplayName("§4 · Keycloak steps retry three times from PT2S, doubling; compensations never give up")
    void retryBudgets() {
        RetryOptions keycloak = OnboardingRules.keycloakOptions().getRetryOptions();
        assertThat(keycloak.getMaximumAttempts()).isEqualTo(3);
        assertThat(keycloak.getInitialInterval()).isEqualTo(Duration.ofSeconds(2));
        assertThat(keycloak.getBackoffCoefficient()).isEqualTo(2.0);

        assertThat(OnboardingRules.recordOptions().getRetryOptions().getMaximumAttempts()).isEqualTo(5);
        assertThat(OnboardingRules.patientOptions().getRetryOptions().getMaximumAttempts())
                .as("zero is unlimited: a compensation that stops retrying leaves a partial approval")
                .isZero();
    }
}
