package com.pml.identity.workflow.onboarding;

import com.pml.identity.domain.enums.OnboardingStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("L1")
@Tag("ET-ORG-001")
@DisplayName("ET-ORG-001-R6 · the six approval steps, their markers and the membership step 2 owns")
class ApprovalSagaStepTest {

    @Test
    @DisplayName("§4 · six steps, in order, marked 1 to 6")
    void sixStepsInOrder() {
        OnboardingStep[] steps = OnboardingStep.values();
        assertThat(steps).hasSize(6);
        for (int index = 0; index < steps.length; index++) {
            assertThat(steps[index].marker()).as(steps[index].name()).isEqualTo(index + 1);
        }
        assertThat(steps).containsExactly(OnboardingStep.ACTIVATE, OnboardingStep.OWNER_MEMBERSHIP,
                OnboardingStep.USER_TYPE, OnboardingStep.REALM_ROLE, OnboardingStep.GROUP_TREE,
                OnboardingStep.APPROVED);
    }

    @Test
    @DisplayName("R6 · step 2's membership id is deterministic, so a retry and a compensation find the same row")
    void ownerMembershipIdIsDeterministic() {
        assertThat(OnboardingStep.ownerMembershipId("org-1")).isEqualTo(OnboardingStep.ownerMembershipId("org-1"));
        assertThat(OnboardingStep.ownerMembershipId("org-1")).isNotEqualTo(OnboardingStep.ownerMembershipId("org-2"));
    }
}
