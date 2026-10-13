package com.pml.identity.account;

import com.pml.identity.domain.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Where a one-time password may and may not travel, enforced on the code that handles it: no log statement
 * of the creation or reset path names a password, and none of the objects that carry one prints it.
 */
@Tag("L1")
@Tag("ET-IDN-001")
@Tag("OWASP")
@DisplayName("the temporary password is never written to a log line or printed by the objects that carry it")
class TemporaryPasswordSecrecySourceTest {

    private static final Pattern COMMENTS = Pattern.compile("/\\*.*?\\*/|//[^\\n]*", Pattern.DOTALL);
    private static final Pattern LOG_STATEMENT = Pattern.compile("\\blog\\.(trace|debug|info|warn|error)\\((?:[^;]|\\n)*?\\);");

    private static final List<String> FILES = List.of(
            "account/StaffAccountSteps.java",
            "account/StaffPasswordReset.java",
            "account/StaffAccountSeal.java",
            "account/TemporaryPasswordGenerator.java",
            "workflow/staff/StaffAccountProcess.java",
            "workflow/staff/StaffAccountActivitiesImpl.java",
            "workflow/staff/StaffAccountWorkflowImpl.java",
            "infrastructure/keycloak/KeycloakService.java",
            "web/graphql/mutation/UserMutationResolver.java",
            "service/impl/UserServiceImpl.java");

    @Test
    @DisplayName("no log statement on the creation or reset path names a password")
    void noLogLineNamesAPassword() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (String relative : FILES) {
            String code = COMMENTS.matcher(Files.readString(
                    Path.of("src/main/java/com/pml/identity", relative))).replaceAll("");
            Matcher statement = LOG_STATEMENT.matcher(code);
            while (statement.find()) {
                if (statement.group().toLowerCase().contains("password")) {
                    offenders.add(relative + ": " + statement.group().replaceAll("\\s+", " "));
                }
            }
        }
        assertThat(offenders).isEmpty();
    }

    @Test
    @DisplayName("the answer, the request and the sealed details print without the password")
    void carriersDoNotPrintThePassword() {
        String secret = "Secret-Value-For-The-Test-7";
        User account = User.builder().id("account-1").build();

        assertThat(new StaffAccountPassword(account, secret).toString()).doesNotContain(secret).contains("issued");
        assertThat(new StaffAccountPassword(account, null).toString()).contains("none");
        assertThat(new StaffAccountRequest("a@example.org", "A", "B", null, com.pml.shared.constants.UserType.ADMIN, "root").toString())
                .doesNotContain("a@example.org");
        assertThat(new StaffAccountSeal.Details("a@example.org", "A", "B", null, null,
                com.pml.shared.constants.UserType.ADMIN, "root", secret).toString()).doesNotContain(secret).doesNotContain("a@example.org");
    }

    @Test
    @DisplayName("a workflow input and result are shaped so the password cannot be in them")
    void workflowShapesCarryNoPassword() {
        var components = new ArrayList<String>();
        for (Class<?> type : new Class<?>[]{com.pml.identity.workflow.staff.StaffAccountWorkflow.Request.class,
                com.pml.identity.workflow.staff.StaffAccountWorkflow.Created.class,
                com.pml.identity.workflow.staff.StaffAccountWorkflow.Progress.class,
                com.pml.identity.workflow.staff.StaffAccountActivities.Claimed.class,
                com.pml.identity.workflow.staff.StaffAccountActivities.KeycloakUser.class,
                com.pml.identity.workflow.staff.StaffAccountActivities.Link.class}) {
            for (var component : type.getRecordComponents()) {
                components.add(type.getSimpleName() + "." + component.getName());
            }
        }
        assertThat(components).noneMatch(name -> name.toLowerCase().contains("password"));
        assertThat(components).contains("Request.sealed");
    }
}
