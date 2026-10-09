package com.pml.identity.workflow;

import com.pml.identity.infrastructure.temporal.WorkflowIds;
import com.pml.identity.workflow.ensure.AccountEnsureActivities;
import com.pml.identity.workflow.mirror.GroupMirrorActivities;
import com.pml.identity.workflow.notify.NotificationActivities;
import com.pml.identity.workflow.notify.NotifyActivities;
import com.pml.identity.workflow.onboarding.OnboardingActivities;
import com.pml.identity.workflow.onboarding.OnboardingKeycloakActivities;
import com.pml.identity.workflow.ownership.OwnershipMirrorActivities;
import com.pml.identity.workflow.ownership.OwnershipTransferActivities;
import com.pml.identity.workflow.reminder.ReminderActivities;
import com.pml.identity.workflow.usersync.UserBackfillActivities;
import com.pml.identity.workflow.usersync.UserSyncActivities;
import io.temporal.activity.ActivityInterface;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("L1")
@Tag("ET-PLT-015")
@DisplayName("ET-PLT-015-R2 · identity's workflow ids and activity type names are unique and derived")
class IdentityWorkflowRegistryTest {

    private static final List<Class<?>> ACTIVITIES = List.of(
            OnboardingActivities.class, OnboardingKeycloakActivities.class,
            OwnershipTransferActivities.class, OwnershipMirrorActivities.class,
            UserSyncActivities.class, UserBackfillActivities.class, ReminderActivities.class,
            NotificationActivities.class, NotifyActivities.class,
            GroupMirrorActivities.class, AccountEnsureActivities.class);

    @Test
    @DisplayName("R2 · each workflow id is its §4 prefix and the business id, and a blank id is refused")
    void ids() {
        assertThat(WorkflowIds.organizerOnboarding("org-1")).isEqualTo("org-onboarding/org-1");
        assertThat(WorkflowIds.ownershipTransfer("t-1")).isEqualTo("ownership/t-1");
        assertThat(WorkflowIds.userSync("kc-1")).isEqualTo("user-sync/kc-1");
        assertThat(WorkflowIds.userBackfill()).isEqualTo("user-backfill");
        assertThat(WorkflowIds.reminder("r-1")).isEqualTo("reminder/r-1");
        assertThat(WorkflowIds.notification("team.accepted:inv-1")).isEqualTo("notify/team.accepted:inv-1");
        assertThatThrownBy(() -> WorkflowIds.reminder(" ")).isInstanceOf(IllegalArgumentException.class);
        String contactKey = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        assertThat(WorkflowIds.accountEnsure(contactKey)).isEqualTo("account-ensure/" + contactKey);
    }

    @Test
    @DisplayName("ET-IDN-004 · an account-ensure id is the keyed hash of a contact and nothing else")
    void accountEnsureIdIsAHash() {
        assertThatThrownBy(() -> WorkflowIds.accountEnsure("buyer@example.com")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WorkflowIds.accountEnsure("+260971234567")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WorkflowIds.accountEnsure("ABCDEF")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WorkflowIds.accountEnsure(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("every activity type name is unique across identity's workers")
    void activityTypeNamesAreUnique() {
        List<String> names = new ArrayList<>();
        for (Class<?> type : ACTIVITIES) {
            String prefix = type.getAnnotation(ActivityInterface.class).namePrefix();
            assertThat(prefix).as(type.getSimpleName() + " declares a name prefix").isNotBlank();
            for (Method method : type.getDeclaredMethods()) {
                names.add(prefix + Character.toUpperCase(method.getName().charAt(0)) + method.getName().substring(1));
            }
        }
        assertThat(names)
                .as("two activities with one type name on one worker fail registration at boot")
                .doesNotHaveDuplicates();
    }
}
