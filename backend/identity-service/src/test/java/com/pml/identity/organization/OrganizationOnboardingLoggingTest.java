package com.pml.identity.organization;

import com.pml.identity.auth.delivery.LogWatch;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.domain.model.User;
import com.pml.identity.repository.OrganizationMemberRepository;
import com.pml.identity.repository.OrganizationRepository;
import com.pml.identity.repository.UserRepository;
import com.pml.identity.repository.VerificationDocumentRepository;
import com.pml.identity.service.OneOrganizationPerPerson;
import com.pml.identity.service.impl.OrganizationOnboardingServiceImpl;
import com.pml.shared.testing.TestClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Creating an organization for a person logs who it was for by id, never by email address or phone number. */
@Tag("L1")
@Tag("ET-IDN-001")
@DisplayName("the onboarding path logs no email address or phone number")
class OrganizationOnboardingLoggingTest {

    @Test
    @DisplayName("creating the default organization for a user writes neither the user's email nor number to any log")
    void createOrganizationLogsIdsOnly() {
        OrganizationRepository organizations = mock(OrganizationRepository.class, invocation ->
                Flux.class.isAssignableFrom(invocation.getMethod().getReturnType()) ? Flux.empty()
                        : Mono.class.isAssignableFrom(invocation.getMethod().getReturnType()) ? Mono.empty() : null);
        when(organizations.save(any(Organization.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(organizations.existsBySlug(Mockito.anyString())).thenReturn(Mono.just(false));
        OrganizationMemberRepository members = mock(OrganizationMemberRepository.class, invocation ->
                Mono.class.isAssignableFrom(invocation.getMethod().getReturnType()) ? Mono.empty() : null);
        when(members.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        OrganizationOnboardingServiceImpl service = new OrganizationOnboardingServiceImpl(organizations,
                mock(VerificationDocumentRepository.class), members, mock(OneOrganizationPerPerson.class),
                mock(UserRepository.class), null, TestClock.frozenAt(Instant.parse("2026-10-04T08:00:00Z")));
        User user = User.builder().id("user-77").email("private.person@example.zm").phoneNumber("+260971234567")
                .firstName("Chanda").lastName("Mwila").build();

        try (LogWatch logs = new LogWatch()) {
            try {
                service.createOrganization(user).block();
            } catch (RuntimeException ignored) {
                // the collaborators are stubs; what is under test is what was logged on the way
            }
            logs.assertNoneContains("private.person@example.zm", "+260971234567", "971234567");
        }
    }
}
