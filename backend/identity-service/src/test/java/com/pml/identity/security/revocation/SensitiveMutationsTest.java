package com.pml.identity.security.revocation;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.pml.shared.security.revocation.FailClosedOnRevocation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/**
 * Which identity mutations fail closed when the caller's token cannot be checked.
 *
 * <p>Marking is by list, not by inference: a mutation that changes a role, a membership, a grant,
 * a payout destination or an account's credentials must refuse rather than assume the token is
 * still good. The allowlist below is the complete set of mutations that are allowed to run on an
 * unchecked token, so adding a mutation without deciding fails this test.</p>
 */
@Tag("L1")
@Tag("ET-IDN-003")
@DisplayName("Identity mutations that change access or money fail closed on revocation")
class SensitiveMutationsTest {

    /** Mutations that touch only the caller's own non-security data, or end a session. */
    private static final Set<String> MAY_RUN_ON_AN_UNCHECKED_TOKEN = Set.of(
            "logout",
            "updateMyProfile",
            "registerDevice", "unregisterDevice",
            "setEventReminder", "cancelEventReminder",
            "markNotificationRead", "markAllNotificationsRead", "deleteNotification",
            "updateNotificationPreferences",
            "requestContactAdd", "requestContactChange", "cancelContactChange",
            "requestContactRemoval", "requestPrimaryContact", "resendContactCode",
            "types");

    @Test
    @DisplayName("every mutation is either marked fail-closed or on the explicit allowlist")
    void everyMutationIsDecided() throws Exception {
        Set<String> unmarked = new TreeSet<>();
        Set<String> marked = new TreeSet<>();

        for (Class<?> type : dgsComponents()) {
            for (Method method : type.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(DgsMutation.class)) {
                    continue;
                }
                boolean failsClosed = AnnotatedElementUtils.hasAnnotation(method, FailClosedOnRevocation.class)
                        || AnnotatedElementUtils.hasAnnotation(type, FailClosedOnRevocation.class);
                (failsClosed ? marked : unmarked).add(method.getName());
            }
        }

        assertThat(unmarked)
                .as("a mutation with no @FailClosedOnRevocation must be one that may run on an unchecked token")
                .isSubsetOf(MAY_RUN_ON_AN_UNCHECKED_TOKEN);
        assertThat(marked).as("the allowlist names only unmarked mutations")
                .doesNotContainAnyElementsOf(MAY_RUN_ON_AN_UNCHECKED_TOKEN);
    }

    @Test
    @DisplayName("role, member, grant, ownership, payout and credential mutations are marked")
    void accessAndMoneyMutationsAreMarked() throws Exception {
        Set<String> mustBeMarked = Set.of(
                "updateMemberRole", "suspendMember", "reactivateMember", "removeMember",
                "grantEventAccess", "bulkGrantEventAccess", "updateEventAccess", "revokeEventAccess",
                "initiateOwnershipTransfer", "acceptOwnershipTransfer",
                "inviteTeamMember", "bulkInviteTeamMembers", "revokeInvitation", "acceptInvitation",
                "updatePayoutConfig", "setBankAccount", "setMobileMoneyAccount", "verifyPayoutAccount",
                "addUserRole", "removeUserRole", "setUserRoles", "suspendUser", "deleteUser",
                "confirmContactChange", "confirmContactRemoval", "setPrimaryContact",
                "approveVerificationDocument", "rejectVerificationDocument");

        Set<String> found = new TreeSet<>();
        for (Class<?> type : dgsComponents()) {
            Arrays.stream(type.getDeclaredMethods())
                    .filter(m -> m.isAnnotationPresent(DgsMutation.class))
                    .filter(m -> AnnotatedElementUtils.hasAnnotation(m, FailClosedOnRevocation.class)
                            || AnnotatedElementUtils.hasAnnotation(type, FailClosedOnRevocation.class))
                    .forEach(m -> found.add(m.getName()));
        }

        assertThat(found).containsAll(mustBeMarked);
    }

    private static Set<Class<?>> dgsComponents() throws ClassNotFoundException {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(DgsComponent.class));
        Set<Class<?>> types = new TreeSet<>((a, b) -> a.getName().compareTo(b.getName()));
        for (BeanDefinition definition : scanner.findCandidateComponents("com.pml.identity")) {
            types.add(Class.forName(definition.getBeanClassName()));
        }
        return types;
    }
}
