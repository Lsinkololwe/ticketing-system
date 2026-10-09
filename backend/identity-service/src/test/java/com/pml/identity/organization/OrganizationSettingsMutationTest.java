package com.pml.identity.organization;

import com.pml.identity.domain.model.Organization;
import com.pml.identity.domain.valueobject.OrganizationSettings;
import com.pml.identity.service.OrganizationMemberService;
import com.pml.identity.service.OrganizationOnboardingService;
import com.pml.identity.service.OrganizationService;
import com.pml.identity.web.graphql.dto.organization.UpdateOrganizationSettingsInput;
import com.pml.identity.web.graphql.mutation.OrganizationMutationResolver;
import com.pml.identity.workflow.onboarding.OrganizerOnboardingProcess;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import com.pml.shared.security.Permission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Changing an organization's settings: every field reaches the stored settings, and the two money
 * switches need a permission only the owner holds.
 */
@Tag("L1")
@Tag("ET-ORG-003")
@DisplayName("Organization settings: every field binds, and only the owner flips the money switches")
class OrganizationSettingsMutationTest {

    private static final String ORG = "org-1";

    private OrganizationService organizations;
    private OrganizationMemberService members;
    private OrganizationMutationResolver resolver;

    @BeforeEach
    void setUp() {
        organizations = mock(OrganizationService.class);
        members = mock(OrganizationMemberService.class);
        resolver = new OrganizationMutationResolver(organizations, mock(OrganizationOnboardingService.class), members,
                mock(com.pml.identity.service.OrganizationAdminService.class), mock(OrganizerOnboardingProcess.class));
        Organization organization = new Organization();
        organization.setId(ORG);
        organization.setSettings(new OrganizationSettings());
        when(organizations.findById(ORG)).thenReturn(Mono.just(organization));
        when(organizations.updateSettings(eq(ORG), any())).thenAnswer(call -> Mono.just(organization));
        when(members.requirePermission("user-admin", ORG, Permission.ORGANIZATION_EDIT)).thenReturn(Mono.empty());
        when(members.requirePermission("user-admin", ORG, Permission.ORGANIZATION_BILLING))
                .thenReturn(Mono.error(new TranslatedRefusal(ErrorCode.ACTOR_NOT_PERMITTED, "requires organization:billing")));
        when(members.requirePermission("user-owner", ORG, Permission.ORGANIZATION_EDIT)).thenReturn(Mono.empty());
        when(members.requirePermission("user-owner", ORG, Permission.ORGANIZATION_BILLING)).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("An admin cannot switch on their own payout requests, and nothing is written")
    void adminCannotFlipMoneySwitches() {
        UpdateOrganizationSettingsInput input = input(null, true);

        assertThatThrownBy(() -> as("user-admin", resolver.updateOrganizationSettings(ORG, input)).block())
                .isInstanceOfSatisfying(DomainRefusal.class,
                        refused -> assertThat(refused.errorCode()).isEqualTo(ErrorCode.ACTOR_NOT_PERMITTED));
        verify(organizations, never()).updateSettings(any(), any());
    }

    @Test
    @DisplayName("An admin changes the other settings without the owner's permission")
    void adminChangesOtherSettings() {
        UpdateOrganizationSettingsInput input = new UpdateOrganizationSettingsInput("PRIVATE", true, null, null, 25,
                null, null, false, null, null);

        as("user-admin", resolver.updateOrganizationSettings(ORG, input)).block();

        verify(organizations).updateSettings(eq(ORG), any(OrganizationSettings.class));
        verify(members, never()).requirePermission("user-admin", ORG, Permission.ORGANIZATION_BILLING);
    }

    @Test
    @DisplayName("The owner flips both switches, and the stored settings carry them")
    void ownerFlipsSwitches() {
        as("user-owner", resolver.updateOrganizationSettings(ORG, input(true, true))).block();

        verify(organizations).updateSettings(eq(ORG), org.mockito.ArgumentMatchers.argThat(settings ->
                settings.isManagersCanViewFinancials() && settings.isAdminsCanRequestPayouts()));
    }

    @Test
    @DisplayName("Every field applies; a null field leaves the stored value alone")
    void appliedWritesEveryField() throws Exception {
        OrganizationSettings current = new OrganizationSettings();
        current.setNotifyOwnerOnEventCreated(true);
        UpdateOrganizationSettingsInput input = new UpdateOrganizationSettingsInput("UNLISTED", true, true, true, 40,
                true, true, false, null, false);

        var applied = OrganizationMutationResolver.class.getDeclaredMethod("applied", OrganizationSettings.class, UpdateOrganizationSettingsInput.class);
        applied.setAccessible(true);
        OrganizationSettings result = (OrganizationSettings) applied.invoke(null, current, input);

        assertThat(result.getDefaultEventVisibility()).isEqualTo("UNLISTED");
        assertThat(result.isRequireEventApproval()).isTrue();
        assertThat(result.isAllowMembersToInvite()).isTrue();
        assertThat(result.isInviteRequiresApproval()).isTrue();
        assertThat(result.getMaxTeamMembers()).isEqualTo(40);
        assertThat(result.isManagersCanViewFinancials()).isTrue();
        assertThat(result.isAdminsCanRequestPayouts()).isTrue();
        assertThat(result.isNotifyOwnerOnMemberJoin()).isFalse();
        assertThat(result.isNotifyOwnerOnEventCreated()).as("null leaves it").isTrue();
        assertThat(result.isNotifyOwnerOnPayoutRequest()).isFalse();
    }

    private static UpdateOrganizationSettingsInput input(Boolean managersCanViewFinancials, Boolean adminsCanRequestPayouts) {
        return new UpdateOrganizationSettingsInput(null, null, null, null, null,
                managersCanViewFinancials, adminsCanRequestPayouts, null, null, null);
    }

    private static <T> Mono<T> as(String userId, Mono<T> call) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(userId).build();
        return call.contextWrite(ReactiveSecurityContextHolder.withAuthentication(new JwtAuthenticationToken(jwt, java.util.List.of())));
    }
}
